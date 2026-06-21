package com.quant.trade.journal.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建交易记录请求 DTO。
 */
public record CreateTradeJournalDTO(

        @NotNull(message = "tradeDate is required")
        LocalDate tradeDate,

        /** 交易时间（精确到分钟） */
        LocalDateTime tradeTime,

        @NotBlank(message = "symbol is required")
        @Size(max = 32, message = "symbol must be at most 32 characters")
        String symbol,

        @Size(max = 128, message = "name must be at most 128 characters")
        String name,

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

        /** 情绪标签列表 */
        List<String> emotionTags,

        /** 错误标签列表 */
        List<String> mistakeTags,

        @Size(max = 1024, message = "actualResult must be at most 1024 characters")
        String actualResult
) {}
