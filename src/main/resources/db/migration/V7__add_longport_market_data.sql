-- ============================================================
-- V7: LongPort 只读行情源 — stock_quote_snapshot + market_data_sync_task + market_data_alert
-- 不修改 V1-V6
-- ============================================================

CREATE TABLE stock_quote_snapshot (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    canonical_symbol VARCHAR(32)    NOT NULL,
    quote_time       DATETIME       NOT NULL,
    current_price    DECIMAL(20, 6) NOT NULL,
    open_price       DECIMAL(20, 6),
    high_price       DECIMAL(20, 6),
    low_price        DECIMAL(20, 6),
    pre_close_price  DECIMAL(20, 6),
    volume           BIGINT         NOT NULL DEFAULT 0,
    amount           DECIMAL(20, 6) NOT NULL DEFAULT 0,
    trade_status     VARCHAR(32),
    data_source      VARCHAR(16)    NOT NULL,
    fetched_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    raw_hash         VARCHAR(64),
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_quote_snapshot UNIQUE (canonical_symbol, data_source, quote_time)
);

CREATE INDEX idx_quote_snapshot_symbol ON stock_quote_snapshot (canonical_symbol);
CREATE INDEX idx_quote_snapshot_time ON stock_quote_snapshot (quote_time);

CREATE TABLE market_data_sync_task (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type           VARCHAR(32)    NOT NULL,
    provider            VARCHAR(16)    NOT NULL,
    scope_json          TEXT           NOT NULL,
    status              VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    idempotency_key     VARCHAR(128)   NOT NULL,
    total_count         INT            NOT NULL DEFAULT 0,
    success_count       INT            NOT NULL DEFAULT 0,
    fail_count          INT            NOT NULL DEFAULT 0,
    inserted_count      INT            NOT NULL DEFAULT 0,
    updated_count       INT            NOT NULL DEFAULT 0,
    skipped_count       INT            NOT NULL DEFAULT 0,
    started_at          DATETIME,
    finished_at         DATETIME,
    last_error_code     VARCHAR(64),
    error_summary_json  TEXT,
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sync_task_idem UNIQUE (idempotency_key)
);

CREATE INDEX idx_sync_task_status ON market_data_sync_task (status);
CREATE INDEX idx_sync_task_type ON market_data_sync_task (task_type);

CREATE TABLE market_data_alert (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_type         VARCHAR(64)    NOT NULL,
    severity           VARCHAR(16)    NOT NULL DEFAULT 'WARN',
    canonical_symbol   VARCHAR(32),
    quote_time         DATETIME,
    trade_date         DATE,
    provider           VARCHAR(16)    NOT NULL DEFAULT 'SYSTEM',
    task_id            BIGINT,
    message            VARCHAR(1024)  NOT NULL,
    trigger_value_json TEXT,
    resolved           BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_alert_resolved ON market_data_alert (resolved);
CREATE INDEX idx_alert_severity ON market_data_alert (severity);
CREATE INDEX idx_alert_symbol ON market_data_alert (canonical_symbol);
