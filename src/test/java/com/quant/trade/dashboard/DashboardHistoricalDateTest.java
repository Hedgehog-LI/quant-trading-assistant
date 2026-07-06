package com.quant.trade.dashboard;

import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.dashboard.service.DashboardService;
import com.quant.trade.dashboard.vo.DashboardTodayVO;
import com.quant.trade.dashboard.vo.DashboardTodoVO;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.service.TradeJournalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dashboard 历史日期口径测试（v0.1.1 收尾修复）。
 * <p>
 * 验证请求历史日期时，todos 与统计只反映该日期的数据，不混入今天的数据。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardHistoricalDateTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TradeJournalService tradeJournalService;

    @Test
    void historicalDateTodosExcludeTodayJournals() {
        // 历史（2026-07-01）与今日各一条未关联计划的买入交易
        createBuyJournal(LocalDate.of(2026, 7, 1), "111111");
        createBuyJournal(LocalDate.now(), "222222");

        DashboardTodayVO historical = dashboardService.getToday(LocalDate.of(2026, 7, 1));
        DashboardTodayVO today = dashboardService.getToday(null);

        // todayJournalCount 严格按请求日期
        assertEquals(1, historical.todayJournalCount());
        // pendingReviewCount 只算截至历史日期的待复盘，绝不含未来交易
        assertEquals(1, historical.pendingReviewCount());
        // PENDING_REVIEW 待办 count 也只算截至历史日期
        assertEquals(1, todoCount(historical.todos(), "PENDING_REVIEW"));

        // UNLINKED_TRADE_PLAN 待办只统计请求日期内的未关联交易
        assertEquals(1, todoCount(historical.todos(), "UNLINKED_TRADE_PLAN"));
        assertTrue(todoCount(today.todos(), "UNLINKED_TRADE_PLAN") >= 1);

        // 历史日期的今日交易数不会把今天的 222222 算进来
        assertEquals(1, historical.todayJournalCount());
    }

    @Test
    void nullDateFallsBackToToday() {
        createBuyJournal(LocalDate.now(), "333333");
        DashboardTodayVO result = dashboardService.getToday(null);
        // date 兜底为今天，todayJournalCount 至少含今日造的 1 条
        assertTrue(result.todayJournalCount() >= 1);
    }

    private long todoCount(List<DashboardTodoVO> todos, String code) {
        return todos.stream()
                .filter(t -> code.equals(t.code()))
                .findFirst()
                .map(DashboardTodoVO::count)
                .orElse(0L);
    }

    private void createBuyJournal(LocalDate date, String symbol) {
        tradeJournalService.create(new CreateTradeJournalDTO(
                date, null, symbol, symbol,
                TradeSideEnum.BUY.getCode(), new BigDecimal("10.00"), 100L,
                null, null, null, null, null,
                null, null, "测试",
                new BigDecimal("9.50"), new BigDecimal("11.00"),
                true, null, null, null));
    }
}
