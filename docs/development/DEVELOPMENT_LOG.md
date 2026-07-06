# Development Log

> 按版本追加开发记录。每条：目标 / 范围 / 前后端改动 / 接口变化 / 测试结果 / 产品决策 / 遗留问题 / 关联文档。**不粘贴命令流水和聊天全文。** 新条目用 `docs/templates/DEVELOPMENT_LOG_TEMPLATE.md`。

---

## 2026-07 — 文档体系治理与上下文加载 Skill

- **目标**：建立可自洽的文档体系，让任意 AI 新会话不依赖历史聊天即可继续开发。
- **范围**：纯文档 + 项目级 skill，**不改业务代码、不改 DB migration**。
- **改动**：
  - 新建：`AI_DEVELOPMENT_INDEX`（路由型）、`DEVELOPMENT_WORKFLOW`、`api/API_INDEX`、`mock/MOCK_REMOTE_CONTRACT`、`development/DEVELOPMENT_LOG`、`decisions/ADR_INDEX`+7 ADR、`acceptance/ACCEPTANCE_LOG`、`templates/` 5 模板、`.claude/skills/qta-context-bootstrap`。
  - 治理：`CLAUDE.md` 删旧必读清单 + Today MVP 指令；`AGENTS.md` 下一阶段优先级重写（删早期建表/指标/策略计划）；`DEVELOPMENT_ROADMAP` 重写（v0.1.1 已完成 + 下一阶段证券主数据，删 Entity/Repository/创建前端）；`FRONTEND_ARCHITECTURE` 按实际 React 项目重写（删 `/api/risk-alerts` 等不存在接口）；`CONVERSATION_HANDOFF` 精简为 Historical；10 个早期文档加 Historical 标记。
  - 契约修正：`MOCK_REMOTE_CONTRACT` 物理 key 带 `qta:` 前缀 + Risk Calculator 前端纯函数（未接 remote adapter）；`API_INDEX` Portfolio 完整路径 `/api/v1/portfolio/positions` 等；`PRODUCT_BLUEPRINT` v0.1.1 "待开发"→"已完成"。
  - 信息真实性优先级写入 `AI_DEVELOPMENT_INDEX §2` + `DEVELOPMENT_WORKFLOW`。
- **测试结果**：后端 `./mvnw test` 121 通过；前端 typecheck/lint/test/build 全绿；`git diff --check` 两仓库干净；grep 主流程无 JPA/Repository 冲突、无旧测试数残留、Controller 路径与 API_INDEX 一致、localStorageClient `qta:` 前缀与 MOCK_REMOTE_CONTRACT 一致。
- **产品决策**：Historical 文档原文保留（不删），仅顶部标记 + 主索引降级；单一事实来源（API_INDEX/MOCK_REMOTE_CONTRACT/ADR/DEVELOPMENT_LOG/ACCEPTANCE_LOG）。
- **关联文档**：`AI_DEVELOPMENT_INDEX.md`、`DEVELOPMENT_WORKFLOW.md`、`acceptance/ACCEPTANCE_LOG.md`。

---

## v0.1.1 — 基础交易闭环优化（含两轮质量收尾 + 最终交付）

**目标**：把计划 / 交易 / 账本 / 快照 / 复盘 / 工作台串成可信、可追溯闭环。

**范围**：6 大功能 + 两轮收尾 + 最终交付（路由 / 解绑 / 历史日期 / FIFO 对齐 / Antd deprecated / 文案 / 文档治理）。

**后端改动**：
- 新增 `PositionSnapshotComparisonManager`（纯计算）、`PositionSnapshotReconciliationManager`（FIFO 对账，复用 `FifoCalculatorManager`）、`DashboardTodoVO` + `DashboardTodoCodeEnum` / `DashboardTodoLevelEnum`、`SnapshotChangeTypeEnum` / `ReconciliationStatusEnum`、6 个对比/对账 VO。
- `TradeJournalManager`：`planId` 关联校验 + `unlinkPlan` 三态；`recalculateReviewStatus`。
- `ReviewManager`：扫全表解析 `linked_journal_ids`（CSV，容忍脏数据 + 去重）；删除保护。
- `DashboardManager`：`buildTodos(date)`（6 类待办，历史日期口径 `trade_date<=date`，STALE 用 `getLatestConfirmedUpTo`）。
- `TradeJournalMapper`：`selectAllOrderedUpTo`（截止时点 FIFO）、`selectByReviewStatusUpTo` / `countByReviewStatusUpTo`（历史日期）。
- `PositionSnapshotMapper`：`selectLatestConfirmedUpTo`。
- 5 个新错误码 + `MessageConstants` 文案。
- **未新增表，未修改 V1-V4 migration。**

**前端改动**：
- `TradeJournalForm` 计划选择器 + 自动带入；`PositionSnapshotInspectionDrawer`（对比 + 对账 + 成本列 + 横向滚动）；`DashboardTodos`（ul/li，无 List）；`dashboardApi`（remote 用后端聚合，mock 同口径）；`settingsApi`（localhost 防误配 + 测试连接）；`positionSnapshotReconciliation`（FIFO 含 totalFee + 稳定排序 + 超卖停止）；`DataManagement`（动态文案 + 导出范围说明）。
- Antd 6.4 deprecated 全清理：`Alert message→title`、`Spin tip→description`、`Space direction→orientation`、`Drawer width→size`。

**接口变化**：
- 新增 `GET /position-snapshots/comparison`、`GET /position-snapshots/{id}/reconciliation`。
- `GET /dashboard/today` 响应增 `todos`（旧字段保留）；待办 `targetPath` 全 `/journal*` 或 `/position-snapshots`（复数）。
- `PUT /trade-journals/{id}` 增 `unlinkPlan`（三态）；响应增 `planDate/planStatus`。
- `reviews` 新增/编辑/删除后回算 reviewStatus；`trade-journals/{id}` 删除前引用保护。

**测试结果**：后端 `./mvnw test` = 121 通过；前端 `typecheck/lint/test/build` = 179 测试通过；Docker 冷构建 + curl 端到端 + Playwright 4 页面控制台 `DEPRECATED_WARNINGS=0, CONSOLE_ERRORS=0`。详见 `../acceptance/ACCEPTANCE_LOG.md`。

**产品决策**：对账只读不改流水；TRADE_AGAINST_PLAN 含 `followedPlan=false`；历史日期统一 `trade_date<=date`；超卖视为 QUANTITY_MISMATCH；mock FIFO 必须复刻后端（含 totalFee）；JSON 导出仅 localStorage 不含 MySQL。

**遗留问题**：浏览器自动化目视仍建议手动复核（Playwright 已验证控制台）；联调测试数据未清理（`TEST01/TEST01C/TEST02/UNLINK1/CMP1/HISTFX1/FUTFX1/OVERFX1` 等）。

**关联文档**：`../features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md`、`../api/POSITION_SNAPSHOT_API.md`、`../api/API_INDEX.md`、`../mock/MOCK_REMOTE_CONTRACT.md`、`../BUILD_CHECKLIST.md`、`../acceptance/ACCEPTANCE_LOG.md`。

---

## v0.1.0 — Today MVP + 交易账本 + 持仓快照

**目标**：本地运行的基础交易记录工具。

**范围**：Dashboard / Watchlist / Trade Plan / Risk / Trade Journal / Review + Portfolio FIFO 账本 + Position Snapshot。

**后端**：Spring Boot 3.5 + MyBatis XML + MapStruct + Flyway V1-V4 + MySQL 8.4 + H2 test；分层 controller/service/manager/dao/model/dto/vo/convert；`ApiResponse` + `ErrorCodeEnum` + `BusinessException`。

**前端**：React 19 + Vite + TypeScript + Ant Design 6 + feature-based + mock/remote 双模式 + `shared/api/client` 动态 baseURL。

**接口**：见 `../api/API_INDEX.md`。

**测试**：后端基础测试 + 前端基础测试（数量低于 v0.1.1，已被覆盖）。

**关联文档**：`../API_TODAY_MVP.md`、`../api/PORTFOLIO_API.md`、`../api/POSITION_SNAPSHOT_API.md`、`../DATABASE_DESIGN.md`、`../CURRENT_ARCHITECTURE_AND_MODULES.md`。
