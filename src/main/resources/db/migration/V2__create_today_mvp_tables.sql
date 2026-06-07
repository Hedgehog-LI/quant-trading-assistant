-- ============================================================
-- V2: Today MVP Tables
-- watchlist, trade_plan, trade_journal, review_note
-- ============================================================

-- 1. watchlist 自选股
CREATE TABLE watchlist (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    symbol          VARCHAR(32)   NOT NULL,
    name            VARCHAR(128)  NOT NULL,
    market          VARCHAR(32),
    group_name      VARCHAR(64),
    watch_reason    VARCHAR(1024),
    trade_style     VARCHAR(32),
    attention_level VARCHAR(32),
    support_price   DECIMAL(20, 6),
    resistance_price DECIMAL(20, 6),
    stop_loss_price DECIMAL(20, 6),
    risk_note       VARCHAR(1024),
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_watchlist_symbol UNIQUE (symbol)
);

CREATE INDEX idx_watchlist_enabled ON watchlist (enabled);
CREATE INDEX idx_watchlist_trade_style ON watchlist (trade_style);

-- 2. trade_plan 盘前计划
CREATE TABLE trade_plan (
    id                        BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_date                 DATE          NOT NULL,
    symbol                    VARCHAR(32)   NOT NULL,
    name                      VARCHAR(128),
    plan_status               VARCHAR(32)   NOT NULL,
    buy_condition             VARCHAR(1024),
    sell_condition            VARCHAR(1024),
    stop_loss_price           DECIMAL(20, 6),
    take_profit_price         DECIMAL(20, 6),
    planned_position_ratio    DECIMAL(10, 6),
    max_loss_amount           DECIMAL(20, 6),
    allowed_to_trade          BOOLEAN       NOT NULL DEFAULT FALSE,
    risk_note                 VARCHAR(1024),
    notes                     VARCHAR(2048),
    created_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_trade_plan_symbol_date UNIQUE (symbol, plan_date)
);

CREATE INDEX idx_trade_plan_date ON trade_plan (plan_date);
CREATE INDEX idx_trade_plan_status ON trade_plan (plan_status);

-- 3. trade_journal 交易记录
CREATE TABLE trade_journal (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date        DATE          NOT NULL,
    trade_time        DATETIME,
    symbol            VARCHAR(32)   NOT NULL,
    name              VARCHAR(128),
    side              VARCHAR(16)   NOT NULL,
    price             DECIMAL(20, 6) NOT NULL,
    quantity          BIGINT        NOT NULL,
    amount            DECIMAL(20, 6),
    position_ratio    DECIMAL(10, 6),
    plan_id           BIGINT,
    reason            VARCHAR(2048),
    plan_stop_loss    DECIMAL(20, 6),
    plan_take_profit  DECIMAL(20, 6),
    followed_plan     BOOLEAN,
    emotion_tags      VARCHAR(512),
    mistake_tags      VARCHAR(512),
    actual_result     VARCHAR(1024),
    review_status     VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_trade_journal_symbol_date ON trade_journal (symbol, trade_date);
CREATE INDEX idx_trade_journal_date ON trade_journal (trade_date);
CREATE INDEX idx_trade_journal_review_status ON trade_journal (review_status);

-- 4. review_note 盘后复盘
CREATE TABLE review_note (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    review_date         DATE          NOT NULL,
    symbol              VARCHAR(32),
    title               VARCHAR(128)  NOT NULL,
    market_context      VARCHAR(2048),
    plan_summary        VARCHAR(2048),
    action_summary      VARCHAR(2048),
    right_things        VARCHAR(2048),
    wrong_things        VARCHAR(2048),
    rule_changes        VARCHAR(2048),
    next_actions        VARCHAR(2048),
    linked_journal_ids  VARCHAR(512),
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_review_note_date ON review_note (review_date);
CREATE INDEX idx_review_note_symbol_date ON review_note (symbol, review_date);
