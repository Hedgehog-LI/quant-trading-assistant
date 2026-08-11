package com.quant.trade.marketdata.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * P1.9-A 行情资产时间/数值格式化工具（存储时区口径）。
 * <p>
 * 分钟 bar 与抓取时间统一按数据存储时区附加 offset 输出秒级 ISO-8601；
 * 采集链路（LongPort quote-time-zone 与 JDBC serverTimezone 均为 Asia/Shanghai）
 * 把分钟 bar 存为 Asia/Shanghai 墙钟时间，因此对任意市场都附加 +08:00，
 * 保证 ISO 字符串表达的瞬时一致、不与存储墙钟错位。
 */
public final class MarketDataAssetTimeFormatter {

    private MarketDataAssetTimeFormatter() {
    }

    /** 数据实际存储时区：与采集链路 quote-time-zone / serverTimezone 保持一致。 */
    public static final ZoneId STORAGE_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter ISO_WITH_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** 分钟/抓取时间：按存储时区附加 offset，输出秒级 ISO-8601（如 2026-07-17T09:30:00+08:00）。 */
    public static String formatStoredTime(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        ZoneOffset offset = STORAGE_ZONE.getRules().getOffset(time);
        return ISO_WITH_OFFSET.format(time.atOffset(offset));
    }

    /** 日期 → YYYY-MM-DD；null 原样返回。 */
    public static String dateText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /** BigDecimal → 去尾零十进制字符串；null 原样返回。 */
    public static String priceText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
