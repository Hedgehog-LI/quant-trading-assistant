package com.quant.trade.marketdata.asset.manager;

import com.quant.trade.marketdata.asset.dto.MarketDataAssetSeriesQueryDTO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetSeriesVO;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketCalendarMapper;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.model.MarketCalendarDO;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import com.quant.trade.marketdata.model.StockMinuteBarDO;
import com.quant.trade.marketdata.util.MarketDataAssetTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * P1.9-A series 区间摘要、覆盖与新鲜度计算。
 * <p>
 * expectedBarCount 沿用 SQL 包含端点语义（bar_start_time &gt;= from 且 &lt;= to），只统计查询
 * from/to 与实际连续竞价时段的交集内应出现的 bar 起点（须 start &lt; sessionEnd；处理上午/午休/下午、
 * 单日部分区间与跨交易日），不按整天恒定量；HK/US 或权威日历未就绪时返回 UNKNOWN 且
 * expected/missing 为 null。actual &gt; expected 时返回 PARTIAL 且 reasonCodes 含 UNEXPECTED_BARS。
 */
@Component
@RequiredArgsConstructor
public class MarketDataAssetSeriesCoverage {

    private static final int RECENT_CALENDAR_DAYS = 20;
    private static final String COVERAGE_VERIFIED = "VERIFIED";
    private static final String COVERAGE_PARTIAL = "PARTIAL";
    private static final String COVERAGE_UNKNOWN = "UNKNOWN";

    private final MarketCalendarMapper calendarMapper;
    private final TradingSessionManager tradingSessionManager;
    private final MarketDataAssetSecurityMeta securityMeta;

    /** 区间摘要计算用最小投影（避免从 VO 字符串回解析）。 */
    public record BarPoint(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                           Long volume, BigDecimal amount) {
    }

    public BarPoint fromDaily(StockDailyBarDO row) {
        return new BarPoint(row.getOpenPrice(), row.getHighPrice(), row.getLowPrice(), row.getClosePrice(),
                row.getVolume(), row.getAmount());
    }

    public BarPoint fromMinute(StockMinuteBarDO row) {
        return new BarPoint(row.getOpenPrice(), row.getHighPrice(), row.getLowPrice(), row.getClosePrice(),
                row.getVolume(), row.getAmount());
    }

    public MarketDataAssetSeriesVO.Summary buildSummary(List<BarPoint> points) {
        if (points.isEmpty()) {
            return new MarketDataAssetSeriesVO.Summary(null, null, null, null, null, null, 0L, null, 0);
        }
        BigDecimal firstOpen = points.get(0).open();
        BigDecimal lastClose = points.get(points.size() - 1).close();
        BigDecimal absoluteChange = null;
        BigDecimal changeRate = null;
        if (lastClose != null) {
            BigDecimal base = firstOpen == null ? BigDecimal.ZERO : firstOpen;
            absoluteChange = lastClose.subtract(base);
            if (base.signum() != 0) {
                changeRate = absoluteChange.divide(base, 10, RoundingMode.HALF_UP);
            }
        }
        BigDecimal highest = null;
        BigDecimal lowest = null;
        long totalVolume = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (BarPoint point : points) {
            if (point.high() != null && (highest == null || point.high().compareTo(highest) > 0)) {
                highest = point.high();
            }
            if (point.low() != null && (lowest == null || point.low().compareTo(lowest) < 0)) {
                lowest = point.low();
            }
            totalVolume += point.volume() == null ? 0L : point.volume();
            if (point.amount() != null) {
                totalAmount = totalAmount.add(point.amount());
            }
        }
        return new MarketDataAssetSeriesVO.Summary(
                MarketDataAssetTimeFormatter.priceText(firstOpen),
                MarketDataAssetTimeFormatter.priceText(lastClose),
                MarketDataAssetTimeFormatter.priceText(absoluteChange),
                MarketDataAssetTimeFormatter.priceText(changeRate),
                MarketDataAssetTimeFormatter.priceText(highest),
                MarketDataAssetTimeFormatter.priceText(lowest),
                totalVolume,
                MarketDataAssetTimeFormatter.priceText(totalAmount),
                points.size());
    }

    /**
     * 质量与新鲜度。
     * <ul>
     *   <li>覆盖：CN 有权威日历可算 VERIFIED/PARTIAL 与缺失数；HK/US 或日历未就绪返回 UNKNOWN。</li>
     *   <li>新鲜度：依据该组合最新 bar/水位与最近已完成交易时段判定；非 CN 或缺少最近日历/最新数据返回 UNKNOWN。</li>
     * </ul>
     */
    public MarketDataAssetSeriesVO.Quality buildQuality(MarketDataAssetSeriesQueryDTO query, String market,
                                                        boolean truncated, int actualBarCount, int suspectBarCount,
                                                        LocalDateTime latestBarTime, LocalDate latestTradeDate,
                                                        LocalDateTime now) {
        List<String> reasonCodes = new ArrayList<>();
        if (truncated) {
            reasonCodes.add("TRUNCATED");
        }
        if (suspectBarCount > 0) {
            reasonCodes.add("SUSPECT_BARS");
        }
        boolean cnMarket = !"HK".equals(market) && !"US".equals(market);
        String marketCode = securityMeta.marketCodeOf(market);
        String freshness = MarketDataAssetSeriesFreshness.UNKNOWN;
        String freshnessDetail = cnMarket ? null : "HK/US 日历未闭环，无法判定新鲜度";
        if (cnMarket) {
            List<LocalDate> recentTradingDays = tradingSessionManager.getTradingDays(marketCode,
                    now.toLocalDate().minusDays(RECENT_CALENDAR_DAYS), now.toLocalDate());
            if (recentTradingDays.isEmpty()) {
                freshnessDetail = "缺少权威交易日历，无法判定新鲜度";
            } else {
                List<int[]> sessions = tradingSessionManager.getSessionWindows(marketCode, false);
                freshness = MarketDataAssetSeriesFreshness.evaluate(
                        query.isDaily(), recentTradingDays, sessions, latestBarTime, latestTradeDate, now,
                        query.isDaily() ? 0 : intervalMinutes(query.interval()));
                if (MarketDataAssetSeriesFreshness.UNKNOWN.equals(freshness)) {
                    freshnessDetail = "缺少最新 bar/水位，无法判定新鲜度";
                }
            }
        }

        if (!cnMarket) {
            return new MarketDataAssetSeriesVO.Quality(COVERAGE_UNKNOWN, actualBarCount, null, null,
                    suspectBarCount, truncated, reasonCodes, freshness, freshnessDetail);
        }
        LocalDate from = query.isDaily() ? query.fromDate() : query.fromTime().toLocalDate();
        LocalDate to = query.isDaily() ? query.toDate() : query.toTime().toLocalDate();
        List<MarketCalendarDO> calendarRows = calendarMapper.selectByRange(marketCode, from, to, null);
        if (calendarRows == null || calendarRows.isEmpty()) {
            return new MarketDataAssetSeriesVO.Quality(COVERAGE_UNKNOWN, actualBarCount, null, null,
                    suspectBarCount, truncated, reasonCodes, freshness, freshnessDetail);
        }
        List<LocalDate> tradingDays = calendarRows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getIsTradingDay()))
                .map(MarketCalendarDO::getTradeDate)
                .sorted()
                .toList();
        List<int[]> sessions = tradingSessionManager.getSessionWindows(marketCode, false);
        long expected = query.isDaily()
                ? tradingDays.size()
                : countExpectedBarStarts(tradingDays, sessions, query.fromTime(), query.toTime(),
                        intervalMinutes(query.interval()));
        // 实际条数多于应有网格起点（如非网格数据点）视为 PARTIAL，并给出 UNEXPECTED_BARS，禁止假绿灯。
        if (actualBarCount > expected) {
            reasonCodes.add("UNEXPECTED_BARS");
        }
        long missing = Math.max(0, expected - actualBarCount);
        if (missing > 0) {
            reasonCodes.add("MISSING_BARS");
        }
        boolean unexpected = actualBarCount > expected;
        return new MarketDataAssetSeriesVO.Quality(
                (missing > 0 || unexpected) ? COVERAGE_PARTIAL : COVERAGE_VERIFIED,
                actualBarCount, (int) expected, (int) missing, suspectBarCount, truncated,
                reasonCodes, freshness, freshnessDetail);
    }

    /**
     * 查询 from/to 与实际连续竞价时段交集内应出现的 bar 起点数。
     * <p>
     * 与 SQL 的包含端点语义一致：bar 起点须满足 start &gt;= from 且 start &lt;= to，且 start &lt; sessionEnd。
     * 对每个交易日与每个连续竞价时段取交集 [max(sessionStart, dayFrom), min(sessionEnd - 1, dayTo)]（两端含），
     * 网格起点 = sessionStart + k*minutes（须 ≤ 交集上界）。首末日按查询时刻截取，其余交易日取全天。
     */
    public static long countExpectedBarStarts(List<LocalDate> tradingDays, List<int[]> sessionWindows,
                                              LocalDateTime fromTime, LocalDateTime toTime, int minutes) {
        long expected = 0;
        for (LocalDate day : tradingDays) {
            int dayFrom = day.equals(fromTime.toLocalDate()) ? minuteOfDay(fromTime) : 0;
            int dayTo = day.equals(toTime.toLocalDate()) ? minuteOfDay(toTime) : MINUTES_PER_DAY;
            for (int[] session : sessionWindows) {
                int start = hhmmToMinutes(session[0]);
                int end = hhmmToMinutes(session[1]);
                int lo = Math.max(start, dayFrom);
                // 包含端点：合法的 bar 起点须 start < sessionEnd，故上界取 min(end - 1, dayTo)。
                int hi = Math.min(end - 1, dayTo);
                if (hi < lo) {
                    continue;
                }
                int offset = Math.max(0, lo - start);
                int first = start + ((offset + minutes - 1) / minutes) * minutes;
                if (first <= hi) {
                    expected += (hi - first) / minutes + 1;
                }
            }
        }
        return expected;
    }

    private static final int MINUTES_PER_DAY = 24 * 60;

    private static int intervalMinutes(String interval) {
        return switch (interval) {
            case WorkbenchConstants.INTERVAL_1M -> 1;
            case WorkbenchConstants.INTERVAL_5M -> 5;
            case WorkbenchConstants.INTERVAL_15M -> 15;
            case WorkbenchConstants.INTERVAL_30M -> 30;
            case WorkbenchConstants.INTERVAL_60M -> 60;
            default -> 1;
        };
    }

    /** HHMM 整数 → 当日分钟数：930 → 570，1130 → 690，1300 → 780，1500 → 900。 */
    private static int hhmmToMinutes(int hhmm) {
        return (hhmm / 100) * 60 + (hhmm % 100);
    }

    private static int minuteOfDay(LocalDateTime time) {
        return time.getHour() * 60 + time.getMinute();
    }
}
