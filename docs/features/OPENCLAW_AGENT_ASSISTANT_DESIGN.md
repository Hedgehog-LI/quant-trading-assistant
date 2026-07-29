# Feature Design: OpenClaw 远程只读助手

> 版本：P1.8 · 状态：设计完成、待开发 · 关联：`../BUILD_CHECKLIST.md`、`../decisions/ADR-0011-openclaw-agent-facade-and-tool-boundary.md`

## 1. 用户目标

用户在公司等不方便打开 QTA 页面时，可以通过已接入 QQ 的 OpenClaw 查询系统运行情况、行情采集状态、板块排行、持仓盈亏和交易待办，并获得带数据时间与证据的可信结论。

首期定位是“远程只读诊断与查询助手”，不是拥有整个系统权限的自动代理。

## 2. 当前事实

- QTA 已有 Dashboard、交易计划、交易记录、持仓账本、行情采集、板块排行和数据质量相关 API。
- 当前没有 Spring Security、springdoc/OpenAPI、`/api/v1/agent/**`、Agent 调用审计或 OpenClaw Tool Plugin。
- 现有 `/api/v1/**` 同时包含查询、修改、删除和任务执行，不能整体暴露给 OpenClaw。
- P1.6 板块自动采集已通过代码和自动化门禁，但服务器真实 Provider 两时间桶验收仍待完成。

## 3. 专家组结论

产品、量化、安全、后端、OpenClaw 和 QA 评审形成以下一致结论：

1. 新增专用 Agent Facade，只暴露白名单聚合查询。
2. OpenAPI 只描述 Agent Facade，是契约和测试依据；OpenClaw 通过原生 Tool Plugin 调用，不动态导入全量 Swagger。
3. 第一期只做 GET 查询，禁止任何状态变更。
4. OpenClaw 只能通过服务器回环地址访问，公网 Nginx 明确阻断 Agent API、OpenAPI JSON、Swagger UI 和 Actuator。
5. 即使同机部署也必须使用独立 Bearer Token、限流和审计。
6. QQ 身份以网关提供的不可变 OpenID 为准，昵称、群身份和模型声称的身份都不可信。
7. 空数据、旧数据、Provider 不可用和查询失败必须明确区分，不能把“HTTP 200 + 空列表”解释为“运行正常”。
8. 写操作延后到第二阶段，并采用可见性白名单、预检、一次性确认、幂等和逐次审批。

## 4. 范围

### 4.1 第一期开发范围

- QTA Agent Facade、独立 OpenAPI 分组、Bearer Token 鉴权、限流和脱敏审计。
- 8 个只读聚合接口及对应 OpenClaw 工具。
- OpenClaw 原生 Tool Plugin、渐进式 Skill、配置模板和部署手册。
- 后端、OpenAPI、安全、插件的自动化测试。
- 建设看板和开发文档同步。

### 4.2 第一期不做

- POST、PUT、PATCH、DELETE Agent 接口。
- 任意 SQL、Shell、curl、文件路径、Docker、MySQL 或通用 HTTP 工具。
- 删除或修改交易、持仓快照、计划、复盘和行情数据。
- 创建任意历史补档或任意修改采集参数。
- 券商账户、订单、真实持仓、下单和撤单。
- 将 Longbridge 或 Agent Token 交给模型、QQ、前端、数据库或 Git。
- 根据排行生成买卖指令或投资建议。

## 5. 目标架构

```text
QQ 私聊（OpenID allowlist）
  -> OpenClaw QTA 专用 Agent
  -> qta-assistant Tool Plugin
  -> http://127.0.0.1:18081/api/v1/agent/**
  -> Agent Controller
  -> Agent Query Service / Manager
  -> 复用现有 Dashboard / Portfolio / MarketData Service
  -> MySQL
```

网络边界：

- QTA 后端生产端口绑定 `127.0.0.1:18081:8080`，或仅加入 OpenClaw 可访问的 Docker 内部网络。
- 公网 `18080` 继续服务前端和普通业务 API，但必须阻断 `/api/v1/agent/**`、`/v3/api-docs/**`、Swagger UI 和非必要 Actuator。
- 可选增加只监听 `127.0.0.1:18082` 的 Agent Nginx 入口。
- OpenClaw 不获得数据库、Docker、Shell 或 QTA 全量 API 权限。

## 6. 用户场景与工具

| 优先级 | 用户问题 | OpenClaw 工具 |
| --- | --- | --- |
| P0 | 今天系统和行情采集正常吗 | `qta_system_health`、`qta_collection_overview` |
| P0 | 哪些任务失败了，原因是什么 | `qta_collection_failures` |
| P0 | 今天哪些板块领涨领跌 | `qta_sector_ranking_summary` |
| P0 | 当前数据更新到几点 | `qta_collection_overview` |
| P0 | 今天还有什么待办 | `qta_today_overview` |
| P1 | 某只证券当前数据如何 | `qta_security_market_summary` |
| P1 | 当前持仓和盈亏如何 | `qta_portfolio_summary` |
| P1 | 有哪些数据质量异常 | `qta_data_quality_alerts` |

工具默认返回摘要，默认最多 10 条、最大 50 条。需要明细时通过任务 ID、批次 ID或证券代码二次查询，禁止一次返回大量分钟线。

## 7. Agent API 草案

> 本节是开发契约草案。代码实现并验收后，唯一正式定义转入 `docs/api/AGENT_ASSISTANT_API.md` 并登记到 `docs/api/API_INDEX.md`。

| operationId | 方法与路径 | 说明 |
| --- | --- | --- |
| `qtaAgentCapabilities` | `GET /api/v1/agent/capabilities` | 能力、版本和只读边界 |
| `qtaAgentSystemHealth` | `GET /api/v1/agent/system/health` | 应用、DB、Provider 摘要 |
| `qtaAgentTodayOverview` | `GET /api/v1/agent/trading/today?date=` | 工作台统计、风险和待办 |
| `qtaAgentPortfolioSummary` | `GET /api/v1/agent/portfolio/summary?asOf=` | 持仓、盈亏和价格口径 |
| `qtaAgentCollectionOverview` | `GET /api/v1/agent/market-data/collection-overview?market=&date=` | 计划、任务和水位 |
| `qtaAgentCollectionFailures` | `GET /api/v1/agent/market-data/failures?market=&since=&limit=` | 最近失败及原因 |
| `qtaAgentDataQualityAlerts` | `GET /api/v1/agent/market-data/alerts?status=&since=&limit=` | 数据质量提醒 |
| `qtaAgentSectorRankingSummary` | `GET /api/v1/agent/market-sectors/ranking-summary?market=&limit=` | 最新领涨、领跌和批次时间 |
| `qtaAgentSecurityMarketSummary` | `GET /api/v1/agent/securities/{canonicalSymbol}/market-summary` | 最新价、分钟线水位和来源 |

实现时允许合并内部查询，但必须保证上述 8 类 OpenClaw 工具能力完整。Controller 只调用 Agent Service；Agent Service 复用现有业务 Service，不建立平行业务 DAO、不复制 FIFO 或行情计算。

## 8. 可信回答契约

Agent 业务数据统一包含：

```json
{
  "conclusion": "采集部分异常",
  "generatedAt": "2026-07-26T10:30:00+08:00",
  "dataAsOf": "2026-07-26T10:25:00+08:00",
  "freshnessStatus": "FRESH",
  "evidence": [
    {
      "type": "SYNC_TASK",
      "id": "123",
      "observedAt": "2026-07-26T10:25:00+08:00"
    }
  ],
  "warnings": [],
  "data": {}
}
```

规则：

- `freshnessStatus` 仅允许 `FRESH / DELAYED / STALE / UNKNOWN`。
- Provider 不可用时必须说“无法确认最新行情”，不得复用旧值冒充实时数据。
- 空集合要区分“尚未采集”“确实无结果”和“查询失败”。
- 持仓盈亏必须说明价格来自手工快照还是外部行情，并给出价格时间。
- 板块排行必须带市场、采集批次和数据时间。
- 所有投资相关回复追加“不构成投资建议”。

外层继续使用项目统一 `ApiResponse`，并增加 `requestId` 便于审计回查。

## 9. 后端设计

推荐包：

```text
com.quant.trade.agent
├── config
├── security
├── controller
├── service
├── manager
├── dao
├── model
├── dto
├── vo
├── convert
├── constant
└── enums
```

技术要求：

- `spring-boot-starter-security` 只对 Agent 和 Agent OpenAPI 路径建立专用安全边界，现有前端 API 保持兼容。
- 使用 `springdoc-openapi-starter-webmvc-api`，只生成 `agent-v1` 分组，不引入生产 Swagger UI。
- `QTA_AGENT_ENABLED=false` 默认关闭。
- Token 只从环境变量或 SecretRef 注入；长度不足时拒绝启用；比较使用常量时间算法。
- 限流按 Agent Client/Key，默认 60 次/分钟；Provider 外联查询使用更低额度。
- 新增 Flyway migration 和 MyBatis XML 保存脱敏审计。
- 新错误使用 `ErrorCodeEnum`；常量进入对应 constant；转换使用 MapStruct。

审计至少记录：

```text
requestId、clientId/keyId、QQ OpenID 摘要、operationCode、
method、path、参数摘要、HTTP 状态、业务错误码、结果条数、
耗时、requestedAt、completedAt
```

严禁记录 Token、Longbridge 凭据、完整请求/响应、异常堆栈或完整持仓明细。

## 10. OpenClaw Plugin 与 Skill

建议放在后端仓库：

```text
integrations/openclaw/qta-assistant/
├── openclaw.plugin.json
├── package.json
├── tsconfig.json
├── src/
│   ├── index.ts
│   ├── client/qta-client.ts
│   ├── schemas/
│   ├── tools/read-tools.ts
│   ├── formatter/result-formatter.ts
│   └── policy/sender-policy.ts
├── skills/qta-assistant/
│   ├── SKILL.md
│   └── references/
│       ├── capabilities.md
│       ├── market-data.md
│       └── troubleshooting.md
├── openapi/qta-agent-openapi.json
└── test/
```

插件职责：

- 使用当前官方 `defineToolPlugin` + TypeBox 静态声明少量固定工具，校验参数、设置超时、裁剪结果和翻译错误。
- 使用 TypeScript ESM；`typebox` 放在运行时 dependencies；`openclaw` peer dependency 不低于官方 Tool Plugin SDK 要求。
- 只读 GET 遇到超时、502、503 最多重试一次；4xx 不重试。
- 连接超时 2 秒，总超时 10 秒；连续失败短时熔断。
- Token 通过插件配置的环境变量或 SecretRef 引用，不进入 manifest。
- QQ 私聊使用 OpenID allowlist；群聊默认关闭；再通过 `toolsBySender` 做第二层限制。
- Skill 只描述工具路由和边界，业务事实始终从 API 获取。

## 11. 第二阶段受控操作（仅设计，不在本轮实现）

候选工具：

- 运行一个已存在的采集计划。
- 重试或收敛一个失败任务。
- 启用/停用一个已有计划。
- 立即采集一个已配置的板块榜单。

必须先完成：

1. 操作工具默认不可见，需显式 allow。
2. 预检返回 actionId、影响范围、预计调用量、风险和过期时间。
3. 用户明确确认后使用一次性令牌执行。
4. `Idempotency-Key` 绑定操作与参数摘要。
5. 审批过期、身份不明、重复确认或载荷变化全部拒绝。

## 12. 测试与验收

### 12.1 无真实密钥即可完成

- 后端全量测试和 package。
- OpenAPI 只包含 Agent GET 路径，operationId 唯一，无 Token 字段和写接口。
- Agent 关闭、无 Token、错误 Token、正确 Token、超限 429。
- 成功和失败调用均有脱敏审计，Token 不入库不进日志。
- Fake Provider 覆盖正常、超时、401、403、429、500、空结果。
- OpenClaw 插件 `plugin:build`、`plugin:validate`、test；参数、超时、错误翻译、结果裁剪通过。
- mock QTA 服务下完成 Tool Plugin 本地调用闭环。
- 前端建设看板同步并通过 typecheck、lint、test、build。

### 12.2 部署后人工验收

- 公网不能访问 Agent API、Agent OpenAPI、Swagger UI、Actuator 和后端直连端口。
- 指定 QQ OpenID 可查询，其他用户和群聊被拒绝。
- “今天采集正常吗”必须返回数量、最后成功时间、水位和证据 ID。
- Provider 不可用、数据为空和数据过期时语义正确。
- OpenClaw 或 QQ 中断不影响后端采集任务。
- P1.6 至少一个有权限市场完成真实两个时间桶和重复桶幂等验收。

## 13. 完成定义

第一期只有在代码、测试、OpenAPI、插件、配置模板、部署手册、接口文档、建设看板和交接文档全部完成后，才能标记“代码交付完成”。服务器网络阻断和 QQ 真实链路未验证时，只能标为“待部署验收”，不得写成生产完成。

## 14. 官方参考

- ZCode Goal：<https://zcode.z.ai/cn/docs/goal>
- OpenClaw Tool Plugin：<https://docs.openclaw.ai/plugins/tool-plugins>
- OpenClaw Plugin Manifest：<https://docs.openclaw.ai/plugins/manifest>
- OpenClaw Plugin Permission：<https://docs.openclaw.ai/plugins/plugin-permission-requests>
- springdoc 兼容说明：<https://springdoc.org/faq.html>
