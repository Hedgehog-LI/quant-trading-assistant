# Database Design

数据库使用 MySQL 8.4，迁移工具使用 Flyway。所有表结构变更都应通过 `src/main/resources/db/migration/` 下的新 migration 文件完成。

当前已发布 V1-V4，实际表结构以 migration 和 `docs/CURRENT_ARCHITECTURE_AND_MODULES.md` 为准。本文件同时记录后续规划；标记为“规划”的表不得被 AI 误认为已经存在。

## 命名约定

- 表名使用 snake_case。
- 主键统一使用 `id bigint primary key auto_increment`。
- 股票代码字段统一使用 `symbol varchar(32)`。
- 交易日期字段使用 `trade_date date`。
- 金额和价格使用 `decimal(20, 6)` 或更高精度。
- 时间字段使用 `created_at`、`updated_at`。

## 当前与规划表

### stock_basic

状态：规划，尚未实现。用途：保存证券基础信息，为后续行情落库提供统一标识。

核心字段：

- `id`
- `symbol`
- `canonical_symbol`
- `name`
- `exchange`
- `market`
- `currency`
- `industry`
- `list_date`
- `list_status`
- `data_source`
- `source_updated_at`
- `created_at`
- `updated_at`

索引：

- unique `uk_stock_basic_canonical_symbol(canonical_symbol)`
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

状态：规划，尚未实现。用途：保存日 K 行情。

核心字段：

- `id`
- `symbol`
- `canonical_symbol`
- `trade_date`
- `open_price`
- `high_price`
- `low_price`
- `close_price`
- `pre_close_price`
- `volume`
- `amount`
- `turnover_rate`
- `adjust_type`
- `data_source`
- `fetched_at`
- `created_at`

索引：

- unique `uk_daily_symbol_date_adjust_source(canonical_symbol, trade_date, adjust_type, data_source)`
- index `idx_daily_date(trade_date)`

### stock_quote_snapshot

状态：规划，尚未实现。用途：保存从外部数据源查询到的价格快照。

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
- `fetched_at`
- `raw_hash`
- `created_at`

该表不得替代现有 `portfolio_price_snapshot`。后者是用户手工维护的估值数据。

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

1. 当前先完成 `docs/features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md`，对比和对账结果不新增结果表。
2. 行情阶段先实现 `stock_basic` 和证券代码规范化。
3. 再实现 CSV 日 K 幂等导入。
4. 外部最新价接入时新增 `stock_quote_snapshot` 和同步任务记录。
5. 技术指标、策略信号和回测表在对应模块开发时逐步落地。

详细行情边界见 `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`。
