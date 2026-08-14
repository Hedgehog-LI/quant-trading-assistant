-- V23: MR-0 data semantics PoC fact tables（契约 D4）。mr0_ 前缀三张 PoC 最小事实表：
-- 证券池快照 / 时点行业成分 / 个股日资金流；纯新增、可废弃（MR-1 正式表仍需 ADR）。
-- DDL 惯用法对齐 V14（DATETIME(6)/CURRENT_TIMESTAMP(6)/DECIMAL(30,6)），无 MySQL 专有方言
-- （幂等写统一走 Mr0PocMapper.xml 的 ON DUPLICATE KEY UPDATE）。单位口径见指标字典总则 D5-D9。

-- 证券池快照（SINA_PUBLIC hs_a 全量分页，as_of_date=抓取日）
CREATE TABLE mr0_universe_snapshot (
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_code          VARCHAR(32)    NOT NULL,
    canonical_symbol       VARCHAR(32)    NOT NULL,
    symbol                 VARCHAR(16),
    name                   VARCHAR(128),
    market                 VARCHAR(8),
    total_market_cap       DECIMAL(30, 6),               -- 元（源万元×10000）
    circulating_market_cap DECIMAL(30, 6),               -- 元
    turnover_rate          DECIMAL(20, 8),               -- 小数（源 %/100）
    as_of_date             DATE           NOT NULL,
    fetched_at             DATETIME(6)    NOT NULL,
    created_at             DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mr0_universe_snapshot UNIQUE (provider_code, canonical_symbol, as_of_date)
);
CREATE INDEX idx_mr0_universe_as_of ON mr0_universe_snapshot (as_of_date);

-- 时点行业成分（SINA_INDUSTRY 互斥分类，非申万，禁混称混算）
CREATE TABLE mr0_industry_membership (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    taxonomy_code     VARCHAR(32)    NOT NULL,
    provider_code     VARCHAR(32)    NOT NULL,
    industry_code     VARCHAR(64)    NOT NULL,
    industry_name     VARCHAR(128)   NOT NULL,
    canonical_symbol  VARCHAR(32)    NOT NULL,
    as_of_date        DATE           NOT NULL,
    fetched_at        DATETIME(6)    NOT NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mr0_industry_membership UNIQUE (taxonomy_code, industry_code, canonical_symbol, as_of_date)
);
CREATE INDEX idx_mr0_membership_symbol ON mr0_industry_membership (canonical_symbol, as_of_date);

-- 个股日资金流（SINA_PUBLIC 主力净流入事实，netamount/r0_net/cate_na 源已是元原值入库）
CREATE TABLE mr0_stock_money_flow_daily (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    canonical_symbol      VARCHAR(32)    NOT NULL,
    trade_date            DATE           NOT NULL,
    provider_code         VARCHAR(32)    NOT NULL,
    main_net_inflow       DECIMAL(30, 6),
    main_net_inflow_ratio DECIMAL(20, 8),
    super_net             DECIMAL(30, 6),
    industry_code         VARCHAR(64),
    industry_net_inflow   DECIMAL(30, 6),
    fetched_at            DATETIME(6)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_mr0_money_flow_daily UNIQUE (canonical_symbol, trade_date, provider_code)
);
CREATE INDEX idx_mr0_money_flow_trade_date ON mr0_stock_money_flow_daily (trade_date);
CREATE INDEX idx_mr0_money_flow_industry ON mr0_stock_money_flow_daily (industry_code, trade_date);
