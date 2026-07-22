# Current Architecture And Modules

> 本文件记录当前实现事实，避免 AI 后续按旧文档误判。若代码变化，本文件也要同步更新。

## 1. 当前技术栈

后端：

- Java 17
- Spring Boot 3.5.x
- Spring Web
- Spring Validation
- Spring Boot Actuator
- MyBatis + XML Mapper
- MapStruct
- Flyway
- MySQL 8.4
- H2 test profile
- Docker Compose

前端：

- React + TypeScript + Vite
- Ant Design
- feature-based 目录结构
- `mock/localStorage` 与 `remote/REST API` 双模式

## 2. 后端分层约定

当前后端按业务模块分包，每个模块尽量保持以下分层：

```text
controller  # REST API 入参、响应、HTTP 语义
service     # 应用服务、事务边界、跨 manager 编排
manager     # 领域规则、校验、计算、DAO 调用封装
dao         # MyBatis Mapper 接口
model       # DO / 数据库对象
dto         # 请求 DTO
vo          # 响应 VO
convert     # MapStruct 转换器
```

规则：

- Controller 不写业务计算。
- Service 管事务和流程编排。
- Manager 写业务规则、校验、计算和 DAO 编排。
- DAO 只负责数据库访问。
- SQL 写在 `src/main/resources/mapper/*.xml`。
- 表结构变更必须走 `src/main/resources/db/migration/V*.sql`。

## 3. 当前已实现模块

| 模块 | 后端包 | 主要 API | 数据表 |
| --- | --- | --- | --- |
| Dashboard | `dashboard` | `/api/v1/dashboard/today` | 聚合查询 |
| Watchlist | `watchlist` | `/api/v1/watchlist` | `watchlist` |
| Trade Plan | `tradeplan` | `/api/v1/trade-plans` | `trade_plan` |
| Risk Calculator | `risk` | `/api/v1/risk/calculations/position-size` | 纯计算 |
| Trade Journal | `journal` | `/api/v1/trade-journals` | `trade_journal` |
| Review | `review` | `/api/v1/reviews` | `review_note` |
| Portfolio Ledger | `portfolio` | `/api/v1/portfolio/*` | `trade_journal`, `portfolio_price_snapshot` |
| Position Snapshot | `portfolio` | `/api/v1/position-snapshots/*` | `portfolio_position_snapshot`, `portfolio_position_snapshot_item` |
| Market Data | `marketdata` | `/api/v1/market-data/*` | 证券主数据、精确代码验证、日/分钟 K、采集计划/任务/水位、自定义分组、市场行业发现/关注/快照；V15 新增全市场板块排行自动采集和关注板块自动采集 |

## 4. 当前数据库迁移

| Migration | 内容 |
| --- | --- |
| `V1__init_schema.sql` | schema marker |
| `V2__create_today_mvp_tables.sql` | watchlist、trade_plan、trade_journal、review_note |
| `V3__add_portfolio_ledger.sql` | 交易费用字段、portfolio_price_snapshot |
| `V4__add_position_snapshot.sql` | 持仓快照主表和明细表 |
| `V5__add_market_data_tables.sql` | 证券主数据 stock_basic、日 K stock_daily_bar |
| `V6__add_fetched_at_to_daily_bar.sql` | stock_daily_bar 增加 fetched_at |
| `V7__add_market_data_provider_tables.sql` | stock_quote_snapshot、market_data_sync_task、market_data_alert |
| `V8__add_market_data_sync_task_parent.sql` | market_data_sync_task 增加 parent_task_id，支持失败重试留痕 |
| `V9__add_market_data_sync_scope_lock.sql` | market_data_sync_scope_lock，同 scope 同步锁 |
| `V10__add_market_data_workbench.sql` | stock_minute_bar、market_trading_session、market_calendar、market_data_sync_plan、market_data_sync_task_item、market_data_watermark |
| `V11__add_market_segment.sql` | market_segment、market_segment_member |
| `V12__add_sub_task_id_to_task_item.sql` | market_data_sync_task_item 增加 sub_task_id（主子任务追踪），支持 `POST /sync-tasks/{taskId}/reconcile` 非终态收敛 + `selectAllByTaskId` 全量查询 |

已发布的 V1-V12 migration 不应修改；后续表结构调整继续新增更高版本 migration。

## 5. 交易账本口径

交易账本基于 `trade_journal` 的买卖流水计算：

- 买入形成 FIFO 批次。
- 卖出按 FIFO 配对。
- 买入成本包含按比例分摊费用。
- 卖出收入扣减卖出费用。
- 当前持仓浮盈依赖 `portfolio_price_snapshot` 手工当前价。

风险提示：

- 本模块不连接实时行情。
- 当前价为用户手工维护。
- 计算结果只用于复盘，不构成投资建议。

## 6. 持仓快照定位

持仓快照不是交易流水，也不是交易账本计算结果。

它表示某个时点券商账户实际显示的持仓盘点：

```text
某日某时刻 -> 用户实际持有哪些股票 -> 数量、成本价、当前价、市值、浮盈亏
```

持仓快照可以用来：

- 和交易账本计算结果做核对。
- 留存每日收盘持仓。
- 后续承接图片识别或 CSV 导入。

当前后端已经完成：

- 草稿创建和整批更新。
- 确认、作废状态流转。
- 历史列表、详情和最新已确认快照查询。
- 后端统一计算成本、市值、浮盈亏、盈亏率和仓位占比。

当前前端已经完成：

- 独立菜单和 `/position-snapshots` 路由。
- 最近已确认快照、历史列表、日期与状态筛选。
- 多行手工录入、计算预览、草稿编辑、确认和作废。
- 快照详情抽屉和 A 股盈亏颜色。
- mock/localStorage 与 remote/REST API 双模式。
- 桌面与窄屏响应式验收、真实 MySQL 前后端联调。

## 7. 后端命令

```bash
./mvnw test
./mvnw package
docker compose up -d --build
curl http://localhost:8080/actuator/health
```

## 8. 前端命令

在 `/Users/joker/code/quant-trading-assistant-web` 执行：

```bash
npm run typecheck
npm run lint
npm run test
npm run build
```

## 9. 不允许的实现方向

- 不添加自动下单。
- 不接真实券商账户。
- 不保存券商密码、交易密码或真实交易 API Key。
- 不把 AI 识别结果未经用户确认直接入正式表。
- 不为了跑通临时注释测试或关闭校验。

## 10. v0.1.1 基础交易闭环优化（已实现）

设计基线 `docs/features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md`，本轮全部落地：

- **交易计划关联交易记录**：`TradeJournalManager.validatePlanLinkage` 校验 `planId` 存在、未取消、证券代码一致；`TradeJournalVO` 增加非持久化展示字段 `planDate`、`planStatus`；`planId` 为空允许保存；一个计划可关联多笔交易且不自动结束。
- **复盘关联一致性**：`ReviewManager` 扫全表解析 `linked_journal_ids`（CSV）构建被引用集合；`ReviewService` 在新增/编辑/删除后对受影响 ID（旧 ∪ 新）回算 reviewStatus；`TradeJournalService.delete` 删除前做引用保护。`ReviewNoteMapper.selectAll` 提供全表扫描能力。
- **持仓快照对比**：新增 `PositionSnapshotComparisonManager`（纯计算）+ `PositionSnapshotService.compare` + `GET /api/v1/position-snapshots/comparison`。五种 changeType、BigDecimal delta、稳定排序、仅 CONFIRMED 且基准严格早于目标。
- **快照与 FIFO 账本对账**：新增 `PositionSnapshotReconciliationManager`（复用 `FifoCalculatorManager`）+ `TradeJournalService.listFlowItemsUpTo`（截止时点过滤）+ `GET /api/v1/position-snapshots/{snapshotId}/reconciliation`。四种 status、同日 trade_time 缺失 warning、只读不写库。
- **工作台待办中心**：`DashboardTodayVO` 增加结构化 `todos`；`DashboardManager.buildTodos` 聚合六类待办（PENDING_REVIEW / UNLINKED_TRADE_PLAN / TRADE_AGAINST_PLAN / MISSING_STOP_LOSS / STALE_POSITION_SNAPSHOT / POSITION_RECONCILIATION_MISMATCH），STALE 阈值 3 自然日，RECONCILE 由 service 层调用对账补充；前端 remote 直接用后端聚合，mock 用同口径纯函数。
- **生产连接防呆**：前端 `settingsApi` 增加 `isLocalhostHost` / `isLocalhostUrl` / `resolveEffectiveApiBaseUrl` / `testBackendConnection`；公网页面禁止保存指向 localhost 的后端地址；设置页展示有效请求地址并提供只读"测试连接"按钮（区分 success / timeout / http_error / business_error / network_error）。

新增错误码：`TRADE_PLAN_NOT_FOUND` / `TRADE_PLAN_NOT_LINKABLE` / `TRADE_PLAN_SYMBOL_MISMATCH` / `JOURNAL_REFERENCED_BY_REVIEW` / `POSITION_SNAPSHOT_COMPARISON_INVALID`。新增枚举：`DashboardTodoCodeEnum` / `DashboardTodoLevelEnum`（common.enums）、`SnapshotChangeTypeEnum` / `ReconciliationStatusEnum`（portfolio.enums）。该阶段未新增任何数据库表。

## 11. 行情基础当前事实

证券主数据和 CSV 日 K 基础已经由 `marketdata` 模块实现。LongPort 只读行情 provider 已完成真实外联验收：

- `stock_quote_snapshot` 存外部最新价快照，不覆盖 `portfolio_price_snapshot`。
- `market_data_sync_task` 存历史日 K 同步任务和失败留痕。
- `market_data_alert` 存 provider 未配置、同步失败、数据质量等提醒。
- LongPort SDK 通过 `runtime-libs/` 运行时 jar 方式加载，vendor jar 不入 Git。
- LongPort 凭据只通过 `.env.longport` / 环境变量注入，不进前端、DB、日志或 Git。
- LongPort 只用于 Quote 行情，不接交易、订单、账户、真实持仓。
- 行情 canonical symbol 支持 `SH/SZ/BJ/HK/US`；港股内部固定五位、美股统一大写。LongPort 映射支持 `HK.02498 <-> 2498.HK`、`US.AAPL <-> AAPL.US` 以及含类别分隔符的美股代码。
- 港美股当前覆盖证券主数据、最新价快照和历史日 K。分钟 K 自动任务仍以交易日历、时区和市场时段补齐为前置条件。
- LongPort SDK 4.3.3 分钟 K 原生 1M/5M/15M/30M/60M 已接入，provider 只转换领域数据，不操作 DAO。
- 市场板块与自定义分组已拆分：`MarketSectorProvider` 负责 Longbridge 行业排行、层级和成分只读查询，`market_segment` 仍只承载用户分组。行业接口使用签名 HTTPS 绕开 4.3.3 缺失 JNI；关注、聚合快照和成分快照由 V14 三表持久化。
- P1.6 采用双层采集：`market_sector_ranking_*` 低成本保存全市场排序事实；用户明确关注的板块才进一步保存成分资金明细。`MarketSectorCollectionScheduler` 以 DB claim、时间桶唯一键、错误阻断/退避保证单实例或多实例下不重复采集。
- 板块 scheduler 按 `Asia/Shanghai`、`Asia/Hong_Kong`、`America/New_York` 判断有效窗口；CN 包含 09:15-09:25 开盘集合竞价并保留 09:25 最终采样，09:26-09:29/午休/收盘后停止周期采集。HK/US 默认只采常规时段，收盘快照分别等待 15/10 分钟。频率为受控选项而非任意数字；节假日精确日历仍属于部署数据治理，周末会直接跳过。
- A 股 `5xxxxx` ETF 可由精确代码验证识别为上交所标的；ETF/指数行情复用现有证券报价和采集计划。
- `MarketDataPlanExecutionService` 在 provider 调用外使用短事务写入 task/item/minute bar/watermark；V13 DB run claim 防止同计划重入。
- `MarketDataIntradayScheduler` 通过可注入 `Clock` 按 A 股交易日/时段/采集频率扫描，非交易时段不创建空任务；启动时收敛遗留执行。

## 12. 后续规划（未实现）

行情 P1.2/P1.3 工作台、采集计划、LongPort 分钟 K、A 股盘中调度、任务明细/水位和板块已实现并通过 Docker MySQL 与最小真实外联验收。下一步尚未完成的是异动观察、港美股盘中时区/日历和多数据源，而不是直接进入策略回测：

- `docs/features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`
- `docs/features/LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`
- `docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`
- `docs/features/MARKET_ALERT_RULES_DESIGN.md`
- `docs/decisions/ADR-0008-longport-quote-only-provider.md`

AI 图片识别暂缓。
