-- ============================================================
-- V19: 板块分析数据治理前置 — 稳定身份 + 就绪门禁列
-- 不修改 V1-V18 已发布 migration（只 ALTER ADD COLUMN，不 DROP 既有 FK/约束）
-- 设计基线: docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md §6.1 / §9 / §10
--           docs/development/tasks/P110-A-BE-MARKET-DISCOVERY-20260812-R1-CONTRACT.md SLICE-01
-- ============================================================

-- 1. market_sector_identity：板块稳定身份（数值 id = API sectorId）
--    自然唯一键 (provider_code, market_code, provider_sector_id, taxonomy_version)
--    valid_from / valid_to 左闭右开区间；soft-archive 不物理删除
CREATE TABLE market_sector_identity (
    id                  BIGINT         PRIMARY KEY AUTO_INCREMENT,
    provider_code       VARCHAR(32)    NOT NULL,
    market_code         VARCHAR(16)    NOT NULL,
    provider_sector_id  VARCHAR(64)    NOT NULL,
    taxonomy_version    VARCHAR(32)    NOT NULL,
    sector_name         VARCHAR(128),
    valid_from          DATE           NOT NULL,
    valid_to            DATE,
    archived            BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_market_sector_identity UNIQUE
        (provider_code, market_code, provider_sector_id, taxonomy_version)
);

CREATE INDEX idx_sector_identity_market ON market_sector_identity (market_code, archived, valid_from);

-- 2. market_sector_identity_lock：身份声明锚点（READ COMMITTED 下 INSERT IGNORE + SELECT ... FOR UPDATE）
--    锚点 = (provider_code, market_code, provider_sector_id)，跨 taxonomy_version 共享同一锚点
CREATE TABLE market_sector_identity_lock (
    id                  BIGINT         PRIMARY KEY AUTO_INCREMENT,
    provider_code       VARCHAR(32)    NOT NULL,
    market_code         VARCHAR(16)    NOT NULL,
    provider_sector_id  VARCHAR(64)    NOT NULL,
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_market_sector_identity_lock UNIQUE
        (provider_code, market_code, provider_sector_id)
);

-- 3. market_calendar 增加来源/校验状态列（默认 INFERRED 不阻断现有 scheduler）
--    现有 scheduler (TradingSessionManager) 只读 is_trading_day，默认值不影响其行为
ALTER TABLE market_calendar ADD COLUMN source_code VARCHAR(32) NULL;
ALTER TABLE market_calendar ADD COLUMN verification_status VARCHAR(24) NOT NULL DEFAULT 'INFERRED';

CREATE INDEX idx_calendar_market_verification
    ON market_calendar (market_code, trade_date, verification_status);

-- 4. market_sector_snapshot / market_sector_member_snapshot 回填稳定 sector_identity_id
--    新增可空列；不删除 V14 的 fk_sector_snapshot_watch ON DELETE CASCADE（BLOCKING_AMENDMENT_01）
--    衍生层只使用 sector_identity_id 作为身份，watch_id 不进入衍生幂等键或跨表 JOIN
ALTER TABLE market_sector_snapshot ADD COLUMN sector_identity_id BIGINT NULL;

CREATE INDEX idx_sector_snapshot_identity
    ON market_sector_snapshot (sector_identity_id, snapshot_time);

ALTER TABLE market_sector_member_snapshot ADD COLUMN sector_identity_id BIGINT NULL;

CREATE INDEX idx_sector_member_snapshot_identity
    ON market_sector_member_snapshot (sector_identity_id);

-- 5. market_sector_ranking_batch 增加 provider_quote_time 列（可空）
--    provider 行情时间；未提供时 readiness 标 SOURCE_TIME_UNKNOWN（设计 §10）。
--    snapshot_time 保持 NOT NULL（系统落库时间）；provider_quote_time 独立，可空。
ALTER TABLE market_sector_ranking_batch ADD COLUMN provider_quote_time DATETIME(6) NULL;
