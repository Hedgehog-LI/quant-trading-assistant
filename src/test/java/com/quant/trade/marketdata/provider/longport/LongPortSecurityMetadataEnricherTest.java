package com.quant.trade.marketdata.provider.longport;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderSecurityInfo;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichReason;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichRequest;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LongPort 元数据补全实现单测（不启动 Spring）。
 * <p>
 * 覆盖：provider 字符串 trim/空白→null 规范化、provider null → PROVIDER_NOT_FOUND、
 * 证券身份一致性（provider 返回证券与请求不一致 → SECURITY_VERIFICATION_FAILED）、
 * provider 业务异常透传。
 */
class LongPortSecurityMetadataEnricherTest {

    private static final String SYMBOL = "SH.600519";

    private static SecurityMetadataEnricher enricher(MarketDataProvider provider) {
        return new LongPortSecurityMetadataEnricher(provider);
    }

    @Test
    void providerStringsAreTrimmedAndBlanksBecomeNull() {
        MarketDataProvider provider = new StubProvider(new ProviderSecurityInfo(
                SYMBOL, "600519.SH", " 贵州茅台 ", "貴州茅台", " Kweichow Moutai ",
                "SSE", "CNY", 100));
        EnrichResult result = enricher(provider).enrich(new EnrichRequest(SYMBOL, false));

        assertTrue(result.enriched());
        assertEquals(EnrichReason.OK, result.reason());
        assertEquals("贵州茅台", result.fields().nameCn(), "trim 后写入");
        assertEquals("Kweichow Moutai", result.fields().nameEn(), "trim 后写入");
        assertEquals(100, result.lotSize());
    }

    @Test
    void blankWhitespaceFieldsNormalizedToNull() {
        MarketDataProvider provider = new StubProvider(new ProviderSecurityInfo(
                SYMBOL, "600519.SH", "   ", "", null, " ", "", 100));
        EnrichResult result = enricher(provider).enrich(new EnrichRequest(SYMBOL, false));

        assertTrue(result.enriched());
        assertNull(result.fields().nameCn(), "纯空白视为 null");
        assertNull(result.fields().nameHk(), "空字符串视为 null");
        assertNull(result.fields().nameEn(), "null 保持 null");
        assertNull(result.fields().exchange(), "纯空白视为 null");
        assertNull(result.fields().currency(), "空字符串视为 null");
    }

    @Test
    void providerNullReturnsProviderNotFound() {
        MarketDataProvider provider = new StubProvider(null);
        EnrichResult result = enricher(provider).enrich(new EnrichRequest(SYMBOL, false));

        assertFalse(result.enriched());
        assertEquals(EnrichReason.PROVIDER_NOT_FOUND, result.reason(), "不能用 OK 掩盖 provider 无数据");
        assertNull(result.fields());
        assertNull(result.lotSize());
    }

    @Test
    void identityMismatchRejectsReturn() {
        // provider 返回的 canonical 与请求不一致（不应把其他证券的静态信息写回当前证券）。
        MarketDataProvider provider = new StubProvider(new ProviderSecurityInfo(
                "HK.00700", "700.HK", "腾讯控股", "騰訊控股", "Tencent",
                "SEHK", "HKD", 100));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> enricher(provider).enrich(new EnrichRequest(SYMBOL, true)));
        assertEquals(ErrorCodeEnum.SECURITY_VERIFICATION_FAILED, exception.getErrorCode());
    }

    @Test
    void identityMatchSucceeds() {
        MarketDataProvider provider = new StubProvider(new ProviderSecurityInfo(
                SYMBOL, "600519.SH", "贵州茅台", "貴州茅台", "Kweichow Moutai",
                "SSE", "CNY", 100));
        EnrichResult result = enricher(provider).enrich(new EnrichRequest(SYMBOL, true));
        assertTrue(result.enriched());
        assertEquals(SYMBOL, result.canonicalSymbol());
    }

    @Test
    void providerBusinessExceptionIsPassedThrough() {
        MarketDataProvider provider = new MarketDataProvider() {
            @Override
            public String getProviderCode() {
                return "LONGPORT";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public ProviderSecurityInfo getSecurityStaticInfo(String canonicalSymbol) {
                throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED,
                        "Longbridge 鉴权失败");
            }

            @Override
            public com.quant.trade.marketdata.provider.MarketDataProvider.ProviderHealthStatus healthCheck() {
                return null;
            }

            @Override
            public java.util.List<com.quant.trade.marketdata.provider.MarketDataProvider.ProviderQuote> getLatestQuotes(
                    java.util.List<String> canonicalSymbols) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<com.quant.trade.marketdata.provider.MarketDataProvider.ProviderDailyBar> getDailyBars(
                    String canonicalSymbol, java.time.LocalDate startDate, java.time.LocalDate endDate,
                    String adjustType) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<com.quant.trade.marketdata.provider.MarketDataProvider.ProviderMinuteBar> getMinuteBars(
                    String canonicalSymbol, java.time.LocalDate startDate, java.time.LocalDate endDate,
                    String intervalType, String adjustType) {
                return java.util.List.of();
            }
        };
        BusinessException exception = assertThrows(BusinessException.class,
                () -> enricher(provider).enrich(new EnrichRequest(SYMBOL, false)));
        assertEquals(ErrorCodeEnum.MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED, exception.getErrorCode());
    }

    /** 最小 stub provider：仅 Static Info 可控。 */
    private static final class StubProvider implements MarketDataProvider {
        private final ProviderSecurityInfo info;

        StubProvider(ProviderSecurityInfo info) {
            this.info = info;
        }

        @Override
        public String getProviderCode() {
            return "LONGPORT";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public ProviderSecurityInfo getSecurityStaticInfo(String canonicalSymbol) {
            return info;
        }

        @Override
        public com.quant.trade.marketdata.provider.MarketDataProvider.ProviderHealthStatus healthCheck() {
            return null;
        }

        @Override
        public java.util.List<com.quant.trade.marketdata.provider.MarketDataProvider.ProviderQuote> getLatestQuotes(
                java.util.List<String> canonicalSymbols) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.quant.trade.marketdata.provider.MarketDataProvider.ProviderDailyBar> getDailyBars(
                String canonicalSymbol, java.time.LocalDate startDate, java.time.LocalDate endDate,
                String adjustType) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.quant.trade.marketdata.provider.MarketDataProvider.ProviderMinuteBar> getMinuteBars(
                String canonicalSymbol, java.time.LocalDate startDate, java.time.LocalDate endDate,
                String intervalType, String adjustType) {
            return java.util.List.of();
        }
    }
}
