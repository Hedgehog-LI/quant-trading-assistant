package com.quant.trade.dashboard.vo;

import com.quant.trade.tradeplan.vo.TradePlanVO;
import com.quant.trade.journal.vo.TradeJournalVO;
import com.quant.trade.watchlist.vo.WatchlistVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日工作台响应 VO。
 * <p>
 * {@code todos} 为结构化待办列表（v0.1.1 新增），{@code riskWarnings} 保留以向后兼容。
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

        /** 风险提醒摘要（向后兼容保留） */
        List<String> riskWarnings,

        /** 高关注自选股快捷列表 */
        List<WatchlistVO> highAttentionStocks,

        /** 今日计划快捷列表 */
        List<TradePlanVO> todayPlans,

        /** 待复盘交易快捷列表 */
        List<TradeJournalVO> pendingReviewJournals,

        /** 结构化待办列表（v0.1.1 新增） */
        List<DashboardTodoVO> todos
) {}
