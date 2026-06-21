package com.quant.trade.portfolio.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 持仓账本汇总统计响应 VO。
 * <p>
 * 胜率、平均收益率、平均持仓天数均按已结算交易笔数等权计算。
 * 存在卖出超过持仓等异常数据的股票不参与统计，但会在 warnings 提示。
 */
public record PortfolioSummaryVO(
        /** 已实现盈亏合计 */
        BigDecimal realizedPnl,
        /** 浮动盈亏合计（未维护当前价的股票不计入） */
        BigDecimal unrealizedPnl,
        /** 总盈亏 = 已实现 + 浮动 */
        BigDecimal totalPnl,
        /** 当前持仓成本合计 */
        BigDecimal currentCost,
        /** 当前持仓市值合计（未维护当前价的股票不计入） */
        BigDecimal currentMarketValue,
        /** 已结算交易数 */
        int closedTradeCount,
        /** 盈利交易数 */
        int winCount,
        /** 亏损交易数 */
        int lossCount,
        /** 胜率（0~1） */
        BigDecimal winRate,
        /** 平均收益率，百分点（按已结算交易等权） */
        BigDecimal averageReturnPoint,
        /** 平均持仓天数（按已结算交易等权） */
        BigDecimal averageHoldingDays,
        /** 告警提示 */
        List<String> warnings,
        /** 免责声明 */
        String disclaimer
) {}
