package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 证券主数据 DO（stock_basic）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBasicDO {
    private Long id;
    private String canonicalSymbol;
    private String symbol;
    private String name;
    private String market;
    private LocalDate listDate;
    private Boolean delisted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
