package com.quant.trade.risk.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * 仓位计算请求 DTO。
 */
public record PositionSizeCalculateDTO(

        /** 总资金 */
        @NotNull(message = "totalCapital is required")
        @DecimalMin(value = "0", inclusive = false, message = "totalCapital must be greater than 0")
        BigDecimal totalCapital,

        /** 单笔风险比例（例如 0.01 表示 1%） */
        @NotNull(message = "riskPercent is required")
        @DecimalMin(value = "0", inclusive = false, message = "riskPercent must be greater than 0")
        @DecimalMax(value = "0.1", message = "riskPercent must not exceed 10%")
        BigDecimal riskPercent,

        /** 计划买入价 */
        @NotNull(message = "buyPrice is required")
        @DecimalMin(value = "0", inclusive = false, message = "buyPrice must be greater than 0")
        BigDecimal buyPrice,

        /** 止损价 */
        @NotNull(message = "stopLossPrice is required")
        @DecimalMin(value = "0", inclusive = false, message = "stopLossPrice must be greater than 0")
        BigDecimal stopLossPrice,

        /** 单票最大仓位比例（0 到 1） */
        @NotNull(message = "maxPositionRatio is required")
        @DecimalMin(value = "0", message = "maxPositionRatio must be at least 0")
        @DecimalMax(value = "1", message = "maxPositionRatio must be at most 1")
        BigDecimal maxPositionRatio,

        /** 最小交易单位（A 股默认 100） */
        @Min(value = 1, message = "lotSize must be at least 1")
        Integer lotSize
) {
        public PositionSizeCalculateDTO {
                if (lotSize == null) {
                        lotSize = 100;
                }
        }
}
