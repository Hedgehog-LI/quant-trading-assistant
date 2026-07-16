-- ============================================================
-- V12: market_data_sync_task_item 增加 sub_task_id，支持主子任务直接追踪
-- 不修改 V1-V11
-- ============================================================

ALTER TABLE market_data_sync_task_item ADD COLUMN sub_task_id BIGINT;

CREATE INDEX idx_task_item_sub_task ON market_data_sync_task_item (sub_task_id);
