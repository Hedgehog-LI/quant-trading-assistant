package com.quant.trade.marketdata.analysis.constant;

import java.math.BigDecimal;
import java.util.Set;

/** 板块分析公式、范围和门禁常量。 */
public final class SectorAnalyticsConstants {

    public static final String PROVIDER_LONGPORT = "LONGPORT";
    public static final String FORMULA_RELATIVE_STRENGTH = "RELATIVE_STRENGTH";
    public static final String FORMULA_ROTATION_PERSISTENCE = "ROTATION_PERSISTENCE";
    public static final String FORMULA_VERSION = "v1";
    public static final String SCOPE_RANKED_UNIVERSE = "RANKED_UNIVERSE";
    public static final String QUALITY_OK = "OK";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final int MINIMUM_COHORT_SIZE = 5;
    public static final int DEFAULT_WINDOW_DAYS = 20;
    public static final int ONE_DAY_STRENGTH_WINDOW_DAYS = 1;
    public static final int RADAR_MOMENTUM_WINDOW_DAYS = 5;
    public static final int MAX_HISTORY_DAYS = 120;
    public static final BigDecimal ROTATION_STRENGTH_THRESHOLD = new BigDecimal("0.5");
    public static final BigDecimal ROTATION_MOMENTUM_THRESHOLD = BigDecimal.ZERO;
    public static final BigDecimal TOP_BUCKET_THRESHOLD = new BigDecimal("0.8");
    public static final Set<Integer> SUPPORTED_WINDOWS = Set.of(5, 10, 20, 50);
    public static final Set<Integer> SUPPORTED_QUERY_WINDOWS = Set.of(1, 5, 10, 20, 50);

    private SectorAnalyticsConstants() {
    }
}
