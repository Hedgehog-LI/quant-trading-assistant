# Database Design

数据库使用 MySQL 8.4，迁移工具使用 Flyway。所有表结构变更都应通过 `src/main/resources/db/migration/` 下的新 migration 文件完成。

当前已发布 V1-V18，实际表结构以 migration 和 `docs/CURRENT_ARCHITECTURE_AND_MODULES.md` 为准。本文件同时记录后续规划；标记为“规划”的表不得被 AI 误认为已经存在。

## 命名约定

- 表名使用 snake_case。
- 主键统一使用 `id bigint primary key auto_increment`。
- 股票代码字段统一使用 `symbol varchar(32)`。
- 交易日期字段使用 `trade_date date`。
- 金额和价格使用 `decimal(20, 6)` 或更高精度。
- 时间字段使用 `created_at`、`updated_at`。

## 当前与规划表

### stock_basic

状态：已由 V5 实现、V17 扩展。用途：保存统一证券标识及本地证券目录，为行情落库、确定性搜索和证券选择提供同一事实源。

核心字段：

- `id`
- `symbol`
- `canonical_symbol`
- `name`
- `market`
- `name_cn`
- `name_hk`
- `name_en`
- `short_name`
- `pinyin_full`
- `pinyin_abbr`
- `exchange`
- `currency`
- `security_type`（`STOCK/ETF/INDEX/REIT/FUND/BOND/WARRANT/OPTION/FUTURE/OTHER`）
- `list_status`（`LISTED/DELISTED/UNKNOWN`）
- `list_date`
- `delisted`
- `data_source`
- `source_updated_at`
- `source_hash`
- `created_at`
- `updated_at`

索引：

- unique `uk_stock_basic_canonical(canonical_symbol)`
- index `idx_stock_basic_market(market)`
- index `idx_stock_basic_symbol(symbol)`
- index `idx_stock_basic_directory_filter(market, security_type, list_status)`
- index `idx_stock_basic_name(name)`
- index `idx_stock_basic_name_cn(name_cn)`
- index `idx_stock_basic_name_en(name_en)`
- index `idx_stock_basic_pinyin_full(pinyin_full)`
- index `idx_stock_basic_pinyin_abbr(pinyin_abbr)`
- index `idx_stock_basic_source_updated(source_updated_at)`

统一标识规则：A 股使用 `SH/SZ/BJ + 数字代码`；港股使用五位内部代码（如 `HK.02498`）；美股使用大写 ticker（如 `US.AAPL`、`US.BRK.B`）。V17 保留既有 `name/delisted` 字段和 `/stocks` CRUD 兼容，并把旧记录的 `delisted` 映射到新 `list_status`。

### stock_alias

状态：已实现（V17 migration）。用途：保存证券曾用名、简称、英文名、中文名和拼音等可检索别名；不建立平行证券主表。

核心字段：

- `id`
- `stock_basic_id` — 外键关联 `stock_basic`，主记录删除时级联清理
- `alias`
- `normalized_alias`
- `normalized_alias_key` — 二进制规范化键，避免数据库 collation 折叠不同 Unicode 值
- `alias_type`（`FORMER_NAME/OLD_TICKER/SHORT_NAME/ENGLISH/TRADITIONAL/USER`）
- `language`
- `data_source`
- `effective_from`
- `effective_to`
- `created_at`
- `updated_at`

索引：

- unique `uk_stock_alias_identity(stock_basic_id, normalized_alias_key, alias_type)`
- index `idx_stock_alias_normalized(normalized_alias)`
- index `idx_stock_alias_normalized_key(normalized_alias_key)`
- index `idx_stock_alias_stock(stock_basic_id)`

CSV 目录导入以 canonical symbol 更新同一 `stock_basic`，以规范化后的证券 id + alias + alias type 幂等写入 `stock_alias`。正式名称变化时旧名称进入 `FORMER_NAME`。任一行非法时整批回滚。

### security_directory_sync_state

状态：已实现（V18 migration）。用途：证券目录同步（D3）按 provider 维护最近成功时间/快照标识/计数/错误；不回写 `stock_basic`，`catalogStatus` 仍由 D1 启发式决定。

核心字段：

- `id`
- `provider`（唯一）
- `last_snapshot_id` / `last_snapshot_hash`
- `last_mode`（FULL/INCREMENTAL）
- `last_success_at`
- `last_inserted_count` / `last_updated_count` / `last_unchanged_count`
- `last_error_code` / `last_error_summary`
- `created_at` / `updated_at`

索引：

- unique `uk_security_directory_sync_state_provider(provider)`

D3 同步任务复用既有 `market_data_sync_task`（`task_type=SECURITY_MASTER_SYNC`，`provider=CSV_SNAPSHOT_DIR`），不新建第二套证券主数据。同步五阶段任一失败整批回滚，保留上一成功目录。

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

### agent_api_audit_log

状态：已实现（V16 migration）。用途：Agent 只读 API 调用持久化脱敏审计。记录 requestId、clientId（Token hash）、senderHash（QQ OpenID hash）、operationCode、method、path、paramSummary（脱敏截断）、httpStatus、errorCode、resultCount、durationMs、requestedAt、completedAt。严禁记录 Token、Longbridge 凭据、完整请求/响应或异常堆栈。

关键字段：

- `id` — 主键
- `request_id` — 请求追踪 ID
- `client_id` — 客户端标识（Token hash）
- `sender_hash` — QQ OpenID hash（可空）
- `operation_code` — 操作码（如 qtaAgentSystemHealth）
- `method` / `path` — HTTP 方法和路径
- `param_summary` — 参数摘要（脱敏截断至 500 字符）
- `http_status` — HTTP 状态码
- `error_code` — 业务错误码（可空）
- `result_count` — 结果条数
- `duration_ms` — 耗时毫秒
- `requested_at` / `completed_at` — 请求开始/完成时间
- `created_at`

## 实施顺序

1. `docs/features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md` 已完成，对比和对账结果不新增结果表。
2. 行情 P1.0 已实现 `stock_basic` 和证券代码规范化。
3. 行情 P1.0 已实现 CSV 日 K 幂等导入。
4. LongPort P1.1 外部最新价接入时新增 `stock_quote_snapshot`、`market_data_sync_task`、`market_data_alert`。
5. 行情 P1.2 已实现工作台/采集计划/分钟线/交易时段/水位（V10：`stock_minute_bar`、`market_trading_session`、`market_calendar`、`market_data_sync_plan`、`market_data_sync_task_item`、`market_data_watermark`）。
6. 行情 P1.3 已实现板块/自定义分组（V11：`market_segment`、`market_segment_member`）。
7. 行情 P1.5 已实现市场行业关注与不可变快照（V14：`market_sector_watch`、`market_sector_snapshot`、`market_sector_member_snapshot`）。
8. 行情 P1.6 已实现全市场板块历史榜单与自动采集（V15：`market_sector_ranking_config`、`market_sector_ranking_batch`、`market_sector_ranking_item`），并扩展关注/快照表的采集频率、claim、质量和错误状态。
9. 证券目录 P1.4b-D1 已实现 `stock_basic` 扩展和 `stock_alias`（V17），支持 CSV 原子幂等导入、本地确定性搜索和详情查询。P1.4b-D3 已实现 `security_directory_sync_state`（V18）与目录同步（CSV 快照 provider、五阶段管线、复用 `market_data_sync_task` 的 `SECURITY_MASTER_SYNC`）。
10. 技术指标、策略信号和回测表在对应模块开发时逐步落地。

详细行情边界见 `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`。

## 板块分析规划表（P1.7，规划 V19+）

> 状态：**规划 V19+**，未实现。本块为板块分析层（相对强弱 / 轮动持续性 / 收益贡献与交易集中度 / 量价确认 / 异动提醒）的衍生指标表设计，绑定更高 Flyway 版本 V19+，不复用 V1-V18 既有版本号。所有衍生表均为独立新表，**只读原始事实表，不写回**（衍生读服务只读 `market_sector_*`/`market_sector_ranking_*`/`stock_*` 原始事实表，禁止 UPDATE/写回/回写/覆盖）。本规划区域不表述任何已落库事实，全部为规划。详细公式与口径见 `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md`。

### 统一版本血缘列（所有板块分析衍生表强制含下列字段）

每张衍生表都强制包含版本与血缘列；公式升级写新 `formula_version` 行，旧行 `is_latest=false` 且填 `superseded_at`，绝不覆盖：

- `formula_code` varchar(64) — 公式标识（`RELATIVE_RETURN_LOG` / `ROTATION_SPEARMAN` / `ROTATION_SECTOR_PERSISTENCE` / `MEMBER_RETURN_CONTRIBUTION` / `TURNOVER_CONCENTRATION` / `VOLUME_CONFIRMATION`）。
- `formula_version` varchar(16) — 公式版本，幂等键必含。
- `parameter_hash` varchar(64) — 输入参数内容哈希。
- `source_provider` varchar(32) — 原始事实 provider。
- `source_batch_id` bigint 或 `source_snapshot_id` bigint — 来源榜单批次/快照标识。
- `source_date_range` varchar(64) — 来源日期区间。
- `calculated_at` datetime — 计算时间。
- `quality_status` varchar(32) — `OK`/`INSUFFICIENT_SAMPLE`/`INSUFFICIENT`/`STALE`/`ORIGIN_CHANGED`。
- `quality_reason` varchar(128) — 降级原因。
- `valid_sample_size` int — 实际有效样本数。
- `is_latest` boolean — 是否当前最新版本。
- `superseded_at` datetime — 被新版本取代时间（可空）。

### 板块分析 V19+ 衍生表（规划）

#### sector_relative_strength_snapshot（规划 V19+）

状态：规划 V19+，未实现。用途：板块相对强弱（N 日对数相对收益 `relativeReturn_N` + RS-rank 百分位）衍生快照。数据来源按 rank_scope 区分（只读原始事实表，不写回）：`FULL_MARKET` 读连续 N 个交易日全市场 CLOSE 榜单历史 `market_sector_ranking_batch`/`market_sector_ranking_item`（`snapshot_type='CLOSE'`，取各板块 `change_rate`）；`WATCHED_ONLY` 读被关注板块 CLOSE 快照 `market_sector_snapshot`（`trigger_type='CLOSE'`，取 `change_rate`）；tracking symbol 基准读 `stock_daily_bar.close_price`（需连续 `N+1` 个收盘价）。

核心字段：`id`（主键，bigint auto_increment）、`sector_identity`（varchar(96)）、`market_code`（varchar(8)，CN/HK/US）、`as_of_date`（date）、`window`（smallint，20/50/120）、`benchmark_type`（varchar(32)，`TRACKING_SYMBOL`/`SECTOR_EQUAL_WEIGHT`）、`benchmark_symbol`（varchar(32)，可空）、`relative_return_n`（decimal(20,10)）、`rs_rank_percentile`（decimal(20,6)，0~1）、`rank_scope`（varchar(16)，`FULL_MARKET`：连续 N 个交易日全市场 CLOSE 榜单历史（`market_sector_ranking_batch`/`market_sector_ranking_item`）重建各板块 N 日合成净值与 `relativeReturn_N` 后排名，含全市场全部板块，等权基准同源为全市场等权；`WATCHED_ONLY`：全市场历史不足时降级，仅 WATCHED 板块 CLOSE 快照（`market_sector_snapshot`）重建排名，附 `quality_reason='RANK_SCOPE_WATCHED_ONLY'`，等权基准同源退化为被关注集合等权，降级展示）、统一版本血缘列、`created_at`/`updated_at`（datetime）。

幂等键：unique `uk_sector_rs(sector_identity, as_of_date, window, formula_version)`（含 `formula_version`）；索引 `idx_sector_rs_market_date(market_code, as_of_date)`。

#### sector_rotation_market_stability（规划 V19+，市场级）

状态：规划 V19+，未实现。用途：**市场级**轮动稳定性（相邻交易日全市场 `rank_no` 向量的 Spearman ρ），键 `(market_code, trade_date, window, formula_version)`，**不含 sector_identity**，不重复存入任何 sector 记录。只读 `market_sector_ranking_batch`/`market_sector_ranking_item`（CLOSE）原始事实表，不写回。

核心字段：`id`、`market_code`、`trade_date`（date）、`window`（5/10/20）、`rank_spearman_mean`（decimal(20,6)）、统一版本血缘列、`created_at`/`updated_at`。

幂等键：unique `uk_sector_rotation_market(market_code, trade_date, window, formula_version)`（市场级，无 sector_identity）；索引 `idx_sector_rotation_market_date(market_code, trade_date)`。

#### sector_rotation_sector_persistence（规划 V19+，板块级）

状态：规划 V19+，未实现。用途：**板块级**位次序列指标（平均位次、位次标准差、头部桶占用率、连续领涨/领跌天数、位次变化）。只读 `market_sector_ranking_item`（CLOSE `rank_no`）原始事实表，不写回。

核心字段：`id`、`sector_identity`、`market_code`、`as_of_date`（date）、`window`（5/10/20）、`mean_rank_percentile`（decimal(20,6)）、`rank_percentile_std_dev`（decimal(20,6)）、`top_bucket_occupancy_rate`（decimal(20,6)）、`consecutive_leading_days`（int）、`consecutive_lagging_days`（int）、`rank_change`（int）、统一版本血缘列、`created_at`/`updated_at`。

幂等键：unique `uk_sector_rotation_sector(sector_identity, as_of_date, window, formula_version)`（含 `formula_version`）；索引 `idx_sector_rotation_sector_market_date(market_code, as_of_date)`。

#### sector_member_return_contribution（规划 V19+，收益贡献）

状态：规划 V19+，未实现。用途：板块真实收益贡献（`weight · memberReturn`，权重优先前收盘价×流通股本，缺失降级等权）。只读 `market_sector_member_snapshot`/`market_sector_snapshot` 原始事实表，不写回。**不得**与交易集中度混称。

核心字段：`id`、`sector_identity`、`market_code`、`trade_date`（date）、`window`（1/5）、`weight_method`（varchar(32)，`FREEFLOAT_PRICE`/`EQUAL_WEIGHT_FALLBACK`）、`sum_contribution`（decimal(20,10)）、`sector_return`（decimal(20,10)）、`residual`（decimal(20,10)）、`top_contributors_json`（text）、`excluded_member_count`（int）、`valid_member_count`（int）、统一版本血缘列、`created_at`/`updated_at`。

幂等键：unique `uk_sector_contribution(sector_identity, trade_date, window, formula_version)`（含 `formula_version`）；索引 `idx_sector_contribution_market_date(market_code, trade_date)`。

#### sector_turnover_concentration（规划 V19+，交易集中度）

状态：规划 V19+，未实现。用途：板块交易集中度（top-K 成交额占比 + 正/负/绝对净流入集中度，净流入分母为 absSum 避免除零/负）。只读 `market_sector_member_snapshot`/`market_sector_snapshot` 原始事实表，不写回。**不得**把成交额占比称为“涨幅贡献/收益贡献”。

核心字段：`id`、`sector_identity`、`market_code`、`trade_date`（date）、`window`（1/5）、`top_k_turnover_share`（decimal(20,6)）、`positive_flow_concentration`（decimal(20,6)，可空）、`negative_flow_concentration`（decimal(20,6)，可空）、`absolute_flow_concentration`（decimal(20,6)，可空）、`top_k`（int）、`top_concentrators_json`（text）、`excluded_member_count`（int）、`valid_member_count`（int）、统一版本血缘列、`created_at`/`updated_at`。

幂等键：unique `uk_sector_concentration(sector_identity, trade_date, window, formula_version)`（含 `formula_version`）；索引 `idx_sector_concentration_market_date(market_code, trade_date)`。

#### sector_volume_confirmation_snapshot（规划 V19+，六状态）

状态：规划 V19+，未实现。用途：板块量价确认（六状态 + 量比）。只读 `market_sector_snapshot` 原始事实表，不写回。

核心字段：`id`、`sector_identity`、`market_code`、`trade_date`（date）、`change_rate`（decimal(20,8)）、`turnover_amount`（decimal(30,6)）、`turnover_ratio`（decimal(20,6)）、`confirmation_status`（varchar(24)，`UP_CONFIRMED`/`UP_UNCONFIRMED`/`DOWN_CONFIRMED`/`DOWN_UNCONFIRMED`/`NEUTRAL`/`INSUFFICIENT`）、统一版本血缘列、`created_at`/`updated_at`。

幂等键：unique `uk_sector_volume(sector_identity, trade_date, formula_version)`（含 `formula_version`）；索引 `idx_sector_volume_market_date(market_code, trade_date)`。

#### market_data_alert 复用（不新增告警表，规划 V19+）

异动提醒复用 V7 版本的 `market_data_alert` 表（该表本身已在 V7 落库），新增 `alert_type=SECTOR_*`（`SECTOR_RS_REVERSAL`、`SECTOR_VOLUME_CONFIRMATION`、`SECTOR_TURNOVER_CONCENTRATION`、`SECTOR_RANK_JUMP` 等），`severity` 取 `INFO/WARN/HIGH`，`trigger_value_json` 存派生指标上下文与 `formula_code`/`formula_version`。不新建第二套告警表。规划 V19+ 仅在应用层新增枚举值与写入逻辑，不新建表结构（若需索引调整由 ST-3 评估）。

#### MyBatis / Flyway 边界（规划 V19+）

- 新表走更高 Flyway 版本 V19+（V19、V20...），SQL 放在 `src/main/resources/db/migration/V19__*.sql` 等。
- MyBatis XML 放在 `src/main/resources/mapper/`，主键 `id bigint auto_increment`，金额/价格 `decimal(20,6)` 或更高精度，时间 `created_at`/`updated_at`。
- 衍生读服务只读原始事实表，不反向 UPDATE 原始表；衍生结果只存新表，可重算、可下线。
