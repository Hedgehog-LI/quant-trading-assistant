package com.quant.trade.marketdata.asset.vo;

import java.util.List;

/**
 * series 响应：证券 + 查询参数回显 + 整体覆盖 + 区间质量 + 区间摘要 + 有界 K 线。
 * <p>
 * 价格/金额以 BigDecimal 十进制字符串输出；分钟时间按数据存储时区附加 offset；
 * {@code bars} 按时间升序，最多 {@code MAX_BARS_PER_REQUEST} 条（超出用 {@code quality.truncated} 标识）。
 */
public record MarketDataAssetSeriesVO(
        MarketDataAssetSecurityVO security,
        Query query,
        Availability availability,
        Quality quality,
        Summary summary,
        List<Bar> bars) {

    /** 回显的查询参数（from/to 为原始输入字符串）。 */
    public record Query(String interval, String from, String to, String adjustType, String dataSource) {
    }

    /** 该组合整体数据覆盖（不受本次查询窗口限制）。 */
    public record Availability(String firstBarTime, String lastBarTime, String latestFetchedAt, String watermarkTime) {
    }

    /**
     * 数据质量与覆盖（按本次查询窗口计算）：
     * CN 有权威日历 → VERIFIED / PARTIAL；HK/US 或日历未就绪 → UNKNOWN。
     */
    public record Quality(String coverageStatus, Integer actualBarCount, Integer expectedBarCount,
                          Integer missingBarCount, Integer suspectBarCount, boolean truncated,
                          List<String> reasonCodes) {
    }

    /** 区间摘要（按本次返回窗口计算；首根开盘价为 0 时 changeRate 为空）。 */
    public record Summary(String firstOpen, String lastClose, String absoluteChange, String changeRate,
                          String highestHigh, String lowestLow, Long totalVolume, String totalAmount,
                          Integer actualBarCount) {
    }

    /** 单根 K 线。 */
    public record Bar(String time, String open, String high, String low, String close, Long volume,
                      String amount, String qualityStatus, String fetchedAt) {
    }
}
