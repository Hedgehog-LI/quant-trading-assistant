-- ============================================================
-- V4: Position Snapshot（实际持仓快照）
-- 用户手工记录某个时点券商页面中的实际持仓，不反推交易流水。
-- ============================================================

CREATE TABLE portfolio_position_snapshot (
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
    snapshot_date          DATE           NOT NULL,
    snapshot_time          DATETIME       NOT NULL,
    snapshot_name          VARCHAR(128),
    source_type            VARCHAR(32)    NOT NULL,
    snapshot_status        VARCHAR(32)    NOT NULL,
    total_cost_amount      DECIMAL(20, 6) NOT NULL DEFAULT 0,
    total_market_value     DECIMAL(20, 6) NOT NULL DEFAULT 0,
    total_unrealized_pnl   DECIMAL(20, 6) NOT NULL DEFAULT 0,
    total_pnl_rate         DECIMAL(20, 6) NOT NULL DEFAULT 0,
    position_count         INT            NOT NULL DEFAULT 0,
    remark                 VARCHAR(1024),
    created_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_position_snapshot_date
    ON portfolio_position_snapshot (snapshot_date);
CREATE INDEX idx_position_snapshot_status
    ON portfolio_position_snapshot (snapshot_status);
CREATE INDEX idx_position_snapshot_source
    ON portfolio_position_snapshot (source_type);

CREATE TABLE portfolio_position_snapshot_item (
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
    snapshot_id            BIGINT         NOT NULL,
    symbol                 VARCHAR(32)    NOT NULL,
    name                   VARCHAR(128),
    market_type            VARCHAR(32)    NOT NULL,
    holding_quantity       BIGINT         NOT NULL,
    available_quantity     BIGINT         NOT NULL,
    cost_price             DECIMAL(20, 6) NOT NULL,
    current_price          DECIMAL(20, 6) NOT NULL,
    cost_amount            DECIMAL(20, 6) NOT NULL,
    market_value           DECIMAL(20, 6) NOT NULL,
    unrealized_pnl         DECIMAL(20, 6) NOT NULL,
    pnl_rate               DECIMAL(20, 6) NOT NULL,
    position_ratio         DECIMAL(20, 6) NOT NULL,
    sort_order             INT            NOT NULL,
    remark                 VARCHAR(512),
    created_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_position_snapshot_item_symbol UNIQUE (snapshot_id, symbol),
    CONSTRAINT fk_position_snapshot_item_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES portfolio_position_snapshot (id) ON DELETE CASCADE
);

CREATE INDEX idx_position_snapshot_item_snapshot
    ON portfolio_position_snapshot_item (snapshot_id);
CREATE INDEX idx_position_snapshot_item_symbol
    ON portfolio_position_snapshot_item (symbol);
