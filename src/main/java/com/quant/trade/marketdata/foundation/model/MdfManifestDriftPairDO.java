package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 漂移校验行：manifest 冻结哈希 + bar 当前内容（bar 缺失时 barId 为 null）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfManifestDriftPairDO {
    private String frozenHash;
    private String canonicalSymbol;
    private LocalDate tradeDate;
    private Long barId;
    private String barSymbol;
    private LocalDate barDate;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volume;
    private BigDecimal amount;
    private String dataSource;
    private String adjustType;
}
