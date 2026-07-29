# ADR-0011: OpenClaw 使用专用 Agent Facade 与 Tool Plugin

- 状态：Accepted
- 日期：2026-07-26
- 决策者：项目维护者与产品、安全、后端、OpenClaw、QA 专家组

## 背景

QTA 已有查询、修改、删除和任务执行 API。用户希望通过服务器上的 OpenClaw 和 QQ 远程查询系统，但当前后端没有统一鉴权、Agent 审计和 OpenAPI 白名单。把现有 Controller 或完整 Swagger 直接交给模型会同时暴露敏感财务数据和状态变更能力。

## 决策

1. 新增 `/api/v1/agent/**` 专用聚合门面，只复用现有 Service。
2. OpenAPI 只描述 Agent 门面；OpenClaw 通过原生 Tool Plugin 注册固定工具。
3. 第一期仅提供只读 GET 工具，使用独立 Bearer Token、限流、审计和 QQ OpenID 白名单。
4. Agent API 只允许服务器回环或受控 Docker 网络访问，公网 Nginx 明确阻断。
5. 写操作作为第二阶段独立建设，必须具备工具可见性白名单、逐次审批、预检、一次性确认和幂等。

## 原因

- 最小权限：模型只看到用户真正需要的少量能力。
- 业务一致：聚合接口复用现有 Service，不复制行情、持仓和交易口径。
- 可审计：每次 QQ 工具调用可回查 requestId、调用人、证据和结果。
- 可演进：OpenAPI 可用于契约测试，Tool Plugin 可独立控制模型上下文和结果大小。
- 可止损：OpenClaw、QQ 或模型异常不会获得数据库、Shell、Docker或全量业务 API。

## 影响

- 后端新增 Agent 模块、Spring Security、springdoc、审计 migration 和测试。
- 仓库新增 OpenClaw Tool Plugin 与渐进式 Skill。
- 生产部署需调整后端监听和 Nginx deny 规则。
- 现有普通前端 API 在本阶段保持兼容；Agent 安全链不能误拦截现有页面请求。
- 尚未实现的 Agent API 不登记到已实现 `API_INDEX`，实现后再同步。

## 替代方案

- 直接导入全量 Swagger：拒绝，权限面过大且会暴露写接口。
- 让 OpenClaw 直接访问 MySQL：拒绝，绕过业务规则和审计。
- 让模型使用通用 curl/Shell：拒绝，无法建立稳定最小权限边界。
- 只依赖服务器回环、不做鉴权：拒绝，同机进程和错误代理配置仍可越权。
- 第一期同时开放写操作：拒绝，QQ 重发、提示注入和模型误判风险尚未被确认与幂等机制覆盖。

## 关联

- `../features/OPENCLAW_AGENT_ASSISTANT_DESIGN.md`
- `../development/OPENCLAW_AGENT_ASSISTANT_IMPLEMENTATION_PLAN.md`
- `ADR-0006-no-auto-trading-no-broker.md`
- `ADR-0008-longport-quote-only-provider.md`
