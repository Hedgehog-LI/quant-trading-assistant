package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 交易日历 DO（market_calendar）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketCalendarDO {
    private Long id;
    private String marketCode;
    private LocalDate tradeDate;
    private Boolean isTradingDay;
    private Boolean isHalfDay;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
