package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** 新增证券主数据请求。 */
public record CreateStockBasicDTO(
    @NotBlank String symbol,
    @NotBlank String market,
    String name,
    LocalDate listDate,
    Boolean delisted
) {}
