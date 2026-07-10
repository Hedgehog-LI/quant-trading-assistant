package com.quant.trade.marketdata.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用 Fake Provider。
 * <p>
 * 不请求外部，通过 setFakeQuotes / setFakeDailyBars 注入数据，
 * 用于 Service 层集成测试（验证同步执行、幂等、计数）。
 */
public class FakeMarketDataProvider implements MarketDataProvider {

    private boolean configured = true;
    private boolean reachable = true;
    private String lastError = null;
    private LocalDateTime lastSuccessAt = LocalDateTime.now();
    private final Map<String, ProviderQuote> quotes = new HashMap<>();
    private final Map<String, List<ProviderDailyBar>> dailyBars = new HashMap<>();

    public void setConfigured(boolean v) { this.configured = v; }
    public void setReachable(boolean v) { this.reachable = v; }

    public void setFakeQuote(String canonical, ProviderQuote quote) {
        quotes.put(canonical, quote);
    }

    public void setFakeDailyBars(String canonical, List<ProviderDailyBar> bars) {
        dailyBars.put(canonical, new ArrayList<>(bars));
    }

    @Override public String getProviderCode() { return "FAKE"; }
    @Override public boolean isConfigured() { return configured; }

    @Override
    public ProviderHealthStatus healthCheck() {
        return new ProviderHealthStatus(configured, reachable, lastError, lastSuccessAt);
    }

    @Override
    public List<ProviderQuote> getLatestQuotes(List<String> canonicalSymbols) {
        List<ProviderQuote> result = new ArrayList<>();
        for (String s : canonicalSymbols) {
            ProviderQuote q = quotes.get(s);
            if (q != null) result.add(q);
        }
        return result;
    }

    @Override
    public List<ProviderDailyBar> getDailyBars(String canonicalSymbol, LocalDate startDate, LocalDate endDate, String adjustType) {
        List<ProviderDailyBar> all = dailyBars.get(canonicalSymbol);
        if (all == null) return List.of();
        return all.stream()
                .filter(b -> !b.tradeDate().isBefore(startDate) && !b.tradeDate().isAfter(endDate))
                .filter(b -> adjustType == null || adjustType.equals(b.adjustType()))
                .toList();
    }
}
