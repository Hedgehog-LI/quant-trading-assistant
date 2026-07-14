-- ============================================================
-- V10: 行情工作台与采集 — 分钟线 / 交易时段 / 日历 / 采集计划 / 任务明细 / 水位
-- 不修改 V1-V9
-- 设计基线: docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md
-- ============================================================

-- 1. 分钟 K 线资产表 (1M/5M/15M/30M/60M)
CREATE TABLE stock_minute_bar (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    canonical_symbol VARCHAR(32)    NOT NULL,
    trade_date       DATE           NOT NULL,
    bar_start_time   DATETIME       NOT NULL,
    bar_end_time     DATETIME       NOT NULL,
    interval_type    VARCHAR(8)     NOT NULL,
    session_type     VARCHAR(16)    NOT NULL DEFAULT 'REGULAR',
    open_price       DECIMAL(20, 6) NOT NULL,
    high_price       DECIMAL(20, 6) NOT NULL,
    low_price        DECIMAL(20, 6) NOT NULL,
    close_price      DECIMAL(20, 6) NOT NULL,
    volume           BIGINT         NOT NULL DEFAULT 0,
    amount           DECIMAL(20, 6) NOT NULL DEFAULT 0,
    turnover_rate    DECIMAL(10, 4),
    adjust_type      VARCHAR(8)     NOT NULL DEFAULT 'NONE',
    data_source      VARCHAR(16)    NOT NULL,
    fetched_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    raw_hash         VARCHAR(64),
    quality_status   VARCHAR(16)    NOT NULL DEFAULT 'VALID',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_minute_bar UNIQUE (canonical_symbol, bar_start_time, interval_type, adjust_type, data_source)
);

CREATE INDEX idx_minute_bar_symbol ON stock_minute_bar (canonical_symbol, interval_type, adjust_type, data_source, bar_start_time);
CREATE INDEX idx_minute_bar_date ON stock_minute_bar (trade_date, interval_type);

-- 2. 交易时段定义表 (市场/交易所的交易窗口模板)
CREATE TABLE market_trading_session (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    market_code     VARCHAR(8)     NOT NULL,
    session_type    VARCHAR(16)    NOT NULL,
    session_name    VARCHAR(32)    NOT NULL,
    start_time      VARCHAR(8)     NOT NULL,
    end_time        VARCHAR(8)     NOT NULL,
    is_auction      BOOLEAN        NOT NULL DEFAULT FALSE,
    sort_order      INT            NOT NULL DEFAULT 0,
    enabled         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_trading_session UNIQUE (market_code, session_type, start_time)
);

CREATE INDEX idx_trading_session_market ON market_trading_session (market_code, enabled);

-- 3. 交易日历表
CREATE TABLE market_calendar (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    market_code     VARCHAR(8)     NOT NULL,
    trade_date      DATE           NOT NULL,
    is_trading_day  BOOLEAN        NOT NULL,
    is_half_day     BOOLEAN        NOT NULL DEFAULT FALSE,
    remark          VARCHAR(64),
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_market_calendar UNIQUE (market_code, trade_date)
);

CREATE INDEX idx_calendar_date ON market_calendar (market_code, trade_date, is_trading_day);

-- 4. 采集计划表 (可复用配置，不直接等同一次执行)
CREATE TABLE market_data_sync_plan (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_name         VARCHAR(128)  NOT NULL,
    task_type         VARCHAR(32)   NOT NULL,
    provider          VARCHAR(16)   NOT NULL,
    scope_json        TEXT          NOT NULL,
    interval_type     VARCHAR(8),
    adjust_type       VARCHAR(8)    NOT NULL DEFAULT 'NONE',
    trigger_type      VARCHAR(16)   NOT NULL DEFAULT 'MANUAL',
    cron_expr         VARCHAR(64),
    include_auction   BOOLEAN       NOT NULL DEFAULT FALSE,
    collect_frequency VARCHAR(16),
    enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
    description       VARCHAR(512),
    last_run_at       DATETIME,
    last_task_id      BIGINT,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_sync_plan_enabled ON market_data_sync_plan (enabled, task_type);
CREATE INDEX idx_sync_plan_trigger ON market_data_sync_plan (trigger_type, enabled);

-- 5. 任务明细表 (单次任务按标的/时间段拆分的执行明细)
CREATE TABLE market_data_sync_task_item (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         BIGINT         NOT NULL,
    plan_id         BIGINT,
    canonical_symbol VARCHAR(32)   NOT NULL,
    scope_detail    VARCHAR(256),
    status          VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    row_count       INT           NOT NULL DEFAULT 0,
    inserted_count  INT           NOT NULL DEFAULT 0,
    updated_count   INT           NOT NULL DEFAULT 0,
    skipped_count   INT           NOT NULL DEFAULT 0,
    error_code      VARCHAR(64),
    error_message   VARCHAR(512),
    started_at      DATETIME,
    finished_at     DATETIME,
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_item_task ON market_data_sync_task_item (task_id);
CREATE INDEX idx_task_item_status ON market_data_sync_task_item (status);
CREATE INDEX idx_task_item_symbol ON market_data_sync_task_item (canonical_symbol);

-- 6. 数据水位表 (provider + symbol + interval + adjustType 的成功水位)
CREATE TABLE market_data_watermark (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    canonical_symbol VARCHAR(32)   NOT NULL,
    data_source      VARCHAR(16)   NOT NULL,
    interval_type    VARCHAR(8)    NOT NULL,
    adjust_type      VARCHAR(8)    NOT NULL DEFAULT 'NONE',
    last_success_time DATETIME     NOT NULL,
    last_trade_date  DATE,
    last_bar_time    DATETIME,
    total_rows       BIGINT        NOT NULL DEFAULT 0,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_watermark UNIQUE (canonical_symbol, data_source, interval_type, adjust_type)
);

CREATE INDEX idx_watermark_symbol ON market_data_watermark (canonical_symbol, interval_type);
