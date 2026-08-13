-- P1.10-A: freeze radar as medium-term strength plus short-term momentum.
-- V20 may already have run locally, so strengthen the schema with an additive migration.
ALTER TABLE sector_analytics_publication_batch
    ADD COLUMN momentum_window_days INT NOT NULL DEFAULT 5;

CREATE INDEX idx_sector_analytics_publication_radar
    ON sector_analytics_publication_batch
       (market_code, window_days, momentum_window_days, status, as_of_date DESC);

ALTER TABLE sector_analytics_calculation_run
    ADD CONSTRAINT uk_sector_analytics_run_scope
        UNIQUE (id, formula_code, provider_code, market_code, as_of_date);

ALTER TABLE sector_analytics_publication_batch
    ADD CONSTRAINT uk_sector_analytics_publication_scope
        UNIQUE (id, provider_code, market_code, as_of_date);

ALTER TABLE sector_analytics_publication_member ADD COLUMN provider_code VARCHAR(32) NULL;
ALTER TABLE sector_analytics_publication_member ADD COLUMN market_code VARCHAR(16) NULL;
ALTER TABLE sector_analytics_publication_member ADD COLUMN as_of_date DATE NULL;

UPDATE sector_analytics_publication_member member
SET provider_code = (
        SELECT batch.provider_code FROM sector_analytics_publication_batch batch
        WHERE batch.id = member.publication_batch_id
    ),
    market_code = (
        SELECT batch.market_code FROM sector_analytics_publication_batch batch
        WHERE batch.id = member.publication_batch_id
    ),
    as_of_date = (
        SELECT batch.as_of_date FROM sector_analytics_publication_batch batch
        WHERE batch.id = member.publication_batch_id
    );

ALTER TABLE sector_analytics_publication_member MODIFY COLUMN provider_code VARCHAR(32) NOT NULL;
ALTER TABLE sector_analytics_publication_member MODIFY COLUMN market_code VARCHAR(16) NOT NULL;
ALTER TABLE sector_analytics_publication_member MODIFY COLUMN as_of_date DATE NOT NULL;

ALTER TABLE sector_analytics_publication_member
    ADD CONSTRAINT fk_sector_publication_member_batch_scope
        FOREIGN KEY (publication_batch_id, provider_code, market_code, as_of_date)
        REFERENCES sector_analytics_publication_batch (id, provider_code, market_code, as_of_date);

ALTER TABLE sector_analytics_publication_member
    ADD CONSTRAINT fk_sector_publication_member_run_scope
        FOREIGN KEY (calculation_run_id, formula_code, provider_code, market_code, as_of_date)
        REFERENCES sector_analytics_calculation_run
            (id, formula_code, provider_code, market_code, as_of_date);
