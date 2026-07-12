package com.quant.trade.marketdata.provider;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.config.LongPortProperties;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderHealthStatus;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortDailyBar;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** LongPort provider 领域转换测试，不请求外部 LongPort。 */
class LongPortMarketDataProviderTest {

    private final LongPortProperties properties = enabledProperties();
    private final LongPortSymbolMapper symbolMapper = new LongPortSymbolMapper();
    private final StubLongPortQuoteClient client = new StubLongPortQuoteClient();
    private final LongPortMarketDataProvider provider =
            new LongPortMarketDataProvider(properties, symbolMapper, client);

    @Test
    void latestQuoteMapsLongPortSymbolBackToCanonical() {
        client.quotes = List.of(new LongPortQuoteClient.LongPortQuote(
                "600519.SH",
                LocalDateTime.of(2026, 7, 11, 15, 0),
                new BigDecimal("1680.12"),
                new BigDecimal("1670.00"),
                new BigDecimal("1690.00"),
                new BigDecimal("1660.00"),
                new BigDecimal("1668.00"),
                1200L,
                new BigDecimal("2016144.00"),
                "Normal"));

        List<MarketDataProvider.ProviderQuote> quotes = provider.getLatestQuotes(List.of("SH.600519"));

        assertEquals(List.of("600519.SH"), client.lastQuoteSymbols);
        assertEquals(1, quotes.size());
        assertEquals("SH.600519", quotes.get(0).canonicalSymbol());
        assertEquals(new BigDecimal("1680.12"), quotes.get(0).currentPrice());
        assertEquals("Normal", quotes.get(0).tradeStatus());
    }

    @Test
    void dailyBarsMapNoneAdjustType() {
        client.dailyBars = List.of(new LongPortQuoteClient.LongPortDailyBar(
                "600519.SH",
                LocalDate.of(2026, 7, 10),
                "NONE",
                new BigDecimal("1670.00"),
                new BigDecimal("1690.00"),
                new BigDecimal("1660.00"),
                new BigDecimal("1680.12"),
                1200L,
                new BigDecimal("2016144.00")));

        List<MarketDataProvider.ProviderDailyBar> bars = provider.getDailyBars(
                "SH.600519", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 11), "NONE");

        assertEquals("600519.SH", client.lastDailySymbol);
        assertEquals("NoAdjust", client.lastSdkAdjustType);
        assertEquals(1, bars.size());
        assertEquals("SH.600519", bars.get(0).canonicalSymbol());
        assertEquals("NONE", bars.get(0).adjustType());
    }

    @Test
    void dailyBarsRejectUnsupportedBackwardAdjust() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                provider.getDailyBars("SH.600519", LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 11), "HF"));

        assertTrue(exception.getMessage().contains("HF 暂不支持"));
    }

    @Test
    void invalidCanonicalSymbolRejectedBeforeCallingClient() {
        assertThrows(BusinessException.class, () -> provider.getLatestQuotes(List.of("HK.00700")));
        assertNull(client.lastQuoteSymbols);
    }

    private LongPortProperties enabledProperties() {
        LongPortProperties p = new LongPortProperties();
        p.setEnabled(true);
        p.setAppKey("app-key");
        p.setAppSecret("app-secret");
        p.setAccessToken("access-token");
        return p;
    }

    private static class StubLongPortQuoteClient implements LongPortQuoteClient {
        private List<String> lastQuoteSymbols;
        private String lastDailySymbol;
        private String lastSdkAdjustType;
        private List<LongPortQuote> quotes = List.of();
        private List<LongPortDailyBar> dailyBars = List.of();

        @Override public boolean isSdkAvailable() { return true; }
        @Override public boolean hasCredentials() { return true; }
        @Override public String unavailableReason() { return null; }
        @Override public ProviderHealthStatus healthCheck() {
            return new ProviderHealthStatus(true, true, null, LocalDateTime.now());
        }

        @Override
        public List<LongPortQuote> getLatestQuotes(List<String> longPortSymbols) {
            lastQuoteSymbols = longPortSymbols;
            return quotes;
        }

        @Override
        public List<LongPortDailyBar> getDailyBars(String longPortSymbol, LocalDate startDate,
                                                   LocalDate endDate, String sdkAdjustTypeName,
                                                   String systemAdjustType) {
            lastDailySymbol = longPortSymbol;
            lastSdkAdjustType = sdkAdjustTypeName;
            return dailyBars;
        }
    }
}
