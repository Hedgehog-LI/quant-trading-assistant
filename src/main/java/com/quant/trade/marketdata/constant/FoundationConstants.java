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

    // ---- 回补任务状态机（R1：增加 QUEUED，持久化后台执行） ----
    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_QUEUED = "QUEUED";
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

    // ---- 回补执行边界（R1：全 A 二维分片） ----
    /** 每分片证券数上限（防单片过大）。 */
    public static final int MAX_CHUNK_SIZE = 500;
    /** 单任务证券数上限（R1：必须容纳全 A 股票池 ≥6000，取 10000 输入保护）。 */
    public static final int MAX_TASK_SYMBOLS = 10_000;
    /** 单任务分片总数上限（证券组×日期窗 二维拆分后的输入保护）。 */
    public static final int MAX_TOTAL_CHUNKS = 40_000;
    /** 回补窗口最早日期（MR-1-BND-D：全 A 日 K 目标 2021-01-01 起）。 */
    public static final String EARLIEST_START_DATE = "2021-01-01";

    // ---- 严格质量门禁（R1） ----
    /** 发布覆盖率阈值（默认沿用 MR-1 的 0.90；可经 qta.data-foundation.publish-coverage-threshold 配置）。 */
    public static final String COVERAGE_GATE_CHECK = "OVERALL_COVERAGE_GATE";
    public static final String BOUNDARY_COVERAGE_CHECK = "BOUNDARY_COVERAGE";
    public static final String LINEAGE_DRIFT_CHECK = "LINEAGE_DRIFT";
    /** manifest 行血缘状态。 */
    public static final String LINEAGE_FROZEN = "FROZEN";
    public static final String LINEAGE_DRIFTED = "DRIFTED";
    /** manifest 来源类型。 */
    public static final String LINEAGE_SOURCE_BACKFILL = "BACKFILL_TASK";
    public static final String LINEAGE_SOURCE_IMPORT = "IMPORT_BATCH";
    /** 崩溃恢复标记。 */
    public static final String RECOVERY_STALE_CODE = "RECOVERED_STALE_RUNNING";

    /** 内置数据集：全 A 日 K（TENCENT_PUBLIC 实验源 / NONE 复权）。 */
    public static final String DATASET_CN_DAILY = "CN_DAILY_BAR";
}
