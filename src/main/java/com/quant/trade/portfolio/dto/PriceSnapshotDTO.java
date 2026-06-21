package com.quant.trade.portfolio.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增或更新手工当前价请求 DTO（upsert：相同 symbol + priceDate 覆盖价格）。
 */
public record PriceSnapshotDTO(

        @NotBlank(message = "symbol is required")
        @Size(max = 32, message = "symbol must be at most 32 characters")
        String symbol,

        @Size(max = 128, message = "name must be at most 128 characters")
        String name,

        @NotNull(message = "currentPrice is required")
        @DecimalMin(value = "0", inclusive = false, message = "currentPrice must be greater than 0")
        BigDecimal currentPrice,

        @NotNull(message = "priceDate is required")
        LocalDate priceDate,

        @Size(max = 512, message = "note must be at most 512 characters")
        String note
) {}
