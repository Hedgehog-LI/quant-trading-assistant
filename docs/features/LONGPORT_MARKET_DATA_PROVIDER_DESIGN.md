# Feature Design: LongPort 只读行情源接入

> 版本：v0.1.4 · 状态：真实外联已验收（2026-07-12 latest quote + daily bar；2026-07-17 A 股分钟 K 最小链路） · 关联：`../BUILD_CHECKLIST.md`、`MARKET_DATA_FOUNDATION_DESIGN.md`、`LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`、`../api/MARKET_DATA_API.md`、`../decisions/ADR-0008-longport-quote-only-provider.md`

## 0. 当前实现事实（2026-07-17 验收口径）

LongPort 只读行情源已端到端打通并验证数据正确性：

- 已完成：`MarketDataProvider` 抽象、`DisabledMarketDataProvider`、`FakeMarketDataProvider`、`LongPortSymbolMapper`、V7-V9 表结构、行情状态/快照/同步任务/提醒 API、前端 `/market-data` 6 Tab。
- 已完成：`LongPortProperties`、`LongPortMarketDataProvider`、`LongPortQuoteClient`、`ReflectiveLongPortQuoteClient`、Docker/env 透传和 SDK 缺失安全降级测试。
- 已完成（2026-07-12）：官方 Java SDK 安装、域名覆盖、真实凭据外联、单 symbol 多日 latest quote + daily bar 落库验收。
- 已完成（2026-07-17）：分钟 K adapter 使用 SDK 4.3.3 原生 1M/5M/15M/30M/60M 周期，按单次 1000 根上限做日期分块，并通过客户端 60 次/30 秒限流器统一约束调用。
- 已完成（2026-07-17）：采集计划执行引擎、A 股交易时段 scheduler、DB claim 与重启恢复。真实最小验收 `SH.601318 / 2026-07-10 / 5M`：provider 返回 49 根，落库 48 根，15:00 边界根按会话规则计入 skipped，水位止于 14:55。
- 启用方式：`qta.market-data.longport.enabled=true`（默认 `false`，安全兜底）。配置项见本文件 §5。
- 运行态：未启用、SDK 未安装或凭据未配置时，`/providers/LONGPORT/status` 返回 `configured=false`，同步或拉取请求返回 `BUSINESS_RULE_VIOLATION`，这是预期业务拦截，不是系统崩溃。
- 页面 502 常见原因：本地 Vite `VITE_DEV_PROXY_TARGET` 指向了未运行的端口，例如 `localhost:18081`；当前 Docker 后端默认暴露 `localhost:8080`。

## 1. 用户目标

用户希望系统不再只依赖手工录入和 CSV，而是可以从 LongPort/长桥 OpenAPI 获取 A 股行情：

- 查询自选股、持仓股和计划股的最新行情。
- 将外部最新价以“行情快照”方式落库，保留来源和抓取时间。
- 将历史日 K 同步到 `stock_daily_bar`，为后续指标、策略和回测提供基础数据。
- 发现数据缺失、权限不足、行情过旧、同步失败等数据质量问题。
- 后续基于量价异常做观察提醒，但不生成自动交易动作。

## 2. 范围

做：

- 在现有 `marketdata` 模块内接入 LongPort 只读 Quote provider。
- 复用已实现的 `stock_basic` 和 `stock_daily_bar`。
- 新增外部最新价快照、行情同步任务、行情异常提醒的 DB/API/UI。
- 支持 mock/remote 双模式，mock 不请求真实行情。
- 在建设看板中体现 P1 行情 provider、历史同步、最新价快照和异常提醒进度。

不做：

- 不调用 LongPort 交易、订单、账户资金、真实持仓、改单、撤单能力。
- 不把 LongPort 当券商账户连接。
- 不在前端、数据库、日志或 Git 中保存 LongPort token/app secret。
- 不让外部行情覆盖手工价格 `portfolio_price_snapshot`、交易流水或持仓快照。
- 不做全市场高频扫描，不做自动下单，不输出确定性买卖建议。

## 3. 数据归属

| 数据 | 归属 | 说明 |
| --- | --- | --- |
| 证券主数据 | DB | 已有 `stock_basic`，作为本地白名单和统一代码来源 |
| 日 K 行情 | DB | 已有 `stock_daily_bar`，LongPort 写入时 `data_source=LONGPORT` |
| 外部最新价快照 | DB | 新增 `stock_quote_snapshot`，保存报价时间、抓取时间、来源 |
| 同步任务 | DB | 新增 `market_data_sync_task`，记录任务状态、范围、错误摘要 |
| 行情异常提醒 | DB | 新增 `market_data_alert`，只表达观察/质量风险 |
| 手工当前价 | DB | 已有 `portfolio_price_snapshot`，不得被外部行情自动覆盖 |
| LongPort 凭据 | SERVER_ENV | 只在服务端环境变量或安全配置中存在 |

## 4. 产品分期

### P1.0 已有基础事实

- `stock_basic` 已由 V5 实现。
- `stock_daily_bar` 已由 V5 实现，V6 增加 `fetched_at`。
- `/api/v1/market-data/stocks` 已支持证券主数据 CRUD。
- `/api/v1/market-data/daily-bars` 已支持日 K 查询。
- `/api/v1/market-data/daily-bars/import` 已支持 CSV 幂等导入。

### P1.1 LongPort Quote Provider MVP

- 新增 provider 抽象：`MarketDataProvider`。
- 已实现 LongPort adapter：`LongPortMarketDataProvider` + `ReflectiveLongPortQuoteClient`（详见 `LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`）。
- 支持 `SH.600519` / `SZ.000001` / `BJ.430047` 与 LongPort `600519.SH` / `000001.SZ` / `430047.BJ` 的双向映射。
- 支持 provider status / health-check，不暴露密钥。
- 支持按证券列表获取最新行情，按需落库为 `stock_quote_snapshot`。

### P1.2 历史日 K 存档

- 新增同步任务 API，按 symbol/date range/adjustType/provider 拉取历史日 K。
- 写入现有 `stock_daily_bar`，用 `(canonical_symbol, trade_date, adjust_type, data_source)` 幂等。
- 返回 inserted / updated / skipped / failed 和错误摘要。

### P1.3 异常提醒 MVP

- 先覆盖数据质量异常：provider 未配置、无权限、限流、行情时间过旧、同步失败、日 K 返回为空。
- 再覆盖轻量量价观察：跳空、放量、缩量、高振幅、临近计划价。
- 所有提醒都要带数据源、触发时间、触发值、阈值和“不构成投资建议”提示。

### P2 指标/策略/回测前置

- 在日 K 和快照稳定后，再做 MA、MACD、RSI、BOLL、成交量均线、ATR。
- 策略信号必须经过风控模块，且不能直接触发交易。

## 5. 后端设计

推荐包结构：

```text
com.quant.trade.marketdata
├── provider
│   ├── MarketDataProvider
│   ├── MarketDataProviderManager
│   ├── model
│   └── longport
│       ├── LongPortMarketDataProvider
│       ├── LongPortClientFactory
│       ├── LongPortSymbolMapper
│       └── LongPortExceptionMapper
├── manager
│   ├── StockDataManager
│   ├── DailyBarIngestManager
│   ├── QuoteSnapshotManager
│   ├── MarketDataSyncTaskManager
│   └── MarketDataAlertManager
├── service
│   ├── StockDataService
│   ├── MarketQuoteService
│   └── MarketDataSyncService
└── controller
    ├── StockDataController
    ├── MarketQuoteController
    └── MarketDataSyncController
```

分层规则：

- Controller 只处理 HTTP 语义。
- Service 管事务和流程编排。
- Manager 做校验、幂等、异常提醒、DAO 编排。
- Provider 只调用外部数据源，不直接写 DB。
- LongPort 字段转换、符号转换、异常映射放 adapter/convert。

配置建议：

```properties
qta.market-data.longport.enabled=false
qta.market-data.longport.app-key=${LONGPORT_APP_KEY:}
qta.market-data.longport.app-secret=${LONGPORT_APP_SECRET:}
qta.market-data.longport.access-token=${LONGPORT_ACCESS_TOKEN:}
qta.market-data.longport.timeout-seconds=${QTA_LONGPORT_TIMEOUT_SECONDS:10}
qta.market-data.longport.quote-time-zone=${QTA_LONGPORT_QUOTE_TIME_ZONE:Asia/Shanghai}
qta.market-data.longport.http-url=${LONGPORT_HTTP_URL:}
qta.market-data.longport.quote-websocket-url=${LONGPORT_QUOTE_WEBSOCKET_URL:}
```

`enabled` 即启用开关：默认 `false` 注入 `DisabledMarketDataProvider`（安全兜底，不连外部）；设为 `true` 且 SDK + 凭据就绪时注入 `LongPortMarketDataProvider`。`http-url` / `quote-websocket-url` 为可选域名覆盖：SDK 4.3.3 默认域名（`openapi.longport.cn` / `openapi-quote.longport.cn`）已废弃，部署必须配为 `https://openapi.longbridge.cn` / `wss://openapi-quote.longbridge.cn/v2`，详见 `../development/LONGPORT_SDK_RUNTIME_INSTALLATION.md` §4.2。

安全要求：

- 默认关闭 LongPort provider。
- 只初始化 quote client，不引入 trade client。
- 所有凭据日志脱敏。
- `.env.example` 只写变量名，不写真实值。
- CI 和单测使用 Fake provider，不依赖真实 LongPort 凭据。
- 官方 SDK 通过 `runtime-libs/` 外部 jar 进入运行时 classpath，不写入 `pom.xml`，保持编译期零依赖（正确坐标 `io.github.longportapp:openapi-sdk:4.3.3`，注意 groupId 含 `app`）。

幂等与限流：

- 实时快照唯一键建议 `(canonical_symbol, data_source, quote_time)`。
- 历史日 K 继续复用现有唯一键。
- 同步任务 `idempotency_key = hash(provider + taskType + symbols + dateRange + adjustType)`。
- 服务端限制并发不超过 5，单批标的不超过 500；遇到权限、额度、参数错误不盲目重试。

## 6. DB 草案

### stock_quote_snapshot

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `canonical_symbol` | 本系统统一代码，如 `SH.600519` |
| `quote_time` | 行情自身时间 |
| `current_price` | 最新价 |
| `open_price` / `high_price` / `low_price` | 当日开高低 |
| `pre_close_price` | 昨收价 |
| `volume` | 成交量 |
| `amount` | 成交额 |
| `trade_status` | provider 交易状态原始 code 或规范化 code |
| `data_source` | `LONGPORT` |
| `fetched_at` | 系统抓取时间 |
| `raw_hash` | 脱敏响应摘要 hash |
| `created_at` / `updated_at` | 系统时间 |

### market_data_sync_task

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `task_type` | `DAILY_BAR_SYNC` / `LATEST_QUOTE_REFRESH` |
| `provider` | `LONGPORT` |
| `scope_json` | 证券、日期、复权范围 |
| `status` | `PENDING` / `RUNNING` / `SUCCEEDED` / `PARTIAL_FAILED` / `FAILED` |
| `idempotency_key` | 防重复提交 |
| `total_count` / `success_count` / `fail_count` | 处理统计 |
| `inserted_count` / `updated_count` / `skipped_count` | 落库统计 |
| `started_at` / `finished_at` | 任务时间 |
| `last_error_code` | 最后错误码 |
| `error_summary_json` | 脱敏错误摘要 |

### market_data_alert

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `alert_type` | 规则类型 |
| `severity` | `INFO` / `WARN` / `HIGH` |
| `canonical_symbol` | 可为空，provider 级异常为空 |
| `quote_time` / `trade_date` | 触发所依赖的数据时间 |
| `provider` | `LONGPORT` / `CSV` / `SYSTEM` |
| `task_id` | 可关联同步任务 |
| `message` | 用户可读说明 |
| `trigger_value_json` | 触发值与阈值 |
| `resolved` | 是否已处理 |
| `created_at` / `updated_at` | 系统时间 |

## 7. API 草案

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/v1/market-data/providers/LONGPORT/status` | 查看 provider 配置状态、最近错误、最近成功时间 |
| POST | `/api/v1/market-data/providers/LONGPORT/health-check` | 触发只读健康检查 |
| POST | `/api/v1/market-data/quotes/latest` | 拉取最新行情，可选择落库 |
| GET | `/api/v1/market-data/quote-snapshots` | 查询外部价格快照 |
| POST | `/api/v1/market-data/sync-tasks/daily-bars` | 创建历史日 K 同步任务 |
| GET | `/api/v1/market-data/sync-tasks` | 查询任务列表 |
| GET | `/api/v1/market-data/sync-tasks/{id}` | 查询任务详情 |
| GET | `/api/v1/market-data/alerts` | 查询行情异常提醒 |
| PATCH | `/api/v1/market-data/alerts/{id}/resolve` | 标记提醒已处理 |

## 8. 前端设计

在现有 `/market-data` 页面增量扩展为 5 个 Tab：

1. 行情状态：LongPort 只读行情源状态、健康检查、最近成功/失败、限流/权限提示。
2. 证券主数据：保留现有 CRUD，显示 provider symbol 映射状态；前端不自己保存密钥。
3. 最新价快照：按自选股/持仓股/手选证券刷新最新价，展示 quote_time/fetched_at/source/staleness。
4. 历史数据同步：创建同步任务，查看 inserted/updated/skipped/failed 和错误摘要。
5. 异常提醒：查看并处理数据质量/量价观察提醒。

文案：

- `LongPort 仅作为只读行情源；本系统不会发起下单、撤单、资金或真实持仓查询。`
- `外部行情只写入行情快照，不会覆盖手工当前价。`
- `行情可能延迟、缺失、限流或权限不足，请以交易所/券商官方显示为准。`

## 9. 建设看板要求

前端 `buildStatusData.ts` 需要更新：

- 将 `market-data-foundation` 从“待开始”改成“P1.0 已完成、P1.1 进行中”。
- 新增/更新子节点：
  - `longport-config-health`
  - `provider-symbol-mapping`
  - `longport-quote-snapshot`
  - `longport-history-sync`
  - `market-data-alerts`
- `daily-bar-import` 明确 CSV 与 LongPort 共用 `stock_daily_bar`，通过 `dataSource` 隔离。
- 手工当前价节点明确外部行情不覆盖 `portfolio_price_snapshot`。

## 10. 验收标准

- [ ] 代码和测试中没有 LongPort 下单、撤单、账户、订单、真实持仓调用。
- [ ] 未配置 LongPort 凭据时，后端能启动，status 返回 `configured=false`。
- [ ] 最新行情可落 `stock_quote_snapshot`，重复 quote_time 不重复写。
- [ ] 历史日 K 可写入 `stock_daily_bar`，重复同步可解释 inserted/updated/skipped。
- [ ] 同步任务有状态、统计、错误摘要和脱敏错误码。
- [ ] 异常提醒有规则 code、severity、触发值、阈值、数据源和时间。
- [ ] 前端 `/market-data` 5 个 Tab 在 mock/remote 下都有 loading/empty/error/retry。
- [ ] 建设看板同步 LongPort 行情接入进度。
- [ ] 后端 `./mvnw test`、`./mvnw package` 通过。
- [ ] 前端 `npm run typecheck`、`lint`、`test`、`build` 通过。

## 11. 参考资料

- LongPort 行情概览：https://open.longportapp.com/zh-CN/docs/quote/overview
- LongPort 实时行情：https://open.longportapp.com/zh-CN/docs/quote/pull/quote
- LongPort 历史 K 线：https://open.longportapp.com/zh-CN/docs/quote/pull/history-candlestick
- LongPort SDK：https://open.longportapp.com/zh-CN/sdk
- LongPort MCP：https://open.longportapp.com/zh-CN/docs/mcp
