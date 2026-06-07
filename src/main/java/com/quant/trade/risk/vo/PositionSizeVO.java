package com.quant.trade.risk.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仓位计算响应 VO。
 */
public record PositionSizeVO(

        /** 单笔亏损金额 = totalCapital × riskPercent */
        BigDecimal riskAmount,

        /** 每股止损距离 = buyPrice − stopLossPrice */
        BigDecimal perShareRisk,

        /** 基于风险的可用股数 = floor(riskAmount / perShareRisk) */
        long riskBasedQuantity,

        /** 基于仓位上限的可用股数 = floor(totalCapital × maxPositionRatio / buyPrice) */
        long positionCapQuantity,

        /** 最终建议股数 = min(riskBased, positionCap)，按手数取整 */
        long finalQuantity,

        /** 预估亏损金额 = finalQuantity × perShareRisk */
        BigDecimal estimatedLoss,

        /** 建仓金额 = finalQuantity × buyPrice */
        BigDecimal positionAmount,

        /** 建仓比例 = positionAmount / totalCapital */
        BigDecimal positionRatio,

        /** 风险等级 */
        String riskLevel,

        /** 风险告警列表 */
        List<String> warnings,

        /** 免责声明 */
        String disclaimer
) {}
