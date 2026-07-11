-- ============================================================
-- V9: market_data_sync_scope_lock — 同 scope 并发重试串行化
-- 不修改 V1-V8
-- ============================================================

CREATE TABLE market_data_sync_scope_lock (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider    VARCHAR(16)   NOT NULL,
    task_type   VARCHAR(32)   NOT NULL,
    scope_hash  VARCHAR(64)   NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_sync_scope_lock UNIQUE (provider, task_type, scope_hash)
);
