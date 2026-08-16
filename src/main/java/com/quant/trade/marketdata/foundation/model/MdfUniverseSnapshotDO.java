package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 历史股票池快照 DO（mdf_universe_snapshot；市值=元、换手率=小数）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfUniverseSnapshotDO {
    private Long id;
    private String providerCode;
    private String canonicalSymbol;
    private String symbol;
    private String name;
    private String market;
    private BigDecimal totalMarketCap;
    private BigDecimal circulatingMarketCap;
    private BigDecimal turnoverRate;
    private LocalDate asOfDate;
    private LocalDateTime fetchedAt;
    private LocalDateTime createdAt;
}
