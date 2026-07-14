package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 数据水位 DO（market_data_watermark）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataWatermarkDO {
    private Long id;
    private String canonicalSymbol;
    private String dataSource;
    private String intervalType;
    private String adjustType;
    private LocalDateTime lastSuccessTime;
    private LocalDate lastTradeDate;
    private LocalDateTime lastBarTime;
    private Long totalRows;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
