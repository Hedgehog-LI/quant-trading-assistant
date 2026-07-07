package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 日 K 响应 VO。 */
public record StockDailyBarVO(
    Long id,
    String canonicalSymbol,
    LocalDate tradeDate,
    String adjustType,
    String dataSource,
    BigDecimal openPrice,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    BigDecimal closePrice,
    Long volume,
    BigDecimal amount,
    LocalDateTime fetchedAt
) {}
