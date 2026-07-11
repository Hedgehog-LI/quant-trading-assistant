-- ============================================================
-- V8: market_data_sync_task 增加 parent_task_id（支持失败重试留痕）
-- 不修改 V1-V7
-- ============================================================

ALTER TABLE market_data_sync_task ADD COLUMN parent_task_id BIGINT;
CREATE INDEX idx_sync_task_parent ON market_data_sync_task (parent_task_id);
