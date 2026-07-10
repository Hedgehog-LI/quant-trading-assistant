package com.quant.trade.marketdata.provider;

import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 未配置 LongPort 时使用的降级 provider（不请求外部）。 */
@Component
public class DisabledMarketDataProvider implements MarketDataProvider {
    @Override public String getProviderCode() { return "LONGPORT"; }
    @Override public boolean isConfigured() { return false; }
    @Override public ProviderHealthStatus healthCheck() {
        return new ProviderHealthStatus(false, false, "LongPort provider 未配置", null);
    }
    @Override public List<ProviderQuote> getLatestQuotes(List<String> canonicalSymbols) {
        throw new UnsupportedOperationException("LongPort provider 未配置，无法获取行情");
    }

    @Override
    public List<ProviderDailyBar> getDailyBars(String canonicalSymbol, LocalDate startDate, LocalDate endDate, String adjustType) {
        throw new UnsupportedOperationException("LongPort provider 未配置，无法获取历史日 K");
    }
}
