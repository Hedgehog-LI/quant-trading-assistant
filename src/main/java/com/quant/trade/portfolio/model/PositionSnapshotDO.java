package com.quant.trade.portfolio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 持仓快照数据库对象。
 * <p>
 * 对应 {@code portfolio_position_snapshot}，记录一次实际持仓盘点的汇总信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionSnapshotDO {

    /** 主键 ID */
    private Long id;

    /** 快照日期 */
    private LocalDate snapshotDate;

    /** 快照具体时间 */
    private LocalDateTime snapshotTime;

    /** 用户可读的快照名称 */
    private String snapshotName;

    /** 数据来源，参见 SnapshotSourceTypeEnum */
    private String sourceType;

    /** 快照状态，参见 SnapshotStatusEnum */
    private String snapshotStatus;

    /** 总持仓成本 */
    private BigDecimal totalCostAmount;

    /** 总持仓市值 */
    private BigDecimal totalMarketValue;

    /** 总浮动盈亏 */
    private BigDecimal totalUnrealizedPnl;

    /** 总浮动盈亏比例，小数表示 */
    private BigDecimal totalPnlRate;

    /** 持仓证券数量 */
    private Integer positionCount;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
