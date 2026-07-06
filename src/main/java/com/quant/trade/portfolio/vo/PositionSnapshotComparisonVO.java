package com.quant.trade.portfolio.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 持仓快照对比响应 VO。
 * <p>
 * 仅比较 {@code CONFIRMED} 状态快照，基准快照时间必须严格早于目标快照时间。
 * 金额均使用 {@link java.math.BigDecimal}，delta 为「目标 - 基准」。
 * 结果仅用于复盘参考，不构成投资建议。
 */
public record PositionSnapshotComparisonVO(

        /** 基准快照 ID */
        Long baseSnapshotId,
        /** 目标快照 ID */
        Long targetSnapshotId,
        /** 基准快照时间 */
        LocalDateTime baseSnapshotTime,
        /** 目标快照时间 */
        LocalDateTime targetSnapshotTime,
        /** 基准快照状态 */
        String baseStatus,
        /** 目标快照状态 */
        String targetStatus,
        /** 总成本变化 */
        BigDecimal totalCostDelta,
        /** 总市值变化 */
        BigDecimal totalMarketValueDelta,
        /** 总浮盈亏变化 */
        BigDecimal totalUnrealizedPnlDelta,
        /** 持仓证券数量变化 */
        int positionCountDelta,
        /** 明细变化列表 */
        List<PositionSnapshotComparisonItemVO> items
) {}
