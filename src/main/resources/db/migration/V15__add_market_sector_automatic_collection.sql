-- Market-wide sector ranking history and watched-sector automatic collection.
CREATE TABLE market_sector_ranking_config (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code              VARCHAR(32)  NOT NULL,
    market_code                VARCHAR(16)  NOT NULL,
    enabled                    BOOLEAN      NOT NULL DEFAULT FALSE,
    intraday_interval_minutes  INT          NOT NULL DEFAULT 0,
    close_snapshot_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    rank_limit                 INT          NOT NULL DEFAULT 100,
    execution_state            VARCHAR(32)  NOT NULL DEFAULT 'IDLE',
    last_intraday_at           DATETIME(6),
    last_close_trade_date      DATE,
    last_success_at            DATETIME(6),
    next_retry_at              DATETIME(6),
    consecutive_failures       INT          NOT NULL DEFAULT 0,
    last_error_code            VARCHAR(64),
    last_error_message         VARCHAR(512),
    run_claim_token            VARCHAR(64),
    run_claimed_at             DATETIME(6),
    created_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_ranking_config UNIQUE (provider_code, market_code)
);

CREATE INDEX idx_sector_ranking_config_scan
    ON market_sector_ranking_config (enabled, execution_state, next_retry_at);

CREATE TABLE market_sector_ranking_batch (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code              VARCHAR(32)  NOT NULL,
    market_code                VARCHAR(16)  NOT NULL,
    trade_date                 DATE         NOT NULL,
    snapshot_type              VARCHAR(16)  NOT NULL,
    snapshot_bucket_time       DATETIME(6)  NOT NULL,
    snapshot_time              DATETIME(6)  NOT NULL,
    item_count                 INT          NOT NULL,
    rising_count               INT          NOT NULL DEFAULT 0,
    falling_count              INT          NOT NULL DEFAULT 0,
    flat_count                 INT          NOT NULL DEFAULT 0,
    leader_sector_id           VARCHAR(64),
    leader_sector_name         VARCHAR(128),
    leader_change_rate         DECIMAL(20, 8),
    laggard_sector_id          VARCHAR(64),
    laggard_sector_name        VARCHAR(128),
    laggard_change_rate        DECIMAL(20, 8),
    quality_status             VARCHAR(16)  NOT NULL DEFAULT 'VALID',
    created_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_ranking_batch UNIQUE
        (provider_code, market_code, snapshot_type, snapshot_bucket_time)
);

CREATE INDEX idx_sector_ranking_batch_history
    ON market_sector_ranking_batch (market_code, trade_date, snapshot_time DESC);

CREATE TABLE market_sector_ranking_item (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id                   BIGINT          NOT NULL,
    rank_no                    INT             NOT NULL,
    provider_sector_id         VARCHAR(64)     NOT NULL,
    sector_name                VARCHAR(128)    NOT NULL,
    change_rate                DECIMAL(20, 8),
    leading_name               VARCHAR(128),
    leading_symbol             VARCHAR(32),
    leading_change_rate        DECIMAL(20, 8),
    indicator_name             VARCHAR(64),
    indicator_value            VARCHAR(128),
    created_at                 DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_ranking_item UNIQUE (batch_id, provider_sector_id),
    CONSTRAINT fk_sector_ranking_item_batch FOREIGN KEY (batch_id)
        REFERENCES market_sector_ranking_batch (id) ON DELETE CASCADE
);

CREATE INDEX idx_sector_ranking_item_rank ON market_sector_ranking_item (batch_id, rank_no);

ALTER TABLE market_sector_watch
    ADD COLUMN auto_collect_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE market_sector_watch ADD COLUMN collect_interval_minutes INT NOT NULL DEFAULT 15;
ALTER TABLE market_sector_watch ADD COLUMN last_auto_collected_at DATETIME(6);
ALTER TABLE market_sector_watch ADD COLUMN collection_state VARCHAR(32) NOT NULL DEFAULT 'IDLE';
ALTER TABLE market_sector_watch ADD COLUMN next_retry_at DATETIME(6);
ALTER TABLE market_sector_watch ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0;
ALTER TABLE market_sector_watch ADD COLUMN last_error_code VARCHAR(64);
ALTER TABLE market_sector_watch ADD COLUMN run_claim_token VARCHAR(64);
ALTER TABLE market_sector_watch ADD COLUMN run_claimed_at DATETIME(6);

CREATE INDEX idx_sector_watch_auto_scan
    ON market_sector_watch (enabled, auto_collect_enabled, collection_state, next_retry_at);

ALTER TABLE market_sector_snapshot
    ADD COLUMN snapshot_bucket_time DATETIME(6);
ALTER TABLE market_sector_snapshot ADD COLUMN trigger_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE market_sector_snapshot ADD COLUMN fetched_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE market_sector_snapshot ADD COLUMN expected_member_count INT;
ALTER TABLE market_sector_snapshot ADD COLUMN valid_member_count INT;
ALTER TABLE market_sector_snapshot ADD COLUMN delayed_member_count INT;
ALTER TABLE market_sector_snapshot ADD COLUMN unmapped_member_count INT;
ALTER TABLE market_sector_snapshot ADD COLUMN quality_status VARCHAR(16) NOT NULL DEFAULT 'VALID';

CREATE UNIQUE INDEX uk_sector_snapshot_bucket
    ON market_sector_snapshot (watch_id, snapshot_bucket_time);

INSERT INTO market_sector_ranking_config
    (provider_code, market_code, enabled, intraday_interval_minutes, close_snapshot_enabled, rank_limit)
VALUES
    ('LONGPORT', 'CN', FALSE, 0, TRUE, 100),
    ('LONGPORT', 'HK', FALSE, 0, TRUE, 100),
    ('LONGPORT', 'US', FALSE, 0, TRUE, 100);
