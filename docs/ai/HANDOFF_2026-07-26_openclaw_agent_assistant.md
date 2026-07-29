# Task Handoff: Agent 只读助手（重构后）

> 状态：代码交付完成，待部署验收。可无缝接手。

## Task

- Goal：按照 ADR-0011 和设计文档实现安全的 Agent 只读 API + OpenClaw Tool Plugin。
- Repo：后端 `/Users/joker/code/quant-trading-assistant`；前端 `/Users/joker/code/quant-trading-assistant-web`。
- 基线：后端 `76423df`，前端 `4898a88`。

## 已完成

1. ✅ 删除旧 openclaw 模块（偏离设计）。
2. ✅ 新增 `com.quant.trade.agent` 模块（config/security/controller/service/dao/model/vo）。
3. ✅ Spring Security（只保护 `/api/v1/agent/**`，现有 API 兼容）。
4. ✅ springdoc agent-v1 分组（只扫描 agent.controller，Swagger UI 关闭）。
5. ✅ Flyway V16 `agent_api_audit_log` 持久化脱敏审计。
6. ✅ 8 个固定 GET 端点 + capabilities + TrustedAnswer 可信回答契约。
7. ✅ AgentQueryService 复用现有 Service，不直接访问 Mapper。
8. ✅ OpenClaw Tool Plugin（`integrations/openclaw/qta-assistant/`，8 tools，TypeBox，超时重试，裁剪，OpenID 白名单，SKILL.md）。
9. ✅ 测试：后端 313 tests（含 agent 14）+ 插件 12 tests + plugin:build + plugin:validate。

## 门禁结果

- `./mvnw test`: **313 tests / 0 failures**
- `./mvnw package`: BUILD SUCCESS
- `git diff --check`: 通过
- 插件 `npm test`: **12 tests passed**
- 插件 `npm run plugin:build`: OK
- 插件 `npm run plugin:validate`: OK
- 前端：待跑（本轮无前端代码改动，但需确认回归）

## 部署验收待办

1. 服务器绑定回环或 Docker 私网。
2. Nginx deny `/api/v1/agent/**`、`/v3/api-docs/**`、Swagger UI、Actuator。
3. 配置 `QTA_AGENT_TOKEN`（32+ 字符随机串）。
4. 配置 `QTA_AGENT_ALLOWED_OPEN_IDS`（QQ OpenID 白名单）。
5. OpenClaw `npm install` + `plugins install` + `plugins enable`。
6. QQ 私聊测试 E2E。
7. 真实 Longbridge 行情联调。

## 关键设计决策

- 使用 Spring Security `SecurityFilterChain` 而非 Servlet Filter（解决事务和兼容性问题）。
- `AgentTokenAuthFilter` 使用 `UsernamePasswordAuthenticationToken` 携带 `ROLE_AGENT`。
- 审计持久化到 MySQL（Flyway V16），不仅内存。
- TrustedAnswer 包含 freshnessStatus/evidence/warnings，区分空/旧/失败/不可用。
- 插件 TypeBox 1.x（非 0.x），ESM，`typebox` 在 dependencies，`openclaw` peer dependency。

## 下一步

- 运行前端门禁确认回归。
- 部署服务器后执行部署验收清单。
