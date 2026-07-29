# Agent Assistant API

> 统一前缀：`/api/v1/agent`。所有接口需要 Bearer Token 鉴权（`Authorization: Bearer <token>`）。
> 第一期全部为只读 GET，禁止任何写操作、自动交易和账户操作。

## 安全边界

- **Spring Security** 只保护 `/api/v1/agent/**` 和 `/v3/api-docs/agent`，现有前端 API 保持兼容。Token/限流 filter 仅在 Security chain 内运行（已禁用 servlet 自动注册）。
- **Bearer Token**：常量时间比较（SHA-256 + `MessageDigest.isEqual`），32+ 字符强度校验，默认关闭（`QTA_AGENT_ENABLED=false`）。
- **限流**：per-client per-minute 内存滑动窗口（默认 60/min），超限返回 429。
- **统一审计**：单一 servlet 级 `AgentAuditFilter`（`OncePerRequestFilter`，注册为最外层 filter）覆盖整个 FilterChain，无论请求被 token/限流 filter 短路、被 Security entry point 拒绝、还是被 Controller 处理/抛异常，都只产生**恰好一条**审计记录（覆盖 200/401/403/404/429/500）。Controller 不再手动审计。Flyway V16 `agent_api_audit_log` 持久化脱敏审计，禁止记录 Token/凭据/完整请求/异常堆栈。
- **requestId 单一来源**：每个响应携带 `X-Request-ID` 响应头，由 `AgentAuditFilter` 生成并写入 request 属性 `agentRequestId`；token/限流 filter、401 `AuthenticationEntryPoint`、Controller 全部复用同一 requestId，确保 header / 错误 body / 审计行三者一致。
- **QQ OpenID 白名单**：`QTA_AGENT_ALLOWED_OPEN_IDS` 配置，为空时拒绝所有 QQ 用户（fail-closed）。
- **Nginx 公网阻断**：`/api/v1/agent/**`、`/v3/api-docs/**`、Swagger UI、非必要 Actuator。

## 可信回答契约

所有业务响应统一包含 `TrustedAnswer`：

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "conclusion": "系统运行正常",
    "generatedAt": "2026-07-26T15:00:00+08:00",
    "dataAsOf": "2026-07-26T14:55:00+08:00",
    "freshnessStatus": "FRESH",
    "evidence": [
      { "type": "PROVIDER_STATUS", "id": "LONGPORT", "observedAt": "2026-07-26T15:00:00+08:00" }
    ],
    "warnings": [],
    "data": {}
  }
}
```

`freshnessStatus` 枚举：`FRESH` / `DELAYED` / `STALE` / `UNKNOWN`

## 接口列表

| operationId | 方法与路径 | 说明 |
| --- | --- | --- |
| `qtaAgentCapabilities` | `GET /api/v1/agent/capabilities` | 能力、版本和只读边界 |
| `qtaAgentSystemHealth` | `GET /api/v1/agent/system/health` | 应用、DB、Provider 摘要 |
| `qtaAgentTodayOverview` | `GET /api/v1/agent/trading/today?date=` | 工作台统计、风险和待办 |
| `qtaAgentPortfolioSummary` | `GET /api/v1/agent/portfolio/summary` | 持仓、盈亏和价格口径 |
| `qtaAgentCollectionOverview` | `GET /api/v1/agent/market-data/collection-overview?market=&date=` | 计划、任务和水位。market 按 canonicalSymbol 前缀过滤水位（SH/SZ/BJ→CN, HK→HK, US→US）；date 按 lastTradeDate 过滤水位。响应含 `marketFilterApplied`、`dateFilterApplied`、`filteredWatermarkCount`。 |
| `qtaAgentCollectionFailures` | `GET /api/v1/agent/market-data/failures?market=&since=&limit=` | 最近失败及原因 |
| `qtaAgentDataQualityAlerts` | `GET /api/v1/agent/market-data/alerts?status=&since=&limit=` | 数据质量提醒 |
| `qtaAgentSectorRankingSummary` | `GET /api/v1/agent/market-sectors/ranking-summary?market=&limit=` | 最新领涨、领跌和批次时间 |
| `qtaAgentSecurityMarketSummary` | `GET /api/v1/agent/securities/{canonicalSymbol}/market-summary` | 最新价、分钟线水位和来源 |

## 参数约束

- `limit`：默认 10，最大 50。
- `date`：`YYYY-MM-DD` 格式。
- `market`：`CN` / `HK` / `US`。
- `canonicalSymbol`：`SH.600519` 格式。
- `status`（alerts）：`resolved` / `unresolved`。

## 错误响应

所有错误响应均为 JSON，包含 `success:false`、`code`（非 `SUCCESS`）和 `requestId`，并携带 `X-Request-ID` 响应头（与 body/审计行一致）。500 响应不泄露内部异常类名/堆栈。

| HTTP | code | 原因 |
|------|------|------|
| 401 | `UNAUTHORIZED` | Missing/Invalid Bearer Token |
| 403 | `FORBIDDEN` | Agent disabled / Token strength insufficient |
| 404 | `NOT_FOUND` | Agent API disabled |
| 429 | `RATE_LIMITED` | Rate limit exceeded（含 `Retry-After: 60`） |
| 500 | `INTERNAL_ERROR` | 服务端内部错误；body 为 `ApiResponse.fail`（`success:false`、`code:INTERNAL_ERROR`、含 `requestId`），不泄露内部异常细节 |

## 配置

```properties
qta.agent.enabled=true
qta.agent.token=<32+ char random token>
qta.agent.rate-limit-per-minute=60
qta.agent.allowed-open-ids=<comma-separated QQ OpenIDs>
```

## OpenClaw 工具映射

| OpenClaw 工具 | 对应 Agent API |
| --- | --- |
| `qta_system_health` | `GET /system/health` |
| `qta_today_overview` | `GET /trading/today` |
| `qta_portfolio_summary` | `GET /portfolio/summary` |
| `qta_collection_overview` | `GET /market-data/collection-overview` |
| `qta_collection_failures` | `GET /market-data/failures` |
| `qta_data_quality_alerts` | `GET /market-data/alerts` |
| `qta_sector_ranking_summary` | `GET /market-sectors/ranking-summary` |
| `qta_security_market_summary` | `GET /securities/{symbol}/market-summary` |
