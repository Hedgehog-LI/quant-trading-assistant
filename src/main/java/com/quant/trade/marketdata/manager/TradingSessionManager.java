package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketCalendarMapper;
import com.quant.trade.marketdata.dao.MarketTradingSessionMapper;
import com.quant.trade.marketdata.model.MarketCalendarDO;
import com.quant.trade.marketdata.model.MarketTradingSessionDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 交易时段与日历领域逻辑。
 * <p>
 * 如果 market_trading_session / market_calendar 表有数据，优先用 DB 数据；
 * 否则回退到 A 股默认交易窗口和周末规则，保证系统在日历未初始化时仍可工作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingSessionManager {

    private final MarketTradingSessionMapper tradingSessionMapper;
    private final MarketCalendarMapper calendarMapper;

    /**
     * 获取指定市场的交易时段窗口列表。
     *
     * @return 每项 int[3] = {startHHMM, endHHMM, isAuction(0/1)}
     */
    public List<int[]> getSessionWindows(String marketCode, boolean includeAuction) {
        List<MarketTradingSessionDO> sessions = tradingSessionMapper.selectByMarket(marketCode, true);
        List<int[]> windows = new ArrayList<>();
        if (sessions == null || sessions.isEmpty()) {
            // 回退到 A 股默认窗口
            windows.add(new int[]{930, 1130, 0});   // AM 连续竞价
            windows.add(new int[]{1300, 1500, 0});   // PM 连续竞价
            windows.add(new int[]{915, 925, 1});     // 集合竞价（开盘）
            windows.add(new int[]{1457, 1500, 1});   // 集合竞价（收盘）
            if (!includeAuction) {
                windows.removeIf(w -> w[2] == 1);
            }
            return windows;
        }
        for (MarketTradingSessionDO s : sessions) {
            boolean isAuction = Boolean.TRUE.equals(s.getIsAuction());
            if (isAuction && !includeAuction) continue;
            int start = parseHHMM(s.getStartTime());
            int end = parseHHMM(s.getEndTime());
            windows.add(new int[]{start, end, isAuction ? 1 : 0});
        }
        return windows;
    }

    /**
     * 判断指定日期是否为交易日。
     * 优先查日历表；日历表无数据则用周末规则回退。
     */
    public boolean isTradingDay(String marketCode, LocalDate date) {
        MarketCalendarDO cal = calendarMapper.selectByMarketAndDate(marketCode, date);
        if (cal != null) {
            return Boolean.TRUE.equals(cal.getIsTradingDay());
        }
        // 回退：周末非交易日
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /**
     * 获取指定日期（含当天）最近的交易日。
     * 优先查日历；无日历则用周末规则回退。
     */
    public LocalDate getLatestTradingDayOnOrBefore(String marketCode, LocalDate date) {
        MarketCalendarDO cal = calendarMapper.selectLatestTradingDayOnOrBefore(marketCode, date);
        if (cal != null) {
            return cal.getTradeDate();
        }
        // 回退：最多往前找 4 天
        LocalDate d = date;
        for (int i = 0; i < 7; i++) {
            if (isTradingDayByWeekend(d)) {
                return d;
            }
            d = d.minusDays(1);
        }
        return date;
    }

    /** 初始化 A 股默认交易时段（幂等，已有数据则跳过）。 */
    public void initDefaultCnASessions() {
        int count = tradingSessionMapper.countByMarket(WorkbenchConstants.MARKET_CN_A);
        if (count > 0) {
            return;
        }
        List<MarketTradingSessionDO> defaults = List.of(
                build(WorkbenchConstants.MARKET_CN_A, WorkbenchConstants.SESSION_AUCTION, "集合竞价（开盘）", "09:15", "09:25", true, 1),
                build(WorkbenchConstants.MARKET_CN_A, WorkbenchConstants.SESSION_AM, "上午连续竞价", "09:30", "11:30", false, 2),
                build(WorkbenchConstants.MARKET_CN_A, WorkbenchConstants.SESSION_PM, "下午连续竞价", "13:00", "14:57", false, 3),
                build(WorkbenchConstants.MARKET_CN_A, WorkbenchConstants.SESSION_AUCTION, "集合竞价（收盘）", "14:57", "15:00", true, 4)
        );
        tradingSessionMapper.batchInsert(defaults);
        log.info("初始化 A 股默认交易时段: {} 条", defaults.size());
    }

    private MarketTradingSessionDO build(String marketCode, String type, String name,
                                         String start, String end, boolean auction, int order) {
        return MarketTradingSessionDO.builder()
                .marketCode(marketCode).sessionType(type).sessionName(name)
                .startTime(start).endTime(end).isAuction(auction).sortOrder(order)
                .enabled(true).build();
    }

    private int parseHHMM(String time) {
        // "09:30" -> 930
        String cleaned = time.replace(":", "");
        return Integer.parseInt(cleaned);
    }

    private boolean isTradingDayByWeekend(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
