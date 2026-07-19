package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dto.VerifySecurityDTO;
import com.quant.trade.marketdata.manager.SecurityCodeManager;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityVerificationServiceTest {

    private final MarketDataProvider provider = mock(MarketDataProvider.class);
    private final SecurityVerificationService service = new SecurityVerificationService(provider, new SecurityCodeManager());

    @Test
    void returnsVerifiedIdentityAndQuoteWithoutPersistence() {
        when(provider.isConfigured()).thenReturn(true);
        when(provider.getProviderCode()).thenReturn("LONGPORT");
        when(provider.getSecurityStaticInfo("HK.02498")).thenReturn(new MarketDataProvider.ProviderSecurityInfo(
                "HK.02498", "2498.HK", "速腾聚创", "速騰聚創", "ROBOSENSE", "SEHK", "HKD", 100));
        when(provider.getLatestQuotes(List.of("HK.02498"))).thenReturn(List.of(new MarketDataProvider.ProviderQuote(
                "HK.02498", LocalDateTime.of(2026, 7, 17, 15, 59), new BigDecimal("38.20"),
                null, null, null, null, 1L, BigDecimal.ONE, "NORMAL")));

        var result = service.verify(new VerifySecurityDTO("HK", "2498"));

        assertEquals("HK.02498", result.canonicalSymbol());
        assertEquals("速騰聚創", result.displayName());
        assertEquals("VERIFIED_QUOTE_AVAILABLE", result.verificationStatus());
        assertEquals(new BigDecimal("38.20"), result.lastPrice());
        verify(provider).getSecurityStaticInfo("HK.02498");
        verify(provider).getLatestQuotes(List.of("HK.02498"));
    }

    @Test
    void keepsVerifiedIdentityWhenQuotePermissionIsMissing() {
        when(provider.isConfigured()).thenReturn(true);
        when(provider.getProviderCode()).thenReturn("LONGPORT");
        when(provider.getSecurityStaticInfo("US.NVDA")).thenReturn(new MarketDataProvider.ProviderSecurityInfo(
                "US.NVDA", "NVDA.US", null, null, "NVIDIA Corporation", "NASD", "USD", 1));
        when(provider.getLatestQuotes(List.of("US.NVDA"))).thenThrow(new BusinessException(
                ErrorCodeEnum.MARKET_DATA_PROVIDER_PERMISSION_DENIED, "无权限"));

        var result = service.verify(new VerifySecurityDTO("US", "NVDA"));

        assertEquals("NO_PERMISSION", result.verificationStatus());
        assertEquals("NVIDIA Corporation", result.displayName());
        assertFalse(result.quoteAvailable());
    }

    @Test
    void distinguishesMissingSecurityAndDisabledProvider() {
        when(provider.isConfigured()).thenReturn(true);
        when(provider.getProviderCode()).thenReturn("LONGPORT");
        when(provider.getSecurityStaticInfo("SH.603308")).thenReturn(null);
        assertEquals("INVALID_SYMBOL", service.verify(new VerifySecurityDTO("CN", "603308")).verificationStatus());

        reset(provider);
        when(provider.isConfigured()).thenReturn(false);
        when(provider.getProviderCode()).thenReturn("LONGPORT");
        when(provider.healthCheck()).thenReturn(new MarketDataProvider.ProviderHealthStatus(false, false, "未配置", null));
        assertEquals("PROVIDER_UNAVAILABLE", service.verify(new VerifySecurityDTO("CN", "603308")).verificationStatus());
    }
}
