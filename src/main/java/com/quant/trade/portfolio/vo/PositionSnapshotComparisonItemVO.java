package com.quant.trade.portfolio.vo;

import java.math.BigDecimal;

/**
 * 持仓快照对比明细响应 VO。
 * <p>
 * 单只证券在两次快照之间的变化。缺失侧的数量和金额按 0 参与 delta 计算。
 */
public record PositionSnapshotComparisonItemVO(

        /** 股票代码（trim+大写） */
        String symbol,
        /** 股票名称 */
        String name,
        /** 变化类型，参见 {@link com.quant.trade.portfolio.enums.SnapshotChangeTypeEnum} */
        String changeType,
        /** 基准持仓数量，基准缺失时为 {@code null} */
        Long baseQuantity,
        /** 目标持仓数量，目标缺失时为 {@code null} */
        Long targetQuantity,
        /** 数量变化 = targetQuantity - baseQuantity */
        Long quantityDelta,
        /** 基准成本价，基准缺失时为 {@code null} */
        BigDecimal baseCostPrice,
        /** 目标成本价，目标缺失时为 {@code null} */
        BigDecimal targetCostPrice,
        /** 市值变化 = target.marketValue - base.marketValue */
        BigDecimal marketValueDelta,
        /** 浮盈亏变化 = target.unrealizedPnl - base.unrealizedPnl */
        BigDecimal unrealizedPnlDelta
) {}
