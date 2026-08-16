package com.quant.trade.marketdata.foundation;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.foundation.provider.HistoricalBarProvider;
import com.quant.trade.marketdata.foundation.provider.ProviderDailyBar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 回补引擎测试桩（providerCode=STUB_TEST，不外联）。
 * 可配置：按 symbol 返回固定日 K；按 symbol 模拟业务失败（可恢复）；记录拉取次数。
 */
public class StubHistoricalBarProvider implements HistoricalBarProvider {

    public static final String PROVIDER_CODE = "STUB_TEST";

    /** 测试桩安全窗（与腾讯一致的 365 天，供分片规划断言引用）。 */
    public static final int INSTANCE_SAFE_WINDOW = 365;

    private final AtomicInteger fetchCount = new AtomicInteger();
    private final Map<String, List<ProviderDailyBar>> barsBySymbol = new ConcurrentHashMap<>();
    private final Set<String> failingSymbols = ConcurrentHashMap.newKeySet();

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String supportedAdjustType() {
        return "NONE";
    }

    @Override
    public int safeRequestWindowDays() {
        return INSTANCE_SAFE_WINDOW;
    }

    @Override
    public List<ProviderDailyBar> getDailyBars(String canonicalSymbol, LocalDate start, LocalDate end) {
        fetchCount.incrementAndGet();
        if (failingSymbols.contains(canonicalSymbol)) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PROVIDER_TIMEOUT,
                    "stub 模拟拉取失败: " + canonicalSymbol);
        }
        return barsBySymbol.getOrDefault(canonicalSymbol, List.of()).stream()
                .filter(bar -> !bar.tradeDate().isBefore(start))
                .filter(bar -> !bar.tradeDate().isAfter(end))
                .toList();
    }

    /** 生成 days 根连续日 K（价格/量固定，VWAP=close 落在 [low,high] 内，单位元/股）。 */
    public void putBars(String canonicalSymbol, LocalDate firstDate, int days) {
        barsBySymbol.put(canonicalSymbol, firstDate.datesUntil(firstDate.plusDays(days))
                .map(date -> new ProviderDailyBar(canonicalSymbol, date,
                        new BigDecimal("10.00"), new BigDecimal("11.00"), new BigDecimal("9.50"),
                        new BigDecimal("10.50"), 1000L, new BigDecimal("10500.00"), null))
                .toList());
    }

    public void failSymbol(String canonicalSymbol) {
        failingSymbols.add(canonicalSymbol);
    }

    public void clearFailing() {
        failingSymbols.clear();
    }

    public void reset() {
        barsBySymbol.clear();
        failingSymbols.clear();
        fetchCount.set(0);
    }

    public int fetchCount() {
        return fetchCount.get();
    }
}
