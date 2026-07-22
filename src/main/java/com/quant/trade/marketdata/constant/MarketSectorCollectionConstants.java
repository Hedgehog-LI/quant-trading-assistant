package com.quant.trade.marketdata.constant;

import java.util.Set;

/** 市场板块自动采集固定口径。 */
public final class MarketSectorCollectionConstants {

    public static final String MARKET_CN = "CN";
    public static final String MARKET_HK = "HK";
    public static final String MARKET_US = "US";
    public static final Set<String> SUPPORTED_MARKETS = Set.of(MARKET_CN, MARKET_HK, MARKET_US);

    public static final Set<Integer> SUPPORTED_INTERVAL_MINUTES = Set.of(0, 5, 10, 15, 30, 60);
    public static final Set<Integer> WATCH_INTERVAL_MINUTES = Set.of(5, 10, 15, 30, 60);

    public static final String SNAPSHOT_INTRADAY = "INTRADAY";
    public static final String SNAPSHOT_CLOSE = "CLOSE";
    public static final String SNAPSHOT_MANUAL = "MANUAL";

    public static final String TRIGGER_AUTO = "AUTO";
    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String QUALITY_VALID = "VALID";
    public static final String QUALITY_SUSPECT = "SUSPECT";

    public static final String STATE_IDLE = "IDLE";
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_BACKOFF = "BACKOFF";
    public static final String STATE_BLOCKED_AUTH = "BLOCKED_AUTH";
    public static final String STATE_BLOCKED_PERMISSION = "BLOCKED_PERMISSION";
    public static final String STATE_BLOCKED_CONFIG = "BLOCKED_CONFIG";

    public static final int DEFAULT_RANK_LIMIT = 100;
    public static final int MAX_RANK_LIMIT = 100;
    public static final int DEFAULT_WATCH_INTERVAL_MINUTES = 15;
    public static final int CLAIM_STALE_MINUTES = 10;
    public static final int MIN_VALID_MEMBER_COVERAGE_PERCENT = 90;

    private MarketSectorCollectionConstants() {
    }
}
