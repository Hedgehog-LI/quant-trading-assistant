package com.quant.trade.marketdata.constant;

import java.util.Set;

/** 行情数据模块常量。 */
public final class MarketDataConstants {

    private MarketDataConstants() {}

    public static final String DATA_SOURCE_CSV = "CSV";
    public static final String DATA_SOURCE_MANUAL = "MANUAL";

    public static final Set<String> VALID_MARKETS = Set.of("SH", "SZ", "BJ");
    public static final Set<String> VALID_ADJUST_TYPES = Set.of("NONE", "QF", "HF");

    public static final String CANONICAL_SYMBOL_REGEX = "^(SH|SZ|BJ)\\.\\d{4,6}$";

    public static final String[] CSV_HEADERS = {
        "canonical_symbol", "trade_date", "open", "high", "low", "close", "volume", "amount", "adjust_type"
    };
    public static final String CSV_TEMPLATE = String.join(",", CSV_HEADERS) + "\n" +
        "SH.600519,2026-07-01,1680.00,1695.00,1678.00,1690.00,25000,42250000.00,NONE\n";

    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB
    public static final int MAX_ROWS = 10000;

    public static final String DEFAULT_PAGE_SIZE = "20";
    public static final int MAX_PAGE_SIZE = 500;
}
