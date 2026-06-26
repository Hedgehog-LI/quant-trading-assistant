# Claude Code Project Context

本仓库是用户自建的 Java 后端量化交易辅助系统。Claude Code 接手时请先阅读：

1. `AGENTS.md`
2. `docs/AI_DEVELOPMENT_INDEX.md`
3. `docs/BUILD_CHECKLIST.md`
4. `docs/CURRENT_ARCHITECTURE_AND_MODULES.md`
5. `docs/CONVERSATION_HANDOFF.md`
6. `docs/AI_HANDOFF.md`
7. `docs/PRODUCT_BLUEPRINT.md`
8. `docs/prompts/CODEX_CLAUDE_PROMPTS.md`

如果当前工具支持项目级 skills，按任务类型读取：

- 产品设计：`.claude/skills/qta-product-design/SKILL.md`
- 后端实现：`.claude/skills/qta-backend-implementation/SKILL.md`
- 前端实现：`.claude/skills/qta-frontend-implementation/SKILL.md`
- 质量验收：`.claude/skills/qta-quality-acceptance/SKILL.md`

如果任务是开发周一可用的 Today MVP，请继续阅读：

9. `docs/claude/BACKEND_TODAY_MVP_IMPLEMENTATION_MANUAL.md`
10. `docs/claude/FRONTEND_MVP_ARCHITECTURE_AND_CLAUDE_MANUAL.md`
11. `docs/claude/CLAUDE_CODE_EXECUTION_GUIDE.md`

## 一句话目标

用 Java + Spring Boot + MySQL 做一个本地运行、可部署到服务器、可持续积累数据的交易辅助系统。它只提供数据、指标、信号、回测、风控、复盘，不做自动下单。

## 重要边界

- 不连接真实券商账户。
- 不保存任何真实交易密钥、券商密码、交易密码。
- 不生成“稳赚”“必涨”“无风险”之类结论。
- 所有买卖提示都应表达为“辅助信号 + 风险提示 + 人工确认”。

## 当前状态

以 `docs/BUILD_CHECKLIST.md` 和 `docs/CURRENT_ARCHITECTURE_AND_MODULES.md` 为准。当前已实现 Today MVP、交易账本和一批后端 REST API；下一阶段重点是“持仓快照 DB + 手工录入 + 历史查询”。

## 开发偏好

- 用户是 Java 开发者，优先使用清晰的 Java 分层结构。
- 前端可以后续生成，当前优先后端 API 和数据模型。
- Python 量化库可以后续通过 REST/CLI 适配，不要直接把本项目变成 Python 项目。
- v0.1 以单体 Spring Boot 为主，避免微服务化。
