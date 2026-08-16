package com.quant.trade.marketdata.foundation.provider;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.poc.PublicMarketDataClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T07：TENCENT_PUBLIC Provider 节流与不可重试语义（契约 AC-03，纯单测：零等待零联网）。
 * 单位换算（手→股、万元→元、%→小数）、指数退避、403 立即失败不重试、最小间隔节流补足。
 */
class TencentPublicHistoricalBarProviderTest {

    private static final LocalDate START = LocalDate.of(2021, 1, 4);
    private static final LocalDate END = LocalDate.of(2021, 1, 8);

    /** 可编程 PublicMarketDataClient 桩（覆写 fetchDailyBars，不外联）。 */
    static class StubClient extends PublicMarketDataClient {
        final AtomicInteger calls = new AtomicInteger();
        volatile java.util.function.Supplier<List<PublicMarketDataClient.DailyBarEntry>> responder =
                () -> List.of();

        @Override
        public List<PublicMarketDataClient.DailyBarEntry> fetchDailyBars(String tencentCode, LocalDate start,
                                                                         LocalDate end) {
            calls.incrementAndGet();
            return responder.get();
        }
    }

    static final class RecordingSleeper implements TencentPublicHistoricalBarProvider.Sleeper {
        final List<Long> sleeps = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            sleeps.add(millis);
        }
    }

    private static PublicMarketDataClient.DailyBarEntry entry() {
        return PublicMarketDataClient.DailyBarEntry.builder()
                .tradeDate(LocalDate.of(2021, 1, 4))
                .open(new BigDecimal("10.00")).close(new BigDecimal("10.80"))
                .high(new BigDecimal("11.00")).low(new BigDecimal("9.50"))
                .volumeHands(new BigDecimal("100"))
                .turnoverPercent(new BigDecimal("2.5"))
                .amountTenThousand(new BigDecimal("1.5"))
                .build();
    }

    @Test
    void convertsUnitsAndTencentCode() {
        assertEquals("sh600519", TencentPublicHistoricalBarProvider.toTencentCode("SH.600519"));
        assertEquals("sz000001", TencentPublicHistoricalBarProvider.toTencentCode("SZ.000001"));

        StubClient client = new StubClient();
        client.responder = () -> List.of(entry());
        TencentPublicHistoricalBarProvider provider = new TencentPublicHistoricalBarProvider(
                client, 0, 500, 3, millis -> { }, System::nanoTime);

        List<ProviderDailyBar> bars = provider.getDailyBars("SH.600519", START, END);

        assertEquals(1, bars.size());
        ProviderDailyBar bar = bars.get(0);
        assertEquals("SH.600519", bar.canonicalSymbol());
        assertEquals(LocalDate.of(2021, 1, 4), bar.tradeDate());
        assertEquals(0, bar.open().compareTo(new BigDecimal("10.00")), "价格原值=元");
        assertEquals(0, bar.high().compareTo(new BigDecimal("11.00")));
        assertEquals(0, bar.low().compareTo(new BigDecimal("9.50")));
        assertEquals(0, bar.close().compareTo(new BigDecimal("10.80")));
        Long expectedShares = 100L * 100L;
        assertEquals(expectedShares, bar.volumeShares(), "量：手 ×100 → 股");
        assertEquals(0, bar.amountYuan().compareTo(new BigDecimal("15000")), "额：万元 ×10000 → 元");
        assertEquals(0, bar.turnoverRate().compareTo(new BigDecimal("0.025")), "换手率：% ÷100 → 小数");
        assertEquals("TENCENT_PUBLIC", provider.providerCode());
        assertEquals("NONE", provider.supportedAdjustType());
    }

    @Test
    void retriesWithExponentialBackoffThenSucceeds() {
        StubClient client = new StubClient();
        AtomicInteger attempt = new AtomicInteger();
        client.responder = () -> {
            if (attempt.incrementAndGet() <= 2) {
                throw new IllegalStateException("公共源请求失败: connection reset");
            }
            return List.of(entry());
        };
        RecordingSleeper sleeper = new RecordingSleeper();
        TencentPublicHistoricalBarProvider provider = new TencentPublicHistoricalBarProvider(
                client, 0, 500, 3, sleeper, System::nanoTime);

        List<ProviderDailyBar> bars = provider.getDailyBars("SH.600519", START, END);

        assertEquals(1, bars.size());
        assertEquals(3, client.calls.get(), "失败两次成功一次 → 3 次尝试");
        assertEquals(List.of(500L, 1000L), sleeper.sleeps, "指数退避：500ms × 2^n");
    }

    @Test
    void permissionDeniedFailsImmediatelyWithoutRetry() {
        StubClient client = new StubClient();
        client.responder = () -> {
            throw new IllegalStateException("公共源响应非 200: status=403 url=https://proxy.finance.qq.com/...");
        };
        RecordingSleeper sleeper = new RecordingSleeper();
        TencentPublicHistoricalBarProvider provider = new TencentPublicHistoricalBarProvider(
                client, 0, 500, 3, sleeper, System::nanoTime);

        BusinessException denied = assertThrows(BusinessException.class,
                () -> provider.getDailyBars("SH.600519", START, END));

        assertEquals(ErrorCodeEnum.MARKET_DATA_PROVIDER_PERMISSION_DENIED.getCode(),
                denied.getErrorCode().getCode());
        assertEquals(1, client.calls.get(), "403 类错误不重试，client 只调用 1 次");
        assertTrue(sleeper.sleeps.isEmpty(), "不进入退避等待");
        assertTrue(denied.getMessage().contains("403"));
    }

    @Test
    void exhaustsAttemptsIntoTimeoutError() {
        StubClient client = new StubClient();
        client.responder = () -> {
            throw new IllegalStateException("公共源请求失败: timeout");
        };
        RecordingSleeper sleeper = new RecordingSleeper();
        TencentPublicHistoricalBarProvider provider = new TencentPublicHistoricalBarProvider(
                client, 0, 200, 3, sleeper, System::nanoTime);

        BusinessException timeout = assertThrows(BusinessException.class,
                () -> provider.getDailyBars("SH.600519", START, END));

        assertEquals(ErrorCodeEnum.MARKET_DATA_PROVIDER_TIMEOUT.getCode(), timeout.getErrorCode().getCode());
        assertEquals(3, client.calls.get());
        assertEquals(List.of(200L, 400L), sleeper.sleeps);
    }

    @Test
    void throttlesToMinimumIntervalBetweenCalls() {
        StubClient client = new StubClient();
        client.responder = () -> List.of(entry());
        RecordingSleeper sleeper = new RecordingSleeper();
        // fake 时钟：每次 nanoTime 前进 100ms
        AtomicLong nanos = new AtomicLong();
        TencentPublicHistoricalBarProvider.NanoClock clock = () -> nanos.addAndGet(100_000_000L);
        TencentPublicHistoricalBarProvider provider = new TencentPublicHistoricalBarProvider(
                client, 300, 500, 1, sleeper, clock);

        provider.getDailyBars("SH.600519", START, END); // 首次：无等待
        provider.getDailyBars("SH.600519", START, END); // 距上次仅 100ms → 补足 200ms

        assertEquals(2, client.calls.get());
        assertEquals(List.of(200L), sleeper.sleeps, "throttle=300ms、间隔 100ms → sleeper 补足 200ms");
    }
}
