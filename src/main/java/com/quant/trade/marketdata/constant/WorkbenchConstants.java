package com.quant.trade.marketdata.constant;

/** 行情工作台与采集相关常量。 */
public final class WorkbenchConstants {

    private WorkbenchConstants() {}

    /** 分钟 K 粒度类型。 */
    public static final String INTERVAL_1M = "1M";
    public static final String INTERVAL_5M = "5M";
    public static final String INTERVAL_15M = "15M";
    public static final String INTERVAL_30M = "30M";
    public static final String INTERVAL_60M = "60M";
    public static final String INTERVAL_1D = "1D";

    /** 交易时段类型。 */
    public static final String SESSION_PRE_MARKET = "PRE_MARKET";
    public static final String SESSION_AUCTION = "AUCTION";
    public static final String SESSION_AM = "AM";
    public static final String SESSION_PM = "PM";
    public static final String SESSION_REGULAR = "REGULAR";
    public static final String SESSION_AFTER_HOURS = "AFTER_HOURS";

    /** 数据质量状态。 */
    public static final String QUALITY_VALID = "VALID";
    public static final String QUALITY_SUSPECT = "SUSPECT";
    public static final String QUALITY_REJECTED = "REJECTED";

    /** 采集计划触发类型。 */
    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String TRIGGER_SCHEDULED = "SCHEDULED";
    public static final String TRIGGER_INTRADAY = "INTRADAY";

    /** 任务明细状态。 */
    public static final String ITEM_PENDING = "PENDING";
    public static final String ITEM_RUNNING = "RUNNING";
    public static final String ITEM_SUCCEEDED = "SUCCEEDED";
    public static final String ITEM_FAILED = "FAILED";
    public static final String ITEM_SKIPPED = "SKIPPED";
    public static final String ITEM_PARTIAL_FAILED = "PARTIAL_FAILED";

    /** 默认 A 股市场代码。 */
    public static final String MARKET_CN_A = "CN_A";

    /** 提醒类型。 */
    public static final String ALERT_QUALITY_CONFLICT = "QUALITY_CONFLICT";
    public static final String ALERT_QUALITY_INVALID_OHLC = "QUALITY_INVALID_OHLC";
    public static final String ALERT_QUALITY_NEGATIVE_VOLUME = "QUALITY_NEGATIVE_VOLUME";
    public static final String ALERT_QUALITY_BAR_OUT_OF_SESSION = "QUALITY_BAR_OUT_OF_SESSION";
    public static final String ALERT_QUOTE_STALE = "QUOTE_STALE";
}
