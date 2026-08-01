-- ============================================================
-- V18: Security directory sync state (D3)
-- Per-provider lightweight state for the security directory sync.
-- Does NOT write back stock_basic; catalogStatus still uses the D1 heuristic.
-- ============================================================

CREATE TABLE security_directory_sync_state (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider              VARCHAR(32)  NOT NULL,
    last_snapshot_id      VARCHAR(128),
    last_snapshot_hash    VARCHAR(128),
    last_mode             VARCHAR(16),
    last_success_at       DATETIME(6),
    last_inserted_count   INT,
    last_updated_count    INT,
    last_unchanged_count  INT,
    last_error_code       VARCHAR(64),
    last_error_summary    VARCHAR(1024),
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_security_directory_sync_state_provider
        UNIQUE (provider)
);
