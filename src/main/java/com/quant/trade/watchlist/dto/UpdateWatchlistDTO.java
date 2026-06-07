package com.quant.trade.watchlist.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 更新自选股请求 DTO。
 */
public record UpdateWatchlistDTO(

        @NotBlank(message = "name is required")
        @Size(max = 128, message = "name must be at most 128 characters")
        String name,

        @Size(max = 32, message = "market must be at most 32 characters")
        String market,

        @Size(max = 64, message = "groupName must be at most 64 characters")
        String groupName,

        @Size(max = 1024, message = "watchReason must be at most 1024 characters")
        String watchReason,

        @Size(max = 32, message = "tradeStyle must be at most 32 characters")
        String tradeStyle,

        @Size(max = 32, message = "attentionLevel must be at most 32 characters")
        String attentionLevel,

        @DecimalMin(value = "0", inclusive = false, message = "supportPrice must be greater than 0")
        BigDecimal supportPrice,

        @DecimalMin(value = "0", inclusive = false, message = "resistancePrice must be greater than 0")
        BigDecimal resistancePrice,

        @DecimalMin(value = "0", inclusive = false, message = "stopLossPrice must be greater than 0")
        BigDecimal stopLossPrice,

        @Size(max = 1024, message = "riskNote must be at most 1024 characters")
        String riskNote
) {}
