package com.quant.trade.marketdata.asset.vo;

/**
 * 已存在的 interval/dataSource/adjustType 组合及其覆盖概况。
 * <p>
 * 时间字符串口径：日 K 为 {@code YYYY-MM-DD}；分钟 K 为含存储时区 offset 的 ISO-8601；
 * {@code watermarkTime} 在无对应水位时为 null。
 */
public record MarketDataAssetCombinationVO(
        String interval,
        String dataSource,
        String adjustType,
        long barCount,
        String firstBarTime,
        String lastBarTime,
        String latestFetchedAt,
        String watermarkTime) {
}
