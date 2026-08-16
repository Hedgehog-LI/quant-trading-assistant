package com.quant.trade.marketdata.constant;

/**
 * 数据底座（mdf_*）常量：状态机、检查族、导入来源标记（ADR-0015）。
 * 公共源 Provider 一律 EXPERIMENTAL/降级来源；导入数据 data_source=IMPORT_CSV_*，不冒充线上 Provider。
 */
public final class FoundationConstants {

    private FoundationConstants() {
    }

    // ---- 数据集版本状态机 ----
    public static final String VERSION_DRAFT = "DRAFT";
    public static final String VERSION_BACKFILLING = "BACKFILLING";
    public static final String VERSION_QUALIFYING = "QUALIFYING";
    public static final String VERSION_QUALIFIED = "QUALIFIED";
    public static final String VERSION_REJECTED = "REJECTED";
    public static final String VERSION_RELEASED = "RELEASED";
    public static final String VERSION_RETIRED = "RETIRED";

    // ---- 回补任务状态机 ----
    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_RUNNING = "RUNNING";
    public static final String TASK_PAUSED = "PAUSED";
    public static final String TASK_SUCCEEDED = "SUCCEEDED";
    public static final String TASK_PARTIAL_FAILED = "PARTIAL_FAILED";
    public static final String TASK_FAILED = "FAILED";

    // ---- 分片状态机 ----
    public static final String CHUNK_PENDING = "PENDING";
    public static final String CHUNK_RUNNING = "RUNNING";
    public static final String CHUNK_SUCCEEDED = "SUCCEEDED";
    public static final String CHUNK_FAILED = "FAILED";
    public static final String CHUNK_SKIPPED = "SKIPPED";

    // ---- 导入通道 ----
    public static final String IMPORT_KIND_UNIVERSE = "UNIVERSE_SNAPSHOT";
    public static final String IMPORT_KIND_CALENDAR = "TRADING_CALENDAR";
    public static final String IMPORT_KIND_DAILY_BAR = "DAILY_BAR";
    public static final String IMPORT_KIND_TAXONOMY = "INDUSTRY_TAXONOMY";
    public static final String IMPORT_KIND_MEMBERSHIP_PIT = "INDUSTRY_MEMBERSHIP_PIT";

    public static final String IMPORT_SOURCE_UNIVERSE = "IMPORT_CSV_UNIVERSE";
    public static final String IMPORT_SOURCE_CALENDAR = "IMPORT_CSV_CALENDAR";
    public static final String IMPORT_SOURCE_DAILY_BAR = "IMPORT_CSV_DAILY";
    public static final String IMPORT_SOURCE_TAXONOMY = "IMPORT_CSV_TAXONOMY";
    public static final String IMPORT_SOURCE_MEMBERSHIP = "IMPORT_CSV_PIT";

    // ---- 质量检查族（13 族，发布门禁输入） ----
    public static final String CHECK_DATE_RANGE_COVERAGE = "DATE_RANGE_COVERAGE";
    public static final String CHECK_UNIVERSE_COVERAGE = "UNIVERSE_COVERAGE";
    public static final String CHECK_DAILY_BAR_GAP = "DAILY_BAR_GAP";
    public static final String CHECK_DUPLICATE_ROWS = "DUPLICATE_ROWS";
    public static final String CHECK_OHLC_VALIDITY = "OHLC_VALIDITY";
    public static final String CHECK_UNIT_ANOMALY = "UNIT_ANOMALY";
    public static final String CHECK_NON_TRADING_DAY = "NON_TRADING_DAY_ANOMALY";
    public static final String CHECK_MEMBERSHIP_OVERLAP = "INDUSTRY_MEMBERSHIP_OVERLAP";
    public static final String CHECK_MEMBERSHIP_INVALID_PERIOD = "INDUSTRY_MEMBERSHIP_INVALID_PERIOD";
    public static final String CHECK_UNMAPPED_INDUSTRY = "UNMAPPED_INDUSTRY_SYMBOL";
    public static final String CHECK_PROVIDER_ADJUST_MIXING = "PROVIDER_ADJUST_MIXING";
    public static final String CHECK_DATA_STALENESS = "DATA_STALENESS";
    public static final String CHECK_EMPTY_DATASET = "EMPTY_DATASET";

    public static final String QUALITY_OK = "OK";
    public static final String QUALITY_WARN = "WARN";
    public static final String QUALITY_FAIL = "FAIL";

    // ---- 回补执行边界 ----
    /** 每分片证券数上限（防单片过大）。 */
    public static final int MAX_CHUNK_SIZE = 500;
    /** 单任务证券数上限（防全市场误操作一次打满公共源）。 */
    public static final int MAX_TASK_SYMBOLS = 2000;
    /** 回补窗口最早日期（MR-1-BND-D：全 A 日 K 目标 2021-01-01 起）。 */
    public static final String EARLIEST_START_DATE = "2021-01-01";

    /** 内置数据集：全 A 日 K（TENCENT_PUBLIC 实验源 / NONE 复权）。 */
    public static final String DATASET_CN_DAILY = "CN_DAILY_BAR";
}
