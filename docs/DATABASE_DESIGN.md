# Database Design

数据库使用 MySQL 8.4，迁移工具使用 Flyway。所有表结构变更都应通过 `src/main/resources/db/migration/` 下的新 migration 文件完成。

当前已发布 V1-V6，实际表结构以 migration 和 `docs/CURRENT_ARCHITECTURE_AND_MODULES.md` 为准。本文件同时记录后续规划；标记为“规划”的表不得被 AI 误认为已经存在。

## 命名约定

- 表名使用 snake_case。
- 主键统一使用 `id bigint primary key auto_increment`。
- 股票代码字段统一使用 `symbol varchar(32)`。
- 交易日期字段使用 `trade_date date`。
- 金额和价格使用 `decimal(20, 6)` 或更高精度。
- 时间字段使用 `created_at`、`updated_at`。

## 当前与规划表

### stock_basic

状态：已由 V5 实现。用途：保存证券基础信息，为后续行情落库提供统一标识。

核心字段：

- `id`
- `symbol`
- `canonical_symbol`
- `name`
- `market`
- `list_date`
- `delisted`
- `created_at`
- `updated_at`

索引：

- unique `uk_stock_basic_canonical(canonical_symbol)`
- index `idx_stock_basic_market(market)`
- index `idx_stock_basic_symbol(symbol)`

### watchlist

用途：保存自选股和关注理由。

核心字段：

- `id`
- `symbol`
- `name`
- `group_name`
- `watch_reason`
- `trade_style`
- `risk_note`
- `enabled`
- `created_at`
- `updated_at`

索引：

- unique `uk_watchlist_symbol(symbol)`
- index `idx_watchlist_enabled(enabled)`

### stock_daily_bar

状态：已由 V5/V6 实现。用途：保存日 K 行情。

核心字段：

- `id`
- `canonical_symbol`
- `trade_date`
- `open_price`
- `high_price`
- `low_price`
- `close_price`
- `volume`
- `amount`
- `adjust_type`
- `data_source`
- `fetched_at`
- `created_at`
- `updated_at`

索引：

- unique `uk_daily_bar_key(canonical_symbol, trade_date, adjust_type, data_source)`
- index `idx_daily_bar_symbol_date(canonical_symbol, trade_date)`
- index `idx_daily_bar_date(trade_date)`

### stock_quote_snapshot

状态：已实现（V7 migration）。用途：保存从外部数据源查询到的价格快照。来源为 LongPort（后端反射 adapter 已实现，真实外联待 SDK 包安装），只作为外部行情快照，不替代手工估值。

核心字段：

- `id`
- `canonical_symbol`
- `quote_time`
- `current_price`
- `open_price`
- `high_price`
- `low_price`
- `pre_close_price`
- `volume`
- `amount`
- `data_source`
- `trade_status`
- `fetched_at`
- `raw_hash`
- `created_at`
- `updated_at`

索引：

- unique `uk_quote_snapshot_symbol_source_time(canonical_symbol, data_source, quote_time)`
- index `idx_quote_snapshot_symbol_time(canonical_symbol, quote_time)`
- index `idx_quote_snapshot_fetched_at(fetched_at)`

该表不得替代现有 `portfolio_price_snapshot`。后者是用户手工维护的估值数据。

### market_data_sync_task

状态：已实现（V7+V8+V9 migration）。用途：记录 LongPort/CSV 等行情同步任务的状态、范围和错误摘要。

核心字段：

- `id`
- `task_type`
- `provider`
- `scope_json`
- `status`
- `idempotency_key`
- `total_count`
- `success_count`
- `fail_count`
- `inserted_count`
- `updated_count`
- `skipped_count`
- `started_at`
- `finished_at`
- `last_error_code`
- `error_summary_json`
- `created_at`
- `updated_at`

索引：

- unique `uk_market_sync_idempotency(idempotency_key)`
- index `idx_market_sync_provider_status(provider, status)`
- index `idx_market_sync_created_at(created_at)`

### market_data_alert

状态：已实现（V7 migration）。用途：保存行情数据质量和量价观察提醒。提醒只用于观察和复盘，不作为交易指令。

核心字段：

- `id`
- `alert_type`
- `severity`
- `canonical_symbol`
- `provider`
- `quote_time`
- `trade_date`
- `task_id`
- `message`
- `trigger_value_json`
- `resolved`
- `created_at`
- `updated_at`

索引：

- index `idx_market_alert_symbol_resolved(canonical_symbol, resolved)`
- index `idx_market_alert_severity_created(severity, created_at)`
- index `idx_market_alert_task(task_id)`

### stock_minute_bar

用途：保存分钟行情，v0.2 后再重点实现。

核心字段：

- `id`
- `symbol`
- `trade_time`
- `interval_type`
- `open_price`
- `high_price`
- `low_price`
- `close_price`
- `volume`
- `amount`

索引：

- unique `uk_minute_symbol_time_interval(symbol, trade_time, interval_type)`

### technical_indicator_daily

用途：保存日线技术指标快照。

核心字段：

- `id`
- `symbol`
- `trade_date`
- `ma5`
- `ma10`
- `ma20`
- `ma60`
- `macd_dif`
- `macd_dea`
- `macd_hist`
- `rsi6`
- `rsi12`
- `boll_mid`
- `boll_upper`
- `boll_lower`
- `volume_ma5`
- `volume_ma20`
- `created_at`

索引：

- unique `uk_indicator_symbol_date(symbol, trade_date)`

### strategy_config

用途：保存策略配置。

核心字段：

- `id`
- `strategy_code`
- `strategy_name`
- `strategy_type`
- `config_json`
- `enabled`
- `created_at`
- `updated_at`

索引：

- unique `uk_strategy_code(strategy_code)`

### strategy_signal

用途：保存策略信号和触发原因。

核心字段：

- `id`
- `symbol`
- `trade_date`
- `strategy_code`
- `signal_type`
- `signal_strength`
- `trigger_reason`
- `indicator_snapshot_json`
- `risk_level`
- `risk_note`
- `created_at`

索引：

- index `idx_signal_symbol_date(symbol, trade_date)`
- index `idx_signal_strategy_date(strategy_code, trade_date)`

### backtest_task

用途：保存回测任务。

核心字段：

- `id`
- `task_name`
- `strategy_code`
- `symbol_scope_json`
- `start_date`
- `end_date`
- `initial_cash`
- `commission_rate`
- `slippage_rate`
- `status`
- `created_at`
- `updated_at`

### backtest_result

用途：保存回测汇总结果。

核心字段：

- `id`
- `task_id`
- `total_return`
- `annual_return`
- `max_drawdown`
- `win_rate`
- `profit_loss_ratio`
- `trade_count`
- `result_json`
- `created_at`

索引：

- index `idx_backtest_result_task(task_id)`

### trade_journal

用途：保存真实或模拟交易记录，支持复盘。

核心字段：

- `id`
- `symbol`
- `trade_date`
- `side`
- `price`
- `quantity`
- `position_ratio`
- `reason`
- `plan_stop_loss`
- `plan_take_profit`
- `actual_result`
- `created_at`

### portfolio_position_snapshot / portfolio_position_snapshot_item

状态：已由 V4 实现。用途：用主表和明细表保存某一时点的实际持仓盘点。

完整字段和状态规则见：

- `src/main/resources/db/migration/V4__add_position_snapshot.sql`
- `docs/features/POSITION_SNAPSHOT_DESIGN.md`
- `docs/api/POSITION_SNAPSHOT_API.md`

### risk_alert

用途：保存风险预警。

核心字段：

- `id`
- `symbol`
- `alert_date`
- `alert_type`
- `risk_level`
- `message`
- `source`
- `resolved`
- `created_at`

### review_note

用途：保存盘后复盘记录。

核心字段：

- `id`
- `review_date`
- `symbol`
- `title`
- `market_context`
- `decision_review`
- `mistake`
- `next_action`
- `created_at`
- `updated_at`

## 实施顺序

1. `docs/features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md` 已完成，对比和对账结果不新增结果表。
2. 行情 P1.0 已实现 `stock_basic` 和证券代码规范化。
3. 行情 P1.0 已实现 CSV 日 K 幂等导入。
4. LongPort P1.1 外部最新价接入时新增 `stock_quote_snapshot`、`market_data_sync_task`、`market_data_alert`。
5. 技术指标、策略信号和回测表在对应模块开发时逐步落地。

详细行情边界见 `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`。
