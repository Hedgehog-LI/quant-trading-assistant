# AI Development Index

> 本文件是后续 Claude Code、Codex、OpenClaw 接手本项目时的总入口。先读本文件，再按任务类型选择对应文档和 skill。

## 1. 项目一句话

Quant Trading Assistant 是一个本地优先、可服务器部署的交易辅助系统，用来记录自选股、交易计划、交易流水、交易账本、持仓快照、复盘和风控信息。

系统不做自动交易，不连接真实券商账户，不保存券商密码或交易 API Key，不输出稳赚或无风险结论。

## 2. 当前仓库

| 仓库 | 路径 | 职责 |
| --- | --- | --- |
| 后端 | `/Users/joker/code/quant-trading-assistant` | Spring Boot、MyBatis、Flyway、MySQL、REST API |
| 前端 | `/Users/joker/code/quant-trading-assistant-web` | React、Vite、TypeScript、Ant Design、mock/remote 双模式 |

后端文档统一沉淀在本仓库 `docs/` 下；前端开发也应先阅读本仓库的产品和架构文档，再进入前端仓库实现。

## 3. 必读顺序

若早期交接文档与 `docs/BUILD_CHECKLIST.md`、`docs/CURRENT_ARCHITECTURE_AND_MODULES.md` 冲突，以后两者记录的当前实现事实为准。

### 任意开发任务

1. `AGENTS.md`
2. `CLAUDE.md`
3. `docs/AI_DEVELOPMENT_INDEX.md`
4. `docs/BUILD_CHECKLIST.md`
5. `docs/CURRENT_ARCHITECTURE_AND_MODULES.md`

### 产品或功能设计任务

继续阅读：

1. `docs/PRODUCT_BLUEPRINT.md`
2. `docs/features/POSITION_SNAPSHOT_DESIGN.md`（涉及持仓快照时）

### 后端实现任务

继续阅读：

1. `docs/API_TODAY_MVP.md`
2. `docs/api/PORTFOLIO_API.md`
3. `docs/api/POSITION_SNAPSHOT_API.md`（涉及持仓快照时）
4. `docs/DATABASE_DESIGN.md`
5. `docs/CURRENT_ARCHITECTURE_AND_MODULES.md`

### 前端实现任务

继续阅读：

1. `docs/FRONTEND_ARCHITECTURE.md`
2. `docs/PRODUCT_BLUEPRINT.md`
3. `docs/CURRENT_ARCHITECTURE_AND_MODULES.md`

### 验收、修复、部署任务

继续阅读：

1. `docs/BUILD_CHECKLIST.md`
2. `docs/claude/CLAUDE_CODE_EXECUTION_GUIDE.md`
3. 相关 API 文档和部署 README

## 4. 项目内 skills

项目级 skills 位于 `.claude/skills/`：

| Skill | 用途 |
| --- | --- |
| `qta-product-design` | 把需求整理成 PRD、模块边界、数据模型、验收标准 |
| `qta-backend-implementation` | 后端分层、Flyway、MyBatis、REST API、测试实现 |
| `qta-frontend-implementation` | 前端页面、API adapter、mock/remote 双模式、UI 验收 |
| `qta-quality-acceptance` | 最终质量门禁、测试、构建、文档和产品验收 |

如果当前 AI 工具支持 skills，应优先启用对应 skill；如果不支持，直接读取对应 `SKILL.md`。

## 5. 当前最高优先级

1. 保证已实现的 Today MVP + 交易账本稳定可用。
2. 将核心业务数据默认落库到后端 DB；`localStorage` 只作为本地开发、离线兜底或迁移前数据源。
3. 保持“持仓快照”DB、API、手工录入、历史查询和双模式联调稳定可用。
4. 下一阶段做 GLM/OCR 图片识别，只作为自动填表生成草稿，不直接无确认入库。

## 6. 数据源原则

| 模式 | 用途 | 数据位置 |
| --- | --- | --- |
| 本地模式 / mock | 本地开发、断网演示、临时记录 | 浏览器 `localStorage` |
| 后端模式 / remote | 正式使用、服务器部署、跨设备一致数据 | 后端 MySQL |

正式部署时，核心业务数据应走 remote 模式并落库。图片识别、CSV 导入、手工录入都必须经过“草稿 -> 人工确认 -> 入库”的流程。

## 7. 每轮开发完成定义

后端任务完成前至少满足：

- `./mvnw test` 通过。
- 涉及打包或部署时 `./mvnw package` 通过。
- 新增表使用 Flyway migration。
- 新增接口更新 API 文档。
- 不新增真实券商连接或密钥存储。

前端任务完成前至少满足：

- `npm run typecheck` 通过。
- `npm run lint` 通过。
- `npm run test` 通过。
- `npm run build` 通过。
- 页面文案不误导用户把 localStorage 当成正式数据源。

产品任务完成前至少满足：

- 明确用户目标、范围、不做什么。
- 明确数据模型、页面流程、接口边界。
- 明确验收标准和风险提示。
