package com.quant.trade.journal.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新交易记录请求 DTO。
 */
public record UpdateTradeJournalDTO(

        @NotBlank(message = "side is required")
        String side,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0", inclusive = false, message = "price must be greater than 0")
        BigDecimal price,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Long quantity,

        @DecimalMin(value = "0", message = "commissionFee must be at least 0")
        BigDecimal commissionFee,

        @DecimalMin(value = "0", message = "stampTax must be at least 0")
        BigDecimal stampTax,

        @DecimalMin(value = "0", message = "transferFee must be at least 0")
        BigDecimal transferFee,

        @DecimalMin(value = "0", message = "otherFee must be at least 0")
        BigDecimal otherFee,

        @DecimalMin(value = "0", message = "totalFee must be at least 0")
        BigDecimal totalFee,

        @DecimalMin(value = "0", message = "positionRatio must be at least 0")
        BigDecimal positionRatio,

        Long planId,

        @Size(max = 2048, message = "reason must be at most 2048 characters")
        String reason,

        @DecimalMin(value = "0", message = "planStopLoss must be at least 0")
        BigDecimal planStopLoss,

        @DecimalMin(value = "0", message = "planTakeProfit must be at least 0")
        BigDecimal planTakeProfit,

        Boolean followedPlan,

        List<String> emotionTags,

        List<String> mistakeTags,

        @Size(max = 1024, message = "actualResult must be at most 1024 characters")
        String actualResult,

        String reviewStatus,

        /**
         * 显式解除计划关联（v0.1.1 三态更新）。
         * <ul>
         *   <li>{@code true}：将 plan_id 置空（解绑）；</li>
         *   <li>{@code null}/{@code false} 且 {@code planId != null}：更新为新 planId；</li>
         *   <li>其余：保持原 planId（部分更新语义不变）。</li>
         * </ul>
         * 禁止用 0/-1 等魔法值表达"解绑"。
         */
        Boolean unlinkPlan
) {}
