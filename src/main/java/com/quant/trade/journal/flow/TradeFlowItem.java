package com.quant.trade.journal.flow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易流水纯数据项（portfolio FIFO 计算器入参契约）。
 * <p>
 * 扁平、无业务行为，由 journal 模块从 {@link com.quant.trade.journal.model.TradeJournalDO} 转换而来，
 * 供 portfolio 计算器按 FIFO 规则配对买卖、计算持仓与盈亏。
 * <p>
 * quantity 为基本类型 long：交易记录的 quantity 在 DB 层为 NOT NULL，且创建时校验 >= 1。
 */
public record TradeFlowItem(
        Long id,
        LocalDate tradeDate,
        LocalDateTime tradeTime,
        String symbol,
        String name,
        /** 交易方向，参见 {@link com.quant.trade.common.enums.TradeSideEnum} */
        String side,
        BigDecimal price,
        long quantity,
        BigDecimal commissionFee,
        BigDecimal stampTax,
        BigDecimal transferFee,
        BigDecimal otherFee,
        BigDecimal totalFee
) {}
