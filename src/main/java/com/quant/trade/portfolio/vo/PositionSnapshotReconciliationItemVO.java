package com.quant.trade.portfolio.vo;

import java.math.BigDecimal;

/**
 * 持仓快照与 FIFO 账本对账明细响应 VO。
 * <p>
 * 数量差异 {@code quantityDifference = snapshotQuantity - ledgerQuantity}，
 * 正值表示快照多于账本，负值表示账本多于快照。
 * 成本字段仅展示，不参与一致性判断。
 */
public record PositionSnapshotReconciliationItemVO(

        /** 股票代码（trim+大写） */
        String symbol,
        /** 股票名称 */
        String name,
        /** 对账状态，参见 {@link com.quant.trade.portfolio.enums.ReconciliationStatusEnum} */
        String status,
        /** 快照持仓数量 */
        long snapshotQuantity,
        /** 账本持仓数量 */
        long ledgerQuantity,
        /** 数量差异 = snapshotQuantity - ledgerQuantity */
        long quantityDifference,
        /** 快照记录的单位成本价 */
        BigDecimal snapshotCostPrice,
        /** 账本 FIFO 平均成本 */
        BigDecimal ledgerAverageCost
) {}
