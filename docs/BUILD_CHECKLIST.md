# Build Checklist

> 本文件记录要做什么、已经做了什么、每轮开发怎样验收。状态应随代码变化持续更新。

## 1. 状态图例

- `[x]` 已完成并已基本验收。
- `[~]` 已实现但仍需补齐文档、体验、部署或边界。
- `[ ]` 未开始或只停留在设计。

## 2. 当前已完成

### 后端基础

- [x] Spring Boot 项目初始化。
- [x] Maven Wrapper。
- [x] Docker Compose + MySQL。
- [x] Flyway migration。
- [x] H2 test profile。
- [x] 统一 `ApiResponse`。
- [x] 全局异常处理。
- [x] MyBatis Mapper + XML SQL。

### Today MVP

- [x] Dashboard 今日工作台 API。
- [x] Watchlist 自选股 CRUD。
- [x] Trade Plan 交易计划 CRUD 和状态更新。
- [x] Risk Calculator 仓位计算。
- [x] Trade Journal 交易记录 CRUD 和复盘状态更新。
- [x] Review 盘后复盘 CRUD。
- [x] API 文档 `docs/API_TODAY_MVP.md`。

### Portfolio Ledger 交易账本

- [x] `trade_journal` 增加费用字段。
- [x] `portfolio_price_snapshot` 当前价快照表。
- [x] FIFO 持仓和已结算盈亏计算。
- [x] 持仓、已结算交易、汇总、单股详情 API。
- [x] 费用字段 null 归一和全量更新语义测试。
- [x] API 文档 `docs/api/PORTFOLIO_API.md`。

## 3. 当前需要补齐或保持同步

- [~] 前端 README 和页面文案必须准确描述 remote/localStorage 边界。
- [~] 生产部署推荐 remote 模式，`apiBaseUrl` 留空走同源 `/api/v1`。
- [~] 文档中旧的 JPA 表述应逐步修正为当前 MyBatis 实现事实。
- [~] 每个新增接口都要补 API 示例。
- [x] 前端“建设看板”已完成并通过 typecheck/lint/test/build 验收。

## 4. 已完成 P0: 建设看板

### 产品设计

- [x] 明确入口：左侧菜单放在 `设置` 下方。
- [x] 明确第一版数据来源：前端静态配置，不新增后端表。
- [x] 明确展示口径：成熟度 M0-M5、状态、优先级、下一步。
- [x] 产出设计文档：`docs/features/BUILD_STATUS_BOARD_DESIGN.md`。

### 前端

- [x] 新增菜单：建设看板。
- [x] 新增路由：`/build-status`。
- [x] 新增静态数据配置：`buildStatusData.ts`。
- [x] 新增总览卡片。
- [x] 新增理财能力条。
- [x] 新增建设树。
- [x] 新增节点详情抽屉。
- [x] 新增图例和筛选。
- [x] 增加测试或至少覆盖数据结构和关键渲染。

### 验收

- [x] 一眼能看到整体成熟度和当前最优先事项。
- [x] 能看到所有建设主线：基础设施、交易闭环、持仓盈亏、证券主数据与行情基础、智能录入、量化分析。
- [x] 能区分已完成、进行中、待开始、有风险、阻塞。
- [x] 能看到持仓快照 P0 已完成验收。
- [x] 页面不需要后端也能展示。

## 5. 已完成 P0: 持仓快照

### 产品设计

- [x] 明确持仓快照与交易账本的区别。
- [x] 明确手工录入优先，图片识别后置。
- [x] 明确草稿、确认、作废状态。
- [x] 产出设计文档：`docs/features/POSITION_SNAPSHOT_DESIGN.md`。

### 后端

- [x] 新增 Flyway migration：`portfolio_position_snapshot`。
- [x] 新增 Flyway migration：`portfolio_position_snapshot_item`。
- [x] 新增枚举：`SnapshotSourceTypeEnum`、`SnapshotStatusEnum`。
- [x] 新增 DTO / VO / DO / Mapper / XML。
- [x] 新增 Manager：校验、金额计算、状态规则。
- [x] 新增 Service：事务、保存草稿、确认、作废、查询。
- [x] 新增 Controller：REST API。
- [x] 新增测试：金额计算、状态流转、查询、删除/作废边界。
- [x] 更新 API 文档：`docs/api/POSITION_SNAPSHOT_API.md`。

### 前端

- [x] 新增菜单：持仓快照。
- [x] 新增快照列表页。
- [x] 新增手工录入/编辑表格。
- [x] 新增快照详情页或详情抽屉。
- [x] 新增最近一次快照卡片。
- [x] 支持 mock/localStorage 与 remote/REST API 双模式。
- [x] 增加测试：计算、API adapter、页面关键状态。

### 验收

- [x] 可以手工录入一批持仓并保存草稿。
- [x] 可以确认快照并落库。
- [x] 可以查询历史快照。
- [x] 可以查看某次快照详情。
- [x] 盈利红色、亏损绿色，符合 A 股习惯。
- [x] 已确认快照默认不可硬删除，只允许作废。

## 6. 已完成并验收: v0.1.1 基础交易闭环优化

设计基线：`docs/features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md`。

### 交易计划与交易记录

- [x] 交易记录表单支持选择交易计划。
- [x] 选择计划后自动带入股票、止损、止盈和计划仓位。
- [x] 后端校验计划存在、未取消且证券代码一致。
- [x] 一个计划允许关联多笔交易，不自动结束计划。
- [x] 列表或详情能看到关联计划。

### 复盘一致性

- [x] 被复盘引用的交易记录禁止直接删除。
- [x] 编辑复盘移除关联后重新计算交易复盘状态。
- [x] 删除复盘后重新计算受影响交易复盘状态。
- [x] 新增和更新复盘不得关联不存在的交易。

### 持仓比较与账本对账

- [x] 支持两个已确认快照的差异比较。
- [x] 支持新增、加仓、减仓、清仓、未变化五种类型。
- [x] 支持快照与指定时点 FIFO 理论持仓数量对账。
- [x] 对账结果只提示，不自动修改交易流水。
- [x] mock 与 remote 口径清晰且有测试。

### 工作台和连接防呆

- [x] 工作台展示待复盘、未关联计划、缺少止损、快照过期和对账差异。
- [x] remote 模式使用后端 Dashboard 聚合结果。
- [x] 生产页面禁止保存指向 localhost 的后端地址。
- [x] 设置页展示有效 API 地址并支持只读连接测试。
- [x] 不写死公网 IP，生产同域默认走 `/api/v1`。

### 验收

- [x] `./mvnw test` 和 `./mvnw package` 通过。
- [x] Docker 冷构建和后端健康检查通过。
- [x] 前端 typecheck、lint、test、build 通过。
- [x] 本地联调覆盖计划关联、复盘回算、快照比较和账本对账。
- [x] API、架构、README、建设看板和本 checklist 同步。

### 收尾修复（v0.1.1+ 质量收尾）

- [x] 快照待办 targetPath 统一为 `/position-snapshots`（修复 404）。
- [x] 交易记录支持解除计划关联（`unlinkPlan` 三态，`plan_id` 真正可置 NULL）。
- [x] Dashboard 历史日期口径统一（todos 与统计用请求 date，快照取截止 date 最新已确认）。
- [x] 本地模式持仓对账改真正 FIFO（多买/部分卖/全卖/超卖 + 平均成本），超卖不判 MATCHED。
- [x] 快照对比校验两份均 CONFIRMED 且基准严格早于目标（同一/反向拦截）。
- [x] TRADE_AGAINST_PLAN 待办加 `followedPlan=false` 触发条件，前后端口径一致。
- [x] 复盘历史脏数据（空段/非法/重复 ID）兼容，不抛 500。
- [x] 对账页表格补成本列（快照成本价/账本平均成本/成本差异），窄屏横向滚动。
- [x] 修复 Antd deprecated 警告、远程模式误导文案、清理误生成 node_modules + `.gitignore`。

### 最终质量修复（v0.1.1 收尾二轮）

- [x] Dashboard 历史日期"数据穿越"修复：`pendingReviewCount`/`pendingReviewJournals`/`riskWarnings`/`PENDING_REVIEW` 待办统一按 `trade_date <= date` 口径（新增 `TradeJournalMapper.selectByReviewStatusUpTo/countByReviewStatusUpTo`）。
- [x] 后端纯超卖遗漏修复：`oversoldSymbols` 进入对账结果集合，空快照+纯卖出返回 `QUANTITY_MISMATCH` + warning。
- [x] 前端 mock FIFO 与后端 `FifoCalculatorManager` 完全一致：买入批次单位成本含 `totalFee`、同日 null `tradeTime` 排有时间之后、稳定 ID 排序、超卖即停止后续计算。
- [x] Antd 6.4 deprecated 清理：`Alert message→title`、`Spin tip→description`、`Space direction→orientation`、`Drawer width→size`、Dashboard `List→ul/li` 已全替换；Playwright 浏览器实测工作台/交易记录/持仓快照/设置 4 个页面，控制台 `DEPRECATED_WARNINGS=0, CONSOLE_ERRORS=0`。
- [x] JSON 导出文案防误导：明确"仅导出浏览器 localStorage，不含后端 MySQL"，文件名改 `qta-local-export`。

## 7. 后续 P1: 证券主数据与行情基础

设计基线：`docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`。

- [ ] 建设 `stock_basic` 和统一证券标识。
- [ ] 明确手工价格、外部价格快照、日 K 的数据边界。
- [ ] 先支持 CSV 日 K 幂等导入。
- [ ] 再接一个外部行情 provider。
- [ ] 行情记录数据来源和抓取时间。
- [ ] API Key 只在服务端配置，不进入前端和仓库。

## 8. 暂缓: 图片识别导入

- [ ] 新增 AI import task 设计。
- [ ] 前端支持粘贴/上传图片。
- [ ] 后端封装 `PositionImageRecognitionProvider`。
- [ ] 先实现 mock provider。
- [ ] 再实现 GLM/OCR provider。
- [ ] 识别结果只生成草稿，不直接确认入库。
- [ ] 增加隐私提示和日志脱敏。

## 9. 后续 P2: 指标、策略、回测

- [ ] 在行情基础阶段完成日 K CSV 导入后，接入指标计算。
- [ ] 指标计算：MA、MACD、RSI、BOLL。
- [ ] 策略信号：均线趋势 + 成交量过滤。
- [ ] 简化回测：手续费、滑点、T+1。
- [ ] 风险提示与报告。

## 10. 每轮开发检查

### 后端

- [ ] 查看现有结构，不盲目新建平行体系。
- [ ] 新表走 Flyway。
- [ ] SQL 写 XML。
- [ ] DTO/VO/DO 字段有清晰注释或自解释命名。
- [ ] 常量进入 `common.constant` 或模块常量。
- [ ] 错误码使用 `ErrorCodeEnum`。
- [ ] 转换使用 MapStruct。
- [ ] `./mvnw test` 通过。
- [ ] 涉及部署时 `./mvnw package` 通过。

### 前端

- [ ] 页面不直接访问 localStorage，走 feature api。
- [ ] remote 模式走 shared client。
- [ ] API Key 不进前端。
- [ ] UI 文案不误导用户。
- [ ] `npm run typecheck` 通过。
- [ ] `npm run lint` 通过。
- [ ] `npm run test` 通过。
- [ ] `npm run build` 通过。

### 文档

- [ ] 新增/修改 API 后更新对应文档。
- [ ] 修改产品语义后更新 `PRODUCT_BLUEPRINT.md`。
- [ ] 修改架构事实后更新 `CURRENT_ARCHITECTURE_AND_MODULES.md`。
- [ ] 修改任务状态后更新本 checklist。
