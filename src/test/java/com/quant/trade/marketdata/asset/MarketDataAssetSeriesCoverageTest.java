package com.quant.trade.marketdata.asset;

import com.quant.trade.marketdata.asset.manager.MarketDataAssetSeriesCoverage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarketDataAssetSeriesCoverage#countExpectedBarStarts} 纯计算单测。
 * <p>
 * 边界语义与 SQL 一致（bar_start_time &gt;= from 且 &lt;= to 的包含端点）：
 * 只统计满足 start &gt;= from、start &lt;= to、start &lt; sessionEnd 的合法网格起点。
 * 连续竞价时段取 A 股回退窗口：上午 09:30-11:30 + 下午 13:00-15:00；5M 网格。
 */
class MarketDataAssetSeriesCoverageTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 17);
    private static final List<LocalDate> ONE_DAY = List.of(DAY);
    private static final List<LocalDate> TWO_DAYS = List.of(
            LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 17));
    private static final List<int[]> SESSIONS = List.of(new int[]{930, 1130}, new int[]{1300, 1500});

    private static long count(String from, String to) {
        return MarketDataAssetSeriesCoverage.countExpectedBarStarts(
                ONE_DAY, SESSIONS, LocalDateTime.parse(from), LocalDateTime.parse(to), 5);
    }

    // ==================== 包含端点 / 网格 ====================

    @Test
    void fromEqualToLegalGridPointCountsOne() {
        // from == to == 09:30：09:30 是合法网格起点，应计入 1 根
        assertThat(count("2026-07-17T09:30:00", "2026-07-17T09:30:00")).isEqualTo(1);
    }

    @Test
    void fromEqualToNonGridPointCountsZero() {
        // from == to == 09:32：不在 5M 网格上，无合法起点
        assertThat(count("2026-07-17T09:32:00", "2026-07-17T09:32:00")).isEqualTo(0);
    }

    @Test
    void nonGridFromToCountsOnlyAlignedGridPoint() {
        // 09:32-09:37 内合法网格起点只有 09:35
        assertThat(count("2026-07-17T09:32:00", "2026-07-17T09:37:00")).isEqualTo(1);
    }

    // ==================== 会话边界 ====================

    @Test
    void toEqualToSessionEndExcludesSessionEndBar() {
        // to == 11:30（会话结束）：11:30 不满足 start < sessionEnd，09:30..11:25 共 24 根
        assertThat(count("2026-07-17T09:30:00", "2026-07-17T11:30:00")).isEqualTo(24);
    }

    @Test
    void fromBeforeSessionStartClampsToSessionStart() {
        // from 早于 09:30：网格仍从 09:30 起，08:00-10:00 内 09:30..10:00 共 7 根
        assertThat(count("2026-07-17T08:00:00", "2026-07-17T10:00:00")).isEqualTo(7);
    }

    @Test
    void toAfterSessionEndClampsToLastValidBar() {
        // to 晚于 15:00：下午最多到 14:55，14:00-16:00 内 14:00..14:55 共 12 根
        assertThat(count("2026-07-17T14:00:00", "2026-07-17T16:00:00")).isEqualTo(12);
    }

    // ==================== 午休 / 跨会话 / 跨交易日 ====================

    @Test
    void lunchGapWindowCountsZero() {
        // 12:00-12:59 完全落在午休，无数合法起点
        assertThat(count("2026-07-17T12:00:00", "2026-07-17T12:59:00")).isEqualTo(0);
    }

    @Test
    void spanningMorningAndAfternoonCountsBothSessions() {
        // 10:00-14:00：上午 10:00..11:25 18 根 + 下午 13:00..14:00 13 根 = 31
        assertThat(count("2026-07-17T10:00:00", "2026-07-17T14:00:00")).isEqualTo(31);
    }

    @Test
    void crossTradingDaysCountsPartialSessions() {
        // 07-16 全天（48）+ 07-17 至 11:30 上午（24）= 72
        long expected = MarketDataAssetSeriesCoverage.countExpectedBarStarts(
                TWO_DAYS, SESSIONS,
                LocalDateTime.parse("2026-07-16T09:30:00"), LocalDateTime.parse("2026-07-17T11:30:00"), 5);
        assertThat(expected).isEqualTo(72);
    }

    // ==================== 退化输入 ====================

    @Test
    void emptyTradingDaysCountsZero() {
        assertThat(MarketDataAssetSeriesCoverage.countExpectedBarStarts(
                Collections.emptyList(), SESSIONS,
                LocalDateTime.parse("2026-07-17T09:30:00"), LocalDateTime.parse("2026-07-17T11:30:00"), 5))
                .isZero();
    }

    @Test
    void emptySessionWindowsCountsZero() {
        assertThat(MarketDataAssetSeriesCoverage.countExpectedBarStarts(
                ONE_DAY, Collections.emptyList(),
                LocalDateTime.parse("2026-07-17T09:30:00"), LocalDateTime.parse("2026-07-17T11:30:00"), 5))
                .isZero();
    }
}
