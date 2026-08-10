package com.quant.trade.marketdata.constant;

import java.util.Map;
import java.util.Set;

/**
 * P1.9-A 行情资产只读查询的常量与范围限制契约。
 * <p>
 * 单次 series 最多返回 2000 bars，SQL 用 {@link #SERIES_FETCH_LIMIT} 判定截断；
 * 粒度范围上限用于查询参数校验，防止无界查询。
 */
public final class MarketDataAssetConstants {

    private MarketDataAssetConstants() {}

    /** 单次 series 最多返回的 bar 数量。 */
    public static final int MAX_BARS_PER_REQUEST = 2000;

    /** SQL 拉取上限：第 2001 条仅用于判定 truncated，不返回前端。 */
    public static final int SERIES_FETCH_LIMIT = MAX_BARS_PER_REQUEST + 1;

    /** 支持的查询粒度（日 K + 分钟 K）。 */
    public static final Set<String> VALID_INTERVALS = Set.of(
            WorkbenchConstants.INTERVAL_1D, WorkbenchConstants.INTERVAL_1M,
            WorkbenchConstants.INTERVAL_5M, WorkbenchConstants.INTERVAL_15M,
            WorkbenchConstants.INTERVAL_30M, WorkbenchConstants.INTERVAL_60M);

    /** 分钟粒度最大自然日范围、日 K 最大年数换算天数（上限用于校验，非静默截断）。 */
    public static final Map<String, Integer> INTERVAL_MAX_NATURAL_DAYS = Map.of(
            WorkbenchConstants.INTERVAL_5M, 30,
            WorkbenchConstants.INTERVAL_15M, 90,
            WorkbenchConstants.INTERVAL_30M, 180,
            WorkbenchConstants.INTERVAL_60M, 365,
            WorkbenchConstants.INTERVAL_1D, 3650);

    /** 1M 粒度最大可查询的交易日数量（需权威交易日历支持）。 */
    public static final int INTERVAL_1M_MAX_TRADING_DAYS = 5;
}
