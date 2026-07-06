# Claude Code Project Context


## 新会话流程（必读）

1. 启用 skill `.claude/skills/qta-context-bootstrap`（分阶段加载上下文）。
2. 读 `docs/AI_DEVELOPMENT_INDEX.md`（路由）+ `docs/DEVELOPMENT_WORKFLOW.md`（流程与文档同步规则）。
3. 冲突裁决按 `AI_DEVELOPMENT_INDEX.md §2`：migration+代码+测试 > 架构事实 > API/DB > 开发/验收日志 > 历史交接。

## 开发结束必做

- 执行 `DEVELOPMENT_WORKFLOW.md §2` 文档同步检查（API/DB/Mock/产品/ADR/DEVELOPMENT_LOG/ACCEPTANCE_LOG）。
- API 变化必须同步 `docs/api/API_INDEX.md` + `docs/mock/MOCK_REMOTE_CONTRACT.md`。
- 重要架构决策必须新增 ADR（`docs/decisions/`）。
- 只有实际验收通过才能勾选 `docs/BUILD_CHECKLIST.md`；不自动 commit/push。

入口与路由统一在 `docs/AI_DEVELOPMENT_INDEX.md`（含 Historical 文档清单与任务类型路由）；本文件不再维护重复必读列表。Historical 文档（`CONVERSATION_HANDOFF.md`、`docs/claude/*`、`docs/prompts/*` 等）**不在主流程**，无需读取。

## 一句话目标

用 Java + Spring Boot + MySQL 做一个本地运行、可部署到服务器、可持续积累数据的交易辅助系统。它只提供数据、指标、信号、回测、风控、复盘，不做自动下单。

## 重要边界

- 不连接真实券商账户。
- 不保存任何真实交易密钥、券商密码、交易密码。
- 不生成“稳赚”“必涨”“无风险”之类结论。
- 所有买卖提示都应表达为“辅助信号 + 风险提示 + 人工确认”。

## 当前状态

v0.1.1 基础交易闭环优化**已完成并验收**（2026-07）。当前事实以 `docs/BUILD_CHECKLIST.md` + `docs/CURRENT_ARCHITECTURE_AND_MODULES.md` + `docs/AI_HANDOFF.md` 为准。下一阶段：证券主数据、统一证券标识、CSV 日 K 导入与行情边界（见 `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`）；AI 图片识别暂缓。

## 开发偏好

- 用户是 Java 开发者，优先使用清晰的 Java 分层结构。
- 前后端均已存在，按任务类型进入对应仓库开发（后端 `/Users/joker/code/quant-trading-assistant`、前端 `/Users/joker/code/quant-trading-assistant-web`）。
- Python 量化库可以后续通过 REST/CLI 适配，不要直接把本项目变成 Python 项目。
- v0.1 以单体 Spring Boot 为主，避免微服务化。
