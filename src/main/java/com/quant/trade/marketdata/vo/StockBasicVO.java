package com.quant.trade.marketdata.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 证券主数据响应 VO。 */
public record StockBasicVO(
    Long id,
    String canonicalSymbol,
    String symbol,
    String name,
    String market,
    LocalDate listDate,
    Boolean delisted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
