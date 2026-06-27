package com.quant.trade.portfolio.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 持仓快照列表与汇总响应 VO。
 */
public record PositionSnapshotSummaryVO(
        Long id,
        LocalDate snapshotDate,
        LocalDateTime snapshotTime,
        String snapshotName,
        String sourceType,
        String snapshotStatus,
        BigDecimal totalCostAmount,
        BigDecimal totalMarketValue,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalPnlRate,
        Integer positionCount,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
