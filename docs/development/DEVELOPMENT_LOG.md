# Development Log

> 按版本追加开发记录。每条：目标 / 范围 / 前后端改动 / 接口变化 / 测试结果 / 产品决策 / 遗留问题 / 关联文档。**不粘贴命令流水和聊天全文。** 新条目用 `docs/templates/DEVELOPMENT_LOG_TEMPLATE.md`。

---

## 2026-07-10 — LongPort 只读行情源产品与架构设计

- **目标**：研究 LongPort/长桥 OpenAPI 是否适合接入 A 股行情，并沉淀下一轮前后端开发设计。
- **范围**：只做产品/架构/文档设计，不改业务实现代码，不接真实交易能力。
- **发现**：
  - 代码事实已包含 `marketdata` 模块、V5/V6、`stock_basic`、`stock_daily_bar`、CSV 日 K 导入和 `/api/v1/market-data/*` 基础接口。
  - 部分文档仍把行情基础标为规划，已在本轮同步。
  - LongPort 能力覆盖实时行情、历史 K 线、MCP/SDK，但 MCP 也暴露交易/账户能力，必须通过 ADR 限定 quote-only。
- **产品决策**：
  - LongPort 只作为只读行情 provider。
  - 最新价进入 `stock_quote_snapshot`，历史日 K 进入 `stock_daily_bar(data_source=LONGPORT)`。
  - 外部行情不得覆盖 `portfolio_price_snapshot` 手工当前价。
  - 异常提醒先做数据质量，再做量价观察，不输出买卖建议。
- **新增文档**：
  - `features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`
  - `features/MARKET_ALERT_RULES_DESIGN.md`
  - `development/2026-07-10-longport-market-data-research.md`
  - `decisions/ADR-0008-longport-quote-only-provider.md`
  - `api/MARKET_DATA_API.md`
  - `prompts/LONGPORT_MARKET_DATA_CLAUDE_PROMPT.md`
- **同步文档**：`PRODUCT_BLUEPRINT.md`、`BUILD_CHECKLIST.md`、`CURRENT_ARCHITECTURE_AND_MODULES.md`、`DATABASE_DESIGN.md`、`api/API_INDEX.md`、`AI_HANDOFF.md`、`decisions/ADR_INDEX.md`。

---

## 2026-07-06 — 生产环境实测验证

- **目标**：验证生产 Nginx → 后端 → MySQL 链路，确认 production-data-mode 真实状态。
- **验证（只读 GET）**：
  - `http://129.204.169.155:18080/` 首页 → HTTP 200。
  - `/api/v1/watchlist` → `success=true, data=[]`。
  - `/api/v1/trade-plans` → `success=true, data=[]`。
  - `/api/v1/dashboard/today` → `success=true`，含完整 date/todos/pendingReviewJournals 数据（1 条 AAPL PENDING 交易）。
- **结论**：生产同源 /api/v1 + Nginx 反代 + Docker qta-server + MySQL 链路**实测通过**。production-data-mode 升级为 DONE/M4。
- **关联**：`acceptance/ACCEPTANCE_LOG.md`、前端 `buildStatusData.ts`。

---

## 2026-07 — 建设看板状态同步与发布收口

- **目标**：让建设看板与 v0.1.1 已验收事实、BUILD_CHECKLIST、PRODUCT_BLUEPRINT 完全一致。
- **范围**：前端看板数据 + 同步机制，不改业务代码/DB。
- **改动**：
  - `buildStatusData.ts` 重写：修正 6 类过期节点（pnl-explainability target、portfolio-pnl IN_PROGRESS→DONE、production-data-mode RISK→DONE、ai-collaboration "已推送"→"已沉淀"、trade-loop/position-snapshot nextActions）；新增 `market-data-foundation` P1 一级节点（stock-basic/daily-bar-import/market-data-provider）；`ai-input` P1→P2；`daily-bar-import` 从 quant-analysis 移入行情基础。
  - `pages/build-status.tsx` 加看板基线提示（v0.1.1 / 2026-07-06 / 与 BUILD_CHECKLIST 同步）。
  - `useBuildStatus` selectedId 初始 `null`（进入/刷新不默认打开抽屉）。
  - `production-data-mode` currentEvidence 分两条（同源 /api/v1 + curl 链路 / mock 4 页面 Playwright）。
  - 同步机制：`DEVELOPMENT_WORKFLOW` + `qta-context-bootstrap` 加 buildStatusData 同步规则；`BUILD_STATUS_BOARD_DESIGN` 标初始基线。
  - 口径统一：BUILD_CHECKLIST/PRODUCT_BLUEPRINT/buildStatusData "证券主数据**与**行情基础"。
- **测试**：`buildStatusData.test.ts` 重写（v0.1.1 DONE/M4、snapshot-comparison 100%、market-data-foundation P1、ai-input 非 P1、无过期下一步、一级分类含行情基础）；新增 `useBuildStatus.test.ts`（初始未选中/选择/关闭）。
- **验收**：后端 121、前端 191 测试通过；浏览器 /build-status 控制台 0 deprecated/error；基线 + P1 行情基础 + 节点显示；production-data-mode 降级 RISK/M3（生产 Nginx 反代未实测，不与"已验证"矛盾）。
- **关联文档**：`BUILD_CHECKLIST.md`、`PRODUCT_BLUEPRINT.md`、`acceptance/ACCEPTANCE_LOG.md`。

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
