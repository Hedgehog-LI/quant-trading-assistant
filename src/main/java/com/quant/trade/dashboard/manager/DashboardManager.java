package com.quant.trade.dashboard.manager;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.constant.RiskConstants;
import com.quant.trade.common.enums.DashboardTodoCodeEnum;
import com.quant.trade.common.enums.DashboardTodoLevelEnum;
import com.quant.trade.common.enums.ReviewStatusEnum;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.dashboard.vo.DashboardTodoVO;
import com.quant.trade.journal.manager.TradeJournalManager;
import com.quant.trade.journal.model.TradeJournalDO;
import com.quant.trade.portfolio.manager.PositionSnapshotManager;
import com.quant.trade.portfolio.model.PositionSnapshotDO;
import com.quant.trade.review.manager.ReviewManager;
import com.quant.trade.tradeplan.manager.TradePlanManager;
import com.quant.trade.tradeplan.model.TradePlanDO;
import com.quant.trade.watchlist.manager.WatchlistManager;
import com.quant.trade.watchlist.model.WatchlistDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dashboard 聚合领域规则层。
 * <p>
 * 负责从各模块聚合数据，生成今日工作台视图、风险提醒和结构化待办。
 * 通过各模块的 Manager 访问数据，不直接调用 Mapper。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardManager {

    /** 已确认快照超过该自然日数视为过期 */
    public static final int STALE_SNAPSHOT_DAYS = 3;

    private final WatchlistManager watchlistManager;
    private final TradePlanManager tradePlanManager;
    private final TradeJournalManager tradeJournalManager;
    private final ReviewManager reviewManager;
    private final PositionSnapshotManager positionSnapshotManager;

    /**
     * 统计启用的自选股数量。
     */
    public long countEnabledWatchlist() {
        return watchlistManager.countEnabled();
    }

    /**
     * 查询高关注自选股列表。
     */
    public List<WatchlistDO> listHighAttentionStocks() {
        return watchlistManager.listHighAttention();
    }

    /**
     * 查询今日交易计划列表。
     */
    public List<TradePlanDO> listTodayPlans(LocalDate date) {
        return tradePlanManager.listByFilter(date, null);
    }

    /**
     * 统计今日允许交易的计划数。
     */
    public long countActivePlans(LocalDate date) {
        return tradePlanManager.countActiveByDate(date);
    }

    /**
     * 统计今日交易记录数。
     */
    public long countTodayJournals(LocalDate date) {
        return tradeJournalManager.countByDate(date);
    }

    /**
     * 统计待复盘数（截至 date，trade_date <= date）。
     */
    public long countPendingReview(LocalDate date) {
        return tradeJournalManager.countPendingReviewUpTo(date);
    }

    /**
     * 查询待复盘交易记录列表（截至 date，trade_date <= date）。
     */
    public List<TradeJournalDO> listPendingReviewJournals(LocalDate date) {
        return tradeJournalManager.listPendingReviewUpTo(date);
    }

    /**
     * 统计今日复盘数。
     */
    public long countTodayReviews(LocalDate date) {
        return reviewManager.countByDate(date);
    }

    /**
     * 构建风险提醒摘要（向后兼容保留）。
     */
    public List<String> buildRiskWarnings(LocalDate date) {
        List<String> warnings = new ArrayList<>();

        List<TradePlanDO> todayPlans = tradePlanManager.listByFilter(date, null);

        // 无止损计划的告警
        long noStopLossCount = todayPlans.stream()
                .filter(p -> Boolean.TRUE.equals(p.getAllowedToTrade()) && p.getStopLossPrice() == null)
                .count();
        if (noStopLossCount > 0) {
            warnings.add(String.format(MessageConstants.DASHBOARD_PLAN_NO_STOP_LOSS, noStopLossCount));
        }

        // 超仓计划的告警
        long overSizedCount = todayPlans.stream()
                .filter(p -> p.getPlannedPositionRatio() != null
                        && p.getPlannedPositionRatio().compareTo(RiskConstants.HIGH_POSITION_RATIO_THRESHOLD) > 0)
                .count();
        if (overSizedCount > 0) {
            warnings.add(String.format(MessageConstants.DASHBOARD_PLAN_OVERSIZED, overSizedCount));
        }

        // 待复盘告警（截至 date）
        long pendingReview = countPendingReview(date);
        if (pendingReview > 0) {
            warnings.add(String.format(MessageConstants.DASHBOARD_PENDING_REVIEW, pendingReview));
        }

        return warnings;
    }

    /**
     * 构建结构化待办列表（不含 POSITION_RECONCILIATION_MISMATCH，该项由 service 层基于对账计算补充）。
     * <p>
     * count==0 的待办不出现。
     *
     * @param date 当前日期
     * @return 待办列表
     */
    public List<DashboardTodoVO> buildTodos(LocalDate date) {
        LocalDate today = date == null ? LocalDate.now() : date;
        List<DashboardTodoVO> todos = new ArrayList<>();

        // PENDING_REVIEW：待复盘交易（截至 date）
        long pendingReview = countPendingReview(today);
        if (pendingReview > 0) {
            todos.add(new DashboardTodoVO(
                    DashboardTodoCodeEnum.PENDING_REVIEW.getCode(),
                    DashboardTodoLevelEnum.WARNING.getCode(),
                    MessageConstants.DASHBOARD_TODO_PENDING_REVIEW_TITLE,
                    MessageConstants.DASHBOARD_TODO_PENDING_REVIEW_DESC,
                    pendingReview,
                    "/journal?reviewStatus=PENDING"));
        }

        // 今日交易记录
        List<TradeJournalDO> todayJournals = tradeJournalManager.listByFilter(today, null, null);

        // UNLINKED_TRADE_PLAN：未关联计划
        long unlinked = todayJournals.stream()
                .filter(j -> j.getPlanId() == null)
                .count();
        if (unlinked > 0) {
            todos.add(new DashboardTodoVO(
                    DashboardTodoCodeEnum.UNLINKED_TRADE_PLAN.getCode(),
                    DashboardTodoLevelEnum.INFO.getCode(),
                    MessageConstants.DASHBOARD_TODO_UNLINKED_PLAN_TITLE,
                    MessageConstants.DASHBOARD_TODO_UNLINKED_PLAN_DESC,
                    unlinked,
                    "/journal"));
        }

        // 预加载今日关联的计划
        Set<Long> planIds = todayJournals.stream()
                .map(TradeJournalDO::getPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, TradePlanDO> planMap = loadPlanMap(planIds);

        // TRADE_AGAINST_PLAN：关联计划 allowedToTrade=false，或交易 followedPlan=false
        long againstPlan = todayJournals.stream()
                .filter(j -> {
                    TradePlanDO plan = j.getPlanId() == null ? null : planMap.get(j.getPlanId());
                    boolean againstAllowed = plan != null && Boolean.FALSE.equals(plan.getAllowedToTrade());
                    boolean notFollowed = Boolean.FALSE.equals(j.getFollowedPlan());
                    return againstAllowed || notFollowed;
                })
                .count();
        if (againstPlan > 0) {
            todos.add(new DashboardTodoVO(
                    DashboardTodoCodeEnum.TRADE_AGAINST_PLAN.getCode(),
                    DashboardTodoLevelEnum.WARNING.getCode(),
                    MessageConstants.DASHBOARD_TODO_TRADE_AGAINST_PLAN_TITLE,
                    MessageConstants.DASHBOARD_TODO_TRADE_AGAINST_PLAN_DESC,
                    againstPlan,
                    "/journal"));
        }

        // MISSING_STOP_LOSS：今日买入交易缺少计划止损
        long missingStopLoss = todayJournals.stream()
                .filter(j -> TradeSideEnum.BUY.getCode().equals(j.getSide()))
                .filter(j -> j.getPlanStopLoss() == null)
                .count();
        if (missingStopLoss > 0) {
            todos.add(new DashboardTodoVO(
                    DashboardTodoCodeEnum.MISSING_STOP_LOSS.getCode(),
                    DashboardTodoLevelEnum.RISK.getCode(),
                    MessageConstants.DASHBOARD_TODO_MISSING_STOP_LOSS_TITLE,
                    MessageConstants.DASHBOARD_TODO_MISSING_STOP_LOSS_DESC,
                    missingStopLoss,
                    "/journal"));
        }

        // STALE_POSITION_SNAPSHOT：截止 date 当日或之前最新已确认快照超过 3 自然日
        PositionSnapshotDO latest = positionSnapshotManager.getLatestConfirmedUpTo(today);
        if (latest != null && latest.getSnapshotTime() != null) {
            long days = ChronoUnit.DAYS.between(latest.getSnapshotTime().toLocalDate(), today);
            if (days > STALE_SNAPSHOT_DAYS) {
                todos.add(new DashboardTodoVO(
                        DashboardTodoCodeEnum.STALE_POSITION_SNAPSHOT.getCode(),
                        DashboardTodoLevelEnum.INFO.getCode(),
                        MessageConstants.DASHBOARD_TODO_STALE_SNAPSHOT_TITLE,
                        MessageConstants.DASHBOARD_TODO_STALE_SNAPSHOT_DESC
                                + "（阈值：" + MessageConstants.DASHBOARD_STALE_SNAPSHOT_THRESHOLD_DAYS_DESC + "）",
                        1L,
                        "/position-snapshots"));
            }
        }

        return todos;
    }

    /**
     * 截止 date 当日或之前最新已确认快照的 ID；不存在时返回 null，供 service 层触发对账待办。
     * 用于历史日期口径：避免查询历史日期时混入未来快照。
     */
    public Long getLatestConfirmedSnapshotIdUpTo(LocalDate date) {
        PositionSnapshotDO latest = positionSnapshotManager.getLatestConfirmedUpTo(date);
        return latest == null ? null : latest.getId();
    }

    private Map<Long, TradePlanDO> loadPlanMap(Set<Long> planIds) {
        Map<Long, TradePlanDO> map = new HashMap<>();
        if (planIds == null || planIds.isEmpty()) {
            return map;
        }
        for (Long id : planIds) {
            TradePlanDO plan = tradePlanManager.selectById(id);
            if (plan != null) {
                map.put(id, plan);
            }
        }
        return map;
    }
}
