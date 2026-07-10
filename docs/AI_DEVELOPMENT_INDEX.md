# AI Development Index

> 唯一总入口。Claude Code / Codex / 其他 AI 接手时**先读本文件**，再按任务类型路由。本文件只做导航，不堆细节；详细事实在各专项文档。

## 1. 项目一句话

Quant Trading Assistant：本地优先、可服务器部署的交易辅助系统（自选股 / 计划 / 交易 / 账本 / 持仓快照 / 复盘 / 风控 / 工作台）。**不自动交易、不连接券商、不保存密钥、不承诺收益。**

## 2. 信息真实性优先级（冲突裁决顺序，从高到低）

1. **Flyway migration + 实际代码 + 自动化测试**（最高事实来源）
2. `CURRENT_ARCHITECTURE_AND_MODULES.md` + `BUILD_CHECKLIST.md`
3. API / 数据库 / 产品设计文档
4. `development/DEVELOPMENT_LOG.md` + `acceptance/ACCEPTANCE_LOG.md`
5. 历史交接 / 历史提示词（仅参考，**不覆盖当前事实**）

**禁止**用旧聊天或旧文档覆盖当前代码事实。

## 3. 新会话推荐阅读顺序

1. 启用项目 skill：`.claude/skills/qta-context-bootstrap`（分阶段加载，避免一次读全部 docs）。
2. 读入口：`AGENTS.md` → `CLAUDE.md` → 本文件 → `AI_HANDOFF.md`。
3. 按下方"任务类型路由"只读必要文档。

## 4. 任务类型路由

| 任务类型 | 必读文档（除入口外） |
| --- | --- |
| 任意开发 | `BUILD_CHECKLIST.md`、`CURRENT_ARCHITECTURE_AND_MODULES.md`、`DEVELOPMENT_WORKFLOW.md` |
| 产品 / 功能设计 | `PRODUCT_BLUEPRINT.md`、`docs/features/<对应设计>.md`、`BUILD_CHECKLIST.md`；新功能用 `docs/templates/FEATURE_DESIGN_TEMPLATE.md` |
| LongPort / 行情 provider | `features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`、`features/MARKET_ALERT_RULES_DESIGN.md`、`api/MARKET_DATA_API.md`、`decisions/ADR-0008-longport-quote-only-provider.md` |
| 后端开发 | `api/API_INDEX.md`、对应 `api/*.md`、`DATABASE_DESIGN.md`、`decisions/ADR_INDEX.md`；启用 `qta-backend-implementation` skill |
| 前端开发 | `FRONTEND_ARCHITECTURE.md`、`mock/MOCK_REMOTE_CONTRACT.md`、对应 feature 设计；启用 `qta-frontend-implementation` skill |
| API 联调 | `api/API_INDEX.md`、对应 `api/*.md`、`mock/MOCK_REMOTE_CONTRACT.md` |
| Mock 开发 | `mock/MOCK_REMOTE_CONTRACT.md`、对应 `features/*/api/*Api.ts` |
| 测试验收 | `BUILD_CHECKLIST.md`、`acceptance/ACCEPTANCE_LOG.md`；启用 `qta-quality-acceptance` skill |
| 部署 / 修复 | `DEVELOPMENT_WORKFLOW.md`（部署段）、`docker-compose.yml`、`src/main/resources/application-*.properties` |

## 5. 单一事实来源映射（避免多份维护同一事实）

| 事实 | 唯一来源 |
| --- | --- |
| 已实现 API 清单 | `api/API_INDEX.md`（链接到 `api/*.md`、`API_TODAY_MVP.md`，不复制定义） |
| 当前架构与模块 | `CURRENT_ARCHITECTURE_AND_MODULES.md` |
| 建设进度 / 勾选 | `BUILD_CHECKLIST.md` |
| Mock / Remote 契约 | `mock/MOCK_REMOTE_CONTRACT.md` |
| 架构决策 | `decisions/ADR_INDEX.md` + `decisions/ADR-XXXX-*.md` |
| 开发历史 | `development/DEVELOPMENT_LOG.md` |
| 验收历史 | `acceptance/ACCEPTANCE_LOG.md` |
| 开发流程与同步规则 | `DEVELOPMENT_WORKFLOW.md` |
| 当前接手事实 | `AI_HANDOFF.md`（精简，历史进 DEVELOPMENT_LOG） |

## 6. Historical 文档（仅参考，不在主流程）

以下文档已标 Historical，新会话**不必读**，仅排查历史时参考：`CONVERSATION_HANDOFF.md`、`docs/claude/*`（早期 MVP 指南，含过时 JPA/Repository 表述）、`docs/prompts/*`（历史执行提示词）、`docs/ARCHITECTURE.md` / `BACKTEST_ENGINE_DESIGN.md` / `STRATEGY_PLUGIN_DESIGN.md`（早期未实现设计）、`docs/ai/SKILL_USAGE.md`。

## 7. 完成定义

**后端**：`./mvnw test` 通过；涉及部署时 `./mvnw package` 通过；新表用 Flyway migration；新接口更新 `api/API_INDEX.md` + 对应 `api/*.md`；不新增券商连接或密钥。

**前端**：`npm run typecheck` / `lint` / `test` / `build` 全过；文案不误导用户把 localStorage 当正式数据源。

**产品**：明确用户目标、范围、不做什么、数据模型、验收标准、风险。

**所有任务结束**：按 `DEVELOPMENT_WORKFLOW.md` 执行"开发结束文档同步检查"。
