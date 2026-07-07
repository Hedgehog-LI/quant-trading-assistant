-- ============================================================
-- V6: stock_daily_bar 增加 fetched_at（数据抓取时间追踪）
-- 不修改 V1-V5，仅追加列
-- ============================================================

ALTER TABLE stock_daily_bar ADD COLUMN fetched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
