package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 外部最新价快照响应 VO。 */
public record StockQuoteSnapshotVO(
    Long id, String canonicalSymbol, LocalDateTime quoteTime,
    BigDecimal currentPrice, BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
    BigDecimal preClosePrice, Long volume, BigDecimal amount, String tradeStatus,
    String dataSource, LocalDateTime fetchedAt
) {}
