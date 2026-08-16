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

### 本地证券目录与确定性搜索（P1.4b-D1）

| 方法 | 路径 | 状态 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/market-data/security-directory/import` | 已实现 | multipart CSV 原子、幂等导入证券及别名 |
| GET | `/api/v1/market-data/securities/search?q=&markets=&types=&includeDelisted=&limit=` | 已实现 | 仅查询本地目录，按冻结规则确定性排序 |
| GET | `/api/v1/market-data/securities/{canonicalSymbol}` | 已实现 | 查询本地证券详情及别名 |

### 证券目录同步基础（P1.4b-D3）

| 方法 | 路径 | 状态 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/market-data/security-directory/sync` | 已实现 | 手动触发证券目录同步，返回同步任务 VO |
| GET | `/api/v1/market-data/security-directory/sync/tasks/{taskId}` | 已实现 | 查询目录同步任务详情，不存在返回 404 |
| GET | `/api/v1/market-data/security-directory/status` | 已实现 | 查询目录同步状态与 catalog 状态，不泄露路径/凭据 |

- `POST /sync` 请求体可选 `{mode}`，默认 `FULL`，合法值为 `FULL`/`INCREMENTAL`。provider 未启用时返回 HTTP 400 + `BUSINESS_RULE_VIOLATION`，且不创建任务、不回显凭据/路径。重复触发同一快照（按内容 hash 内容身份）返回既有 PENDING/RUNNING/SUCCEEDED 任务，不重复执行；失败后允许 retry 并建立 `parent_task_id` 链。
- 同步任务复用 `market_data_sync_task`：`task_type=SECURITY_MASTER_SYNC`，`provider=CSV_SNAPSHOT_DIR`，`scope_json={provider, snapshotId, snapshotHash, mode}`。
- 同步五阶段：解析 → 校验 → staging/diff → 质量门禁（非空快照 `MARKET_DATA_EMPTY_RESULT`、必填字段/唯一性 `DAILY_BAR_VALIDATION_ERROR`、数量波动 `BUSINESS_RULE_VIOLATION` 默认阈值 0.30）→ 原子发布（单事务 upsert，任一阶段失败整批回滚保留上一成功目录，失败不修改任何 `list_status`）。质量门禁默认值经 `qta.market-data.security-directory.*` 配置。
- `GET /status` 返回 `SecurityDirectoryStatusVO{providerCode, providerEnabled, providerConfigured, lastSuccessAt, lastSnapshotId, lastMode, lastErrorCode, catalogStatus, catalogUpdatedAt, stale, degraded}`；`catalogStatus/catalogUpdatedAt/stale/degraded` 沿用 D1 启发式（`MAX(source_updated_at)` + 48h），`lastSuccessAt/lastSnapshotId` 来自 `security_directory_sync_state`。
- 默认安全关闭：`qta.market-data.security-directory.enabled=false`、`scheduler.enabled=false`。provider 未启用 / CSV 路径缺失 / 内容非法时应用仍可启动，D1 搜索/详情/导入和 `/stocks` CRUD 不受影响。每日增量（默认 cron `2 30 6 * * *` Asia/Shanghai）与每周全量对账（默认 `0 30 4 * * MON`）调度仅在显式启用时装配。

搜索参数：

- `q` 必填；支持 canonical symbol、裸代码、正式名称、曾用名/其他别名、拼音全拼及首字母。
- `markets`、`types` 可选，逗号分隔；市场为 `SH/SZ/BJ/HK/US`，证券类型为 `STOCK/ETF/INDEX/REIT/FUND/BOND/WARRANT/OPTION/FUTURE/OTHER`。
- `includeDelisted` 默认 `false`；退市证券仅在显式传 `true` 时参与检索。
- `limit` 默认 `20`，最大 `100`。

排序分档固定为：canonical symbol 精确命中 → 裸代码精确命中 → 正式名称精确命中 → 正式名称前缀 → 别名精确/前缀 → 拼音全拼/首字母前缀 → 名称/别名包含；同分再按正常上市优先、请求中的市场顺序、规范化显示名、`canonicalSymbol` 排序，不依赖数据库偶然顺序。`matchedBy` 为 `CANONICAL_SYMBOL_EXACT/RAW_SYMBOL_EXACT/FORMAL_NAME_EXACT/FORMAL_NAME_PREFIX/ALIAS_EXACT/ALIAS_PREFIX/PINYIN_FULL_PREFIX/PINYIN_ABBR_PREFIX/NAME_CONTAINS/ALIAS_CONTAINS`。

搜索响应示例：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "items": [
      {
        "canonicalSymbol": "SH.603308",
        "symbol": "603308",
        "displayName": "应流股份",
        "name": "应流股份",
        "nameCn": "应流股份",
        "nameHk": null,
        "nameEn": null,
        "shortName": "应流股份",
        "market": "SH",
        "exchange": "SSE",
        "currency": "CNY",
        "securityType": "STOCK",
        "listStatus": "LISTED",
        "matchedBy": "PINYIN_ABBR_PREFIX"
      }
    ],
    "catalogStatus": "READY",
    "catalogUpdatedAt": "2026-07-29T10:00:00",
    "stale": false,
    "degraded": false
  },
  "timestamp": "2026-07-29T10:00:01"
}
```

详情响应除搜索项字段外，还包含 `pinyinFull/pinyinAbbr/listDate/delisted/dataSource/sourceUpdatedAt/sourceHash/aliases`。未找到时返回 HTTP 404。

CSV 必填表头：

```csv
canonical_symbol,name,market,exchange,currency,security_type,list_status,data_source,source_updated_at
```

可选表头为 `name_cn,name_hk,name_en,short_name,pinyin_full,pinyin_abbr,list_date,source_hash,aliases`。`aliases` 用 `|` 分隔，每项格式为 `ALIAS_TYPE:LANGUAGE:VALUE`；允许的 alias type 为 `FORMER_NAME/OLD_TICKER/SHORT_NAME/ENGLISH/TRADITIONAL/USER`。导入支持 UTF-8 BOM 和 RFC 4180 引号，限制 50 MiB、200000 行；任一非法行会以可解释的 `line/field/reasonCode/message` 拒绝整批，避免部分落库。

导入响应示例：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "totalRows": 3,
    "inserted": 3,
    "updated": 0,
    "unchanged": 0,
    "aliasesInserted": 2,
    "aliasesUnchanged": 0,
    "formerNamesAdded": 0,
    "failed": 0,
    "errors": []
  },
  "timestamp": "2026-07-29T10:00:01"
}
```

同一 CSV 重复导入不会重复证券或别名；正式名称发生变化时，旧正式名称会写入 `FORMER_NAME` alias。导入与搜索都只访问本地 `stock_basic/stock_alias`，不会调用报价、K 线、LongPort，也不会创建采集任务。目录为空时返回 `catalogStatus=EMPTY`；非空目录以最大 `sourceUpdatedAt` 表示更新时间，超过 48 小时才标记 `stale=true`。D3 可替换这一临时新鲜度口径。

### 证券元数据按需补全（P1.4b-D3-03）

| 方法 | 路径 | 状态 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/market-data/security-directory/enrich` | 已实现 | 对本地目录已存在的精确 canonical symbol，按需调用 LongPort Static Info 补全元数据；可选部分持久化 |

请求体示例：

```json
{
  "canonicalSymbol": "SH.600519",
  "persist": false
}
```

- `canonicalSymbol` 必填，必须是精确 canonical symbol（`SH.600519` / `HK.02498` / `US.AAPL`），经 `CanonicalSymbolUtils.normalize` 规范化，长度上限 32（`@Size`，超限返回 `VALIDATION_ERROR`）；不做名称模糊搜索/联想。
- `persist` 可选，默认 `false`。`false` 时只查询/展示，不落库；`true` 时只补全 `stock_basic` 中为空的元数据。

响应示例（`persist=false`）：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "canonicalSymbol": "SH.600519",
    "enriched": true,
    "providerCode": "LONGPORT",
    "fields": {
      "nameCn": "贵州茅台",
      "nameHk": "貴州茅台",
      "nameEn": "Kweichow Moutai",
      "exchange": "SSE",
      "currency": "CNY"
    },
    "lotSize": 100,
    "persisted": false,
    "reason": "OK"
  },
  "timestamp": "2026-08-02T15:00:00"
}
```

字段说明：

- `enriched`：是否成功从 provider 拿到静态信息（`false`=provider 未找到该证券，非异常）。
- `providerCode`：provider 标识（`LONGPORT`，或 disabled 兜底的 `DISABLED`）。
- `fields`：可补全字段集合 `{nameCn, nameHk, nameEn, exchange, currency}`，均可空。
- `lotSize`：**顶层字段**（不在 `fields` 内），每手股数。**只返回，不持久化**：`stock_basic` 无 `lot_size` 列，不扩表、不新增 migration。
- `persisted`：是否实际写入了 `stock_basic` 行。
- `reason`：结果原因枚举：`OK`（provider 返回数据；persist=false 展示，或 persist=true 实际写入）/ `NO_CHANGE`（`persist=true` 但没有任何字段被写入）/ `PROVIDER_NOT_FOUND`（provider 返回 null，未找到该证券的静态信息，不落库）。

持久化规则（`persist=true`）：

- 由 Mapper 的**原子条件更新**保证：只在数据库当前字段仍为 null/空字符串/纯空白时写入，**本地已有非空字段无论 LongPort 返回值是否不同都保留本地值**（数据库层保证，不依赖调用前读取结果）。
- 可补字段仅限 `name_cn / name_hk / name_en / exchange / currency`。
- **不修改 `source_updated_at` / `data_source` / `source_hash`**：`source_updated_at` 是证券目录新鲜度依据，不能被 LongPort 补全更新时间污染；本轮不新增 Flyway migration、不新增来源追踪表。
- `lotSize` 不持久化。
- 外部 LongPort 调用在数据库事务外执行；只有最终的单条条件 UPDATE 保持原子性。
- 更新为 0 时重新查询：行不存在 → 404 `STOCK_NOT_FOUND`；行存在但无可补字段 → `persisted=false / reason=NO_CHANGE`。
- LongPort 返回的字符串统一 trim，null/空字符串/纯空白视为 null，禁止写入数据库。
- provider 返回证券与请求 `canonicalSymbol` 不一致时拒绝返回与持久化（400 `SECURITY_VERIFICATION_FAILED`），不把其他证券的静态信息写入当前证券。

错误码：

| HTTP | `code` | 触发条件 |
| --- | --- | --- |
| 400 | `BUSINESS_RULE_VIOLATION` | LongPort provider 未启用（disabled enricher 直接抛异常，经 `GlobalExceptionHandler`；不创建任务、不回显凭据） |
| 400 | `INVALID_CANONICAL_SYMBOL` | `canonicalSymbol` 格式不合法（normalize 失败） |
| 400 | `VALIDATION_ERROR` | 请求体未通过 Bean Validation（如 `canonicalSymbol` 为空、长度超过 32） |
| 400 | `SECURITY_VERIFICATION_FAILED` | provider 返回证券与请求不一致 |
| 400 | `MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED` | LongPort Static Info 鉴权失败（provider 透传具体码）；同类还有 `MARKET_DATA_PROVIDER_TIMEOUT`/`PERMISSION_DENIED`/`RATE_LIMITED` 等 `MARKET_DATA_PROVIDER_*` 码 |
| 404 | `STOCK_NOT_FOUND` | 本地目录无该 canonical symbol，或补全期间该行被删除（service 抛 `SecurityDirectoryNotFoundException`，复用 controller 既有 404 handler） |

所有错误响应均不含凭据/密钥/完整 token 字面量。默认 `qta.market-data.longport.enabled=false` 时装配 `DisabledSecurityMetadataEnricher`，应用正常启动、D1 搜索/导入/详情不受影响。本端点不接报价、K 线、交易、账户、订单或调度器。

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

> 当前 DELETE watch 会级联历史快照，这是 P1.7-A 明确阻断项。P1.7-A 必须先回填稳定 `sectorId`、移除级联删除并把 DELETE 改为归档关注关系；迁移验收前不得启用 P1.7 衍生计算。

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

## 5. 板块分析接口设计（P1.7，规划，未实现）

> 状态：**规划/未实现，v1.1 专家复审修订**。P1.7-A 数据就绪门禁先于 P1.7-B 分析 API。MVP 包含每日总览、相对强弱、轮动、资金趋势、交易集中度、量价和提醒；收益贡献后置 P1.7-C。

统一前缀（规划）：`/api/v1/market-data/sector-analytics/*`，统一响应 `ApiResponse<T>`。列表统一返回 `PageData{page,size,total,sortBy,sortDirection,anchorType,anchorId,items}`：单公式使用 `CALCULATION_RUN`，薄切片使用 `RANKING_BATCH`，高级总览使用 `PUBLICATION_BATCH`。首次请求解析并返回锚点，`page>1` 必须回传对应 `calculationRunId/rankingBatchId/publicationBatchId`，否则 400；防止并发发布导致跨页漂移。衍生接口未显式选 run 时，`formulaVersion/parameterHash` 必须同时传或同时省略；省略时固定采用端点默认 `v1` 并由请求窗口 + 冻结默认阈值计算 parameter hash，不采用“最新版本”。单公式响应携带完整血缘和质量；依赖排行时追加 scope/coverage。跨公式总览返回 `publicationBatchId`，每个模块分别返回自己的 `calculationRunId`。

排序白名单：daily overview 为 `changeRate/rsRankPercentile/rankPercentileChange/flowIntensity/alertCount/sectorId`（THIN 仅允许 `changeRate/sectorId`）；relative strength 为 `relativeReturnN/rsRankPercentile/sectorId`；rotation market 为 `tradeDate/rankSpearmanMean`；rotation sector 为 `meanRankPercentile/rankPercentileChange/sectorId`；flow 为 `flowIntensity/flowIntensityChange/sectorId`；concentration 为 `topKTurnoverShare/absoluteFlowConcentration/sectorId`；volume 为 `turnoverRatio/changeRate/sectorId`。非法字段 400；除时序端点追加 `tradeDate ASC` 外，其余都追加 `sectorId ASC`。

质量状态统一为 `OK/DEGRADED/NO_DERIVED_DATA/INSUFFICIENT_RAW/INSUFFICIENT_SAMPLE/STALE/ORIGIN_CHANGED/BLOCKED_AUTH/BLOCKED_PERMISSION/BACKOFF`。上游阻断作为数据状态返回，不被伪装为“暂无数据”。

### 5.0 每日板块总览与数据就绪（规划，未实现）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/sector-analytics/readiness?market=&asOfDate=` | 单位、完整性、CLOSE、日历、来源时间及计算 run 水位 |
| GET | `/api/v1/market-data/sector-analytics/daily-overview?market=&asOfDate=&viewMode=THIN|ADVANCED&page=&size=&sortBy=&sortDirection=&rankingBatchId=&publicationBatchId=` | 确定性薄切片或同一发布批次的高级聚合 |

`daily-overview` 固定 CLOSE 口径，`viewMode` 默认 `THIN`。THIN 首屏解析 CLOSE `rankingBatchId` 并返回，page>1 必须回传且与 market/asOfDate 匹配；ADVANCED 同理使用 `publicationBatchId`。锚点与模式或范围冲突均 400。未给高级 batch 时按 `published_at DESC,id DESC` 选择该 market/date 最新已发布批次；无批次返回 `NO_DERIVED_DATA`。显式日期无 CLOSE 不回退，两种模式都使用同一 `PageData` 外壳。

总览摘要固定返回 Top/Bottom 各 5 个板块（`leaders`/`laggards`）；完整列表通过同一锚点下的 `pageData` 分页读取。

readiness 响应固定为：`market/requestedAsOfDate/resolvedAsOfDate/ready/overallStatus/gates[]/latestCloseBatchId/latestPublicationBatchId/lastSuccessfulAt`。每个 gate 返回 `code/status(required: PASS|BLOCKED|DEGRADED)/required/evidence/qualityReasonCodes`；必需 gate（UNIT、IDENTITY、WATCH_SNAPSHOT_RETENTION、CLOSE、CALENDAR、CURRENCY、MANIFEST）任一 BLOCKED 则 `ready=false`，不得启动衍生计算。

```json
{
  "asOfDate": "2026-07-31",
  "snapshotType": "CLOSE",
  "rankingScope": "RANKED_UNIVERSE",
  "rankingBatchId": 88,
  "leaders": [{"sectorId": 1001, "sectorName": "半导体", "changeRate": "0.0240"}],
  "laggards": [{"sectorId": 1009, "sectorName": "煤炭", "changeRate": "-0.0180"}],
  "flowScope": "WATCHED_SECTORS",
  "watchedSectorCount": 6,
  "validFlowSectorCount": 5,
  "watchedFlows": [{"sectorId": 1001, "netInflow": "1200000", "currencyCode": "CNY", "sourceQuoteTime": "2026-07-31T15:00:00+08:00"}],
  "pageData": {"page":1,"size":20,"total":1,"sortBy":"changeRate","sortDirection":"DESC","anchorType":"RANKING_BATCH","anchorId":88,"items":[{"sectorId":1001,"changeRate":"0.0240"}]},
  "publicationBatchId": null,
  "modules": {
    "relativeStrength": {"status": "NO_DERIVED_DATA", "calculationRunId": null, "value": null},
    "rotationPersistence": {"status": "NO_DERIVED_DATA", "calculationRunId": null, "value": null},
    "capitalFlowTrend": {"status": "NO_DERIVED_DATA", "calculationRunId": null, "value": null},
    "volumeConfirmation": {"status": "NO_DERIVED_DATA", "calculationRunId": null, "value": null},
    "alerts": {"status": "NO_DERIVED_DATA", "count": 0, "highestSeverity": null}
  },
  "qualityStatus": "DEGRADED",
  "qualityReasonCodes": ["RANKED_UNIVERSE_NOT_FULL_MARKET", "DERIVED_MODULES_NOT_PUBLISHED"]
}
```

高级总览只能选择一个已发布 `publicationBatchId`。每个模块同时返回 `scope/eligibility/status/calculationRunId/formulaVersion/parameterHash/qualityReasonCodes`：排行模块 scope 为 `RANKED_UNIVERSE`，资金/集中度/量价为 `WATCHED_SECTORS`。排行中的未关注板块在 watched 模块固定返回 `eligibility=NOT_WATCHED,value=null,qualityReasonCodes=['NOT_IN_WATCHED_SCOPE']`，不得伪装成计算失败或沿用旧值。

高级总览单行示例（所有模块必须属于 `publicationBatchId=9001`）：

```json
{
  "publicationBatchId": 9001,
  "sectorId": 1001,
  "rankScope": "RANKED_UNIVERSE",
  "changeRate": "0.0240",
  "sourceQuoteTime": "2026-07-31T15:00:00+08:00",
  "relativeStrength": {"scope":"RANKED_UNIVERSE","eligibility":"ELIGIBLE","status":"DEGRADED","value":"0.925000","calculationRunId":101,"formulaVersion":"v1","parameterHash":"sha256:rs","qualityReasonCodes":["RANKED_UNIVERSE_NOT_FULL_MARKET"]},
  "rotationPersistence": {"scope":"RANKED_UNIVERSE","eligibility":"ELIGIBLE","status":"DEGRADED","rankPercentileChange":"0.300000","meanRankPercentile":"0.800000","calculationRunId":103,"formulaVersion":"v1","parameterHash":"sha256:rotation","qualityReasonCodes":["RANKED_UNIVERSE_NOT_FULL_MARKET"]},
  "capitalFlowTrend": {"scope":"WATCHED_SECTORS","eligibility":"ELIGIBLE","status":"OK","flowIntensity":"0.024000","flowIntensityChange":"0.010000","calculationRunId":106,"formulaVersion":"v1","parameterHash":"sha256:flow","qualityReasonCodes":[]},
  "volumeConfirmation": {"scope":"WATCHED_SECTORS","eligibility":"ELIGIBLE","status":"OK","value":"UP_CONFIRMED","calculationRunId":105,"formulaVersion":"v1","parameterHash":"sha256:volume","qualityReasonCodes":[]},
  "alerts": {"status":"OK","count":1,"highestSeverity":"WARN","publicationBatchId":9001,"calculationRunIds":[103,105]}
}
```

### 5.1 板块相对强弱（规划，未实现）

| 方法 | 路径（规划） | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/sector-analytics/relative-strength?market=&window=&asOfDate=&page=&size=&sortBy=&sortDirection=&formulaVersion=&parameterHash=&calculationRunId=` | 按市场/窗口查询 RS-rank 排行（未实现） |
| GET | `/api/v1/market-data/sector-analytics/relative-strength/{sectorId}?window=&asOfDate=&formulaVersion=&parameterHash=&calculationRunId=` | 单板块详情与 tracking symbol 对照（未实现） |

请求示例：

```
GET /api/v1/market-data/sector-analytics/relative-strength?market=CN&window=20&asOfDate=2026-07-31&page=1&size=20
```

响应示例（`ApiResponse<T>`，含 `formulaCode`/`formulaVersion`/`benchmarkType`）：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "market": "CN",
    "window": 20,
    "asOfDate": "2026-07-31",
    "benchmarkType": "RANK_SET_EQUAL_WEIGHT",
    "benchmarkSymbol": null,
    "formulaCode": "RELATIVE_RETURN_LOG",
    "formulaVersion": "v1",
    "parameterHash": "sha256:example",
    "calculationRunId": 101,
    "rankScope": "RANKED_UNIVERSE",
    "coverageRate": null,
    "isTruncated": true,
    "pageData": {"page":1,"size":20,"total":1,"sortBy":"rsRankPercentile","sortDirection":"DESC","anchorType":"CALCULATION_RUN","anchorId":101,"items":[
      {
        "sectorId": 1001,
        "sectorName": "半导体",
        "relativeReturnN": "0.0304731969",
        "rsRankPercentile": "0.925000",
        "rankScope": "RANKED_UNIVERSE",
        "qualityStatus": "DEGRADED",
        "qualityReasonCodes": ["RANKED_UNIVERSE_NOT_FULL_MARKET"],
        "validSampleSize": 20
      }
    ]}
  },
  "timestamp": "2026-07-31T16:00:00"
}
```

当前 LongPort 无独立总数/分页且上限 100，MVP 固定返回 `RANKED_UNIVERSE + DEGRADED + RANKED_UNIVERSE_NOT_FULL_MARKET`。`VERIFIED_FULL_MARKET` 仅为未来预留，禁止用返回条数反填 expected count。公共 RS 统一使用固定 cohort 的共同 rank-set 等权基准：先取窗口内每日排行稳定身份的交集，每日基准和板块收益都只使用该 cohort；中途进入/退出者不补值，cohort 指纹纳入参数与来源哈希。

tracking symbol 详情对照固定读取 `stock_daily_bar(adjustType=NONE)`，仅表达**未复权价格收益**，不代表含分红的总回报或可投资 ETF 业绩。响应必须返回 `trackingSymbol/alignedStartDate/alignedEndDate/sectorPriceReturn/trackingPriceReturn/returnSpread/missingSectorDates/missingTrackingDates/latestBarTime/adjustType/qualityStatus/qualityReasonCodes`。两边按同市场权威交易日取日期交集并用区间首末收盘价计算简单价格收益；任一端点缺失或中间缺日时 `returnSpread=null` 并降级，不前向填补，也不改变公共 RS 基准。

### 5.2 板块轮动持续性（规划，未实现；市场级与板块级分端点）

| 方法 | 路径（规划） | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/sector-analytics/rotation-market-stability?market=&window=&startDate=&endDate=&page=&size=&sortBy=&sortDirection=&formulaVersion=&parameterHash=&calculationRunId=` | 排行样本级 Spearman ρ 时序（未实现） |
| GET | `/api/v1/market-data/sector-analytics/rotation-sector-persistence?market=&window=&asOfDate=&page=&size=&sortBy=&sortDirection=&formulaVersion=&parameterHash=&calculationRunId=` | 板块级持续性排行（未实现） |
| GET | `/api/v1/market-data/sector-analytics/rotation-sector-persistence/{sectorId}?window=&asOfDate=&formulaVersion=&parameterHash=&calculationRunId=` | 单板块位次序列指标（未实现） |

排行样本稳定性响应示例（节选，不归属单板块；MVP 不得称为全市场）：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "pageData": {"page":1,"size":20,"total":1,"sortBy":"tradeDate","sortDirection":"ASC","anchorType":"CALCULATION_RUN","anchorId":102,"items":[{
    "market": "CN",
    "window": 10,
    "tradeDate": "2026-07-31",
    "formulaCode": "ROTATION_SPEARMAN",
    "formulaVersion": "v1",
    "parameterHash": "sha256:rotation-example",
    "calculationRunId": 102,
    "rankScope": "RANKED_UNIVERSE",
    "sourceCoverageRate": null,
    "isTruncated": true,
    "rankSpearmanMean": "0.948683",
    "minPairCoverage": "0.820000",
    "avgPairCoverage": "0.910000",
    "validPairCount": 9,
    "weightedIntersectionCount": 720,
    "validSampleSize": 9,
    "qualityStatus": "DEGRADED",
    "qualityReasonCodes": ["RANKED_UNIVERSE_NOT_FULL_MARKET"]
    }]}
  },
  "timestamp": "2026-07-31T16:00:00"
}
```

板块级持续性响应示例（节选，含 6 个位次指标）：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "market": "CN",
    "window": 5,
    "asOfDate": "2026-07-31",
    "formulaCode": "ROTATION_SECTOR_PERSISTENCE",
    "formulaVersion": "v1",
    "parameterHash": "sha256:persistence-example",
    "calculationRunId": 103,
    "rankScope": "RANKED_UNIVERSE",
    "coverageRate": null,
    "isTruncated": true,
    "pageData": {"page":1,"size":20,"total":1,"sortBy":"meanRankPercentile","sortDirection":"DESC","anchorType":"CALCULATION_RUN","anchorId":103,"items":[
      {
        "sectorId": 1001,
        "meanRankPercentile": "0.800000",
        "rankPercentileStdDev": "0.1870828693",
        "topBucketOccupancyRate": "0.400000",
        "consecutiveLeadingDays": 2,
        "consecutiveLaggingDays": 0,
        "rankPercentileChange": "0.500000",
        "qualityStatus": "DEGRADED",
        "qualityReasonCodes": ["RANKED_UNIVERSE_NOT_FULL_MARKET"]
      }
    ]}
  },
  "timestamp": "2026-07-31T16:00:00"
}
```

### 5.3 板块资金趋势与交易集中度（规划，未实现）

| 方法 | 路径（规划） | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/sector-analytics/capital-flow-trend?market=&tradeDate=&window=&page=&size=&sortBy=&sortDirection=&formulaVersion=&parameterHash=&calculationRunId=` | 关注板块资金趋势（未实现） |
| GET | `/api/v1/market-data/sector-analytics/capital-flow-trend/{sectorId}?startDate=&endDate=&window=&formulaVersion=&parameterHash=&calculationRunId=` | 单板块资金历史（未实现） |
| GET | `/api/v1/market-data/sector-analytics/turnover-concentration?market=&tradeDate=&window=1&topK=&page=&size=&sortBy=&sortDirection=&formulaVersion=&parameterHash=&calculationRunId=` | 查询关注板块单 CLOSE 快照交易集中度（未实现） |
| GET | `/api/v1/market-data/sector-analytics/turnover-concentration/{sectorId}?tradeDate=&window=&topK=&formulaVersion=&parameterHash=&calculationRunId=` | 单板块集中度明细（未实现） |

收益贡献接口在 P1.7 MVP 中不存在。只有 point-in-time 成分与 `t-1` 权重数据门禁通过后，P1.7-C 才能新增该契约。

资金趋势响应固定 `flowScope=WATCHED_SECTORS`，并包含 `watchedSectorCount/validFlowSectorCount/netInflow/currencyCode/turnoverAmount/flowIntensity/cumulativeNetInflowN/meanFlowIntensityN/positiveFlowDaysRate/flowIntensityChange` 及统一血缘和质量字段。交易集中度与量价确认同样固定 `dataScope=WATCHED_SECTORS` 并返回 watched/valid sector count，任何列表标题不得写“全市场排行”。

交易集中度响应示例（节选；MVP 固定 `window=1`；正/负项是资金方向占比，只有 top-K 项是集中度；`absSum=0` 时置空 + `INSUFFICIENT_RAW`）：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "market": "CN",
    "tradeDate": "2026-07-31",
    "window": 1,
    "formulaCode": "TURNOVER_CONCENTRATION",
    "formulaVersion": "v1",
    "parameterHash": "sha256:concentration-example",
    "calculationRunId": 104,
    "dataScope": "WATCHED_SECTORS",
    "watchedSectorCount": 6,
    "validSectorCount": 5,
    "pageData": {"page":1,"size":20,"total":1,"sortBy":"topKTurnoverShare","sortDirection":"DESC","anchorType":"CALCULATION_RUN","anchorId":104,"items":[
      {
        "sectorId": 1001,
        "topKTurnoverShare": "0.382000",
        "positiveFlowShare": "0.850000",
        "negativeFlowShare": "0.150000",
        "absoluteFlowConcentration": "0.750000",
        "topK": 3,
        "excludedMemberCount": 2,
        "validMemberCount": 24,
        "qualityStatus": "OK",
        "qualityReasonCodes": [],
        "topTurnoverMembers": [{"canonicalSymbol":"SH.600519","turnoverAmount":"1200000.000000","turnoverShare":"0.180000"}],
        "topAbsoluteFlowMembers": [{"canonicalSymbol":"SH.600036","netInflow":"-300000.000000","absoluteFlowShare":"0.220000"}]
      }
    ]}
  },
  "timestamp": "2026-07-31T16:00:00"
}
```

### 5.4 板块量价确认（规划，未实现，六状态）

| 方法 | 路径（规划） | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/sector-analytics/volume-confirmation?market=&tradeDate=&page=&size=&sortBy=&sortDirection=&formulaVersion=&parameterHash=&calculationRunId=` | 查询关注板块量价确认状态（未实现） |
| GET | `/api/v1/market-data/sector-analytics/volume-confirmation/{sectorId}?tradeDate=&formulaVersion=&parameterHash=&calculationRunId=` | 单板块量价确认详情（未实现） |

响应示例（节选，`confirmationStatus` 为六状态 `UP_CONFIRMED/UP_UNCONFIRMED/DOWN_CONFIRMED/DOWN_UNCONFIRMED/NEUTRAL/INSUFFICIENT`；下跌放量是 `DOWN_CONFIRMED` 而非背离）：

```json
{
  "success": true,
  "code": "OK",
  "data": {
    "market": "CN",
    "tradeDate": "2026-07-31",
    "formulaCode": "VOLUME_CONFIRMATION",
    "formulaVersion": "v1",
    "parameterHash": "sha256:volume-example",
    "calculationRunId": 105,
    "dataScope": "WATCHED_SECTORS",
    "watchedSectorCount": 6,
    "validSectorCount": 5,
    "pageData": {"page":1,"size":20,"total":2,"sortBy":"turnoverRatio","sortDirection":"DESC","anchorType":"CALCULATION_RUN","anchorId":105,"items":[
      {
        "sectorId": 1001,
        "changeRate": "0.031000",
        "turnoverAmount": "50000000.000000",
        "turnoverRatio": "1.650000",
        "confirmationStatus": "UP_CONFIRMED",
        "qualityStatus": "OK",
        "qualityReasonCodes": []
      },
      {
        "sectorId": 1002,
        "changeRate": "-0.020000",
        "turnoverAmount": "42000000.000000",
        "turnoverRatio": "1.300000",
        "confirmationStatus": "DOWN_CONFIRMED",
        "qualityStatus": "OK",
        "qualityReasonCodes": []
      }
    ]}
  },
  "timestamp": "2026-07-31T16:00:00"
}
```

### 5.5 板块异动提醒（规划，未实现，复用 /alerts）

板块异动提醒复用现有 `/api/v1/market-data/alerts`，但 P1.7-A 必须先扩展表和查询链路：`subjectType=SECTOR`、数值 `sectorId`、`alertType`、`dedupKey`、`calculationRunId`、`publicationBatchId`。板块提醒必须属于所选发布批次；触发 run 非空时必须是该 batch 成员。重复调度返回同一事件而不是重复插入。

每条板块提醒必须返回 `summary/evidenceCodes/evidenceValues/qualityReasonCodes`。证据顺序为指标越界、相对位置变化、关注板块资金趋势、量价确认、数据质量；这是观察证据而非因果解释，P1.7-C 前不得声称某成分“导致”板块涨跌。

固定触发契约：`SECTOR_RANK_JUMP` 为百分位变化绝对值达到 0.30（0.50 且无降级才可 HIGH）；`SECTOR_RS_REVERSAL` 仅指前日 `rsPercentile<=0.2` 到当日 `>=0.8` 的 BULLISH regime，或前日 `rsPercentile>=0.8` 到当日 `<=0.2` 的 BEARISH regime；`SECTOR_VOLUME_CONFIRMATION` 为完整五日基线下量比 ≥2.0 且绝对涨跌 ≥0.03（≥0.05 且无降级才可 HIGH）；`SECTOR_TURNOVER_CONCENTRATION` 为 top-K 成交额占比 ≥0.60（≥0.75 且样本合格才 WARN）。Z-score 仅作 evidence，不触发或升级。阈值纳入 `parameterHash`；`STALE/ORIGIN_CHANGED/BLOCKED_*/BACKOFF` 不产新提醒，其他降级不得产 HIGH。只有量价规则使用其固定阈值，其他规则缺历史即不产提醒。

```
GET /api/v1/market-data/alerts?severity=&resolved=&subjectType=SECTOR&sectorId=&publicationBatchId=&alertTypePrefix=SECTOR_&page=&size=
```

异动提醒仅为观察提示，不构成投资建议，不产生交易动作。

### 5.6 错误码（规划）

板块分析 API 不直接外联 provider，但 readiness/overview 必须把上游 `BLOCKED_AUTH/BLOCKED_PERMISSION/BACKOFF` 作为数据状态返回，避免“空数据”误导。错误按下列语义区分：

- 请求参数错误（market/window/date 非法）→ 复用 `VALIDATION_ERROR`（HTTP 400）。
- 公式版本不存在 → `MARKET_SECTOR_ANALYTICS_FORMULA_VERSION_NOT_FOUND`（HTTP 404）；实施任务必须同步修改全局异常映射，不能继续把所有 `BusinessException` 固定返回 400。
- 真正无法查询（衍生表/原始事实不可读且非上述情形）→ 规划码 `MARKET_SECTOR_ANALYTICS_DATA_UNAVAILABLE`（待 ST-B2 落库）。
- 尚无衍生数据 → HTTP 200 + `qualityStatus=NO_DERIVED_DATA` + `qualityReasonCodes`。
- 原始事实不足 → HTTP 200 + `qualityStatus=INSUFFICIENT_RAW` + `qualityReasonCodes`。
- 数据陈旧 → HTTP 200 + `qualityStatus=STALE` + `qualityReasonCodes`。
- 样本不足 → HTTP 200 + `qualityStatus=INSUFFICIENT_SAMPLE` + `qualityReasonCodes`，前端降级展示，不产 HIGH 提醒。

200 + `qualityStatus` 非 `OK` 的响应统一在字段层降级标注，由前端灰显处理；不作为错误码返回。规划码遵循 `MARKET_SECTOR_*` 前缀，与现有 `MARKET_SECTOR_PROVIDER_UNAVAILABLE` / `MARKET_DATA_*` 命名一致。

## 6. P1.9 行情数据资产只读查询（已实现，P1.9-D 闭环增强）

> 设计：`../features/MARKET_DATA_ASSET_CENTER_DESIGN.md`。接口只读已落库行情，不调用 provider。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/assets?market=&keyword=&page=1&size=20` | 分页查询至少存在一条日 K 或分钟 K 的行情资产；`market=CN` 聚合 SH/SZ/BJ，最大 size=100 |
| GET | `/api/v1/market-data/assets/{canonicalSymbol}/availability` | 查询该证券已存在的 interval/dataSource/adjustType、首末时间、条数、最新抓取和水位 |
| GET | `/api/v1/market-data/assets/{canonicalSymbol}/series?interval=&from=&to=&adjustType=&dataSource=` | 返回最多 2000 条升序 bars，以及区间摘要、质量、覆盖和截断状态 |
| GET | `/api/v1/market-data/assets/{canonicalSymbol}/related-tasks?interval=&from=&to=&page=&size=` | 查询范围相关的计划/任务；不声称 bar 级精确血缘 |

实现约束：

- `interval` 为 `1D/1M/5M/15M/30M/60M`；日 K 与分钟 K 不拼接。
- 必须显式选择单一 `dataSource` 和 `adjustType`，禁止来源/复权混合。
- SQL 使用 symbol + interval/source/adjust + 时间上下界 + `LIMIT 2001`；禁止全表加载后截断。
- 只返回前 2000 条，第 2001 条仅用于 `truncated=true`。
- CN 在权威日历/时段可用时计算覆盖；HK/US 日历未闭环时 `coverageStatus=UNKNOWN` 且 expected/missing 为 null。
- `expectedBarCount` 沿用 SQL 的包含端点语义（`bar_start_time >= from` 且 `<= to`），只统计满足 `start >= from`、`start <= to`、`start < sessionEnd` 的合法网格起点（处理上午/午休/下午、单日部分区间与跨交易日；`to` 落在会话结束时不含该 bar），不按整天恒定量；HK/US 或日历未就绪时返回 UNKNOWN 且 expected/missing 为 null，不得用实际条数反填。实际条数多于 expected（如非网格数据点）时 `coverageStatus=PARTIAL` 且 `reasonCodes` 含 `UNEXPECTED_BARS`，禁止 `actual=1/expected=0/VERIFIED` 之类的假绿灯。
- 新鲜度 `quality.freshness` 与每个 `combination.freshness` 为 `FRESH/STALE/UNKNOWN`：依据该组合最新 bar/水位与最近已完成交易时段判定（日 K 按最新交易日、分钟 K 按最新 bar 起点），不按自然日猜测；无权威日历或无法判断时必为 `UNKNOWN` 且 `freshnessDetail` 给出原因（如“缺少权威交易日历，无法判定新鲜度”“HK/US 日历未闭环，无法判定新鲜度”）。
- 空范围返回 200 + `bars=[]`；非法参数或范围超限返回 400；证券不存在返回 404。
- 分钟 K 的 `from`/`to` 接受带 offset 的 ISO-8601（按 offset 折算到 Asia/Shanghai）或不带 offset 的本地墙钟时间；折算到 Asia/Shanghai 后必须是整分钟（`second` 与 `nano` 均为 0），非整分钟返回 400 `VALIDATION_ERROR`，禁止静默截断取整。
- 价格、金额与比率使用 BigDecimal 字符串；分钟时间返回含市场 offset 的 ISO-8601，bars 按时间升序。
- 本查询只读现有表，不调用 provider、不写 DB、不创建任务、不新增 migration。

错误码：

- `MARKET_DATA_ASSET_RANGE_TOO_LARGE`：时间范围或 bar 上限不满足查询契约。
- `MARKET_DATA_ASSET_COMBINATION_NOT_FOUND`：证券存在但所选 interval/source/adjust 组合不存在；页面应先查 availability 避免正常触发。
- 其他格式/日期错误复用 `VALIDATION_ERROR`，证券不存在复用 `STOCK_NOT_FOUND`。

## 7. 数据底座接口（QTA V2-1，已实现，ADR-0015；R1 修复收口后）

> 前缀 `/api/v1/market-data/data-foundation`。语义：数据集定义与版本、历史回补任务（**R1：二维分片=证券组×Provider 安全日期窗（腾讯 365 天，防 640 截断）；QUEUED 状态机+后台 worker 持久化执行；claim 崩溃恢复**）、CSV/快照导入（kind+file_hash 幂等；**R1：DAILY_BAR 必绑 datasetVersionId 且行入版本 manifest**）、质量检查与发布门禁（**R1：16 族检查基于版本 manifest 域，总体覆盖/首末边界低于 0.90 阈值 FAIL（`qta.data-foundation.publish-coverage-threshold` 可配），血缘漂移阻断发布**）。日 K/证券/日历事实复用既有表，不复制。单位冻结：价格=元、volume=股、amount=元、换手率=小数。运行时证据：`docs/development/tasks/QTA-V2-DATA-FOUNDATION-V21-RUNTIME-VERIFICATION{-R1}.md`。

### 7.1 数据集与版本

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/datasets` | 创建数据集定义（body：datasetCode/datasetName/marketCode/barType(首期 DAILY)/frequency(1D)/providerCode/adjustType(首期仅 NONE；IMPORT_* 前缀=导入类数据集)/description） |
| GET | `/datasets` | 数据集列表（含 currentVersionId 发布指针与 unitCaliber 单位口径） |
| GET | `/datasets/{code}/versions` | 版本列表（status ∈ DRAFT/BACKFILLING/QUALIFYING/QUALIFIED/REJECTED/RELEASED/RETIRED；isCurrentReleased） |
| POST | `/datasets/{code}/versions` | 手动建版本（仅 IMPORT_* 数据集；body：startDate/endDate） |
| GET | `/datasets/{code}/released` | 当前已发布版本（未发布 data=null） |

请求/响应示例（创建导入数据集）：

```json
POST /api/v1/market-data/data-foundation/datasets
{"datasetCode":"CN_DAILY_IMPORT_FIXTURE","datasetName":"A股日K导入数据集","marketCode":"CN",
 "barType":"DAILY","frequency":"1D","providerCode":"IMPORT_CSV_DAILY","adjustType":"NONE","description":"..."}
→ {"success":true,"code":"SUCCESS","data":{"id":1,"datasetCode":"CN_DAILY_IMPORT_FIXTURE",
   "unitCaliber":"价格=元，volume=股，amount=元，换手率=小数（ADR-0015 单位冻结）","currentVersionId":null,...}}
```

### 7.2 历史回补任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/backfill-tasks` | 创建（body：datasetCode/marketCode/providerCode/frequency/adjustType 须与数据集定义完全一致；startDate ≥ 2021-01-01；endDate ≤ 今天；symbols ≤ 10000（R1：容纳全 A 股票池），空=最新池全量；chunkSize 1-500 默认 50）。**R1：二维分片=证券组×日期窗（Provider.safeRequestWindowDays，腾讯 365 自然日），chunk.start/end=实际请求区间，分片总数 ≤ 40000**；证券范围写 mdf_backfill_task_symbol（不塞 symbols_json）；同 scope 存在 PENDING/QUEUED/RUNNING/PAUSED 任务时拒绝重复创建 |
| GET | `/backfill-tasks?status=&page=&pageSize=` | 分页列表（PageResultVO） |
| GET | `/backfill-tasks/{id}` | 详情：planned/success/fail/skip（标的数）+ inserted/updated（行数）+ totalChunks/succeededChunks/failedChunks + lastError；**symbols 仅在 ≤50 时返回（全 A 任务避免巨列表，plannedCount 恒可见）** |
| GET | `/backfill-tasks/{id}/chunks` | 分片明细（chunkIndex/status/attempts/各计数/lastErrorCode/lastErrorMessage/实际日期窗）；供前端轮询 |
| POST | `/backfill-tasks/{id}/run` | **R1：异步——仅做状态转换（PENDING/PAUSED/PARTIAL_FAILED/FAILED→QUEUED）快速返回 BackfillTaskVO（QUEUED 或已被 worker 认领的 RUNNING），不等待执行**；后台 worker（可配轮询 `qta.data-foundation.worker.poll-ms`/并发 `worker.concurrency`；**R2：信号量有界并发，槽满跳过轮询**）条件认领执行，断点续跑跳过终态分片。**R2：worker 全程所有权 fencing——执行期心跳续租（id+RUNNING+token 三重校验），暂停/回队/恢复/新 owner 抢占后旧 worker 立即停止且不可写任务与分片状态**；同 scope 存在 PENDING/QUEUED/RUNNING/PAUSED 任务时重复创建拒绝 |
| POST | `/backfill-tasks/{id}/pause` | 暂停（**QUEUED/RUNNING 均可**；释放 claim，worker 逐证券检查后停止） |
| POST | `/backfill-tasks/{id}/chunks/retry` | 重试失败分片（FAILED→PENDING 保留 attempts，随后**入队**返回 QUEUED；仅 PARTIAL_FAILED/FAILED 可调用）。容器重启/claim 超时的 RUNNING 任务由启动+定时恢复自动回队（chunk RUNNING→PENDING，错误置 RECOVERED_STALE_RUNNING 可追溯） |

### 7.3 质量与发布

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/dataset-versions/{id}/quality-check` | **R1：16 族检查全部基于版本 manifest 域**（版本归属行；同窗其他 Provider 合法共存不参与判定）并落结果+覆盖水位，版本转 QUALIFIED/REJECTED。检查族：EMPTY_DATASET / DATE_RANGE_COVERAGE / UNIVERSE_COVERAGE / DAILY_BAR_GAP / DUPLICATE_ROWS / OHLC_VALIDITY / UNIT_ANOMALY / NON_TRADING_DAY_ANOMALY / INDUSTRY_MEMBERSHIP_OVERLAP / INDUSTRY_MEMBERSHIP_INVALID_PERIOD / UNMAPPED_INDUSTRY_SYMBOL / PROVIDER_ADJUST_MIXING / DATA_STALENESS + **OVERALL_COVERAGE_GATE / BOUNDARY_COVERAGE / LINEAGE_DRIFT**（R1 新增：总体覆盖=manifest 行/期望行（日历交易日×范围证券，上市日缺失假设窗口起点、DELISTED 剔除）；首末交易日边界覆盖；冻结版本底层事实漂移检测）。**FAIL 族扩大：总体覆盖/边界覆盖低于阈值（默认 0.90）必 FAIL（截断与严重不完整不得发布）** |
| GET | `/dataset-versions/{id}/quality` | 质量结果列表（checkCode/status(OK/WARN/FAIL)/affectedCount/detailJson/checkedAt） |
| GET | `/dataset-versions/{id}/coverage` | 覆盖水位（canonicalSymbol/firstDate/lastDate/rowCount/expectedDays/coveredDays/coverageRatio） |
| POST | `/dataset-versions/{id}/publish` | 发布（仅 QUALIFIED 且无 FAIL；**R1：发布前冻结 manifest/content hash；已冻结版本先做漂移校验，漂移→DRIFTED+拒绝**；事务内同数据集旧 RELEASED→RETIRED、current_version_id 指针切换）。`GET /datasets/{code}/released` 与版本 VO 返回 `contentHash/manifestRowCount/lineageStatus`（FROZEN 可复现声明；DRIFTED 不得声称可复现） |

### 7.4 CSV/快照导入

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/imports?kind=&datasetVersionId=` | multipart 字段 `file`（≤50MB、≤20 万行）；kind ∈ UNIVERSE_SNAPSHOT / TRADING_CALENDAR / DAILY_BAR / INDUSTRY_TAXONOMY / INDUSTRY_MEMBERSHIP_PIT；表头不匹配整批 400；同内容重复导入幂等返回既有批次。**R1：DAILY_BAR 必须提供导入类 datasetVersionId（provider/adjust/market 与目标版本不一致拒绝），导入行归属该版本 manifest（来源=IMPORT_BATCH+批次 id）；其余 kind 的 datasetVersionId 可选（仅留批次血缘）** |
| GET | `/imports?kind=&page=&pageSize=` | 批次列表 |
| GET | `/imports/{id}` | 批次详情（inserted/updated/skipped/rejected + errorReportJson：行级错误数组 [{recordNumber, reason, raw}]，上限 50 条） |

导入 schema（表头冻结；单位元/股/小数；日期 YYYY-MM-DD；落库 data_source=IMPORT_CSV_* 不冒充线上 Provider）：

```text
UNIVERSE_SNAPSHOT:       symbol,name,market,total_market_cap,circulating_market_cap,turnover_rate,as_of_date
TRADING_CALENDAR:        market_code,trade_date,is_trading_day   （首期仅 CN；true/false）
DAILY_BAR:               symbol,trade_date,open,high,low,close,volume,amount
INDUSTRY_TAXONOMY:       taxonomy_code,taxonomy_name,provider_code,note
INDUSTRY_MEMBERSHIP_PIT: taxonomy_code,industry_code,industry_name,symbol,effective_from,effective_to
                         （effective_to 空=至今；同 symbol 半开区间不得交叉，文件内重叠行拒绝）
```

### 7.5 错误码

`DATA_FOUNDATION_DATASET_NOT_FOUND`（数据集不存在）/ `DATA_FOUNDATION_DATASET_CONFLICT`（请求与数据集定义不一致）/ `DATA_FOUNDATION_VERSION_NOT_FOUND` / `DATA_FOUNDATION_BACKFILL_STATE_INVALID`（状态不允许该操作）/ `DATA_FOUNDATION_BACKFILL_DUPLICATE`（同 scope 活跃任务已存在）/ `DATA_FOUNDATION_BACKFILL_RUNNING`（任务执行中或状态不可 run）/ `DATA_FOUNDATION_UNIVERSE_EMPTY`（全量回补但池快照为空）/ `DATA_FOUNDATION_IMPORT_KIND_INVALID` / `DATA_FOUNDATION_IMPORT_FILE_INVALID`（表头/格式不合法）/ `DATA_FOUNDATION_QUALITY_GATE_FAILED`（发布门禁未通过）。文件类复用 `CSV_EMPTY_FILE`/`CSV_FILE_TOO_LARGE`/`CSV_TOO_MANY_ROWS`。
