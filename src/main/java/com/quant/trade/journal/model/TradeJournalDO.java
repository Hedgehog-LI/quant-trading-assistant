package com.quant.trade.journal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易记录数据库对象。
 * <p>
 * 对应数据库表 {@code trade_journal}，记录手工录入的真实或模拟交易。
 * <p>
 * 注意：交易记录为手工录入，不允许接券商自动同步。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeJournalDO {

    /** 主键 ID */
    private Long id;

    /** 交易日期 */
    private LocalDate tradeDate;

    /** 交易时间（精确到分钟） */
    private LocalDateTime tradeTime;

    /** 股票代码 */
    private String symbol;

    /** 股票名称 */
    private String name;

    /** 交易方向，参见 {@link com.quant.trade.common.enums.TradeSideEnum} */
    private String side;

    /** 成交价 */
    private BigDecimal price;

    /** 成交数量（股） */
    private Long quantity;

    /** 成交金额（自动计算 = price × quantity） */
    private BigDecimal amount;

    /** 佣金 */
    private BigDecimal commissionFee;

    /** 印花税 */
    private BigDecimal stampTax;

    /** 过户费 */
    private BigDecimal transferFee;

    /** 其他费用 */
    private BigDecimal otherFee;

    /** 总费用（自动计算：传入则以传入值为准，否则为各项之和） */
    private BigDecimal totalFee;

    /** 仓位比例（0 到 1） */
    private BigDecimal positionRatio;

    /** 关联的交易计划 ID */
    private Long planId;

    /** 交易理由 */
    private String reason;

    /** 计划止损价 */
    private BigDecimal planStopLoss;

    /** 计划止盈价 */
    private BigDecimal planTakeProfit;

    /** 是否按计划执行 */
    private Boolean followedPlan;

    /** 情绪标签（逗号分隔字符串，API 层暴露为 List） */
    private String emotionTags;

    /** 错误标签（逗号分隔字符串，API 层暴露为 List） */
    private String mistakeTags;

    /** 实际结果描述 */
    private String actualResult;

    /** 复盘状态，参见 {@link com.quant.trade.common.enums.ReviewStatusEnum} */
    private String reviewStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
