package com.quant.trade.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 数据水位 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataWatermarkVO {
    private Long id;
    private String canonicalSymbol;
    private String dataSource;
    private String intervalType;
    private String adjustType;
    private LocalDateTime lastSuccessTime;
    private LocalDate lastTradeDate;
    private LocalDateTime lastBarTime;
    private Long totalRows;
    private LocalDateTime updatedAt;
}
