package com.quant.trade.dashboard.manager;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.constant.RiskConstants;
import com.quant.trade.common.enums.ReviewStatusEnum;
import com.quant.trade.journal.manager.TradeJournalManager;
import com.quant.trade.journal.model.TradeJournalDO;
import com.quant.trade.review.manager.ReviewManager;
import com.quant.trade.tradeplan.manager.TradePlanManager;
import com.quant.trade.tradeplan.model.TradePlanDO;
import com.quant.trade.watchlist.manager.WatchlistManager;
import com.quant.trade.watchlist.model.WatchlistDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard 聚合领域规则层。
 * <p>
 * 负责从各模块聚合数据，生成今日工作台视图和风险提醒。
 * 通过各模块的 Manager 访问数据，不直接调用 Mapper。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardManager {

    private final WatchlistManager watchlistManager;
    private final TradePlanManager tradePlanManager;
    private final TradeJournalManager tradeJournalManager;
    private final ReviewManager reviewManager;

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
     * 统计待复盘数。
     */
    public long countPendingReview() {
        return tradeJournalManager.countByReviewStatus(ReviewStatusEnum.PENDING.getCode());
    }

    /**
     * 查询待复盘交易记录列表。
     */
    public List<TradeJournalDO> listPendingReviewJournals() {
        return tradeJournalManager.listByFilter(null, null, ReviewStatusEnum.PENDING.getCode());
    }

    /**
     * 统计今日复盘数。
     */
    public long countTodayReviews(LocalDate date) {
        return reviewManager.countByDate(date);
    }

    /**
     * 构建风险提醒摘要。
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

        // 待复盘告警
        long pendingReview = countPendingReview();
        if (pendingReview > 0) {
            warnings.add(String.format(MessageConstants.DASHBOARD_PENDING_REVIEW, pendingReview));
        }

        return warnings;
    }
}
