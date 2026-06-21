package com.quant.trade.portfolio.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 当前持仓响应 VO。
 * <p>
 * 未维护手工当前价时，currentPrice/marketValue/unrealizedPnl/unrealizedReturnPoint 返回 null，
 * 并在 warnings 提示。
 */
public record PositionVO(
        /** 股票代码 */
        String symbol,
        /** 股票名称 */
        String name,
        /** 当前持仓数量 */
        long quantity,
        /** 平均成本（含买入费用摊） */
        BigDecimal averageCost,
        /** 持仓成本合计 */
        BigDecimal costAmount,
        /** 手工当前价（未维护时为 null） */
        BigDecimal currentPrice,
        /** 持仓市值（未维护当前价时为 null） */
        BigDecimal marketValue,
        /** 浮动盈亏（未维护当前价时为 null） */
        BigDecimal unrealizedPnl,
        /** 浮动收益率，百分点（未维护当前价时为 null） */
        BigDecimal unrealizedReturnPoint,
        /** 剩余持仓中最早买入日期 */
        LocalDate firstBuyDate,
        /** 持有天数（today - firstBuyDate） */
        long holdingDays,
        /** 告警提示 */
        List<String> warnings
) {}
