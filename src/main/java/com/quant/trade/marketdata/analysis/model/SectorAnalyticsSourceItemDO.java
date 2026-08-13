package com.quant.trade.marketdata.analysis.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 板块分析使用的收盘排行来源明细。 */
@Data
public class SectorAnalyticsSourceItemDO {
    private Long batchId;
    private LocalDate tradeDate;
    private Long sectorIdentityId;
    private String sectorName;
    private String providerSectorId;
    private BigDecimal changeRate;
    private String leadingName;
    private String leadingSymbol;
}
