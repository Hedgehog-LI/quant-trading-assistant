package com.quant.trade.tradeplan.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * 更新交易计划请求 DTO。
 */
public record UpdateTradePlanDTO(

        @NotBlank(message = "planStatus is required")
        @Size(max = 32, message = "planStatus must be at most 32 characters")
        String planStatus,

        @Size(max = 1024, message = "buyCondition must be at most 1024 characters")
        String buyCondition,

        @Size(max = 1024, message = "sellCondition must be at most 1024 characters")
        String sellCondition,

        @DecimalMin(value = "0", inclusive = false, message = "stopLossPrice must be greater than 0")
        BigDecimal stopLossPrice,

        @DecimalMin(value = "0", inclusive = false, message = "takeProfitPrice must be greater than 0")
        BigDecimal takeProfitPrice,

        @DecimalMin(value = "0", message = "plannedPositionRatio must be at least 0")
        @DecimalMax(value = "1", message = "plannedPositionRatio must be at most 1")
        BigDecimal plannedPositionRatio,

        @DecimalMin(value = "0", message = "maxLossAmount must be at least 0")
        BigDecimal maxLossAmount,

        Boolean allowedToTrade,

        @Size(max = 1024, message = "riskNote must be at most 1024 characters")
        String riskNote,

        @Size(max = 2048, message = "notes must be at most 2048 characters")
        String notes
) {}
