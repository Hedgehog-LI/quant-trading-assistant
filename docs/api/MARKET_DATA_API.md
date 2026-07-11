# Market Data API

> 统一前缀：`/api/v1/market-data`。统一响应：`ApiResponse<T>`。当前实现事实以代码为准；LongPort 相关接口为 v0.1.2 设计目标。

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

## 2. v0.1.2 LongPort 设计目标接口

### Provider 状态

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/market-data/providers/LONGPORT/status` | 查看 LongPort 是否启用、是否配置、最近成功/失败、脱敏错误 |
| POST | `/api/v1/market-data/providers/LONGPORT/health-check` | 触发只读健康检查 |

状态响应建议：

```json
{
  "provider": "LONGPORT",
  "enabled": true,
  "configured": true,
  "readOnly": true,
  "lastSuccessAt": "2026-07-10T10:00:00",
  "lastErrorCode": null,
  "lastErrorMessage": null
}
```

### 最新价快照

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/market-data/quotes/latest` | 按证券列表拉取最新行情，可选择落库 |
| GET | `/api/v1/market-data/quote-snapshots?canonicalSymbol=&fromTime=&toTime=&page=&size=` | 查询外部价格快照 |

请求示例：

```json
{
  "canonicalSymbols": ["SH.600519", "SZ.000001"],
  "provider": "LONGPORT",
  "persist": true
}
```

响应项建议：

```json
{
  "canonicalSymbol": "SH.600519",
  "providerSymbol": "600519.SH",
  "currentPrice": "1680.000000",
  "preClosePrice": "1670.000000",
  "openPrice": "1675.000000",
  "highPrice": "1690.000000",
  "lowPrice": "1668.000000",
  "volume": 25000,
  "amount": "42000000.000000",
  "quoteTime": "2026-07-10T14:55:00",
  "fetchedAt": "2026-07-10T14:55:03",
  "dataSource": "LONGPORT",
  "stale": false
}
```

### 历史日 K 同步任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/market-data/sync-tasks/daily-bars` | 创建历史日 K 同步任务 |
| GET | `/api/v1/market-data/sync-tasks?taskType=&provider=&status=&page=&size=` | 查询同步任务列表 |
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

**重试语义**：
- PENDING/RUNNING/SUCCEEDED：同 scope 幂等返回已有任务。
- FAILED/PARTIAL_FAILED：创建新 retry 任务，`parentTaskId` 指向该 scope 最新任务；旧任务保留可追溯。
- 连续多次重试不会唯一键冲突，每次 retry 的 idempotencyKey 含时间戳保证唯一。

任务状态：

```text
PENDING -> RUNNING -> SUCCEEDED / PARTIAL_FAILED / FAILED / CANCELLED
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
