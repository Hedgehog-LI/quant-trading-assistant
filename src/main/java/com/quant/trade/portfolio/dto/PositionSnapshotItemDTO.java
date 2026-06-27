package com.quant.trade.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 持仓快照明细请求 DTO。
 *
 * @param symbol            股票代码
 * @param name              股票名称
 * @param marketType        交易市场，SH/SZ/BJ/UNKNOWN；为空时按 UNKNOWN 处理
 * @param holdingQuantity   持仓数量
 * @param availableQuantity 可卖数量；为空时按持仓数量处理
 * @param costPrice         单位持仓成本
 * @param currentPrice      快照时点当前价
 * @param remark            明细备注
 */
public record PositionSnapshotItemDTO(

        @NotBlank(message = "symbol is required")
        @Size(max = 32, message = "symbol must be at most 32 characters")
        String symbol,

        @Size(max = 128, message = "name must be at most 128 characters")
        String name,

        @Size(max = 32, message = "marketType must be at most 32 characters")
        String marketType,

        @NotNull(message = "holdingQuantity is required")
        @Min(value = 1, message = "holdingQuantity must be greater than 0")
        Long holdingQuantity,

        @PositiveOrZero(message = "availableQuantity must be at least 0")
        Long availableQuantity,

        @NotNull(message = "costPrice is required")
        @DecimalMin(value = "0", message = "costPrice must be at least 0")
        BigDecimal costPrice,

        @NotNull(message = "currentPrice is required")
        @DecimalMin(value = "0", message = "currentPrice must be at least 0")
        BigDecimal currentPrice,

        @Size(max = 512, message = "remark must be at most 512 characters")
        String remark
) {
}
