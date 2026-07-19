-- 行情采集执行引擎：计划级短事务 claim，防止同一计划重叠执行。
-- 外部 provider 调用不在持有 claim 的数据库事务中执行。
ALTER TABLE market_data_sync_plan ADD COLUMN run_claim_token VARCHAR(64);
ALTER TABLE market_data_sync_plan ADD COLUMN run_claimed_at DATETIME;
ALTER TABLE market_data_sync_plan ADD COLUMN running_task_id BIGINT;

CREATE INDEX idx_sync_plan_run_claim ON market_data_sync_plan (run_claimed_at);
