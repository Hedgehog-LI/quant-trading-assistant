# Database Design

数据库使用 MySQL 8.4，迁移工具使用 Flyway。所有表结构变更都应通过 `src/main/resources/db/migration/` 下的新 migration 文件完成。

## 命名约定

- 表名使用 snake_case。
- 主键统一使用 `id bigint primary key auto_increment`。
- 股票代码字段统一使用 `symbol varchar(32)`。
- 交易日期字段使用 `trade_date date`。
- 金额和价格使用 `decimal(20, 6)` 或更高精度。
- 时间字段使用 `created_at`、`updated_at`。

## v0.1 核心表

### stock_basic

用途：保存股票基础信息。

核心字段：

- `id`
- `symbol`
- `name`
- `exchange`
- `market`
- `industry`
- `list_date`
- `status`
- `created_at`
- `updated_at`

索引：

- unique `uk_stock_basic_symbol(symbol)`
- index `idx_stock_basic_industry(industry)`

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

用途：保存日 K 行情。

核心字段：

- `id`
- `symbol`
- `trade_date`
- `open_price`
- `high_price`
- `low_price`
- `close_price`
- `pre_close_price`
- `volume`
- `amount`
- `turnover_rate`
- `created_at`

索引：

- unique `uk_daily_symbol_date(symbol, trade_date)`
- index `idx_daily_date(trade_date)`

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

### position_snapshot

用途：保存持仓快照。

核心字段：

- `id`
- `snapshot_date`
- `symbol`
- `quantity`
- `cost_price`
- `market_price`
- `market_value`
- `profit_loss`
- `position_ratio`
- `created_at`

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

## 实施建议

第一版 migration 不必一次实现所有字段，但至少要先落：

- `stock_basic`
- `watchlist`
- `stock_daily_bar`
- `technical_indicator_daily`
- `strategy_signal`
- `trade_journal`
- `risk_alert`
- `review_note`

复杂表如 `stock_minute_bar`、`backtest_result` 可以在回测模块开发时补充。
