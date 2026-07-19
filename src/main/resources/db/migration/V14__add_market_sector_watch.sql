-- Provider industry watch list and immutable observation snapshots.
CREATE TABLE market_sector_watch (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code       VARCHAR(32)    NOT NULL,
    provider_sector_id  VARCHAR(64)    NOT NULL,
    market_code         VARCHAR(16)    NOT NULL,
    sector_name         VARCHAR(128)   NOT NULL,
    top_name            VARCHAR(128),
    tracking_symbol     VARCHAR(32),
    enabled             BOOLEAN        NOT NULL DEFAULT TRUE,
    last_refreshed_at   DATETIME(6),
    last_error          VARCHAR(512),
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_market_sector_watch UNIQUE (provider_code, provider_sector_id)
);

CREATE INDEX idx_sector_watch_market ON market_sector_watch (market_code, enabled);

CREATE TABLE market_sector_snapshot (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    watch_id                BIGINT          NOT NULL,
    snapshot_time           DATETIME(6)     NOT NULL,
    rank_indicator          VARCHAR(32)     NOT NULL,
    change_rate             DECIMAL(20, 8),
    year_to_date_change_rate DECIMAL(20, 8),
    leading_name            VARCHAR(128),
    leading_symbol          VARCHAR(32),
    leading_change_rate     DECIMAL(20, 8),
    constituent_count       INT,
    rise_count              INT,
    fall_count              INT,
    flat_count              INT,
    total_net_inflow        DECIMAL(30, 6),
    total_turnover_amount   DECIMAL(30, 6),
    total_volume            DECIMAL(30, 6),
    data_source             VARCHAR(32)     NOT NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_snapshot UNIQUE (watch_id, snapshot_time),
    CONSTRAINT fk_sector_snapshot_watch FOREIGN KEY (watch_id) REFERENCES market_sector_watch (id) ON DELETE CASCADE
);

CREATE INDEX idx_sector_snapshot_history ON market_sector_snapshot (watch_id, snapshot_time DESC);

CREATE TABLE market_sector_member_snapshot (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    snapshot_id         BIGINT          NOT NULL,
    canonical_symbol    VARCHAR(32)     NOT NULL,
    security_name       VARCHAR(128)    NOT NULL,
    current_price       DECIMAL(20, 6),
    previous_close      DECIMAL(20, 6),
    change_rate         DECIMAL(20, 8),
    net_inflow          DECIMAL(30, 6),
    turnover_amount     DECIMAL(30, 6),
    volume              DECIMAL(30, 6),
    total_shares        DECIMAL(30, 6),
    circulating_shares  DECIMAL(30, 6),
    tags                VARCHAR(512),
    trade_status        INT,
    is_delayed          BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_member_snapshot UNIQUE (snapshot_id, canonical_symbol),
    CONSTRAINT fk_sector_member_snapshot FOREIGN KEY (snapshot_id) REFERENCES market_sector_snapshot (id) ON DELETE CASCADE
);

CREATE INDEX idx_sector_member_symbol ON market_sector_member_snapshot (canonical_symbol, snapshot_id);
