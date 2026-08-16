-- V24: QTA V2-1 A 股历史数据底座正式模型（ADR-0015，契约 D2/D5/D6）。
-- mdf_ 前缀表族为数据底座语义表；日 K/证券/日历事实复用 stock_daily_bar/stock_basic/market_calendar（禁止复制）。
-- DDL 惯用法对齐 V14/V23：DATETIME(6)/CURRENT_TIMESTAMP(6)/DECIMAL(30,6)，无 MySQL 专有方言，
-- 幂等写统一走 Mapper XML 的 ON DUPLICATE KEY UPDATE。单位冻结：价格=元、volume=股、amount=元、
-- 换手率=小数、市值=元（导入 schema 同口径，见 ADR-0015 §2）。

-- 数据集定义（一个 dataset_code 一行；current_version_id 指向当前 RELEASED 版本，发布事务内切换）
CREATE TABLE mdf_dataset (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_code        VARCHAR(64)    NOT NULL,
    dataset_name        VARCHAR(128)   NOT NULL,
    market_code         VARCHAR(8)     NOT NULL,              -- 首期 CN
    bar_type            VARCHAR(16)    NOT NULL,              -- DAILY / MINUTE（首期 DAILY）
    frequency           VARCHAR(8)     NOT NULL,              -- 1D（预留 1M/5M...）
    provider_code       VARCHAR(32)    NOT NULL,              -- TENCENT_PUBLIC / IMPORT_CSV 等
    adjust_type         VARCHAR(8)     NOT NULL,              -- NONE / HFQ / QFQ（首期仅 NONE 有 Provider 支撑）
    unit_caliber        VARCHAR(255)   NOT NULL,              -- 口径描述（价格元/量股/额元/换手小数）
    description         VARCHAR(512),
    current_version_id  BIGINT,                               -- 发布指针（无发布版本为 NULL）
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_dataset_code UNIQUE (dataset_code)
);

-- 数据集版本（状态机 DRAFT→BACKFILLING→QUALIFYING→QUALIFIED/REJECTED→RELEASED/RETIRED）
CREATE TABLE mdf_dataset_version (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id          BIGINT         NOT NULL,
    version_code        VARCHAR(32)    NOT NULL,              -- v1, v2 ...（服务端按 dataset 递增）
    status              VARCHAR(16)    NOT NULL,              -- DRAFT/BACKFILLING/QUALIFYING/QUALIFIED/REJECTED/RELEASED/RETIRED
    start_date          DATE           NOT NULL,
    end_date            DATE           NOT NULL,
    source_provider     VARCHAR(32)    NOT NULL,              -- 事实来源（回补 provider 或 IMPORT_CSV_*）
    source_note         VARCHAR(512),                         -- 来源说明（含导入批次 id 等）
    row_count           BIGINT         NOT NULL DEFAULT 0,    -- 版本覆盖的事实行数（质量检查时刷新）
    qualified_at        DATETIME(6),
    released_at         DATETIME(6),
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_dataset_version UNIQUE (dataset_id, version_code),
    CONSTRAINT fk_mdf_version_dataset FOREIGN KEY (dataset_id) REFERENCES mdf_dataset (id)
);
CREATE INDEX idx_mdf_version_status ON mdf_dataset_version (status);

-- 历史股票池快照（as_of 快照事实；证券身份登记复用 stock_basic，不另建主数据）
CREATE TABLE mdf_universe_snapshot (
    id                        BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code             VARCHAR(32)    NOT NULL,        -- SINA_PUBLIC / IMPORT_CSV_UNIVERSE
    canonical_symbol          VARCHAR(32)    NOT NULL,
    symbol                    VARCHAR(16),
    name                      VARCHAR(128),
    market                    VARCHAR(8)     NOT NULL,        -- SH/SZ/BJ
    total_market_cap          DECIMAL(30, 6),                 -- 元
    circulating_market_cap    DECIMAL(30, 6),                 -- 元
    turnover_rate             DECIMAL(20, 8),                 -- 小数
    as_of_date                DATE           NOT NULL,
    fetched_at                DATETIME(6)    NOT NULL,
    created_at                DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_universe_snapshot UNIQUE (provider_code, canonical_symbol, as_of_date)
);
CREATE INDEX idx_mdf_universe_as_of ON mdf_universe_snapshot (as_of_date);

-- 行业分类体系（SINA_INDUSTRY=新浪互斥行业，非申万，禁混称；申万待凭据后另行登记）
CREATE TABLE mdf_industry_taxonomy (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    taxonomy_code     VARCHAR(32)    NOT NULL,
    taxonomy_name     VARCHAR(128)   NOT NULL,
    provider_code     VARCHAR(32)    NOT NULL,
    is_mutually_exclusive TINYINT    NOT NULL DEFAULT 1,
    note              VARCHAR(512),
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_taxonomy_code UNIQUE (taxonomy_code)
);

-- PIT 行业成分（半开区间 [effective_from, effective_to)；effective_to NULL=至今；
-- 同 taxonomy 同 symbol 区间重叠由导入校验与质量检查双防线拒绝）
CREATE TABLE mdf_industry_membership (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    taxonomy_code     VARCHAR(32)    NOT NULL,
    industry_code     VARCHAR(64)    NOT NULL,
    industry_name     VARCHAR(128)   NOT NULL,
    canonical_symbol  VARCHAR(32)    NOT NULL,
    effective_from    DATE           NOT NULL,
    effective_to      DATE,                            -- NULL=至今
    source_provider   VARCHAR(32)    NOT NULL,         -- IMPORT_CSV_PIT / SINA_PUBLIC(当前快照,显式时点假设)
    fetched_at        DATETIME(6)    NOT NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_industry_membership UNIQUE (taxonomy_code, canonical_symbol, effective_from)
);
CREATE INDEX idx_mdf_membership_industry ON mdf_industry_membership (taxonomy_code, industry_code, effective_from);
CREATE INDEX idx_mdf_membership_symbol ON mdf_industry_membership (canonical_symbol, effective_from);

-- 数据覆盖水位（按版本×证券；expected_days 来自交易日历或日 K 推导，coverage_ratio=covered/expected）
CREATE TABLE mdf_coverage_watermark (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_version_id  BIGINT         NOT NULL,
    canonical_symbol    VARCHAR(32)    NOT NULL,
    first_date          DATE,
    last_date           DATE,
    row_count           BIGINT         NOT NULL DEFAULT 0,
    expected_days       BIGINT         NOT NULL DEFAULT 0,
    covered_days        BIGINT         NOT NULL DEFAULT 0,
    coverage_ratio      DECIMAL(10, 8),
    calculated_at       DATETIME(6)    NOT NULL,
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_coverage UNIQUE (dataset_version_id, canonical_symbol),
    CONSTRAINT fk_mdf_coverage_version FOREIGN KEY (dataset_version_id) REFERENCES mdf_dataset_version (id)
);
CREATE INDEX idx_mdf_coverage_ratio ON mdf_coverage_watermark (dataset_version_id, coverage_ratio);

-- 历史回补任务（机制沿用 market_data_sync_task 模式：短事务+claim token；范围锁复用 sync_scope_lock）
CREATE TABLE mdf_backfill_task (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_code        VARCHAR(64)    NOT NULL,
    dataset_version_id  BIGINT,                             -- 执行完成后归属的版本
    market_code         VARCHAR(8)     NOT NULL,
    provider_code       VARCHAR(32)    NOT NULL,
    frequency           VARCHAR(8)     NOT NULL,
    adjust_type         VARCHAR(8)     NOT NULL,
    start_date          DATE           NOT NULL,
    end_date            DATE           NOT NULL,
    symbols_json        TEXT,                               -- 显式 symbol 列表（空=全池，执行时解析）
    symbols_hash        VARCHAR(64)    NOT NULL,            -- sha256(symbols_json 规范化)，幂等键组成
    chunk_size          INT            NOT NULL DEFAULT 50, -- 每分片证券数
    status              VARCHAR(16)    NOT NULL,            -- PENDING/RUNNING/PAUSED/SUCCEEDED/PARTIAL_FAILED/FAILED
    planned_count       INT            NOT NULL DEFAULT 0,
    success_count       INT            NOT NULL DEFAULT 0,
    fail_count          INT            NOT NULL DEFAULT 0,
    skip_count          INT            NOT NULL DEFAULT 0,
    inserted_count      BIGINT         NOT NULL DEFAULT 0,
    updated_count       BIGINT         NOT NULL DEFAULT 0,
    claim_token         VARCHAR(64),
    claimed_at          DATETIME(6),
    last_error_code     VARCHAR(64),
    last_error_message  VARCHAR(512),
    started_at          DATETIME(6),
    finished_at         DATETIME(6),
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
    -- 注意：scope 不设 UNIQUE。活跃任务防重（PENDING/RUNNING/PAUSED 不得重复建）由服务层
    -- countActiveByScope 保证；终态任务允许同 scope 再建以支持幂等重跑（事实幂等靠 stock_daily_bar ODKU）。
);
CREATE INDEX idx_mdf_backfill_scope ON mdf_backfill_task (dataset_code, provider_code, adjust_type, start_date, end_date, symbols_hash);
CREATE INDEX idx_mdf_backfill_status ON mdf_backfill_task (status);

-- 回补分片（chunk_index 顺序执行；断点续跑=跳过终态分片从首个 PENDING/FAILED 继续）
CREATE TABLE mdf_backfill_chunk (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id             BIGINT         NOT NULL,
    chunk_index         INT            NOT NULL,
    symbols_json        TEXT           NOT NULL,            -- 本片证券列表
    start_date          DATE           NOT NULL,
    end_date            DATE           NOT NULL,
    status              VARCHAR(16)    NOT NULL,            -- PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED
    attempts            INT            NOT NULL DEFAULT 0,
    inserted_count      BIGINT         NOT NULL DEFAULT 0,
    updated_count       BIGINT         NOT NULL DEFAULT 0,
    skipped_count       BIGINT         NOT NULL DEFAULT 0,
    failed_count        BIGINT         NOT NULL DEFAULT 0,
    last_error_code     VARCHAR(64),
    last_error_message  VARCHAR(512),
    started_at          DATETIME(6),
    finished_at         DATETIME(6),
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_backfill_chunk UNIQUE (task_id, chunk_index),
    CONSTRAINT fk_mdf_chunk_task FOREIGN KEY (task_id) REFERENCES mdf_backfill_task (id)
);
CREATE INDEX idx_mdf_chunk_status ON mdf_backfill_chunk (task_id, status);

-- CSV/快照导入批次（可审计；错误行报告存 JSON，查询 API 可查看；data_source 标记 IMPORT_CSV_* 不冒充线上 Provider）
CREATE TABLE mdf_import_batch (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    import_kind       VARCHAR(32)    NOT NULL,              -- UNIVERSE_SNAPSHOT/TRADING_CALENDAR/DAILY_BAR/INDUSTRY_TAXONOMY/INDUSTRY_MEMBERSHIP_PIT
    provider_code     VARCHAR(32)    NOT NULL,              -- IMPORT_CSV_UNIVERSE 等
    file_name         VARCHAR(255)   NOT NULL,
    file_hash         VARCHAR(64)    NOT NULL,              -- sha256（幂等证据：同内容重复导入结果一致）
    inserted_count    INT            NOT NULL DEFAULT 0,
    updated_count     INT            NOT NULL DEFAULT 0,
    skipped_count     INT            NOT NULL DEFAULT 0,
    rejected_count    INT            NOT NULL DEFAULT 0,
    status            VARCHAR(16)    NOT NULL,              -- COMPLETED/FAILED
    error_report_json LONGTEXT,                             -- 错误行数组（行号/原因/原行摘要）
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_import_batch UNIQUE (import_kind, file_hash)
);

-- 数据质量检查结果（13 族；version+check_code 唯一；FAIL 或空数据阻断发布）
CREATE TABLE mdf_quality_result (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_version_id  BIGINT         NOT NULL,
    check_code          VARCHAR(64)    NOT NULL,
    status              VARCHAR(8)     NOT NULL,            -- OK/WARN/FAIL
    affected_count      BIGINT         NOT NULL DEFAULT 0,
    detail_json         LONGTEXT,                           -- 结构化明细（受影响 symbol/date 摘要）
    checked_at          DATETIME(6)    NOT NULL,
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mdf_quality_result UNIQUE (dataset_version_id, check_code),
    CONSTRAINT fk_mdf_quality_version FOREIGN KEY (dataset_version_id) REFERENCES mdf_dataset_version (id)
);
