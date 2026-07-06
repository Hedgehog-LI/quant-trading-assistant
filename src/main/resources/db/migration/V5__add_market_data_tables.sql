-- ============================================================
-- V5: Market Data Foundation — stock_basic + stock_daily_bar
-- 证券主数据与日 K 行情（P1 第一阶段）
-- ============================================================

CREATE TABLE stock_basic (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    canonical_symbol VARCHAR(32)    NOT NULL,
    symbol           VARCHAR(16)    NOT NULL,
    name             VARCHAR(128),
    market           VARCHAR(8)     NOT NULL,
    list_date        DATE,
    delisted         BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_basic_canonical UNIQUE (canonical_symbol)
);

CREATE INDEX idx_stock_basic_market ON stock_basic (market);
CREATE INDEX idx_stock_basic_symbol ON stock_basic (symbol);

CREATE TABLE stock_daily_bar (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    canonical_symbol VARCHAR(32)    NOT NULL,
    trade_date       DATE           NOT NULL,
    adjust_type      VARCHAR(8)     NOT NULL,
    data_source      VARCHAR(16)    NOT NULL,
    open_price       DECIMAL(20, 6) NOT NULL,
    high_price       DECIMAL(20, 6) NOT NULL,
    low_price        DECIMAL(20, 6) NOT NULL,
    close_price      DECIMAL(20, 6) NOT NULL,
    volume           BIGINT         NOT NULL DEFAULT 0,
    amount           DECIMAL(20, 6) NOT NULL DEFAULT 0,
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_bar_key UNIQUE (canonical_symbol, trade_date, adjust_type, data_source)
);

CREATE INDEX idx_daily_bar_symbol_date ON stock_daily_bar (canonical_symbol, trade_date);
CREATE INDEX idx_daily_bar_date ON stock_daily_bar (trade_date);
