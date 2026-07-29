-- ============================================================
-- V17: Local-first security directory and deterministic search
-- ============================================================

ALTER TABLE stock_basic ADD COLUMN name_cn VARCHAR(128);
ALTER TABLE stock_basic ADD COLUMN name_hk VARCHAR(128);
ALTER TABLE stock_basic ADD COLUMN name_en VARCHAR(128);
ALTER TABLE stock_basic ADD COLUMN short_name VARCHAR(128);
ALTER TABLE stock_basic ADD COLUMN pinyin_full VARCHAR(256);
ALTER TABLE stock_basic ADD COLUMN pinyin_abbr VARCHAR(128);
ALTER TABLE stock_basic ADD COLUMN exchange VARCHAR(32);
ALTER TABLE stock_basic ADD COLUMN currency VARCHAR(8);
ALTER TABLE stock_basic ADD COLUMN security_type VARCHAR(16) NOT NULL DEFAULT 'STOCK';
ALTER TABLE stock_basic ADD COLUMN list_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE stock_basic ADD COLUMN data_source VARCHAR(32);
ALTER TABLE stock_basic ADD COLUMN source_updated_at DATETIME;
ALTER TABLE stock_basic ADD COLUMN source_hash VARCHAR(128);

UPDATE stock_basic
SET list_status = CASE WHEN delisted = TRUE THEN 'DELISTED' ELSE 'UNKNOWN' END;

CREATE INDEX idx_stock_basic_directory_filter
    ON stock_basic (market, security_type, list_status);
CREATE INDEX idx_stock_basic_name ON stock_basic (name);
CREATE INDEX idx_stock_basic_name_cn ON stock_basic (name_cn);
CREATE INDEX idx_stock_basic_name_en ON stock_basic (name_en);
CREATE INDEX idx_stock_basic_pinyin_full ON stock_basic (pinyin_full);
CREATE INDEX idx_stock_basic_pinyin_abbr ON stock_basic (pinyin_abbr);
CREATE INDEX idx_stock_basic_source_updated ON stock_basic (source_updated_at);

CREATE TABLE stock_alias (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    stock_basic_id   BIGINT       NOT NULL,
    alias            VARCHAR(256) NOT NULL,
    normalized_alias VARCHAR(256) NOT NULL,
    alias_type       VARCHAR(32)  NOT NULL,
    language         VARCHAR(16),
    data_source      VARCHAR(32)  NOT NULL,
    effective_from   DATE,
    effective_to     DATE,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_alias_identity
        UNIQUE (stock_basic_id, normalized_alias, alias_type),
    CONSTRAINT fk_stock_alias_stock_basic
        FOREIGN KEY (stock_basic_id) REFERENCES stock_basic(id) ON DELETE CASCADE
);

CREATE INDEX idx_stock_alias_normalized ON stock_alias (normalized_alias);
CREATE INDEX idx_stock_alias_stock ON stock_alias (stock_basic_id);
