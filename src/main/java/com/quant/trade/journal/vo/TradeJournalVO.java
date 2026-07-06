package com.quant.trade.journal.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易记录响应 VO。
 * <p>
 * {@code planDate}、{@code planStatus}、{@code warnings} 为非持久化展示字段：
 * {@code planDate}、{@code planStatus} 由 service 根据 {@code planId} 查询关联的交易计划后填充，
 * 无关联计划时为 {@code null}；{@code warnings} 为创建/校验时的运行时提示，不落库。
 */
public record TradeJournalVO(
        Long id,
        LocalDate tradeDate,
        LocalDateTime tradeTime,
        String symbol,
        String name,
        String side,
        BigDecimal price,
        Long quantity,
        BigDecimal amount,
        BigDecimal commissionFee,
        BigDecimal stampTax,
        BigDecimal transferFee,
        BigDecimal otherFee,
        BigDecimal totalFee,
        BigDecimal positionRatio,
        Long planId,
        /** 关联计划的计划日期（非持久化，由 service 填充） */
        LocalDate planDate,
        /** 关联计划的状态，参见 {@link com.quant.trade.common.enums.PlanStatusEnum}（非持久化，由 service 填充） */
        String planStatus,
        String reason,
        BigDecimal planStopLoss,
        BigDecimal planTakeProfit,
        Boolean followedPlan,
        List<String> emotionTags,
        List<String> mistakeTags,
        String actualResult,
        String reviewStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 创建时返回的风险提示（不持久化） */
        List<String> warnings
) {

    /**
     * 重建 VO 并填充非持久化字段（planDate/planStatus/warnings）。
     *
     * @param planDate   关联计划日期，无关联时为 {@code null}
     * @param planStatus 关联计划状态，无关联时为 {@code null}
     * @param warnings   运行时提示，可为空列表
     * @return 重建后的 VO
     */
    public TradeJournalVO withPlanAndWarnings(LocalDate planDate, String planStatus, List<String> warnings) {
        return new TradeJournalVO(
                id, tradeDate, tradeTime, symbol, name, side, price, quantity, amount,
                commissionFee, stampTax, transferFee, otherFee, totalFee, positionRatio, planId,
                planDate, planStatus, reason, planStopLoss, planTakeProfit,
                followedPlan, emotionTags, mistakeTags, actualResult, reviewStatus,
                createdAt, updatedAt, warnings);
    }
}
