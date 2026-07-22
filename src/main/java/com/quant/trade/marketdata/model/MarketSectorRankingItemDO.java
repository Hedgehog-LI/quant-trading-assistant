package com.quant.trade.marketdata.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 全市场行业排行明细 DO。 */
@Data
@Builder
public class MarketSectorRankingItemDO {
    private Long id;
    private Long batchId;
    private Integer rankNo;
    private String providerSectorId;
    private String sectorName;
    private BigDecimal changeRate;
    private String leadingName;
    private String leadingSymbol;
    private BigDecimal leadingChangeRate;
    private String indicatorName;
    private String indicatorValue;
    private LocalDateTime createdAt;
}
