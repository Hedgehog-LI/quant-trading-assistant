package com.quant.trade.marketdata.constant;

/** 证券目录同步（D3）相关常量。集中管理，禁止散落魔法字符串。 */
public final class SecurityDirectoryConstants {

    private SecurityDirectoryConstants() {}

    /** 复用 market_data_sync_task 的目录同步任务类型。 */
    public static final String TASK_TYPE_SECURITY_MASTER_SYNC = "SECURITY_MASTER_SYNC";

    /** CSV 快照目录 provider code（≤16 字符，匹配 market_data_sync_task.provider 列长）。 */
    public static final String PROVIDER_CODE_CSV_SNAPSHOT_DIR = "CSV_SNAPSHOT_DIR";

    /** 同步模式。 */
    public static final String MODE_FULL = "FULL";
    public static final String MODE_INCREMENTAL = "INCREMENTAL";

    /** 默认 cron：每日增量（Asia/Shanghai 06:30:02）。 */
    public static final String DEFAULT_DAILY_CRON = "2 30 6 * * *";
    /** 默认 cron：每周全量对账（Asia/Shanghai 周一 04:30）。 */
    public static final String DEFAULT_WEEKLY_CRON = "0 30 4 * * MON";

    /** 默认数量波动阈值（相对偏差 ≥0.30 拒绝）。 */
    public static final double DEFAULT_ROW_COUNT_SWING_THRESHOLD = 0.30d;

    /** 质量门禁标识。 */
    public static final String GATE_ROW_COUNT_SWING = "ROW_COUNT_SWING";
    public static final String GATE_REQUIRED_FIELD = "REQUIRED_FIELD";
    public static final String GATE_UNIQUENESS = "UNIQUENESS";
    public static final String GATE_EMPTY_SNAPSHOT = "EMPTY_SNAPSHOT";

    /** CSV 快照 provider 不可用原因码。 */
    public static final String REASON_PROVIDER_DISABLED = "PROVIDER_DISABLED";
    public static final String REASON_FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String REASON_PARSE_ERROR = "PARSE_ERROR";

    /** Scope JSON 字段。 */
    public static final String SCOPE_PROVIDER = "provider";
    public static final String SCOPE_SNAPSHOT_ID = "snapshotId";
    public static final String SCOPE_SNAPSHOT_HASH = "snapshotHash";
    public static final String SCOPE_MODE = "mode";
}
