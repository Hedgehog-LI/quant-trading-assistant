package com.quant.trade.portfolio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 持仓快照明细数据库对象。
 * <p>
 * 对应 {@code portfolio_position_snapshot_item}，记录快照中的单只证券持仓。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionSnapshotItemDO {

    /** 主键 ID */
    private Long id;

    /** 所属快照 ID */
    private Long snapshotId;

    /** 股票代码 */
    private String symbol;

    /** 股票名称 */
    private String name;

    /** 交易市场，参见 PositionMarketTypeEnum */
    private String marketType;

    /** 持仓数量 */
    private Long holdingQuantity;

    /** 当时可卖数量 */
    private Long availableQuantity;

    /** 单位持仓成本 */
    private BigDecimal costPrice;

    /** 快照时点当前价 */
    private BigDecimal currentPrice;

    /** 持仓成本金额 */
    private BigDecimal costAmount;

    /** 当前市值 */
    private BigDecimal marketValue;

    /** 浮动盈亏 */
    private BigDecimal unrealizedPnl;

    /** 浮动盈亏比例，小数表示 */
    private BigDecimal pnlRate;

    /** 本证券占快照总市值比例，小数表示 */
    private BigDecimal positionRatio;

    /** 明细显示顺序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
