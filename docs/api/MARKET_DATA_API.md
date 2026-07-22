# Market Data API

> 统一前缀：`/api/v1/market-data`。统一响应：`ApiResponse<T>`。当前实现事实以代码为准；LongPort 只读行情接口、DB、任务留痕、反射式 SDK adapter 已实现；官方 Java SDK 已装入 `runtime-libs/`（vendor jar 被 gitignore，不入 Git）；真实单 symbol 外联已于 2026-07-12 验收通过（latest quote + daily bar 落 `dataSource=LONGPORT`）。

## 1. 当前已实现接口

### 证券主数据

| 方法 | 路径 | 状态 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/market-data/stocks?market=&keyword=&page=&size=` | 已实现 | 分页查询证券主数据 |
| POST | `/api/v1/market-data/stocks` | 已实现 | 新增证券主数据 |
| GET | `/api/v1/market-data/stocks/{canonicalSymbol}` | 已实现 | 查询单个证券 |
| PUT | `/api/v1/market-data/stocks/{id}` | 已实现 | 更新名称、上市日期、退市状态 |
| DELETE | `/api/v1/market-data/stocks/{canonicalSymbol}` | 已实现 | 无日 K 关联时删除证券 |

### 精确证券代码验证

| 方法 | 路径 | 状态 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/market-data/securities/verify` | 已实现 | 选择 CN/HK/US 并输入精确代码，读取 LongPort Static Info + Quote；只读、不落库 |

请求示例：

```json
{"market":"HK","code":"2498"}
```

返回 `canonicalSymbol/providerSymbol/displayName/exchange/currency/lotSize`，报价可用时还返回 `lastPrice/quoteTime/tradeStatus`。`verificationStatus` 为 `VERIFIED_QUOTE_AVAILABLE`、`VERIFIED_NO_QUOTE`、`INVALID_SYMBOL`、`PROVIDER_UNAVAILABLE` 或 `NO_PERMISSION` 等明确状态。Static Info 已成功但 Quote 不可用时，不能把证券误判为不存在。

当前精确转换：`CN + 603308 -> SH.603308`、`HK + 2498 -> HK.02498`、`US + NVDA -> US.NVDA`。该接口不做名称模糊搜索，也不创建采集计划；前端必须等用户确认后才把代码加入计划 scope。

### 日 K 数据

| 方法 | 路径 | 状态 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/market-data/daily-bars?canonicalSymbol=&fromDate=&toDate=&adjustType=&dataSource=&page=&size=` | 已实现 | 分页查询日 K |
| POST | `/api/v1/market-data/daily-bars/import` | 已实现 | CSV 幂等导入日 K |
| GET | `/api/v1/market-data/daily-bars/template` | 已实现 | 下载 CSV 模板 |

CSV 表头：

```csv
canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type
```

当前 CSV 导入规则：

- `canonical_symbol` 支持 A 股、港股和美股：`SH.600519` / `HK.02498` / `US.AAPL`。港股不足五位会补零，美股代码统一大写。
- `adjust_type` 支持 `NONE` / `QF` / `HF`。
- `data_source` 固定为 `CSV`。
- 幂等键：`canonical_symbol + trade_date + adjust_type + data_source`。
- 文件内相同幂等键且内容一致则跳过；内容冲突则整批拒绝。

## 2. LongPort 只读行情接口（真实外联已验收）

### 实现状态

- 默认 `qta.market-data.longport.enabled=false`，使用 `DisabledMarketDataProvider`，不请求外部。
- 设置 `qta.market-data.longport.enabled=true` 后，Spring 注入 `LongPortMarketDataProvider`。
- `LongPortMarketDataProvider` 通过 `ReflectiveLongPortQuoteClient` 运行时反射调用官方 Java SDK：
  - `Config.fromApikey(...)` 或 `Config.fromApikeyEnv()`
  - 可选 `Config.httpUrl(...)` / `Config.quoteWebsocketUrl(...)` 覆盖默认域名
  - `QuoteContext.create(config)`
  - `QuoteContext#getQuote(String[])`
  - `QuoteContext#getHistoryCandlesticksByDate(...)`
- 官方 Java SDK 坐标 `io.github.longportapp:openapi-sdk:4.3.3`（注意 groupId 含 `app`）；`openapi-sdk-4.3.3.jar` 内置全平台 native，已装入 `runtime-libs/`（vendor jar 被 gitignore，不入 Git），容器内只读挂载到 `/app/libs`。安装与验收步骤见 `../development/LONGPORT_SDK_RUNTIME_INSTALLATION.md`。
- **部署必须配置域名覆盖**（SDK 默认域名 `openapi.longport.cn` / `openapi-quote.longport.cn` 已废弃）：
  - `LONGPORT_HTTP_URL=https://openapi.longbridge.cn`
  - `LONGPORT_QUOTE_WEBSOCKET_URL=wss://openapi-quote.longbridge.cn/v2`
- 未安装 SDK 时，应用仍能启动，status 返回 `configured=false` + `LongPort Java SDK 未安装或未进入运行时 classpath`。

### Provider 状态

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/providers/LONGPORT/status` | 查看 LongPort 是否启用、是否配置、最近成功/失败、脱敏错误 |
| POST | `/api/v1/market-data/providers/LONGPORT/health-check` | 触发只读健康检查 |

当前默认 provider 是 `DisabledMarketDataProvider`。在 LongPort 未启用、SDK 未安装或未配置凭据时：

- status 返回 `200` + `configured=false`。
- `/quotes/latest` 返回 `400` + `BUSINESS_RULE_VIOLATION`。
- `/sync-tasks/daily-bars` 返回 `400` + `BUSINESS_RULE_VIOLATION`，并在任务/提醒表留痕。
- 上述 400 是业务拦截，不应显示成系统崩溃。

当前状态响应实际结构：

```json
{
  "providerCode": "LONGPORT",
  "configured": true,
  "reachable": true,
  "lastError": null,
  "lastSuccessAt": "2026-07-10T10:00:00"
}
```

### 最新价快照

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/market-data/quotes/latest` | 按证券列表拉取最新行情，可选择落库 |
| GET | `/api/v1/market-data/quote-snapshots?canonicalSymbol=&dataSource=&page=&size=` | 查询外部价格快照 |

请求示例：

```json
{
  "canonicalSymbols": ["SH.600519", "HK.02498", "US.AAPL"],
  "persist": true
}
```

请求约束：

- `canonicalSymbols` 必填且不能为空。
- 单次最多 500 个证券代码。
- 代码格式为 `SH.600519` / `SZ.000001` / `BJ.430047` / `HK.02498` / `US.AAPL`；后端统一转大写，并将 `HK.2498` 规范化为 `HK.02498`。
- LongPort provider 映射示例：`HK.02498 -> 2498.HK`、`US.AAPL -> AAPL.US`、`US.BRK.B -> BRK.B.US`。
- `persist=true` 时写入 `stock_quote_snapshot`；`persist=false` 只返回本次请求结果。

响应项实际结构：

```json
{
  "id": 1,
  "canonicalSymbol": "HK.02498",
  "currentPrice": "22.500000",
  "preClosePrice": "22.000000",
  "openPrice": "22.100000",
  "highPrice": "22.800000",
  "lowPrice": "21.900000",
  "volume": 1000,
  "amount": "22500.000000",
  "quoteTime": "2026-07-16T15:55:00",
  "dataSource": "LONGPORT",
  "fetchedAt": "2026-07-16T15:55:03"
}
```

### 历史日 K 同步任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/market-data/sync-tasks/daily-bars` | 创建历史日 K 同步任务 |
| GET | `/api/v1/market-data/sync-tasks?provider=&status=&page=&size=` | 查询同步任务列表 |
| GET | `/api/v1/market-data/sync-tasks/{id}` | 查询同步任务详情 |

请求示例（当前结构化 DTO）：

```json
{
  "taskType": "DAILY_BAR_SYNC",
  "provider": "LONGPORT",
  "canonicalSymbol": "US.AAPL",
  "startDate": "2026-06-01",
  "endDate": "2026-07-01",
  "adjustType": "NONE"
}
```

复权类型：

- `NONE`：LongPort `AdjustType.NoAdjust`。
- `QF`：LongPort `AdjustType.ForwardAdjust`。
- `HF`：当前官方 Java SDK 未提供后复权枚举，后端返回 `BUSINESS_RULE_VIOLATION`。

**重试语义**：
- PENDING/RUNNING/SUCCEEDED：同 scope 幂等返回已有任务。
- FAILED/PARTIAL_FAILED：创建新 retry 任务，`parentTaskId` 指向该 scope 最新任务；旧任务保留可追溯。
- 连续多次重试不会唯一键冲突，每次 retry 的 idempotencyKey 含时间戳保证唯一。

任务状态：

```text
PENDING -> RUNNING -> SUCCEEDED / PARTIAL_FAILED / FAILED /
```

### 行情异常提醒

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/alerts?severity=&resolved=&canonicalSymbol=&page=&size=` | 查询行情异常提醒 |
| PATCH | `/api/v1/market-data/alerts/{id}/resolve` | 标记提醒已处理 |

## 3. 行情工作台、采集计划、分钟 K、水位（P1.2）

### 行情工作台概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/workbench/overview` | 工作台概览（provider 状态、提醒计数、交易时段、数据计数，接 DAO 真实查询） |

### 采集计划

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/market-data/sync-plans` | 创建采集计划 |
| GET | `/api/v1/market-data/sync-plans?taskType=&provider=&enabled=&page=&size=` | 分页查询采集计划 |
| GET | `/api/v1/market-data/sync-plans/{id}` | 查询单个采集计划 |
| PUT | `/api/v1/market-data/sync-plans/{id}` | 更新采集计划 |
| POST | `/api/v1/market-data/sync-plans/{id}/toggle?enabled=true` | 启停采集计划 |
| POST | `/api/v1/market-data/sync-plans/{id}/run` | 手动执行采集计划 |

手动执行说明：
- 支持 `DAILY_BAR_BACKFILL` 和 `MINUTE_BAR_BACKFILL`；`INTRADAY_MINUTE_REFRESH` 只由 scheduler 触发，不伪装成手工执行。
- `MINUTE_BAR_BACKFILL` 必须使用 `triggerType=MANUAL`，并配置 symbols、startDate、endDate、intervalType。
- `INTRADAY_MINUTE_REFRESH` 必须使用 `triggerType=INTRADAY` 且配置 collectFrequency；当前只开放 A 股，港美股会在创建/更新时明确拒绝。
- scope 用 Jackson 解析，支持 `canonicalSymbol` / `symbols` / `startDate` / `endDate`。
- 执行生成 `sync_task` + 逐 symbol `task_item`，短事务幂等写入 `stock_minute_bar`、更新 watermark 和 plan 的 `lastRunAt` / `lastTaskId`。provider 网络调用不在 DB 长事务中。
- 同一计划使用 V13 DB run claim 防重入；服务重启时将遗留 task/item 收敛为 `FAILED` 并释放 claim。
- provider 凭据失效、无权限、限流、超时、空数据和未知异常分别记录错误码，不留永久 `RUNNING`。凭据失效使用 `MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED`，真实 403/301604 权限不足使用 `MARKET_DATA_PROVIDER_PERMISSION_DENIED`。

创建采集计划请求示例：

```json
{
  "planName": "茅台日K补档",
  "taskType": "DAILY_BAR_BACKFILL",
  "provider": "LONGPORT",
  "scopeJson": "{\"canonicalSymbol\":\"SH.600519\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-07-10\"}",
  "adjustType": "NONE",
  "triggerType": "MANUAL"
}
```

历史分钟补档请求示例：

```json
{
  "planName": "茅台 5M 单日补档",
  "taskType": "MINUTE_BAR_BACKFILL",
  "provider": "LONGPORT",
  "scopeJson": "{\"symbols\":[\"SH.600519\"],\"startDate\":\"2026-07-10\",\"endDate\":\"2026-07-10\"}",
  "intervalType": "5M",
  "adjustType": "NONE",
  "triggerType": "MANUAL",
  "includeAuction": false
}
```

计划响应包含 `configurationStatus` / `validationErrors` / `manuallyRunnable` / `automaticallyRunnable`，用于将历史非法计划标记为“需要修正”。

### 盘中调度语义

- Spring Scheduler 默认每 30 秒扫描已启用、`triggerType=INTRADAY` 且通过统一校验的计划。
- A 股连续竞价窗口为 09:30-11:30、13:00-15:00；是否允许集合竞价由 `includeAuction` 决定。首根 bar 闭合前不请求。
- 非交易日、午休、收盘后、未到 collectFrequency 或上次任务未完成时直接跳过，不创建空任务。

### 任务明细

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/sync-tasks/{taskId}/items?status=&page=&size=` | 查询任务执行明细 |
| POST | `/api/v1/market-data/sync-tasks/{taskId}/reconcile` | 幂等收敛非终态任务，返回更新后的父任务 |

任务明细响应包含 `subTaskId`、`status`、`rowCount`、`insertedCount`、`updatedCount`、`skippedCount`、`errorCode/errorMessage`、`startedAt/finishedAt`。父任务为 `RUNNING` 时，查询明细会尝试懒收敛；失败时记录警告并降级返回旧明细，不伪装为收敛成功。

主动收敛规则：

- 通过独立事务 Service 执行，避免 Spring 同 Bean 自调用导致事务失效。
- 子任务终态映射到 item；缺失 `subTaskId` 或子任务不存在时，item 标记 `FAILED` 并记录原因。
- 父任务六类 count 直接汇总子任务真实字段；存在非终态 item 时父任务保持 `RUNNING` 且不写 `finishedAt`。
- 接口幂等；已终态父任务直接返回当前状态。

### 分钟 K

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/minute-bars?canonicalSymbol=&intervalType=&adjustType=&dataSource=&fromTime=&toTime=&tradeDate=&page=&size=` | 分页查询分钟 K |
| POST | `/api/v1/market-data/minute-bars` | 写入分钟 K（带质量校验 + 交易日/时段校验 + 幂等 + 水位） |

分钟 K 写入质量校验：
- OHLC 非法或 volume/amount 负 → `REJECTED`（不写库 + alert）
- 非交易日 → `REJECTED`（不写库 + alert）
- bar 时间不在交易窗口 → `SUSPECT`（写库但标记 + alert）
- 幂等键冲突且内容不同 → `CONFLICT`（不覆盖 + alert）
- 幂等键冲突且内容相同 → `SKIPPED`
- 计划执行链路还会过滤未闭合 bar 和计划交易时段外 bar；后者计入 task `skippedCount` 但不落库。

LongPort SDK 4.3.3 分钟粒度使用原生 `Min_1/Min_5/Min_15/Min_30/Min_60`，不伪造聚合。历史日期区间按官方单次最多 1000 条限制分段，并在 client 边界遵守 30 秒 60 次限流。

### 交易时段 / 日历

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/trading-sessions` | A 股交易时段（启动时 @PostConstruct 幂等初始化，GET 只读不写） |
| GET | `/api/v1/market-data/trading-sessions/is-trading-day?marketCode=&date=` | 判断是否交易日 |

### 水位

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/watermarks?canonicalSymbol=&dataSource=&intervalType=&page=&size=` | 分页查询数据水位 |

### 板块 / 自定义分组（P1.3）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/market-data/segments` | 创建板块 |
| GET | `/api/v1/market-data/segments?segmentType=&enabled=&keyword=&page=&size=` | 分页查询板块 |
| GET | `/api/v1/market-data/segments/{id}` | 查询单个板块 |
| PUT | `/api/v1/market-data/segments/{id}` | 更新板块 |
| DELETE | `/api/v1/market-data/segments/{id}` | 删除板块 |
| GET | `/api/v1/market-data/segments/{id}/members` | 查询板块成员 |
| POST | `/api/v1/market-data/segments/{id}/members` | 添加板块成员 |
| DELETE | `/api/v1/market-data/segments/{id}/members/{canonicalSymbol}` | 移除板块成员 |

### 市场板块目录与关注快照（P1.5）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/sector-catalog/industry-rankings?market=CN&indicator=leading-gainer&sortType=single&limit=20` | 查询 A/H/US 行业排行 |
| GET | `/api/v1/market-data/sector-catalog/industry-peers?market=CN&providerSectorId=BK/SH/IN40159` | 查询行业层级摘要 |
| POST | `/api/v1/market-data/sector-catalog/watches` | 关注行业并立即保存首份聚合/成分快照 |
| GET | `/api/v1/market-data/sector-catalog/watches?market=CN` | 查询行业关注及最新快照 |
| GET | `/api/v1/market-data/sector-catalog/watches/{id}` | 查询单个行业关注 |
| POST | `/api/v1/market-data/sector-catalog/watches/{id}/refresh` | 手动采集新快照 |
| POST | `/api/v1/market-data/sector-catalog/watches/{id}/toggle?enabled=false` | 启停关注 |
| DELETE | `/api/v1/market-data/sector-catalog/watches/{id}` | 删除关注及其历史快照 |
| GET | `/api/v1/market-data/sector-catalog/watches/{id}/snapshots?page=1&size=30` | 查询聚合历史 |
| GET | `/api/v1/market-data/sector-catalog/snapshots/{snapshotId}/members` | 查询某次成分快照 |
| PUT | `/api/v1/market-data/sector-catalog/watches/{id}/collection` | 配置关注板块自动采集和频率 |
| GET | `/api/v1/market-data/sector-catalog/ranking-configs` | 查询 CN/HK/US 全市场榜单采集配置和运行状态 |
| PUT | `/api/v1/market-data/sector-catalog/ranking-configs/{market}` | 更新某市场自动采集配置 |
| POST | `/api/v1/market-data/sector-catalog/ranking-configs/{market}/run` | 立即采集一次全市场板块榜单 |
| GET | `/api/v1/market-data/sector-catalog/ranking-history?market=&tradeDate=&snapshotType=&page=&size=` | 分页查询历史榜单批次 |
| GET | `/api/v1/market-data/sector-catalog/ranking-history/{batchId}/items` | 查询批次内完整板块排名 |

`market` 仅支持 `CN/HK/US`；排行指标支持 `leading-gainer`、`today-trend`、`popularity`、`market-cap`、`revenue`、`revenue-growth`、`net-profit`、`net-profit-growth`。返回字段包含 provider 板块 ID、涨跌幅、领涨标的和指标值。

关注请求示例：

```json
{
  "market": "CN",
  "providerSectorId": "BK/SH/IN40159",
  "trackingSymbol": "SH.512480"
}
```

`trackingSymbol` 可不填。行业成分快照中的 `netInflow`、`turnoverAmount`、`volume` 分别聚合到行业快照；`delayed` 原样保存 provider 延迟标记。

全市场排行配置示例。`intradayIntervalMinutes` 只允许 `0/5/10/15/30/60`，其中 `0` 表示不做盘中采集；`closeSnapshotEnabled` 独立控制收盘快照：

```json
{
  "enabled": true,
  "intradayIntervalMinutes": 15,
  "closeSnapshotEnabled": true,
  "rankLimit": 100
}
```

关注板块配置示例，频率只允许 `5/10/15/30/60`：

```json
{
  "autoCollectEnabled": true,
  "collectIntervalMinutes": 15
}
```

自动采集按 CN/HK/US 各自市场时区和有效交易窗口运行。CN 包含 09:15-09:25 开盘集合竞价，并在 09:25 保存最后一个竞价采样；09:26-09:29、午休和收盘后不生成周期性 `INTRADAY`。HK/US 默认只覆盖常规时段。收盘快照每日最多一份，等待时间为 CN 5 分钟、HK 15 分钟、US 10 分钟。每个时间桶只写一份；并发实例通过 DB claim 互斥。`executionState/collectionState` 可能为 `IDLE/ACTIVE/BACKOFF/BLOCKED_AUTH/BLOCKED_PERMISSION/BLOCKED_CONFIG`。鉴权和权限错误进入阻断态，修改配置后复位；限流、超时及临时异常按 1/2/5/10/30 分钟退避。历史批次区分 `INTRADAY/CLOSE/MANUAL`。

接口只读；行业关注和快照按 P1.5 设计落库。provider 未配置时返回 `MARKET_SECTOR_PROVIDER_UNAVAILABLE`；Access Token 无效或过期返回 `MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED`；账号缺少行业行情权限返回 `MARKET_DATA_PROVIDER_PERMISSION_DENIED`，不伪造空数据。ETF/指数继续作为普通证券使用报价与采集计划；A 股 `5xxxxx` ETF 已支持精确代码验证。

## 4. 安全约束

- 所有 LongPort 相关接口必须只读。
- 前端不得传递 LongPort token/app secret。
- 后端不得在错误响应中返回密钥、完整原始响应或 OAuth 凭据。
- 未配置 provider 时返回业务状态，不影响应用启动。
- 真实外联前必须确认官方 SDK jar/native libs 已安装到后端运行时 classpath（`runtime-libs/`，vendor jar 被 gitignore）；不要提交密钥或 vendor 大体积 native 包到 Git。部署必须配置 `LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL` 域名覆盖（见 §2）。
