package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 日 K 行情 DO（stock_daily_bar）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDailyBarDO {
    private Long id;
    private String canonicalSymbol;
    private LocalDate tradeDate;
    private String adjustType;
    private String dataSource;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volume;
    private BigDecimal amount;
    private LocalDateTime fetchedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
