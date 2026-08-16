-- V25: 数据底座修复收口 R1（Repair Addendum）：全 A 二维分片范围表 + 版本血缘 manifest + QUEUED 状态支撑。
-- V24 已提交不可改；本 migration 纯新增/增量 ALTER。惯用法对齐 V14/V23/V24（DATETIME(6)/DECIMAL/无专有方言；
-- H2 不支持逗号多列 ADD COLUMN，全部拆分独立语句）。

-- 1) 任务证券范围规范化表（全 A ≥6000 只不再塞 task.symbols_json）
CREATE TABLE mdf_backfill_task_symbol (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id           BIGINT        NOT NULL,
    canonical_symbol  VARCHAR(32)   NOT NULL,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_task_symbol UNIQUE (task_id, canonical_symbol),
    CONSTRAINT fk_mdf_task_symbol_task FOREIGN KEY (task_id) REFERENCES mdf_backfill_task (id)
);
CREATE INDEX idx_mdf_task_symbol_symbol ON mdf_backfill_task_symbol (canonical_symbol);

-- 2) 不可变版本血缘 manifest：版本归属的 stock_daily_bar 行 + 行内容哈希 + 来源（backfill task / import batch）
--    双唯一键：bar_id 去重 + 业务键(symbol,trade_date) 每版本每键一行；发布前冻结 content_hash。
CREATE TABLE mdf_dataset_version_manifest (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_version_id  BIGINT        NOT NULL,
    bar_id              BIGINT        NOT NULL,
    canonical_symbol    VARCHAR(32)   NOT NULL,
    trade_date          DATE          NOT NULL,
    row_hash            CHAR(64)      NOT NULL,
    source_type         VARCHAR(16)   NOT NULL,
    source_id           BIGINT        NOT NULL,
    included_at         DATETIME(6)   NOT NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_manifest_bar UNIQUE (dataset_version_id, bar_id),
    CONSTRAINT uk_mdf_manifest_key UNIQUE (dataset_version_id, canonical_symbol, trade_date)
);
CREATE INDEX idx_mdf_manifest_version ON mdf_dataset_version_manifest (dataset_version_id);
CREATE INDEX idx_mdf_manifest_source ON mdf_dataset_version_manifest (source_type, source_id);

-- 3) 版本血缘列：内容哈希/manifest 行数/血缘状态（FROZEN=发布冻结, DRIFTED=底层事实漂移）
ALTER TABLE mdf_dataset_version ADD COLUMN content_hash CHAR(64);
ALTER TABLE mdf_dataset_version ADD COLUMN manifest_row_count BIGINT;
ALTER TABLE mdf_dataset_version ADD COLUMN lineage_status VARCHAR(16);

-- 4) 导入批次血缘：DAILY_BAR 必绑版本；其余 kind 可空但保留关联
ALTER TABLE mdf_import_batch ADD COLUMN dataset_version_id BIGINT;

-- 5) 任务排队时间（QUEUED 状态支撑；claimed_at 已有）
ALTER TABLE mdf_backfill_task ADD COLUMN queued_at DATETIME(6);
