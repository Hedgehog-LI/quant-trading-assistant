package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** 日 K 同步请求 DTO（scope 字段解析后的结构）。 */
public record DailyBarSyncRequestDTO(
    @NotBlank String canonicalSymbol,
    LocalDate startDate,
    LocalDate endDate,
    String adjustType
) {}
