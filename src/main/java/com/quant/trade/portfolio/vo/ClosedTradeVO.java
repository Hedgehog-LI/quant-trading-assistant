package com.quant.trade.portfolio.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 已结算交易响应 VO（一笔卖出对应一个已结算交易，可能配对多个买入批次）。
 */
public record ClosedTradeVO(
        String symbol,
        String name,
        /** 买入日期（多批次时取最早） */
        LocalDate buyDate,
        /** 卖出日期 */
        LocalDate sellDate,
        /** 持有天数（sellDate - buyDate） */
        long holdingDays,
        /** 配对数量 */
        long quantity,
        /** 买入均价（含买入费用摊） */
        BigDecimal buyAveragePrice,
        /** 卖出均价 */
        BigDecimal sellAveragePrice,
        /** 配对买入成本合计 */
        BigDecimal costAmount,
        /** 卖出毛收入（price × quantity） */
        BigDecimal sellAmount,
        /** 本笔交易总费用（买入费用按比例摊 + 卖出费用） */
        BigDecimal totalFee,
        /** 已实现盈亏（卖出净收入 - 配对买入成本） */
        BigDecimal realizedPnl,
        /** 收益率，百分点（realizedPnl / costAmount × 100） */
        BigDecimal returnPoint,
        /** 是否盈利 */
        boolean profitable,
        /** 配对的买入交易记录 ID 列表 */
        List<Long> buyJournalIds,
        /** 卖出交易记录 ID */
        Long sellJournalId
) {}
