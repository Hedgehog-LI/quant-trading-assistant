package com.quant.trade.dashboard.service;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.enums.DashboardTodoCodeEnum;
import com.quant.trade.common.enums.DashboardTodoLevelEnum;
import com.quant.trade.dashboard.manager.DashboardManager;
import com.quant.trade.dashboard.vo.DashboardTodoVO;
import com.quant.trade.dashboard.vo.DashboardTodayVO;
import com.quant.trade.journal.convert.TradeJournalConverter;
import com.quant.trade.portfolio.service.PositionSnapshotService;
import com.quant.trade.portfolio.vo.PositionSnapshotReconciliationVO;
import com.quant.trade.tradeplan.convert.TradePlanConverter;
import com.quant.trade.watchlist.convert.WatchlistConverter;
import com.quant.trade.watchlist.vo.WatchlistVO;
import com.quant.trade.tradeplan.vo.TradePlanVO;
import com.quant.trade.journal.vo.TradeJournalVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dashboard 聚合应用服务。
 * <p>
 * 负责事务边界和数据聚合编排，所有 DB 查询通过 {@link DashboardManager} 完成。
 * 待办列表由 manager 计算基础 5 项，本层补充最新快照的 FIFO 对账待办。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardManager dashboardManager;
    private final WatchlistConverter watchlistConverter;
    private final TradePlanConverter tradePlanConverter;
    private final TradeJournalConverter tradeJournalConverter;
    private final PositionSnapshotService positionSnapshotService;

    /**
     * 获取今日工作台数据。
     */
    @Transactional(readOnly = true)
    public DashboardTodayVO getToday(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }

        List<WatchlistVO> highAttentionStocks = watchlistConverter.toVOList(
                dashboardManager.listHighAttentionStocks());
        List<TradePlanVO> todayPlans = tradePlanConverter.toVOList(
                dashboardManager.listTodayPlans(date));
        List<TradeJournalVO> pendingJournals = tradeJournalConverter.toVOList(
                dashboardManager.listPendingReviewJournals(date));

        List<DashboardTodoVO> todos = buildTodos(date);

        return new DashboardTodayVO(
                date,
                dashboardManager.countEnabledWatchlist(),
                dashboardManager.countActivePlans(date),
                dashboardManager.countTodayJournals(date),
                dashboardManager.countPendingReview(date),
                dashboardManager.countTodayReviews(date),
                dashboardManager.buildRiskWarnings(date),
                highAttentionStocks,
                todayPlans,
                pendingJournals,
                todos
        );
    }

    /**
     * 构建待办列表：manager 算基础 5 项，service 补 POSITION_RECONCILIATION_MISMATCH，
     * 并按 RISK > WARNING > INFO 排序（同级别保留计算顺序）。
     * <p>
     * 全部使用传入 date 口径，避免请求历史日期时混入今天的数据；date 为 null 时由调用方兜底为今天。
     */
    private List<DashboardTodoVO> buildTodos(LocalDate date) {
        List<DashboardTodoVO> todos = new ArrayList<>(dashboardManager.buildTodos(date));
        DashboardTodoVO reconcileTodo = buildReconcileTodo(date);
        if (reconcileTodo != null) {
            todos.add(reconcileTodo);
        }
        todos.sort(Comparator.comparingInt(DashboardService::todoLevelOrder));
        return todos;
    }

    private DashboardTodoVO buildReconcileTodo(LocalDate date) {
        Long snapshotId = dashboardManager.getLatestConfirmedSnapshotIdUpTo(date);
        if (snapshotId == null) {
            return null;
        }
        try {
            PositionSnapshotReconciliationVO recon = positionSnapshotService.reconcile(snapshotId);
            if (recon == null || !recon.hasMismatch()) {
                return null;
            }
            return new DashboardTodoVO(
                    DashboardTodoCodeEnum.POSITION_RECONCILIATION_MISMATCH.getCode(),
                    DashboardTodoLevelEnum.WARNING.getCode(),
                    MessageConstants.DASHBOARD_TODO_RECONCILE_MISMATCH_TITLE,
                    MessageConstants.DASHBOARD_TODO_RECONCILE_MISMATCH_DESC,
                    recon.mismatchCount(),
                    "/position-snapshots");
        } catch (Exception e) {
            // 对账失败不应阻断工作台展示
            log.warn("Failed to build reconciliation todo for snapshot {}", snapshotId, e);
            return null;
        }
    }

    /** RISK 优先，其次 WARNING，最后 INFO。 */
    private static int todoLevelOrder(DashboardTodoVO todo) {
        return switch (todo.level()) {
            case "RISK" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }
}
