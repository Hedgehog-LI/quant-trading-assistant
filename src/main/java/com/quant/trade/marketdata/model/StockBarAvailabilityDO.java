package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * availability 聚合行（stock_daily_bar / stock_minute_bar 只读投影）。
 * <p>
 * 日 K 聚合使用 {@link #firstBarDate}/{@link #lastBarDate}（intervalType 为 null）；
 * 分钟 K 聚合使用 {@link #firstBarTime}/{@link #lastBarTime}（intervalType 为粒度）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBarAvailabilityDO {
    /** 分钟 K 粒度；日 K 聚合时为 null。 */
    private String intervalType;
    private String dataSource;
    private String adjustType;
    private long barCount;
    /** 日 K 聚合使用。 */
    private LocalDate firstBarDate;
    private LocalDate lastBarDate;
    /** 分钟 K 聚合使用。 */
    private LocalDateTime firstBarTime;
    private LocalDateTime lastBarTime;
    private LocalDateTime latestFetchedAt;
}
