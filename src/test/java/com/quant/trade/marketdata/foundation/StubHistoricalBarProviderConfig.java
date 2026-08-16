package com.quant.trade.marketdata.foundation;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 将 STUB_TEST 回补 Provider 注入 Spring 上下文（HistoricalBarProviderRegistry 自动收录）。
 */
@TestConfiguration
public class StubHistoricalBarProviderConfig {

    @Bean
    public StubHistoricalBarProvider stubHistoricalBarProvider() {
        return new StubHistoricalBarProvider();
    }
}
