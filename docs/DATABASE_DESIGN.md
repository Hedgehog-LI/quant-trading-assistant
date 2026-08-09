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

> 状态：**规划 V19+，v1.1 专家复审修订**。先实现 P1.7-A 数据就绪与计算运行，再实现 P1.7-B 衍生指标。MVP 不建立收益贡献表。

### P1.7-A 数据治理表与原始事实扩展（规划）

- `market_sector_identity`：`id bigint` 主键，自然唯一键 `(provider_code, market_code, provider_sector_id, taxonomy_version)`，有效区间左闭右开。新增唯一锚点表 `market_sector_identity_lock(provider_code, market_code, provider_sector_id)`；READ COMMITTED 写事务 `INSERT IGNORE` 后 `SELECT ... FOR UPDATE` 锁锚点，再校验所有 taxonomy version 区间不重叠。
- 原始快照生命周期迁移：给 snapshot/member snapshot 回填 `sector_identity_id`，移除 watch 级联删除；DELETE watch 仅归档关注关系，历史事实保留。
- `sector_analytics_publication_batch`：跨公式一致发布单元。run 和 member 都保存 provider/market/as-of-date；member 分别用含这些范围字段的复合 FK 指向 batch 与 run，数据库拒绝跨范围成员。required hash 纳入 formula/version/parameter，group hash再纳入 source manifest/run id；成员集合、范围、READY 状态和 hash 全部在发布事务重算。衍生结果行不保存 batch ID。
- `sector_analytics_calculation_run`：单个公式的版本、参数哈希、来源 manifest、候选/READY/发布状态、DB claim 和错误；唯一键 `(formula_code, formula_version, parameter_hash, source_manifest_hash)`，通过 publication member 归属批次。
- 扩展 `market_sector_ranking_batch`：`expected_item_count/actual_item_count/is_truncated/coverage_rate/taxonomy_version`。无法证明完整时仅为 `RANKED_UNIVERSE`。
- 扩展板块快照事实：provider quote time、currency code、金额单位、累计周期/重置语义、规范化交易状态；P1.7-A 验收前不得计算资金趋势或陈旧状态。
- 扩展 `market_calendar`：`source_code/verification_status`；长窗口仅接受 `EXCHANGE_FILE/MANUAL_VERIFIED`，HK/US 缺少验证日历时 fail closed，不使用周末推断。

### 统一版本血缘列（所有板块分析衍生表强制含下列字段）

每张衍生表都强制包含版本与血缘列；单公式结果通过 calculation run 发布，跨公式总览通过 publication batch 原子发布，旧 run/batch 保留且绝不覆盖：

- `formula_code` varchar(64) — 公式标识（`RELATIVE_RETURN_LOG` / `ROTATION_SPEARMAN` / `ROTATION_SECTOR_PERSISTENCE` / `CAPITAL_FLOW_TREND` / `TURNOVER_CONCENTRATION` / `VOLUME_CONFIRMATION`）。
- `formula_version` varchar(16) — 公式版本，幂等键必含。
- `parameter_hash` varchar(64) — 输入参数内容哈希并纳入唯一键。
- `calculation_run_id` bigint — 已发布计算运行。
- `source_provider` varchar(32) — 原始事实 provider。
- `source_date_range` varchar(64) — 来源日期区间。
- `source_manifest_hash` varchar(64) — 完整输入 ID 清单哈希。
- `calculated_at` datetime — 计算时间。
- `quality_status` varchar(32) — `OK/DEGRADED/NO_DERIVED_DATA/INSUFFICIENT_RAW/INSUFFICIENT_SAMPLE/STALE/ORIGIN_CHANGED/BLOCKED_AUTH/BLOCKED_PERMISSION/BACKOFF`。
- `quality_reason_codes` text — 结构化原因码 JSON 数组。
- `valid_sample_size` int — 实际有效样本数。
- `published_at` datetime — 所属 calculation run 的原子发布时间。

### 板块分析 V19+ 衍生表（规划）

#### sector_relative_strength_snapshot（规划 V19+）

状态：规划 V19+，未实现。用途：共同 rank-set 等权基准下的相对强弱。`change_rate` 冻结为 decimal ratio；只消费通过完整性门禁的 CLOSE 榜单。范围为 `VERIFIED_FULL_MARKET/RANKED_UNIVERSE`，tracking symbol 只作详情对照。

核心字段：`id`、`sector_identity_id` FK、`market_code`、`as_of_date`、`window`、`benchmark_type='RANK_SET_EQUAL_WEIGHT'`、`relative_return_n`、`rs_rank_percentile`、`rank_scope`、`coverage_rate`、统一版本血缘列和时间列。

幂等键：unique `uk_sector_rs(calculation_run_id, sector_identity_id, as_of_date, window)`。

#### sector_rotation_market_stability（规划 V19+，市场级）

状态：规划 V19+，未实现。用途：**排行样本级**轮动稳定性（相邻交易日按稳定身份对齐、由 `change_rate` 重算平均秩后的 Spearman ρ），键 `(market_code, trade_date, window, formula_version)`，不含板块身份。MVP 固定 `RANKED_UNIVERSE`，不得称为全市场。

核心字段：`id`、`market_code`、`trade_date`、`window`、`rank_scope`、`source_coverage_rate`、`is_truncated`、`rank_spearman_mean`（按 intersection count 加权）、`min_pair_coverage/avg_pair_coverage/valid_pair_count/weighted_intersection_count`、统一版本血缘列和时间列。

幂等键：unique `uk_sector_rotation_market(calculation_run_id, source_provider, market_code, trade_date, window)`。

`sector_rotation_pair_metric` 保存每个相邻日对的 left/right/intersection count、`pair_coverage=min(intersection/left, intersection/right)`、ρ 和原因码；唯一键 `(calculation_run_id, market_code, left_trade_date, right_trade_date)`。

#### sector_rotation_sector_persistence（规划 V19+，板块级）

状态：规划 V19+，未实现。用途：**板块级**位次序列指标。按 CLOSE `change_rate` 重算平均秩/百分位；持久化 `rank_no` 只作交叉校验，不参与公式。

核心字段：`id`、`sector_identity_id` FK、`market_code`、`as_of_date`、`window`、`rank_scope`、`mean_rank_percentile`、`rank_percentile_std_dev`、`top_bucket_occupancy_rate`（百分位 ≥0.8）、`consecutive_leading_days`/`consecutive_lagging_days`（按截面最大/最小 `change_rate`）、`rank_percentile_change`、统一版本血缘列和时间列。

幂等键：unique `uk_sector_rotation_sector(calculation_run_id, sector_identity_id, as_of_date, window)`。

#### sector_capital_flow_trend（规划 V19+，资金趋势）

状态：规划 V19+，未实现。用途：保存**关注板块范围**的净流入方向、持续性和变化速度。核心字段：`sector_identity_id/market_code/trade_date/window/flow_scope='WATCHED_SECTORS'/currency_code/net_inflow/turnover_amount/flow_intensity/cumulative_net_inflow_n/mean_flow_intensity_n/positive_flow_days_rate/flow_intensity_change`、统一版本血缘列和时间列。

幂等键：unique `uk_sector_flow_trend(calculation_run_id, sector_identity_id, trade_date, window)`。

`sector_member_return_contribution` 后置到 P1.7-C；在 point-in-time 成分和 `t-1` 权重门禁通过前不得建表。

#### sector_turnover_concentration（规划 V19+，交易集中度）

状态：规划 V19+，未实现。用途：单个 CLOSE 快照的板块交易集中度（top-K 成交额占比 + 正/负资金方向占比 + top-K 绝对流量集中度）。正/负方向占比之和为 1，不得称为集中度或收益贡献。

核心字段：`id`、`sector_identity_id` FK、`market_code`、`trade_date`、`window`（MVP 固定 1）、`data_scope='WATCHED_SECTORS'`、`top_k_turnover_share`、`positive_flow_share`、`negative_flow_share`、`absolute_flow_concentration`、`top_k`、`top_turnover_members_json`、`top_absolute_flow_members_json`、`excluded_member_count`、`valid_member_count`、统一版本血缘列和时间列。`absSum=0` 时相关字段为空且质量为 `INSUFFICIENT_RAW`。

幂等键：unique `uk_sector_concentration(calculation_run_id, sector_identity_id, trade_date, window, top_k)`。

#### sector_volume_confirmation_snapshot（规划 V19+，六状态）

状态：规划 V19+，未实现。用途：板块量价确认（六状态 + 量比）。只读 `market_sector_snapshot` 原始事实表，不写回。

核心字段：`id`、`sector_identity_id` FK、`market_code`、`trade_date`、`data_scope='WATCHED_SECTORS'`、`change_rate`、`turnover_amount`、`turnover_ratio`、`confirmation_status`、统一版本血缘列和时间列。量比必须有完整 `t-5..t-1` 五个 CLOSE。

幂等键：unique `uk_sector_volume(calculation_run_id, sector_identity_id, trade_date)`。

#### market_data_alert 扩展（复用表，规划 V19+）

复用 V7 表但必须迁移增加 `subject_type`、`sector_identity_id`、`dedup_key`、`calculation_run_id`、`publication_batch_id`；板块提醒必须绑定 batch，非空 run 通过 `(publication_batch_id, calculation_run_id)` 复合 FK 指向 publication member。dedup 覆盖板块、交易日、类型、batch、参数与证据。Controller/Service/Mapper/XML 同步支持主体与批次过滤。

#### MyBatis / Flyway 边界（规划 V19+）

- 新表走更高 Flyway 版本 V19+（V19、V20...），SQL 放在 `src/main/resources/db/migration/V19__*.sql` 等。
- MyBatis XML 放在 `src/main/resources/mapper/`，主键 `id bigint auto_increment`，金额/价格 `decimal(20,6)` 或更高精度，时间 `created_at`/`updated_at`。
- 衍生读服务只读原始事实表，不反向 UPDATE 原始表；衍生结果只存新表，可重算、可下线。
