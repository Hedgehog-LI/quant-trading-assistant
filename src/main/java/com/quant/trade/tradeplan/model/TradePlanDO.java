package com.quant.trade.tradeplan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易计划数据库对象。
 * <p>
 * 对应数据库表 {@code trade_plan}，记录盘前计划、买入条件、止损止盈等纪律性信息。
 * <p>
 * 注意：计划只是用户的盘前纪律记录，不是交易建议。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradePlanDO {

    /** 主键 ID */
    private Long id;

    /** 计划日期 */
    private LocalDate planDate;

    /** 股票代码 */
    private String symbol;

    /** 股票名称 */
    private String name;

    /** 计划状态，参见 {@link com.quant.trade.common.enums.PlanStatusEnum} */
    private String planStatus;

    /** 买入条件描述 */
    private String buyCondition;

    /** 卖出条件描述 */
    private String sellCondition;

    /** 止损价 */
    private BigDecimal stopLossPrice;

    /** 止盈价 */
    private BigDecimal takeProfitPrice;

    /** 计划仓位比例（0 到 1） */
    private BigDecimal plannedPositionRatio;

    /** 本计划最大可承受亏损金额 */
    private BigDecimal maxLossAmount;

    /** 今日是否允许交易 */
    private Boolean allowedToTrade;

    /** 风险备注 */
    private String riskNote;

    /** 备注 */
    private String notes;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
