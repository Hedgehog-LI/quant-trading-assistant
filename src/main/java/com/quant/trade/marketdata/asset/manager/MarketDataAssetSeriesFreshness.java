package com.quant.trade.marketdata.asset.manager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 组合数据新鲜度判定（FRESH / STALE / UNKNOWN）。
 * <p>
 * 依据该组合最新 bar / 水位与“最近已完成交易时段”比较，不按自然日猜测：
 * - FRESH：最新 bar（或日 K 最新交易日）不早于最近一个已闭合/已收盘交易时段的期望值；
 * - STALE：最新 bar 落后于该期望值；
 * - UNKNOWN：无权威交易日历、连续竞价时段未知、或缺少最新 bar/水位。
 * <p>
 * 纯计算类：交易日序列、连续竞价时段与判定时刻均由调用方传入，便于单元测试。
 */
public final class MarketDataAssetSeriesFreshness {

    public static final String FRESH = "FRESH";
    public static final String STALE = "STALE";
    public static final String UNKNOWN = "UNKNOWN";

    private MarketDataAssetSeriesFreshness() {
    }

    /**
     * @param daily            日 K 组合（true）或分钟 K 组合（false）
     * @param tradingDays      最近的权威交易日（升序，可含判定当日）
     * @param sessionWindows   连续竞价时段（每项 int[2]={startHHMM,endHHMM}，不含集合竞价）
     * @param latestBarTime    该组合最新分钟 bar 起点（存储时区墙钟；日 K 传 null）
     * @param latestTradeDate  该组合最新交易日（日 K 水位；分钟 K 传 null）
     * @param now              判定时刻（存储时区墙钟）
     * @param minutes          分钟粒度（分钟 K 有效；日 K 传 0）
     */
    public static String evaluate(boolean daily, List<LocalDate> tradingDays, List<int[]> sessionWindows,
                                  LocalDateTime latestBarTime, LocalDate latestTradeDate,
                                  LocalDateTime now, int minutes) {
        if (tradingDays == null || tradingDays.isEmpty()
                || sessionWindows == null || sessionWindows.isEmpty()) {
            return UNKNOWN;
        }
        if (daily) {
            return evaluateDaily(tradingDays, sessionWindows, latestTradeDate, now);
        }
        return evaluateMinute(tradingDays, sessionWindows, latestBarTime, now, minutes);
    }

    private static String evaluateDaily(List<LocalDate> tradingDays, List<int[]> sessions,
                                        LocalDate latestTradeDate, LocalDateTime now) {
        if (latestTradeDate == null) {
            return UNKNOWN;
        }
        LocalDate expected = latestCompletedTradingDay(tradingDays, sessions, now);
        if (expected == null) {
            return UNKNOWN;
        }
        return latestTradeDate.isBefore(expected) ? STALE : FRESH;
    }

    private static String evaluateMinute(List<LocalDate> tradingDays, List<int[]> sessions,
                                         LocalDateTime latestBarTime, LocalDateTime now, int minutes) {
        if (latestBarTime == null || minutes <= 0) {
            return UNKNOWN;
        }
        LocalDateTime expected = latestCompletedBarStart(tradingDays, sessions, now, minutes);
        if (expected == null) {
            return UNKNOWN;
        }
        return latestBarTime.isBefore(expected) ? STALE : FRESH;
    }

    /** 最近已完成交易日：now 当日为交易日且已收盘则取当日，否则取最近的前一个交易日。 */
    private static LocalDate latestCompletedTradingDay(List<LocalDate> tradingDays, List<int[]> sessions,
                                                       LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate latest = tradingDays.stream()
                .filter(day -> !day.isAfter(today))
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (latest == null) {
            return null;
        }
        if (latest.equals(today) && minuteOfDay(now) < lastSessionEndMinute(sessions)) {
            // 今日尚未收盘 → 上一交易日
            return tradingDays.stream()
                    .filter(day -> day.isBefore(today))
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        }
        return latest;
    }

    /** 分钟 K：最近一个已完整闭合的 bar 起点。 */
    private static LocalDateTime latestCompletedBarStart(List<LocalDate> tradingDays, List<int[]> sessions,
                                                         LocalDateTime now, int minutes) {
        LocalDate today = now.toLocalDate();
        LocalDate refDay = tradingDays.stream()
                .filter(day -> !day.isAfter(today))
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (refDay == null) {
            return null;
        }
        if (refDay.isBefore(today)) {
            // 非交易日或盘前：最近前一交易日最后一根闭合 bar
            return lastBarStartOfDay(refDay, sessions, minutes);
        }
        LocalDateTime expected = lastClosedBarStartWithinDay(refDay, sessions, now, minutes);
        if (expected != null) {
            return expected;
        }
        // 开盘首根尚未闭合 → 落到前一个交易日
        LocalDate previous = tradingDays.stream()
                .filter(day -> day.isBefore(today))
                .max(Comparator.naturalOrder())
                .orElse(null);
        return previous == null ? null : lastBarStartOfDay(previous, sessions, minutes);
    }

    /** 当日最后一根已闭合 bar 起点（该日所有连续竞价时段均已结束）。 */
    private static LocalDateTime lastBarStartOfDay(LocalDate day, List<int[]> sessions, int minutes) {
        int best = -1;
        for (int[] session : sessions) {
            int start = hhmmToMinutes(session[0]);
            int end = hhmmToMinutes(session[1]);
            best = Math.max(best, lastGridStart(start, end, minutes));
        }
        return best < 0 ? null : day.atTime(best / 60, best % 60);
    }

    /** 查询时刻落在交易日内时：最近已闭合 bar 起点（bar 结束时刻 ≤ now）。 */
    private static LocalDateTime lastClosedBarStartWithinDay(LocalDate day, List<int[]> sessions,
                                                             LocalDateTime now, int minutes) {
        int nowMin = minuteOfDay(now);
        int best = -1;
        for (int[] session : sessions) {
            int start = hhmmToMinutes(session[0]);
            int end = hhmmToMinutes(session[1]);
            if (nowMin <= start) {
                continue; // 时段未开始
            }
            if (nowMin >= end) {
                best = Math.max(best, lastGridStart(start, end, minutes)); // 时段已结束
                continue;
            }
            // 时段进行中：最新闭合 bar = 满足 t + minutes <= nowMin 的最大网格起点
            int completed = (nowMin - start) / minutes - 1;
            if (completed >= 0) {
                best = Math.max(best, start + completed * minutes);
            }
        }
        return best < 0 ? null : day.atTime(best / 60, best % 60);
    }

    /** 网格起点（sStart 起每 minutes 一个，须 < sEnd）的最大值；无合法起点返回 -1。 */
    private static int lastGridStart(int start, int end, int minutes) {
        if (minutes <= 0 || end <= start) {
            return -1;
        }
        int count = (end - 1 - start) / minutes;
        return count < 0 ? -1 : start + count * minutes;
    }

    private static int lastSessionEndMinute(List<int[]> sessions) {
        int end = 0;
        for (int[] session : sessions) {
            end = Math.max(end, hhmmToMinutes(session[1]));
        }
        return end;
    }

    /** HHMM 整数 → 当日分钟数：930 → 570，1130 → 690，1300 → 780，1500 → 900。 */
    private static int hhmmToMinutes(int hhmm) {
        return (hhmm / 100) * 60 + (hhmm % 100);
    }

    private static int minuteOfDay(LocalDateTime time) {
        return time.getHour() * 60 + time.getMinute();
    }
}
