package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 证券别名 DO（stock_alias）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAliasDO {
    private Long id;
    private Long stockBasicId;
    private String alias;
    private String normalizedAlias;
    private byte[] normalizedAliasKey;
    private String aliasType;
    private String language;
    private String dataSource;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
