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

- `canonical_symbol` 使用 `SH.600519` / `SZ.000001` / `BJ.430047`。
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
  "canonicalSymbols": ["SH.600519", "SZ.000001"],
  "persist": true
}
```

请求约束：

- `canonicalSymbols` 必填且不能为空。
- 单次最多 500 个证券代码。
- 代码格式为 `SH.600519` / `SZ.000001` / `BJ.430047`，后端会统一转大写。
- `persist=true` 时写入 `stock_quote_snapshot`；`persist=false` 只返回本次请求结果。

响应项实际结构：

```json
{
  "id": 1,
  "canonicalSymbol": "SH.600519",
  "currentPrice": "1680.000000",
  "preClosePrice": "1670.000000",
  "openPrice": "1675.000000",
  "highPrice": "1690.000000",
  "lowPrice": "1668.000000",
  "volume": 25000,
  "amount": "42000000.000000",
  "quoteTime": "2026-07-10T14:55:00",
  "dataSource": "LONGPORT",
  "fetchedAt": "2026-07-10T14:55:03"
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
  "canonicalSymbol": "SH.600519",
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

## 3. 安全约束

- 所有 LongPort 相关接口必须只读。
- 前端不得传递 LongPort token/app secret。
- 后端不得在错误响应中返回密钥、完整原始响应或 OAuth 凭据。
- 未配置 provider 时返回业务状态，不影响应用启动。
- 真实外联前必须确认官方 SDK jar/native libs 已安装到后端运行时 classpath（`runtime-libs/`，vendor jar 被 gitignore）；不要提交密钥或 vendor 大体积 native 包到 Git。部署必须配置 `LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL` 域名覆盖（见 §2）。
