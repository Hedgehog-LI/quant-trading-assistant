package com.quant.trade.dashboard;

import com.quant.trade.dashboard.service.DashboardService;
import com.quant.trade.dashboard.vo.DashboardTodayVO;
import com.quant.trade.dashboard.vo.DashboardTodoVO;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.portfolio.dto.CreatePositionSnapshotDTO;
import com.quant.trade.portfolio.dto.PositionSnapshotItemDTO;
import com.quant.trade.portfolio.service.PositionSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dashboard 待办中心聚合测试（v0.1.1 功能五）。
 * <p>
 * 验证 PENDING_REVIEW / UNLINKED_TRADE_PLAN / MISSING_STOP_LOSS / STALE_POSITION_SNAPSHOT 的触发。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardTodosTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TradeJournalService tradeJournalService;

    @Autowired
    private PositionSnapshotService positionSnapshotService;

    @Test
    void pendingReviewTodoTriggered() {
        createBuyJournal("300750", true);
        DashboardTodayVO vo = dashboardService.getToday(null);
        assertTrue(vo.todos().stream().anyMatch(t -> "PENDING_REVIEW".equals(t.code())));
        assertTrue(vo.todos().stream().anyMatch(t -> "UNLINKED_TRADE_PLAN".equals(t.code())));
    }

    @Test
    void missingStopLossTodoTriggered() {
        createBuyJournal("600519", false);
        DashboardTodayVO vo = dashboardService.getToday(null);
        assertTrue(vo.todos().stream()
                .anyMatch(t -> "MISSING_STOP_LOSS".equals(t.code()) && t.count() >= 1));
    }

    @Test
    void staleSnapshotTodoTriggered() {
        createOldConfirmedSnapshot();
        DashboardTodayVO vo = dashboardService.getToday(null);
        assertTrue(vo.todos().stream().anyMatch(t -> "STALE_POSITION_SNAPSHOT".equals(t.code())));
    }

    @Test
    void todosNeverNullAndSortedByLevel() {
        DashboardTodayVO vo = dashboardService.getToday(null);
        assertNotNull(vo.todos());
        // 如有 RISK，应排在 WARNING/INFO 之前
        List<String> levels = vo.todos().stream().map(DashboardTodoVO::level).toList();
        int firstInfo = levels.indexOf("INFO");
        int firstWarning = levels.indexOf("WARNING");
        int firstRisk = levels.indexOf("RISK");
        if (firstRisk >= 0 && firstWarning >= 0) {
            assertTrue(firstRisk < firstWarning);
        }
        if (firstWarning >= 0 && firstInfo >= 0) {
            assertTrue(firstWarning < firstInfo);
        }
    }

    private void createBuyJournal(String symbol, boolean withStopLoss) {
        tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.now(), null, symbol, symbol + "名称",
                "BUY", new BigDecimal("10.00"), 100L,
                null, null, null, null, null,
                null, null, "测试",
                withStopLoss ? new BigDecimal("9.50") : null,
                null, true, null, null, null));
    }

    private void createOldConfirmedSnapshot() {
        positionSnapshotService.create(new CreatePositionSnapshotDTO(
                LocalDate.now().minusDays(5),
                LocalDateTime.now().minusDays(5),
                "旧快照", "MANUAL", "CONFIRMED", null,
                List.of(new PositionSnapshotItemDTO(
                        "300750", "宁德时代", "SH", 100L, 100L,
                        new BigDecimal("10.00"), new BigDecimal("10.00"), null))));
    }

    @Test
    void followedPlanFalseTriggersTradeAgainstPlan() {
        createBuyWithFollowedPlan("600519", false);
        DashboardTodayVO vo = dashboardService.getToday(null);
        assertTrue(vo.todos().stream()
                .anyMatch(t -> "TRADE_AGAINST_PLAN".equals(t.code()) && t.count() >= 1));
    }

    private void createBuyWithFollowedPlan(String symbol, Boolean followedPlan) {
        tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.now(), null, symbol, symbol + "名称",
                "BUY", new BigDecimal("10.00"), 100L,
                null, null, null, null, null,
                null, null, "测试",
                new BigDecimal("9.50"), new BigDecimal("11.00"), followedPlan,
                null, null, null));
    }
}
