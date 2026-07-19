package com.quant.trade.marketdata.constant;

import java.util.Set;

/** 行情数据模块常量。 */
public final class MarketDataConstants {

    private MarketDataConstants() {}

    // 数据来源
    public static final String DATA_SOURCE_CSV = "CSV";
    public static final String DATA_SOURCE_MANUAL = "MANUAL";
    public static final String DATA_SOURCE_LONGPORT = "LONGPORT";

    // Provider
    public static final String PROVIDER_CODE_LONGPORT = "LONGPORT";
    public static final int LONGPORT_MAX_QUOTE_SYMBOLS = 500;
    public static final String LONGPORT_SDK_MISSING_MESSAGE = "LongPort Java SDK 未安装或未进入运行时 classpath";
    public static final String LONGPORT_CREDENTIALS_MISSING_MESSAGE = "LongPort 凭据未配置";
    public static final String LONGPORT_PROVIDER_DISABLED_MESSAGE = "LongPort provider 未启用";

    // 同步任务
    public static final String TASK_TYPE_DAILY_BAR_SYNC = "DAILY_BAR_SYNC";
    public static final String TASK_TYPE_LATEST_QUOTE_REFRESH = "LATEST_QUOTE_REFRESH";
    public static final Set<String> VALID_TASK_TYPES = Set.of(TASK_TYPE_DAILY_BAR_SYNC, TASK_TYPE_LATEST_QUOTE_REFRESH);

    // 任务状态
    public static final String TASK_STATUS_PENDING = "PENDING";
    public static final String TASK_STATUS_RUNNING = "RUNNING";
    public static final String TASK_STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String TASK_STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";
    public static final String TASK_STATUS_FAILED = "FAILED";

    // 提醒严重级别
    public static final String ALERT_SEVERITY_INFO = "INFO";
    public static final String ALERT_SEVERITY_WARN = "WARN";
    public static final String ALERT_SEVERITY_HIGH = "HIGH";
    public static final Set<String> VALID_ALERT_SEVERITIES = Set.of(ALERT_SEVERITY_INFO, ALERT_SEVERITY_WARN, ALERT_SEVERITY_HIGH);

    // 提醒类型
    public static final String ALERT_TYPE_PROVIDER_NOT_CONFIGURED = "PROVIDER_NOT_CONFIGURED";
    public static final String ALERT_TYPE_SYNC_FAILED = "SYNC_FAILED";
    public static final String ALERT_TYPE_EMPTY_DAILY_BARS = "EMPTY_DAILY_BARS";
    public static final String ALERT_TYPE_STALE_QUOTE = "STALE_QUOTE";

    // 通用
    public static final String MARKET_SH = "SH";
    public static final String MARKET_SZ = "SZ";
    public static final String MARKET_BJ = "BJ";
    public static final String MARKET_HK = "HK";
    public static final String MARKET_US = "US";
    public static final Set<String> VALID_MARKETS = Set.of(
            MARKET_SH, MARKET_SZ, MARKET_BJ, MARKET_HK, MARKET_US);
    public static final Set<String> VALID_ADJUST_TYPES = Set.of("NONE", "QF", "HF");
    public static final String A_SHARE_SYMBOL_REGEX = "\\d{4,6}";
    public static final String HK_SYMBOL_REGEX = "\\d{5}";
    public static final String US_SYMBOL_REGEX = "[A-Z0-9]+(?:[.-][A-Z0-9]+)*";
    public static final String CANONICAL_SYMBOL_REGEX =
            "^(?:(?:SH|SZ|BJ)\\.\\d{4,6}|HK\\.\\d{5}|US\\.[A-Z0-9]+(?:[.-][A-Z0-9]+)*)$";

    // CSV
    public static final String[] CSV_HEADERS = {
        "canonical_symbol", "trade_date", "open", "high", "low", "close", "volume", "amount", "adjust_type"
    };
    public static final String CSV_TEMPLATE = String.join(",", CSV_HEADERS) + "\n" +
        "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    public static final int MAX_ROWS = 10000;

    // 分页
    public static final String DEFAULT_PAGE_SIZE = "20";
    public static final int MAX_PAGE_SIZE = 500;
}
