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

        String reviewStatus
) {}
