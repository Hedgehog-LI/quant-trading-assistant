package com.quant.trade.marketdata.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 测试用 Fake Provider。
 * <p>
 * 不请求外部，通过 setFakeQuotes / setFakeDailyBars 注入数据，
 * 用于 Service 层集成测试（验证同步执行、幂等、计数）。
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "qta.market-data.fake", name = "enabled", havingValue = "true")
public class FakeMarketDataProvider implements MarketDataProvider {

    private boolean configured = true;
    private boolean reachable = true;
    private String lastError = null;
    private LocalDateTime lastSuccessAt = LocalDateTime.now();
    private final Map<String, ProviderQuote> quotes = new HashMap<>();
    private final Map<String, ProviderSecurityInfo> securityInfos = new HashMap<>();
    private final Map<String, List<ProviderDailyBar>> dailyBars = new HashMap<>();
    private final Map<String, List<ProviderMinuteBar>> minuteBars = new HashMap<>();

    @Value("${qta.market-data.fake.failure-symbol:}")
    private String failureSymbol;

    public void setConfigured(boolean v) { this.configured = v; }
    public void setReachable(boolean v) { this.reachable = v; }

    public void setFakeQuote(String canonical, ProviderQuote quote) {
        quotes.put(canonical, quote);
    }

    public void setFakeSecurityInfo(String canonical, ProviderSecurityInfo info) {
        securityInfos.put(canonical, info);
    }

    public void setFakeDailyBars(String canonical, List<ProviderDailyBar> bars) {
        dailyBars.put(canonical, new ArrayList<>(bars));
    }

    public void setFakeMinuteBars(String canonical, List<ProviderMinuteBar> bars) {
        minuteBars.put(canonical, new ArrayList<>(bars));
    }

    public void setFailureSymbol(String failureSymbol) { this.failureSymbol = failureSymbol; }

    @Override public String getProviderCode() { return "FAKE"; }
    @Override public boolean isConfigured() { return configured; }

    @Override
    public ProviderHealthStatus healthCheck() {
        return new ProviderHealthStatus(configured, reachable, lastError, lastSuccessAt);
    }

    @Override
    public ProviderSecurityInfo getSecurityStaticInfo(String canonicalSymbol) {
        return securityInfos.get(canonicalSymbol);
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

    @Override
    public List<ProviderMinuteBar> getMinuteBars(String canonicalSymbol, LocalDate startDate, LocalDate endDate,
                                                 String intervalType, String adjustType) {
        if (canonicalSymbol != null && canonicalSymbol.equalsIgnoreCase(failureSymbol)) {
            throw new com.quant.trade.common.exception.BusinessException(
                    com.quant.trade.common.exception.ErrorCodeEnum.MARKET_DATA_PROVIDER_TIMEOUT,
                    "Fake provider 受控超时: " + canonicalSymbol);
        }
        List<ProviderMinuteBar> configuredBars = minuteBars.get(canonicalSymbol);
        if (configuredBars != null) {
            return configuredBars.stream()
                    .filter(b -> !b.barStartTime().toLocalDate().isBefore(startDate)
                            && !b.barStartTime().toLocalDate().isAfter(endDate))
                    .filter(b -> intervalType.equals(b.intervalType()))
                    .toList();
        }
        int minutes = switch (intervalType) {
            case "1M" -> 1;
            case "5M" -> 5;
            case "15M" -> 15;
            case "30M" -> 30;
            case "60M" -> 60;
            default -> throw new IllegalArgumentException("不支持的 Fake 分钟粒度: " + intervalType);
        };
        List<ProviderMinuteBar> generated = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() >= 6) continue;
            for (int i = 0; i < 2; i++) {
                LocalDateTime start = date.atTime(10, i * minutes);
                BigDecimal base = new BigDecimal("10.00").add(new BigDecimal(i).multiply(new BigDecimal("0.10")));
                generated.add(new ProviderMinuteBar(canonicalSymbol, start, intervalType,
                        adjustType == null ? "NONE" : adjustType, base, base.add(new BigDecimal("0.20")),
                        base.subtract(new BigDecimal("0.10")), base.add(new BigDecimal("0.10")),
                        1000L + i, new BigDecimal("10000.00")));
            }
        }
        return generated;
    }
}
