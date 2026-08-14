package com.quant.trade.marketdata.asset.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 已入库行情资产目录聚合行。 */
@Data
public class MarketDataAssetCatalogDO {
    private String canonicalSymbol;
    private String name;
    private String nameCn;
    private String nameHk;
    private String nameEn;
    private String market;
    private String currency;
    private long dailyBarCount;
    private long minuteBarCount;
    private int minuteIntervalCount;
    private LocalDate firstDailyDate;
    private LocalDate lastDailyDate;
    private LocalDateTime firstMinuteTime;
    private LocalDateTime lastMinuteTime;
    private LocalDateTime latestDailyFetchedAt;
    private LocalDateTime latestMinuteFetchedAt;
}
