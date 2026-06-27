package com.quant.trade.portfolio.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 持仓快照明细响应 VO。
 */
public record PositionSnapshotItemVO(
        Long id,
        Long snapshotId,
        String symbol,
        String name,
        String marketType,
        Long holdingQuantity,
        Long availableQuantity,
        BigDecimal costPrice,
        BigDecimal currentPrice,
        BigDecimal costAmount,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal pnlRate,
        BigDecimal positionRatio,
        Integer sortOrder,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
