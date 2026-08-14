package com.quant.trade.marketdata.asset.vo;

/** 已入库行情资产目录项。 */
public record MarketDataAssetCatalogItemVO(
        MarketDataAssetSecurityVO security,
        long dailyBarCount,
        long minuteBarCount,
        int minuteIntervalCount,
        String firstDailyDate,
        String lastDailyDate,
        String firstMinuteTime,
        String lastMinuteTime,
        String latestFetchedAt
) {
}
