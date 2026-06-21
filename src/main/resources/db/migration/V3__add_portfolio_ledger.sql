-- ============================================================
-- V3: Portfolio Ledger（交易账本：持仓与盈亏）
-- 1) trade_journal 增加交易费用字段（佣金 / 印花税 / 过户费 / 其他 / 总费用）
-- 2) 新增 portfolio_price_snapshot 手工当前价快照表
-- ============================================================

-- 1. trade_journal 费用字段
--    允许 NULL：历史交易记录无费用数据，业务计算时按 0 处理。
ALTER TABLE trade_journal ADD COLUMN commission_fee DECIMAL(20, 6);
ALTER TABLE trade_journal ADD COLUMN stamp_tax      DECIMAL(20, 6);
ALTER TABLE trade_journal ADD COLUMN transfer_fee   DECIMAL(20, 6);
ALTER TABLE trade_journal ADD COLUMN other_fee      DECIMAL(20, 6);
ALTER TABLE trade_journal ADD COLUMN total_fee      DECIMAL(20, 6);

-- 2. portfolio_price_snapshot 手工当前价
--    用户手工维护的某只股票某日的当前价，用于估算浮动盈亏。
--    不连接实时行情，不是真实盘口价。
CREATE TABLE portfolio_price_snapshot (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    symbol        VARCHAR(32)    NOT NULL,
    name          VARCHAR(128),
    current_price DECIMAL(20, 6) NOT NULL,
    price_date    DATE           NOT NULL,
    note          VARCHAR(512),
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_portfolio_price_symbol_date UNIQUE (symbol, price_date)
);

CREATE INDEX idx_portfolio_price_symbol ON portfolio_price_snapshot (symbol);
CREATE INDEX idx_portfolio_price_date   ON portfolio_price_snapshot (price_date);
