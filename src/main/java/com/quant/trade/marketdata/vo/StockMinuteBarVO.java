package com.quant.trade.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 分钟 K 线 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMinuteBarVO {
    private Long id;
    private String canonicalSymbol;
    private LocalDate tradeDate;
    private LocalDateTime barStartTime;
    private LocalDateTime barEndTime;
    private String intervalType;
    private String sessionType;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private Long volume;
    private BigDecimal amount;
    private BigDecimal turnoverRate;
    private String adjustType;
    private String dataSource;
    private String qualityStatus;
    private LocalDateTime fetchedAt;
}
