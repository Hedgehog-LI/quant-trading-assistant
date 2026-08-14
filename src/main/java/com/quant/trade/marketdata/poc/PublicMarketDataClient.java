package com.quant.trade.marketdata.poc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MR-0 PoC 公共无凭据行情客户端（只读；契约 D1：TENCENT_PUBLIC 日 K + SINA_PUBLIC 证券池/行业/资金流）。
 * 无凭据、无密钥常量、无重试框架；连接与读超时均 10s。失败抛 IllegalStateException，信息只携带
 * ≤200 字符截断片段（禁止打印超长响应原文）。端点为非官方公共端点（授权风险见 Provider 矩阵），
 * 不构成 MR-1 生产选型决策。httpGet 为 protected 供测试打桩，单测零联网。
 */
@Component
public class PublicMarketDataClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final Charset GBK = Charset.forName("GBK");
    private static final int SNIPPET_MAX = 200;
    private static final Pattern CATALOG_JSON =
            Pattern.compile("S_Finance_bankuai_sinaindustry\\s*=\\s*(\\{.*\\})");
    private static final String SINA_NODE_URL =
            "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** 新浪证券池/行业成分条目（原始单位：市值万元、换手率百分数）。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UniverseEntry {
        @JsonProperty("symbol") private String sinaSymbol;
        private String canonicalSymbol, code, name, mktcap, nmc, turnoverratio;
        private String market;
        private BigDecimal totalMarketCapTenThousand, circulatingMarketCapTenThousand, turnoverRatioPercent;
    }

    /** 腾讯日 K 条目（原始单位：量手、额万元、换手率百分数；价格原值 NONE 复权）。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyBarEntry {
        private LocalDate tradeDate;
        private BigDecimal open, close, high, low, volumeHands, turnoverPercent, amountTenThousand;
    }

    /** 新浪个股日资金流条目（netamount/r0_net/cate_na 源单位已是元）。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MoneyFlowEntry {
        @JsonProperty("opendate") private LocalDate tradeDate;
        @JsonProperty("netamount") private BigDecimal netAmount;
        @JsonProperty("ratioamount") private BigDecimal ratioAmount;
        @JsonProperty("r0_net") private BigDecimal r0Net;
        @JsonProperty("cate_na") private BigDecimal cateNa;
    }

    /** 分页拉取新浪证券池快照（node=hs_a 全 A，symbol 升序）。 */
    public List<UniverseEntry> fetchUniversePage(int page, int num) {
        return parseNodeEntries(httpGet(SINA_NODE_URL + "?page=" + page + "&num=" + num
                + "&sort=symbol&asc=1&node=hs_a", StandardCharsets.UTF_8));
    }

    /** 拉取新浪行业目录（GBK JS 赋值片段），返回 code→name（保持抓取顺序）。 */
    public Map<String, String> fetchIndustryCatalog() {
        return parseCatalog(httpGet("https://vip.stock.finance.sina.com.cn/q/view/newSinaHy.php", GBK));
    }

    /** 分页拉取某新浪行业 node 的成分（原始 sina symbol）。 */
    public List<String> fetchIndustryMembers(String industryCode, int page, int num) {
        List<String> symbols = new ArrayList<>();
        parseNodeEntries(httpGet(SINA_NODE_URL + "?page=" + page + "&num=" + num
                + "&sort=symbol&asc=1&node=" + industryCode, StandardCharsets.UTF_8))
                .forEach(entry -> symbols.add(entry.getSinaSymbol()));
        return symbols;
    }

    /** 拉取腾讯不复权日 K（结尾空 fq 段=NONE，契约 D7）。tencentCode 形如 sh600519 / sh000001(指数)。 */
    public List<DailyBarEntry> fetchDailyBars(String tencentCode, LocalDate start, LocalDate end) {
        String body = httpGet("https://proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get"
                + "?param=" + tencentCode + ",day," + start + "," + end + ",640,", StandardCharsets.UTF_8);
        JsonNode day = readTree(body).path("data").path(tencentCode).path("day");
        if (!day.isArray()) {
            throw new IllegalStateException("腾讯日 K 缺少 data." + tencentCode + ".day: " + snippet(body));
        }
        List<DailyBarEntry> bars = new ArrayList<>();
        for (JsonNode row : day) {
            bars.add(DailyBarEntry.builder().tradeDate(LocalDate.parse(row.path(0).asText()))
                    .open(decimal(row.path(1))).close(decimal(row.path(2)))
                    .high(decimal(row.path(3))).low(decimal(row.path(4)))
                    .volumeHands(decimal(row.path(5))).turnoverPercent(decimal(row.path(7)))
                    .amountTenThousand(decimal(row.path(8))).build());
        }
        return bars;
    }

    /** 拉取新浪个股日资金流全部历史（实测单股 3991 条），窗口过滤由调用方完成。 */
    public List<MoneyFlowEntry> fetchMoneyFlow(String sinaCode) {
        return parseList(httpGet("https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/"
                + "MoneyFlow.ssl_qsfx_zjlrqs?daima=" + sinaCode, StandardCharsets.UTF_8),
                new TypeReference<List<MoneyFlowEntry>>() { });
    }

    /** HTTP GET（无凭据、无重试）。protected 供测试以 fixture 打桩。 */
    protected String httpGet(String url, Charset charset) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT).header("User-Agent", USER_AGENT).GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("公共源响应非 200: status=" + response.statusCode() + " url=" + url);
            }
            return new String(response.body(), charset);
        } catch (IOException exception) {
            throw new IllegalStateException("公共源请求失败: " + exception.getMessage() + " url=" + url, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("公共源请求被中断: url=" + url, exception);
        }
    }

    /** 解析 getHQNodeData 页（universe 与行业成分同构），并补齐 canonical/market/换算前置字段。 */
    List<UniverseEntry> parseNodeEntries(String body) {
        List<UniverseEntry> entries = parseList(body, new TypeReference<List<UniverseEntry>>() { });
        for (UniverseEntry entry : entries) {
            entry.setCanonicalSymbol(toCanonical(entry.getSinaSymbol()));
            entry.setMarket(entry.getSinaSymbol().substring(0, 2).toUpperCase(Locale.ROOT));
            entry.setTotalMarketCapTenThousand(decimalOf(entry.getMktcap()));
            entry.setCirculatingMarketCapTenThousand(decimalOf(entry.getNmc()));
            entry.setTurnoverRatioPercent(decimalOf(entry.getTurnoverratio()));
        }
        return entries;
    }

    /** 解析新浪行业目录（GBK JS 片段；值形如 "new_blhy,玻璃行业,19,..."）。 */
    Map<String, String> parseCatalog(String body) {
        Matcher matcher = CATALOG_JSON.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("新浪行业目录响应结构无法识别: " + snippet(body));
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = readTree(matcher.group(1)).fields(); it.hasNext();) {
            String[] parts = it.next().getValue().asText().split(",");
            if (parts.length >= 2) {
                result.put(parts[0].trim(), parts[1].trim());
            }
        }
        return result;
    }

    /** sina/tencent 同型前缀映射（项目约定）：sh600519→SH.600519、bj920000→BJ.920000。 */
    static String toCanonical(String prefixedSymbol) {
        if (prefixedSymbol == null || prefixedSymbol.length() <= 2) {
            throw new IllegalArgumentException("公共源代码格式不合法: " + prefixedSymbol);
        }
        return CanonicalSymbolUtils.normalize(prefixedSymbol.substring(0, 2).toUpperCase(Locale.ROOT)
                + "." + prefixedSymbol.substring(2));
    }

    private <T> List<T> parseList(String body, TypeReference<List<T>> type) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            List<T> values = objectMapper.readValue(body, type);
            return values == null ? List.of() : values;
        } catch (IOException exception) {
            throw new IllegalStateException("公共源响应解析失败: " + snippet(body), exception);
        }
    }

    private JsonNode readTree(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node == null || node.isNull() || node.isMissingNode()) {
                throw new IllegalStateException("公共源响应为空: " + snippet(body));
            }
            return node;
        } catch (IOException exception) {
            throw new IllegalStateException("公共源响应解析失败: " + snippet(body), exception);
        }
    }

    private static BigDecimal decimal(JsonNode value) {
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return new BigDecimal(value.asText().trim());
    }

    private static BigDecimal decimalOf(String text) {
        return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
    }

    private static String snippet(String body) {
        String text = body == null ? "" : body;
        return text.length() <= SNIPPET_MAX ? text : text.substring(0, SNIPPET_MAX) + "...(截断)";
    }
}
