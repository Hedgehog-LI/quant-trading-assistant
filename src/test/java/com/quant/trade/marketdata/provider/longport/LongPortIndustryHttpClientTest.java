package com.quant.trade.marketdata.provider.longport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.config.LongPortProperties;
import com.quant.trade.marketdata.analysis.derived.RankAverageRanker;
import com.quant.trade.marketdata.analysis.derived.RelativeStrengthCalculator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongPortIndustryHttpClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void signsNumericRankRequestAndMapsOfficialResponse() throws IOException {
        server = startServer("/v1/quote/industry/rank", exchange -> {
            assertEquals("market=CN&indicator=0&sort_type=0&limit=3", exchange.getRequestURI().getRawQuery());
            assertSigned(exchange);
            respond(exchange, """
                    {"code":0,"data":{"items":[{"lists":[{
                      "name":"综合油气公司","counter_id":"BK/SH/IN40159","chg":"0.0240",
                      "leading_name":"中国石油","leading_counter_id":"ST/SH/601857",
                      "leading_chg":"0.0310","value_name":"涨幅","value_data":"2.40%"
                    }]}]}}
                    """);
        });

        LongPortIndustryHttpClient client = client();
        var item = client.getIndustryRank("CN", "leading-gainer", "single", 3).get(0);

        assertEquals("BK/SH/IN40159", item.counterId());
        assertEquals("SH.601857", item.leadingSymbol());
        assertEquals("综合油气公司", item.name());
        assertNotNull(item.sourceQuoteTime());
    }

    @Test
    void unitEndToEnd0_0240KeepsProviderDecimalRatioThroughCalculation() throws IOException {
        server = startServer("/v1/quote/industry/rank", exchange -> respond(exchange, """
                {"code":0,"data":{"items":[{"lists":[{
                  "name":"综合油气公司","counter_id":"BK/SH/IN40159","chg":"0.0240",
                  "value_name":"涨幅","value_data":"2.40%"
                }]}]}}
                """));
        var providerItem = client().getIndustryRank("CN", "leading-gainer", "single", 5).get(0);
        List<RelativeStrengthCalculator.DailyReturns> days = new java.util.ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            Map<Long, BigDecimal> values = new LinkedHashMap<>();
            values.put(1L, day == 1 ? providerItem.changeRate() : BigDecimal.ZERO);
            values.put(2L, BigDecimal.ZERO);
            values.put(3L, BigDecimal.ZERO);
            values.put(4L, BigDecimal.ZERO);
            values.put(5L, BigDecimal.ZERO);
            days.add(new RelativeStrengthCalculator.DailyReturns(LocalDate.of(2026, 1, day), values));
        }

        var result = new RelativeStrengthCalculator(new RankAverageRanker()).calculate(days, 5, 5)
                .stream().filter(row -> row.sectorIdentityId().equals(1L)).findFirst().orElseThrow();

        assertEquals(0, providerItem.changeRate().compareTo(result.sectorReturn()));
        String formatted = providerItem.changeRate().multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
        assertEquals(providerItem.indicatorValue(), formatted);
    }

    @Test
    void signsPeerRequestAndMapsHierarchy() throws IOException {
        server = startServer("/v1/quote/industries/peers", exchange -> {
            assertEquals("type=1&market=CN&industry_id=&counter_id=BK%2FSH%2FIN40159",
                    exchange.getRequestURI().getRawQuery());
            assertSigned(exchange);
            respond(exchange, """
                    {"code":0,"data":{"top":{"name":"能源"},"chain":{
                      "market":"CN","name":"综合油气公司","counter_id":"BK/SH/IN40159",
                      "stock_num":3,"chg":"0.0240","ytd_chg":"0.1150","next":[{"name":"子行业"}]
                    }}}
                    """);
        });

        var peer = client().getIndustryPeers("CN", "BK/SH/IN40159");

        assertEquals("能源", peer.topName());
        assertEquals(3, peer.stockCount());
        assertTrue(peer.hasChildren());
    }

    @Test
    void mapsIndustryConstituentsAndCapitalFields() throws IOException {
        server = startServer("/v1/quote/index-constituents", exchange -> {
            assertEquals("counter_id=BK%2FSH%2FIN40159", exchange.getRequestURI().getRawQuery());
            assertSigned(exchange);
            respond(exchange, """
                    {"code":0,"data":{"rise_num":1,"fall_num":0,"flat_num":0,"stocks":[{
                      "counter_id":"ST/SH/601857","name":"中国石油","last_done":"10.29",
                      "prev_close":"9.99","chg":"0.0300","inflow":"281223809",
                      "balance":"2693440762","amount":"2650040","total_shares":"183020977818",
                      "circulating_shares":"161922077818","tags":["领涨龙头","成交龙头"],
                      "trade_status":108,"delay":false
                    }]}}
                    """);
        });

        var result = client().getIndustryConstituents("BK/SH/IN40159");

        assertEquals(1, result.riseCount());
        assertEquals("SH.601857", result.stocks().get(0).canonicalSymbol());
        assertEquals("领涨龙头,成交龙头", result.stocks().get(0).tags());
    }

    @Test
    void mapsInvalidTokenToAuthenticationFailureInsteadOfPermissionDenied() throws IOException {
        server = startServer("/v1/quote/industry/rank", exchange ->
                respond(exchange, 401, "{\"code\":401,\"message\":\"token invalid\"}"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client().getIndustryRank("CN", "leading-gainer", "single", 3));

        assertEquals(ErrorCodeEnum.MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("鉴权失败"));
    }

    @Test
    void keepsEntitlementFailureSeparateFromInvalidToken() throws IOException {
        server = startServer("/v1/quote/industry/rank", exchange ->
                respond(exchange, 403, "{\"code\":301604,\"message\":\"permission denied\"}"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client().getIndustryRank("CN", "leading-gainer", "single", 3));

        assertEquals(ErrorCodeEnum.MARKET_DATA_PROVIDER_PERMISSION_DENIED, exception.getErrorCode());
    }

    private LongPortIndustryHttpClient client() {
        LongPortProperties properties = new LongPortProperties();
        properties.setEnabled(true);
        properties.setAppKey("test-key");
        properties.setAppSecret("test-secret");
        properties.setAccessToken("test-token");
        properties.setHttpUrl("http://localhost:" + server.getAddress().getPort());
        return new LongPortIndustryHttpClient(properties, new ObjectMapper(), HttpClient.newHttpClient(),
                Clock.fixed(Instant.ofEpochSecond(1_721_234_567L), ZoneOffset.UTC));
    }

    private HttpServer startServer(String path, ExchangeHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext(path, exchange -> handler.handle(exchange));
        httpServer.start();
        return httpServer;
    }

    private void assertSigned(HttpExchange exchange) {
        assertEquals("test-key", exchange.getRequestHeaders().getFirst("X-Api-Key"));
        assertEquals("test-token", exchange.getRequestHeaders().getFirst("Authorization"));
        assertEquals("1721234567", exchange.getRequestHeaders().getFirst("X-Timestamp"));
        String signature = exchange.getRequestHeaders().getFirst("X-Api-Signature");
        assertNotNull(signature);
        assertTrue(signature.startsWith("HMAC-SHA256 SignedHeaders=authorization;x-api-key;x-timestamp"));
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, 200, body);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Date", "Thu, 1 Jan 2026 00:00:00 GMT");
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
