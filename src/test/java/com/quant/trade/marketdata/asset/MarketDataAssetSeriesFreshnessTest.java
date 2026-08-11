package com.quant.trade.marketdata.asset;

import com.quant.trade.marketdata.asset.manager.MarketDataAssetSeriesFreshness;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarketDataAssetSeriesFreshness} 纯计算单测：固定判定时刻、交易日与连续竞价时段。
 * <p>
 * 连续竞价时段取 A 股回退窗口：上午 09:30-11:30 + 下午 13:00-15:00；
 * 5M 网格：AM 570..685、PM 780..895，全日最后一根起点 14:55。
 */
class MarketDataAssetSeriesFreshnessTest {

    private static final List<LocalDate> TRADING_DAYS =
            List.of(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 17));
    private static final List<int[]> SESSIONS = List.of(new int[]{930, 1130}, new int[]{1300, 1500});
    private static final int MINUTES = 5;

    private static LocalDateTime at(String text) {
        return LocalDateTime.parse(text);
    }

    private static String evaluateDaily(LocalDate latestTradeDate, LocalDateTime now) {
        return MarketDataAssetSeriesFreshness.evaluate(true, TRADING_DAYS, SESSIONS, null, latestTradeDate, now, 0);
    }

    private static String evaluateMinute(LocalDateTime latestBarTime, LocalDateTime now) {
        return MarketDataAssetSeriesFreshness.evaluate(false, TRADING_DAYS, SESSIONS, latestBarTime, null, now, MINUTES);
    }

    // ==================== 日 K ====================

    @Test
    void dailyAfterCloseWithTodayBarIsFresh() {
        assertThat(evaluateDaily(LocalDate.of(2026, 7, 17), at("2026-07-17T15:30:00"))).isEqualTo("FRESH");
    }

    @Test
    void dailyAfterCloseWithYesterdayBarIsStale() {
        assertThat(evaluateDaily(LocalDate.of(2026, 7, 16), at("2026-07-17T15:30:00"))).isEqualTo("STALE");
    }

    @Test
    void dailyBeforeCloseUsesPreviousTradingDayAsExpected() {
        // 今日 10:00 尚未收盘 → 期望最近已收盘交易日 07-16
        assertThat(evaluateDaily(LocalDate.of(2026, 7, 16), at("2026-07-17T10:00:00"))).isEqualTo("FRESH");
        assertThat(evaluateDaily(LocalDate.of(2026, 7, 15), at("2026-07-17T10:00:00"))).isEqualTo("STALE");
    }

    @Test
    void dailyMissingLatestTradeDateIsUnknown() {
        assertThat(evaluateDaily(null, at("2026-07-17T15:30:00"))).isEqualTo("UNKNOWN");
    }

    @Test
    void dailyWithoutCalendarIsUnknown() {
        assertThat(MarketDataAssetSeriesFreshness.evaluate(true, List.of(), SESSIONS, null,
                LocalDate.of(2026, 7, 17), at("2026-07-17T15:30:00"), 0)).isEqualTo("UNKNOWN");
    }

    // ==================== 分钟 K ====================

    @Test
    void minuteIntradayLatestClosedBarIsFresh() {
        // 11:00 时最新已闭合 bar 起点 10:55
        assertThat(evaluateMinute(at("2026-07-17T10:55:00"), at("2026-07-17T11:00:00"))).isEqualTo("FRESH");
    }

    @Test
    void minuteIntradayMissingLatestBarIsStale() {
        assertThat(evaluateMinute(at("2026-07-17T10:50:00"), at("2026-07-17T11:00:00"))).isEqualTo("STALE");
    }

    @Test
    void minuteAfterCloseLatestBarOfDayIsFresh() {
        // 15:30 收盘后最新闭合 bar 起点 14:55
        assertThat(evaluateMinute(at("2026-07-17T14:55:00"), at("2026-07-17T15:30:00"))).isEqualTo("FRESH");
        assertThat(evaluateMinute(at("2026-07-17T14:50:00"), at("2026-07-17T15:30:00"))).isEqualTo("STALE");
    }

    @Test
    void minuteBeforeOpenFallsBackToPreviousDay() {
        // 09:00 盘前 → 期望前一日最后一根闭合 bar 14:55
        assertThat(evaluateMinute(at("2026-07-16T14:55:00"), at("2026-07-17T09:00:00"))).isEqualTo("FRESH");
        assertThat(evaluateMinute(at("2026-07-16T14:45:00"), at("2026-07-17T09:00:00"))).isEqualTo("STALE");
    }

    @Test
    void minuteFirstBarNotYetClosedFallsBackToPreviousDay() {
        // 开盘首根（09:30）尚未闭合 → 期望前一日 14:55
        assertThat(evaluateMinute(at("2026-07-16T14:55:00"), at("2026-07-17T09:30:00"))).isEqualTo("FRESH");
        assertThat(evaluateMinute(at("2026-07-16T14:50:00"), at("2026-07-17T09:30:00"))).isEqualTo("STALE");
    }

    @Test
    void minuteOnNonTradingDayUsesLastTradingDay() {
        // 07-18 为周六（不在交易日列表）→ 期望 07-17 14:55
        assertThat(evaluateMinute(at("2026-07-17T14:55:00"), at("2026-07-18T12:00:00"))).isEqualTo("FRESH");
        assertThat(evaluateMinute(at("2026-07-17T14:45:00"), at("2026-07-18T12:00:00"))).isEqualTo("STALE");
    }

    @Test
    void minuteMissingLatestBarTimeIsUnknown() {
        assertThat(evaluateMinute(null, at("2026-07-17T11:00:00"))).isEqualTo("UNKNOWN");
    }

    @Test
    void minuteWithoutSessionsIsUnknown() {
        assertThat(MarketDataAssetSeriesFreshness.evaluate(false, TRADING_DAYS, List.of(),
                at("2026-07-17T10:55:00"), null, at("2026-07-17T11:00:00"), MINUTES)).isEqualTo("UNKNOWN");
    }
}
