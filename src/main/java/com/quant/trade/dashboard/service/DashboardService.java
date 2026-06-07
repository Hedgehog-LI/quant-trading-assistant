package com.quant.trade.dashboard.service;

import com.quant.trade.dashboard.manager.DashboardManager;
import com.quant.trade.dashboard.vo.DashboardTodayVO;
import com.quant.trade.journal.convert.TradeJournalConverter;
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
import java.util.List;

/**
 * Dashboard 聚合应用服务。
 * <p>
 * 负责事务边界和数据聚合编排，所有 DB 查询通过 {@link DashboardManager} 完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardManager dashboardManager;
    private final WatchlistConverter watchlistConverter;
    private final TradePlanConverter tradePlanConverter;
    private final TradeJournalConverter tradeJournalConverter;

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
                dashboardManager.listPendingReviewJournals());

        return new DashboardTodayVO(
                date,
                dashboardManager.countEnabledWatchlist(),
                dashboardManager.countActivePlans(date),
                dashboardManager.countTodayJournals(date),
                dashboardManager.countPendingReview(),
                dashboardManager.countTodayReviews(date),
                dashboardManager.buildRiskWarnings(date),
                highAttentionStocks,
                todayPlans,
                pendingJournals
        );
    }
}
