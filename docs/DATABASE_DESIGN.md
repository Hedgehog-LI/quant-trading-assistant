# Database Design

数据库使用 MySQL 8.4，迁移工具使用 Flyway。所有表结构变更都应通过 `src/main/resources/db/migration/` 下的新 migration 文件完成。

当前已发布 V1-V15，实际表结构以 migration 和 `docs/CURRENT_ARCHITECTURE_AND_MODULES.md` 为准。本文件同时记录后续规划；标记为“规划”的表不得被 AI 误认为已经存在。

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

统一标识规则：A 股使用 `SH/SZ/BJ + 数字代码`；港股使用五位内部代码（如 `HK.02498`）；美股使用大写 ticker（如 `US.AAPL`、`US.BRK.B`）。现有 `varchar(32)` 字段可容纳这些格式，本轮无需 migration。

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

状态：已实现（V7 migration）。用途：保存从外部数据源查询到的价格快照。来源为 LongPort（后端反射 adapter 已实现，真实外联已于 2026-07-12 验收通过），只作为外部行情快照，不替代手工估值。

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

状态：已实现（V10 migration）。用途：保存 1M/5M/15M/30M/60M 分钟 K，支撑历史补档、盘中采集、量价异动和后续指标/回测。详细设计见 `features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`。

核心字段：

- `id`
- `canonical_symbol`
- `trade_date`
- `bar_start_time`
- `bar_end_time`
- `interval_type`
- `session_type`
- `open_price`
- `high_price`
- `low_price`
- `close_price`
- `volume`
- `amount`
- `turnover_rate`
- `adjust_type`
- `data_source`
- `fetched_at`
- `raw_hash`
- `quality_status`
- `created_at`
- `updated_at`

索引：

- unique `uk_minute_bar_key(canonical_symbol, bar_start_time, interval_type, adjust_type, data_source)`
- index `idx_minute_bar_symbol_time(canonical_symbol, interval_type, adjust_type, data_source, bar_start_time)`
- index `idx_minute_bar_trade_date(trade_date, interval_type)`

### market_trading_session

状态：已实现（V10 migration）。用途：保存 A 股交易时段定义（集合竞价/上午/下午等），分钟 K 写入时据此做时段校验。

幂等键：`market_code + session_type + trade_date`（启动时 `@PostConstruct` 幂等初始化默认 A 股时段，GET 请求只读不写防死锁）。

### market_calendar

状态：已实现（V10 migration）。用途：交易日历，用于判断某市场某日是否交易日（`/trading-sessions/is-trading-day`）。分钟 K 写入做交易日校验时使用。

幂等键：`market_code + trade_date`。

### market_data_sync_plan

状态：已实现（V10 + V13 migration）。用途：行情采集计划（采集任务配置），支持任务类型/provider/scope/enabled/trigger，提供 CRUD + 启停 + 手动执行 `POST /sync-plans/{id}/run`。手工执行支持 `DAILY_BAR_BACKFILL` 和 `MINUTE_BAR_BACKFILL`；`INTRADAY_MINUTE_REFRESH` 由 scheduler 触发。

V13 新增 `run_claim_token` / `run_claimed_at` / `running_task_id` 与 claim 索引，用条件 UPDATE 在 DB 层防止同一计划重叠执行。正常终态释放 claim；服务启动时收敛遗留 task/item 后释放。

幂等键：`task_type + provider + scope_hash`（同任务同源同 scope 唯一）。

### market_data_sync_task_item

状态：已实现（V10 migration）。用途：单个 sync_task 下按 symbol/范围的执行明细，记录每个标的的成功/失败/跳过/错误码，支撑任务执行过程可查（`GET /sync-tasks/{taskId}/items`）。

V12 新增 `sub_task_id`，关联逐标的日 K 子任务。父任务为 `RUNNING` 时，可通过查询明细触发安全懒收敛，或调用 `POST /sync-tasks/{taskId}/reconcile` 主动收敛；收敛直接汇总子任务六类 count，不从 item 状态反推行数。

幂等键：`task_id + canonical_symbol + scope_key`。

### market_data_watermark

状态：已实现（V10 migration）。用途：按数据源/标的/interval 记录已落库数据的最新时间水位，支撑补档范围判断和重复抓取避免。`GET /watermarks` 查询。

幂等键：`canonical_symbol + data_source + interval_type + adjust_type`。

### market_segment

状态：已实现（V11 migration）。用途：板块/自定义分组主表（行业/概念/自定义），支持 CRUD + 启停 + `memberCount` 冗余字段，与成员表数量保持一致。

幂等键：`segment_type + segment_code`（或名称唯一，按代码为准）。

### market_segment_member

状态：已实现（V11 migration）。用途：板块成员明细，记录某板块下包含的 `canonical_symbol` 及其加入时间/排序。板块删除级联清理成员；同板块同 symbol 不允许重复。

幂等键：`segment_id + canonical_symbol`。

### market_sector_watch / market_sector_snapshot / market_sector_member_snapshot

状态：已实现（V14，V15 扩展）。用途：保存用户明确关注的 provider 行业、聚合快照和逐成分行情资金事实。V15 增加自动采集开关/频率、运行 claim、失败状态、时间桶、触发类型和质量覆盖字段。自动快照以 `watch_id + snapshot_bucket_time` 幂等；手工快照的桶可为空。

### market_sector_ranking_config

状态：已实现（V15）。用途：按 provider + CN/HK/US 保存全市场行业榜单采集配置与运行状态。盘中频率只允许 `0/5/10/15/30/60`，并单独控制收盘快照；保存 claim、最近成功、失败次数、下次重试及结构化错误。

### market_sector_ranking_batch / market_sector_ranking_item

状态：已实现（V15）。用途：批次保存某市场某时间桶的全行业宽度、领涨/领跌和质量，明细保存完整排名及领涨标的。`provider_code + market_code + snapshot_type + snapshot_bucket_time` 唯一，明细以 `batch_id + provider_sector_id` 唯一并随批次级联删除。

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
5. 行情 P1.2 已实现工作台/采集计划/分钟线/交易时段/水位（V10：`stock_minute_bar`、`market_trading_session`、`market_calendar`、`market_data_sync_plan`、`market_data_sync_task_item`、`market_data_watermark`）。
6. 行情 P1.3 已实现板块/自定义分组（V11：`market_segment`、`market_segment_member`）。
7. 行情 P1.5 已实现市场行业关注与不可变快照（V14：`market_sector_watch`、`market_sector_snapshot`、`market_sector_member_snapshot`）。
8. 行情 P1.6 已实现全市场板块历史榜单与自动采集（V15：`market_sector_ranking_config`、`market_sector_ranking_batch`、`market_sector_ranking_item`），并扩展关注/快照表的采集频率、claim、质量和错误状态。
9. 技术指标、策略信号和回测表在对应模块开发时逐步落地。

详细行情边界见 `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`。
