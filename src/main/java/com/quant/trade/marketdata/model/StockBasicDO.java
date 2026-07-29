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
    private String nameCn;
    private String nameHk;
    private String nameEn;
    private String shortName;
    private String pinyinFull;
    private String pinyinAbbr;
    private String exchange;
    private String currency;
    private String securityType;
    private String listStatus;
    private String dataSource;
    private LocalDateTime sourceUpdatedAt;
    private String sourceHash;
    private LocalDate listDate;
    private Boolean delisted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
