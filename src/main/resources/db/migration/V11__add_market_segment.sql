-- ============================================================
-- V11: 板块/自定义分组 — market_segment + market_segment_member
-- 不修改 V1-V10
-- 设计基线: docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md §7.2
-- ============================================================

CREATE TABLE market_segment (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    segment_code    VARCHAR(64)    NOT NULL,
    segment_name    VARCHAR(128)   NOT NULL,
    segment_type    VARCHAR(32)    NOT NULL DEFAULT 'CUSTOM',
    description     VARCHAR(512),
    enabled         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_segment_code UNIQUE (segment_code)
);

CREATE INDEX idx_segment_type ON market_segment (segment_type, enabled);

CREATE TABLE market_segment_member (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    segment_id      BIGINT         NOT NULL,
    canonical_symbol VARCHAR(32)   NOT NULL,
    sort_order      INT            NOT NULL DEFAULT 0,
    remark          VARCHAR(128),
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_segment_member UNIQUE (segment_id, canonical_symbol)
);

CREATE INDEX idx_segment_member_segment ON market_segment_member (segment_id);
CREATE INDEX idx_segment_member_symbol ON market_segment_member (canonical_symbol);
