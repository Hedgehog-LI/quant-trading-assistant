-- P1.10-A: stable identity on ranking facts, calculation lineage and atomic publication.
ALTER TABLE market_sector_ranking_item ADD COLUMN sector_identity_id BIGINT NULL;

INSERT IGNORE INTO market_sector_identity
    (provider_code, market_code, provider_sector_id, taxonomy_version, sector_name, valid_from, archived)
SELECT b.provider_code, b.market_code, i.provider_sector_id, 'LONGPORT_INDUSTRY_V1',
       MAX(i.sector_name), MIN(b.trade_date), FALSE
FROM market_sector_ranking_item i
JOIN market_sector_ranking_batch b ON b.id = i.batch_id
GROUP BY b.provider_code, b.market_code, i.provider_sector_id;

UPDATE market_sector_ranking_item i
SET sector_identity_id = (
    SELECT identity.id
    FROM market_sector_identity identity
    JOIN market_sector_ranking_batch b ON b.id = i.batch_id
    WHERE identity.provider_code = b.provider_code
      AND identity.market_code = b.market_code
      AND identity.provider_sector_id = i.provider_sector_id
      AND identity.taxonomy_version = 'LONGPORT_INDUSTRY_V1'
)
WHERE i.sector_identity_id IS NULL;

CREATE INDEX idx_sector_ranking_item_identity
    ON market_sector_ranking_item (sector_identity_id, batch_id);

ALTER TABLE market_sector_ranking_item
    ADD CONSTRAINT fk_sector_ranking_item_identity FOREIGN KEY (sector_identity_id)
        REFERENCES market_sector_identity (id);

CREATE TABLE sector_analytics_calculation_run (
    id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code            VARCHAR(32)  NOT NULL,
    market_code              VARCHAR(16)  NOT NULL,
    as_of_date               DATE         NOT NULL,
    formula_code             VARCHAR(64)  NOT NULL,
    formula_version          VARCHAR(32)  NOT NULL,
    window_days              INT          NOT NULL,
    parameter_hash           VARCHAR(64)  NOT NULL,
    source_manifest_hash     VARCHAR(64)  NOT NULL,
    source_manifest          TEXT         NOT NULL,
    status                   VARCHAR(24)  NOT NULL,
    quality_status           VARCHAR(32)  NOT NULL,
    reason_codes             VARCHAR(1024),
    sample_size              INT          NOT NULL DEFAULT 0,
    started_at               DATETIME(6)  NOT NULL,
    completed_at             DATETIME(6),
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_analytics_run UNIQUE
        (formula_code, formula_version, parameter_hash, source_manifest_hash)
);

CREATE INDEX idx_sector_analytics_run_query
    ON sector_analytics_calculation_run (market_code, as_of_date, window_days, status);

CREATE TABLE sector_analytics_publication_batch (
    id                          BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code               VARCHAR(32)  NOT NULL,
    market_code                 VARCHAR(16)  NOT NULL,
    as_of_date                  DATE         NOT NULL,
    window_days                 INT          NOT NULL,
    formula_version             VARCHAR(32)  NOT NULL,
    parameter_hash              VARCHAR(64)  NOT NULL,
    required_formula_set_hash   VARCHAR(64)  NOT NULL,
    source_manifest_group_hash  VARCHAR(64)  NOT NULL,
    scope_code                  VARCHAR(32)  NOT NULL,
    status                      VARCHAR(24)  NOT NULL,
    quality_status              VARCHAR(32)  NOT NULL,
    published_at                DATETIME(6),
    created_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_analytics_publication UNIQUE
        (provider_code, market_code, as_of_date, window_days, formula_version,
         parameter_hash, source_manifest_group_hash)
);

CREATE INDEX idx_sector_analytics_publication_latest
    ON sector_analytics_publication_batch (market_code, window_days, status, as_of_date DESC);

CREATE TABLE sector_analytics_publication_member (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    publication_batch_id  BIGINT       NOT NULL,
    calculation_run_id    BIGINT       NOT NULL,
    formula_code          VARCHAR(64)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_analytics_publication_member UNIQUE
        (publication_batch_id, formula_code),
    CONSTRAINT fk_sector_publication_member_batch FOREIGN KEY (publication_batch_id)
        REFERENCES sector_analytics_publication_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_sector_publication_member_run FOREIGN KEY (calculation_run_id)
        REFERENCES sector_analytics_calculation_run (id)
);

