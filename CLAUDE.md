# Claude Code Project Context

本仓库是用户自建的 Java 后端量化交易辅助系统。Claude Code 接手时请先阅读：

1. `AGENTS.md`
2. `docs/CONVERSATION_HANDOFF.md`
3. `docs/AI_HANDOFF.md`
4. `docs/ARCHITECTURE.md`
5. `docs/DEVELOPMENT_ROADMAP.md`
6. `docs/prompts/CODEX_CLAUDE_PROMPTS.md`

如果任务是开发周一可用的 Today MVP，请继续阅读：

7. `docs/claude/BACKEND_TODAY_MVP_IMPLEMENTATION_MANUAL.md`
8. `docs/claude/FRONTEND_MVP_ARCHITECTURE_AND_CLAUDE_MANUAL.md`
9. `docs/claude/CLAUDE_CODE_EXECUTION_GUIDE.md`

## 一句话目标

用 Java + Spring Boot + MySQL 做一个本地运行、可部署到服务器、可持续积累数据的交易辅助系统。它只提供数据、指标、信号、回测、风控、复盘，不做自动下单。

## 重要边界

- 不连接真实券商账户。
- 不保存任何真实交易密钥、券商密码、交易密码。
- 不生成“稳赚”“必涨”“无风险”之类结论。
- 所有买卖提示都应表达为“辅助信号 + 风险提示 + 人工确认”。

## 当前状态

- Spring Boot 项目已经初始化。
- Java 版本为 17。
- 数据库选型为 MySQL 8.4。
- Flyway 已接入，目前只有初始化 marker 表。
- Docker Compose 已接入 app + mysql。
- 业务模块尚未开始开发。

## 开发偏好

- 用户是 Java 开发者，优先使用清晰的 Java 分层结构。
- 前端可以后续生成，当前优先后端 API 和数据模型。
- Python 量化库可以后续通过 REST/CLI 适配，不要直接把本项目变成 Python 项目。
- v0.1 以单体 Spring Boot 为主，避免微服务化。
