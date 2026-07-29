# OpenClaw 远程只读助手实施计划

> 状态：待开发 · 基线：后端 `76423df`、前端 `4898a88` · 设计：`../features/OPENCLAW_AGENT_ASSISTANT_DESIGN.md`

## 1. Goal Mode 可验证目标

在不依赖真实 Longbridge 密钥、服务器权限或 QQ 凭据的前提下，完成 QTA 只读 Agent API、安全边界、审计、独立 OpenAPI、OpenClaw Tool Plugin、自动化测试和文档交付，使其具备部署后进行 QQ 白名单验收的条件。

## 2. 阶段与门禁

### G0 基线和上下文

- 输出 Task Context Manifest。
- 核对两仓库 git 状态，不覆盖用户改动。
- 只读设计、ADR、实施计划和直接相关代码。
- 修正 `AI_HANDOFF` 中 P1.6 `293 tests` 为验收日志事实 `299 tests`。

### G1 后端基础与安全

- 增加 `spring-boot-starter-security` 和兼容 Spring Boot 3.5 的 springdoc webmvc API starter，不引入 Swagger UI。
- 新增 `agent` 模块及 `QTA_AGENT_ENABLED=false` 默认配置。
- 新增 Agent Bearer Token、错误响应、限流、requestId 和脱敏审计。
- 新增下一号 Flyway migration 和 MyBatis XML；不得修改 V1-V15。
- 安全链只保护 Agent 与 Agent OpenAPI 路径，现有前端接口保持兼容。

门禁：关闭、缺失 Token、错误 Token、正确 Token、限流、审计测试全部通过。

### G2 只读聚合 API

- 实现设计中的 8 类只读工具能力。
- Controller 薄层；调用 Agent Service；复用现有业务 Service。
- 禁止 Agent Controller/Service 直接访问现有业务 Mapper。
- 统一数据时间、新鲜度、证据、警告和免责声明。
- 空、旧、失败、Provider 不可用语义明确。

门禁：Service/MockMvc 测试覆盖正常、空数据、旧数据、失败和边界 limit。

### G3 Agent OpenAPI

- 分组仅包含 `/api/v1/agent/**`。
- 固定唯一 operationId、Bearer scheme、参数约束和错误响应。
- 生产默认关闭 Swagger UI。
- 生成并保存插件使用的 OpenAPI JSON 快照。

门禁：契约测试确认无 POST/PUT/PATCH/DELETE、无非 Agent 路径、无 Token/密钥 Schema。

### G4 OpenClaw Tool Plugin

- 在 `integrations/openclaw/qta-assistant/` 建官方原生插件。
- 注册固定只读工具，不动态暴露全量 OpenAPI。
- Token 使用环境变量或 SecretRef；manifest 只含配置 schema。
- 支持参数校验、2 秒连接超时、10 秒总超时、只读一次重试、结果裁剪和错误翻译。
- 建渐进式 Skill，常规调用不加载完整 API 或开发日志。
- 提供 mock QTA 测试服务和单元/集成测试。

门禁：plugin build、validate、test 全部通过；mock 服务下工具调用闭环。

### G5 文档与建设看板

- 实现后新增 `docs/api/AGENT_ASSISTANT_API.md` 并登记 `API_INDEX`。
- 更新 `.env.example`、Docker/部署示例和 Nginx deny 示例，不写真实 Token。
- 更新数据库、架构、产品、BUILD_CHECKLIST、AI_HANDOFF、DEVELOPMENT_LOG。
- 追加实际执行结果到 ACCEPTANCE_LOG。
- 更新前端 `buildStatusData.ts` 与测试：只能标“代码交付”或“待部署验收”，不能提前标生产完成。
- 写本轮 compact handoff。

### G6 全量门禁

后端：

```bash
git diff --check
./mvnw test
./mvnw package
```

插件：

```bash
npm test
npm run plugin:build
npm run plugin:validate
```

前端：

```bash
npm run typecheck
npm run lint
npm run test
npm run build
```

安全扫描必须确认：

- Git diff 无真实 Token、Longbridge 凭据和私有地址。
- OpenAPI 无写接口和非 Agent Controller。
- 日志/审计无 Authorization 值和完整敏感响应。
- 现有普通 API 的兼容测试通过。

## 3. 生产验收（不作为本地 Goal 的外部阻塞）

本地代码交付后，服务器需另行完成：

1. QTA 后端只绑定回环或 Docker 私网。
2. 公网 Nginx deny Agent、OpenAPI、Swagger UI、Actuator。
3. OpenClaw 配置只读 Token SecretRef。
4. QQ 私聊只允许指定 OpenID，群聊关闭，`toolsBySender` 二次限制。
5. 指定 QQ 可查询；其他用户、错误 Token、公网访问均被拒绝。
6. P1.6 至少一个有权限市场跨两个交易时间桶验收，重复桶不新增批次。

服务器、QQ 或真实 Provider 不可用时，ZCode 不得伪造通过；记录为部署验收待办，但继续完成所有不依赖外部凭据的代码、测试和文档。

## 4. 明确禁止

- 不实现第二阶段写工具。
- 不为赶进度降低鉴权、审计或网络边界。
- 不读取或打印用户真实 `.env.longport`、Agent Token、QQ OpenID。
- 不自动 commit、push 或部署。
- 不无限循环测试；同一失败最多一轮直接修复和重跑，仍失败则写精确 blocker。

## 5. 完成定义

- G1-G6 全部通过。
- 所有新增代码和文档与实际结果一致。
- 前端能力矩阵同步。
- 工作区只剩本轮可解释改动。
- `docs/ai/HANDOFF_2026-07-26_openclaw_agent_assistant.md` 更新为可无缝接手状态。
