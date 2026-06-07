package com.quant.trade.watchlist.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 自选股数据库对象。
 * <p>
 * 对应数据库表 {@code watchlist}，包含自选股基础信息和关注理由。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistDO {

    /** 主键 ID */
    private Long id;

    /** 股票代码（如 300750） */
    private String symbol;

    /** 股票名称（如 宁德时代） */
    private String name;

    /** 市场类型，参见 {@link com.quant.trade.common.enums.MarketTypeEnum} */
    private String market;

    /** 分组名称 */
    private String groupName;

    /** 关注理由 */
    private String watchReason;

    /** 交易风格，参见 {@link com.quant.trade.common.enums.TradeStyleEnum} */
    private String tradeStyle;

    /** 关注等级，参见 {@link com.quant.trade.common.enums.AttentionLevelEnum} */
    private String attentionLevel;

    /** 支撑位价格 */
    private BigDecimal supportPrice;

    /** 压力位价格 */
    private BigDecimal resistancePrice;

    /** 默认止损价 */
    private BigDecimal stopLossPrice;

    /** 风险备注 */
    private String riskNote;

    /** 是否启用（false 表示软删除） */
    private Boolean enabled;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
