package com.quant.trade.dashboard.vo;

import com.quant.trade.tradeplan.vo.TradePlanVO;
import com.quant.trade.journal.vo.TradeJournalVO;
import com.quant.trade.watchlist.vo.WatchlistVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日工作台响应 VO。
 */
public record DashboardTodayVO(

        /** 当前日期 */
        LocalDate date,

        /** 启用自选股数量 */
        long enabledWatchlistCount,

        /** 今日有效交易计划数量 */
        long activePlanCount,

        /** 今日交易记录数量 */
        long todayJournalCount,

        /** 待复盘交易数量 */
        long pendingReviewCount,

        /** 今日复盘数量 */
        long todayReviewCount,

        /** 风险提醒摘要 */
        List<String> riskWarnings,

        /** 高关注自选股快捷列表 */
        List<WatchlistVO> highAttentionStocks,

        /** 今日计划快捷列表 */
        List<TradePlanVO> todayPlans,

        /** 待复盘交易快捷列表 */
        List<TradeJournalVO> pendingReviewJournals
) {}
