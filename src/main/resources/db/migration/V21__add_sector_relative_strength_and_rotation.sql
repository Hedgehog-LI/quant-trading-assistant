-- P1.10-A: explainable relative-strength and sector-persistence derived assets.
CREATE TABLE sector_relative_strength_snapshot (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    calculation_run_id    BIGINT         NOT NULL,
    sector_identity_id    BIGINT         NOT NULL,
    as_of_date             DATE           NOT NULL,
    window_days            INT            NOT NULL,
    sector_return          DECIMAL(24, 12) NOT NULL,
    benchmark_return       DECIMAL(24, 12) NOT NULL,
    relative_return        DECIMAL(24, 12) NOT NULL,
    rs_rank_percentile     DECIMAL(20, 12) NOT NULL,
    quality_status         VARCHAR(32)    NOT NULL,
    reason_codes           VARCHAR(1024),
    created_at             DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_relative_strength UNIQUE
        (calculation_run_id, sector_identity_id),
    CONSTRAINT fk_sector_rs_run FOREIGN KEY (calculation_run_id)
        REFERENCES sector_analytics_calculation_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_sector_rs_identity FOREIGN KEY (sector_identity_id)
        REFERENCES market_sector_identity (id)
);

CREATE INDEX idx_sector_rs_query
    ON sector_relative_strength_snapshot (as_of_date, window_days, rs_rank_percentile DESC);

CREATE TABLE sector_rotation_sector_persistence (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    calculation_run_id         BIGINT         NOT NULL,
    sector_identity_id         BIGINT         NOT NULL,
    as_of_date                  DATE           NOT NULL,
    window_days                 INT            NOT NULL,
    current_rank                DECIMAL(20, 8)  NOT NULL,
    previous_rank               DECIMAL(20, 8),
    mean_rank_percentile        DECIMAL(20, 12) NOT NULL,
    rank_percentile_std_dev     DECIMAL(20, 12) NOT NULL,
    top_bucket_occupancy_rate   DECIMAL(20, 12) NOT NULL,
    consecutive_leading_days    INT            NOT NULL,
    consecutive_lagging_days    INT            NOT NULL,
    rank_percentile_change      DECIMAL(20, 12),
    quality_status              VARCHAR(32)    NOT NULL,
    reason_codes                VARCHAR(1024),
    created_at                  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sector_rotation_persistence UNIQUE
        (calculation_run_id, sector_identity_id),
    CONSTRAINT fk_sector_rotation_run FOREIGN KEY (calculation_run_id)
        REFERENCES sector_analytics_calculation_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_sector_rotation_identity FOREIGN KEY (sector_identity_id)
        REFERENCES market_sector_identity (id)
);

CREATE INDEX idx_sector_rotation_query
    ON sector_rotation_sector_persistence
       (as_of_date, window_days, mean_rank_percentile DESC);
