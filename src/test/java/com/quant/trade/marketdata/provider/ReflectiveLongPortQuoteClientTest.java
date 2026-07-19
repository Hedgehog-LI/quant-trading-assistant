package com.quant.trade.marketdata.provider;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.config.LongPortProperties;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortDailyBar;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortQuote;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient.LongPortMinuteBar;
import com.quant.trade.marketdata.provider.longport.ReflectiveLongPortQuoteClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 反射式 LongPort client 在 SDK 缺失时应安全降级，而不是影响应用启动。 */
class ReflectiveLongPortQuoteClientTest {

    @Test
    void missingSdkReturnsSafeUnavailableStatus() {
        LongPortProperties properties = new LongPortProperties();
        properties.setEnabled(true);
        ReflectiveLongPortQuoteClient client = new ReflectiveLongPortQuoteClient(properties, new ClassLoader(null) {});

        assertFalse(client.isSdkAvailable());
        assertFalse(client.hasCredentials());
        assertEquals(MarketDataConstants.LONGPORT_SDK_MISSING_MESSAGE, client.unavailableReason());

        var status = client.healthCheck();
        assertFalse(status.configured());
        assertFalse(status.reachable());
        assertEquals(MarketDataConstants.LONGPORT_SDK_MISSING_MESSAGE, status.lastError());
    }

    @Test
    void reflectiveSdkPathMapsQuoteAndDailyBars() {
        LongPortProperties properties = enabledProperties();
        ReflectiveLongPortQuoteClient client = new ReflectiveLongPortQuoteClient(properties);

        assertTrue(client.isSdkAvailable());
        assertTrue(client.hasCredentials());
        assertTrue(client.healthCheck().configured());
        assertTrue(client.healthCheck().reachable());

        var staticInfo = client.getSecurityStaticInfo("600519.SH");
        assertEquals("600519.SH", staticInfo.longPortSymbol());
        assertEquals("贵州茅台", staticInfo.nameCn());
        assertEquals("SSE", staticInfo.exchange());
        assertEquals(100, staticInfo.lotSize());

        List<LongPortQuote> quotes = client.getLatestQuotes(List.of("600519.SH"));
        assertEquals(1, quotes.size());
        LongPortQuote quote = quotes.get(0);
        assertEquals("600519.SH", quote.longPortSymbol());
        assertEquals(LocalDateTime.of(2026, 7, 10, 15, 0), quote.quoteTime());
        assertEquals(new BigDecimal("1680.120000"), quote.currentPrice());
        assertEquals(1200L, quote.volume());
        assertEquals("Normal", quote.tradeStatus());

        List<LongPortDailyBar> bars = client.getDailyBars("600519.SH", LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10), "NoAdjust", "NONE");
        assertEquals(1, bars.size());
        LongPortDailyBar bar = bars.get(0);
        assertEquals("600519.SH", bar.longPortSymbol());
        assertEquals(LocalDate.of(2026, 7, 10), bar.tradeDate());
        assertEquals("NONE", bar.adjustType());
        assertEquals(new BigDecimal("1680.120000"), bar.closePrice());

        List<LongPortMinuteBar> minuteBars = client.getMinuteBars("600519.SH", LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10), "Min_5", "NoAdjust", "5M", "NONE");
        assertEquals(1, minuteBars.size());
        assertEquals("5M", minuteBars.get(0).intervalType());
        assertEquals(LocalDateTime.of(2026, 7, 10, 0, 0), minuteBars.get(0).barStartTime());

    }

    /**
     * 配置了 httpUrl / quoteWebsocketUrl 域名覆盖时，反射路径仍应正常 healthCheck / quote。
     * 覆盖 SDK 默认域名废弃后必须同时覆盖 HTTP 与 quote ws 两个 endpoint 的场景。
     */
    @Test
    void reflectiveSdkPathHonoursDomainOverrides() {
        LongPortProperties properties = enabledProperties();
        properties.setHttpUrl("https://openapi.longbridge.cn");
        properties.setQuoteWebsocketUrl("wss://openapi-quote.longbridge.cn/v2");
        ReflectiveLongPortQuoteClient client = new ReflectiveLongPortQuoteClient(properties);

        assertTrue(client.isSdkAvailable());
        assertTrue(client.hasCredentials());
        assertTrue(client.healthCheck().configured());
        assertTrue(client.healthCheck().reachable());

        List<LongPortQuote> quotes = client.getLatestQuotes(List.of("600519.SH"));
        assertEquals(1, quotes.size());
        assertEquals("600519.SH", quotes.get(0).longPortSymbol());

        List<LongPortDailyBar> bars = client.getDailyBars("600519.SH", LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 10), "NoAdjust", "NONE");
        assertEquals(1, bars.size());
        assertEquals(LocalDate.of(2026, 7, 10), bars.get(0).tradeDate());
    }

    @Test
    void invalidTokenIsReportedAsAuthenticationFailure() {
        ReflectiveLongPortQuoteClient client = new ReflectiveLongPortQuoteClient(enabledProperties());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.getSecurityStaticInfo("TOKEN_INVALID"));

        assertEquals(ErrorCodeEnum.MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("鉴权失败"));
    }

    private LongPortProperties enabledProperties() {
        LongPortProperties properties = new LongPortProperties();
        properties.setEnabled(true);
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        properties.setAccessToken("access-token");
        return properties;
    }
}
