package com.quant.trade.marketdata.provider.longport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.config.LongPortProperties;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Longbridge 行业接口的签名 HTTPS 实现。
 *
 * <p>官方 Java 4.3.3 native 未导出行业 JNI 方法，本客户端按同版本官方 httpclient
 * 的 HMAC-SHA256 规则调用只读行业端点，不依赖 vendor native。</p>
 */
@Slf4j
public class LongPortIndustryHttpClient implements LongPortSectorClient {

    private static final String DEFAULT_HTTP_URL = "https://openapi.longbridge.com";
    private static final String RANK_PATH = "/v1/quote/industry/rank";
    private static final String PEERS_PATH = "/v1/quote/industries/peers";
    private static final String CONSTITUENTS_PATH = "/v1/quote/index-constituents";
    private static final String SIGNED_HEADERS = "authorization;x-api-key;x-timestamp";
    private static final Map<String, String> INDICATOR_CODES = Map.of(
            "leading-gainer", "0", "today-trend", "1", "popularity", "2", "market-cap", "3",
            "revenue", "4", "revenue-growth", "5", "net-profit", "6", "net-profit-growth", "7");
    private static final Map<String, String> SORT_CODES = Map.of("single", "0", "multi", "1");

    private final LongPortProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;

    public LongPortIndustryHttpClient(LongPortProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds())).build(), Clock.systemUTC());
    }

    LongPortIndustryHttpClient(LongPortProperties properties, ObjectMapper objectMapper,
                               HttpClient httpClient, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    @Override
    public boolean isConfigured() {
        return properties.isEnabled() && properties.hasCredentials();
    }

    @Override
    public List<LongPortIndustryRank> getIndustryRank(String market, String indicator,
                                                       String sortType, int limit) {
        String indicatorCode = requiredCode(INDICATOR_CODES, indicator, "行业排行指标");
        String sortCode = requiredCode(SORT_CODES, sortType, "行业排行排序方式");
        String query = "market=" + encode(market) + "&indicator=" + indicatorCode
                + "&sort_type=" + sortCode + "&limit=" + limit;
        JsonNode data = executeGet(RANK_PATH, query);
        List<LongPortIndustryRank> result = new ArrayList<>();
        for (JsonNode group : data.path("items")) {
            for (JsonNode item : group.path("lists")) {
                result.add(new LongPortIndustryRank(text(item, "name"), text(item, "counter_id"),
                        decimal(item, "chg"), text(item, "leading_name"), leadingSymbol(item, market),
                        decimal(item, "leading_chg"), text(item, "value_name"), text(item, "value_data")));
            }
        }
        return result;
    }

    @Override
    public LongPortIndustryPeer getIndustryPeers(String market, String counterId) {
        String query = "type=1&market=" + encode(market) + "&industry_id=&counter_id=" + encode(counterId);
        JsonNode data = executeGet(PEERS_PATH, query);
        JsonNode chain = data.path("chain");
        if (chain.isMissingNode() || chain.isNull()) {
            return null;
        }
        return new LongPortIndustryPeer(text(chain, "market", market), text(data.path("top"), "name"),
                text(chain, "name"), text(chain, "counter_id"), integer(chain, "stock_num"),
                decimal(chain, "chg"), decimal(chain, "ytd_chg"), chain.path("next").size() > 0);
    }

    @Override
    public LongPortIndustryConstituents getIndustryConstituents(String counterId) {
        JsonNode data = executeGet(CONSTITUENTS_PATH, "counter_id=" + encode(counterId));
        List<LongPortIndustryConstituent> stocks = new ArrayList<>();
        for (JsonNode item : data.path("stocks")) {
            stocks.add(new LongPortIndustryConstituent(counterSymbol(item), text(item, "name"),
                    decimal(item, "last_done"), decimal(item, "prev_close"), decimal(item, "chg"),
                    decimal(item, "inflow"), decimal(item, "balance"), decimal(item, "amount"),
                    decimal(item, "total_shares"), decimal(item, "circulating_shares"), tags(item),
                    integer(item, "trade_status"), item.path("delay").asBoolean(false)));
        }
        return new LongPortIndustryConstituents(integer(data, "rise_num"), integer(data, "fall_num"),
                integer(data, "flat_num"), stocks);
    }

    private JsonNode executeGet(String path, String query) {
        ensureConfigured();
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String appKey = properties.effectiveAppKey();
        String accessToken = properties.effectiveAccessToken();
        String signature = signature(path, query, timestamp, appKey, accessToken,
                properties.effectiveAppSecret());
        URI uri = URI.create(baseUrl() + path + "?" + query);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("X-Api-Key", appKey)
                .header("Authorization", accessToken)
                .header("X-Timestamp", timestamp)
                .header("X-Api-Signature", signature)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "qta-market-sector/1.0")
                .GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PROVIDER_TIMEOUT, "Longbridge 行业请求被中断");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodeEnum.MARKET_SECTOR_PROVIDER_UNAVAILABLE,
                    "Longbridge 行业请求失败: " + exception.getClass().getSimpleName());
        }
    }

    private JsonNode parseResponse(int statusCode, String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        int code = root.path("code").asInt(statusCode);
        String message = root.path("message").asText("unknown error");
        if (statusCode == 429 || code == 301606) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PROVIDER_RATE_LIMITED, "Longbridge 行业接口限流");
        }
        if (statusCode == 401 || isAuthenticationFailure(message)) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED,
                    MarketDataConstants.LONGPORT_AUTHENTICATION_FAILED_MESSAGE);
        }
        if (statusCode == 403 || code == 301604) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PROVIDER_PERMISSION_DENIED,
                    "Longbridge 行业接口无权限");
        }
        if (statusCode < 200 || statusCode >= 300 || code != 0) {
            log.warn("Longbridge industry API failed: httpStatus={}, code={}, message={}",
                    statusCode, code, message);
            throw new BusinessException(ErrorCodeEnum.MARKET_SECTOR_PROVIDER_UNAVAILABLE,
                    "Longbridge 行业接口失败: code=" + code + ", message=" + message);
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_EMPTY_RESULT, "Longbridge 行业接口未返回数据");
        }
        return data;
    }

    private boolean isAuthenticationFailure(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        return lower.contains("token invalid") || lower.contains("invalid token")
                || lower.contains("token expired") || lower.contains("expired token")
                || lower.contains("authentication failed") || lower.contains("unauthorized");
    }

    private String signature(String path, String query, String timestamp, String appKey,
                             String accessToken, String appSecret) {
        String signedValues = "authorization:" + accessToken + "\n"
                + "x-api-key:" + appKey + "\n"
                + "x-timestamp:" + timestamp + "\n";
        String canonical = "GET|" + path + "|" + query + "|" + signedValues + "|" + SIGNED_HEADERS + "|";
        String stringToSign = "HMAC-SHA256|" + digest("SHA-1", canonical.getBytes(StandardCharsets.UTF_8));
        String hmac = hmacSha256(stringToSign, appSecret);
        return "HMAC-SHA256 SignedHeaders=" + SIGNED_HEADERS + ", Signature=" + hmac;
    }

    private String digest(String algorithm, byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(value));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "Longbridge 签名摘要初始化失败");
        }
    }

    private String hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "Longbridge HMAC 初始化失败");
        }
    }

    private String baseUrl() {
        String configured = properties.getHttpUrl();
        String value = configured == null || configured.isBlank() ? DEFAULT_HTTP_URL : configured.trim();
        return value.replaceAll("/+$", "");
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCodeEnum.MARKET_SECTOR_PROVIDER_UNAVAILABLE,
                    "Longbridge 行业数据源未配置");
        }
    }

    private String requiredCode(Map<String, String> mappings, String value, String label) {
        String code = mappings.get(value);
        if (code == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, label + "不合法");
        }
        return code;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String leadingSymbol(JsonNode item, String market) {
        String counterId = text(item, "leading_counter_id");
        String[] parts = counterId == null ? new String[0] : counterId.split("/");
        if (parts.length == 3) {
            return switch (parts[1]) {
                case "SH", "SZ", "BJ" -> parts[1] + "." + parts[2];
                case "HK" -> "HK." + parts[2];
                case "US" -> "US." + parts[2];
                default -> null;
            };
        }
        String ticker = text(item, "leading_ticker");
        return ticker == null ? null : switch (market) {
            case "HK" -> "HK." + ticker;
            case "US" -> "US." + ticker;
            default -> ticker;
        };
    }

    private String counterSymbol(JsonNode item) {
        String counterId = text(item, "counter_id");
        String[] parts = counterId == null ? new String[0] : counterId.split("/");
        if (parts.length != 3) {
            return null;
        }
        return switch (parts[1]) {
            case "SH", "SZ", "BJ" -> parts[1] + "." + parts[2];
            case "HK" -> "HK." + parts[2];
            case "US" -> "US." + parts[2];
            default -> null;
        };
    }

    private String tags(JsonNode item) {
        List<String> values = new ArrayList<>();
        item.path("tags").forEach(tag -> values.add(tag.asText()));
        return String.join(",", values);
    }

    private String text(JsonNode node, String field) {
        return text(node, field, null);
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : new BigDecimal(value);
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.intValue() : null;
    }
}
