package com.quant.trade.marketdata;

import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.service.MarketQuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** 启用 LongPort 但凭据未配置时，应用应可启动并返回可解释状态。 */
@SpringBootTest(properties = "qta.market-data.longport.enabled=true")
@ActiveProfiles("test")
class LongPortEnabledMissingCredentialsContextTest {

    @Autowired
    private MarketQuoteService marketQuoteService;

    @Test
    void contextStartsAndReportsMissingCredentials() {
        var status = marketQuoteService.getProviderStatus();

        assertEquals(MarketDataConstants.PROVIDER_CODE_LONGPORT, status.providerCode());
        assertFalse(status.configured());
        assertFalse(status.reachable());
        assertEquals(MarketDataConstants.LONGPORT_CREDENTIALS_MISSING_MESSAGE, status.lastError());
    }
}
