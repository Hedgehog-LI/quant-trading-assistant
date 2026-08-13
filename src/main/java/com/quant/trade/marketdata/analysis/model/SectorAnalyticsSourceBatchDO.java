package com.quant.trade.marketdata.analysis.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 板块分析使用的收盘排行来源批次。 */
@Data
public class SectorAnalyticsSourceBatchDO {
    private Long id;
    private String providerCode;
    private String marketCode;
    private LocalDate tradeDate;
    private LocalDateTime providerQuoteTime;
    private Integer itemCount;
}
