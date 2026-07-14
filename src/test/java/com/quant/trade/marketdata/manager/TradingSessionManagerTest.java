package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketCalendarMapper;
import com.quant.trade.marketdata.dao.MarketTradingSessionMapper;
import com.quant.trade.marketdata.model.MarketTradingSessionDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 交易时段 manager 单测：DB 空时回退到 A 股默认窗口和周末规则。 */
@ExtendWith(MockitoExtension.class)
class TradingSessionManagerTest {

    @Mock
    private MarketTradingSessionMapper tradingSessionMapper;
    @Mock
    private MarketCalendarMapper calendarMapper;

    @InjectMocks
    private TradingSessionManager manager;

    @Test
    void emptyDbFallsBackToDefaultWindows() {
        when(tradingSessionMapper.selectByMarket(eq("CN_A"), eq(true))).thenReturn(List.of());
        List<int[]> windows = manager.getSessionWindows("CN_A", false);
        assertFalse(windows.isEmpty());
        // 回退应含 AM + PM 连续竞价
        assertTrue(windows.stream().anyMatch(w -> w[0] == 930 && w[1] == 1130));
        assertTrue(windows.stream().anyMatch(w -> w[0] == 1300 && w[1] == 1500));
    }

    @Test
    void defaultWindowsExcludeAuctionWhenNotIncluded() {
        when(tradingSessionMapper.selectByMarket(eq("CN_A"), eq(true))).thenReturn(List.of());
        List<int[]> windows = manager.getSessionWindows("CN_A", false);
        assertTrue(windows.stream().noneMatch(w -> w[2] == 1));
    }

    @Test
    void defaultWindowsIncludeAuctionWhenIncluded() {
        when(tradingSessionMapper.selectByMarket(eq("CN_A"), eq(true))).thenReturn(List.of());
        List<int[]> windows = manager.getSessionWindows("CN_A", true);
        assertTrue(windows.stream().anyMatch(w -> w[2] == 1));
    }

    @Test
    void dbWindowsRespected() {
        MarketTradingSessionDO am = MarketTradingSessionDO.builder()
                .marketCode("CN_A").sessionType("AM").sessionName("AM")
                .startTime("09:30").endTime("11:30").isAuction(false).sortOrder(1).enabled(true).build();
        when(tradingSessionMapper.selectByMarket("CN_A", true)).thenReturn(List.of(am));
        List<int[]> windows = manager.getSessionWindows("CN_A", true);
        assertEquals(1, windows.size());
        assertEquals(930, windows.get(0)[0]);
        assertEquals(1130, windows.get(0)[1]);
    }

    @Test
    void weekendNotTradingDayWhenNoCalendar() {
        when(calendarMapper.selectByMarketAndDate("CN_A", LocalDate.of(2026, 7, 11)))
                .thenReturn(null);
        // 2026-07-11 是周六
        assertFalse(manager.isTradingDay("CN_A", LocalDate.of(2026, 7, 11)));
    }

    @Test
    void weekdayIsTradingDayWhenNoCalendar() {
        when(calendarMapper.selectByMarketAndDate("CN_A", LocalDate.of(2026, 7, 10)))
                .thenReturn(null);
        // 2026-07-10 是周五
        assertTrue(manager.isTradingDay("CN_A", LocalDate.of(2026, 7, 10)));
    }

    @Test
    void calendarOverridesWeekendRule() {
        when(calendarMapper.selectByMarketAndDate("CN_A", LocalDate.of(2026, 7, 11)))
                .thenReturn(com.quant.trade.marketdata.model.MarketCalendarDO.builder()
                        .isTradingDay(true).build());
        // 日历标记为交易日，即使周六也返回 true
        assertTrue(manager.isTradingDay("CN_A", LocalDate.of(2026, 7, 11)));
    }

    @Test
    void initDefaultSkipsWhenAlreadyExists() {
        when(tradingSessionMapper.countByMarket(WorkbenchConstants.MARKET_CN_A)).thenReturn(4);
        manager.initDefaultCnASessions();
        verify(tradingSessionMapper, never()).batchInsert(anyList());
    }

    @Test
    void initDefaultInsertsWhenEmpty() {
        when(tradingSessionMapper.countByMarket(WorkbenchConstants.MARKET_CN_A)).thenReturn(0);
        manager.initDefaultCnASessions();
        verify(tradingSessionMapper, times(1)).batchInsert(anyList());
    }
}
