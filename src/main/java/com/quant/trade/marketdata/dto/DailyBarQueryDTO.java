package com.quant.trade.marketdata.dto;

import java.time.LocalDate;

/** 日 K 查询参数。 */
public record DailyBarQueryDTO(
    String canonicalSymbol,
    LocalDate fromDate,
    LocalDate toDate,
    String adjustType,
    String dataSource
) {}
