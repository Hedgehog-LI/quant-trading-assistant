# Mock / Remote Contract

> 前端 mock（localStorage）与 remote（REST API）的职责、双模式支持、数据规则与**必须一致**的计算口径。**Mock 不是正式数据库**，仅用于本地开发 / 离线演示。

## 1. 职责

- **mock**：数据在浏览器 localStorage，离线可用，不依赖后端；适合本地开发、断网演示、临时记录。
- **remote**：数据通过 REST API 写入后端 MySQL，正式使用、服务器部署、跨设备一致。
- **切换**：`settings.apiMode`（mock/remote），每次请求现读（见 `shared/api/client.ts` `buildApiBaseUrl`），切换无需刷新页面。

## 2. 双模式支持矩阵

| 模块 | mock | remote | 备注 |
| --- | --- | --- | --- |
| Watchlist | ✅ | ✅ | |
| Trade Plan | ✅ | ✅ | |
| Trade Journal | ✅ | ✅ | remote 返回含 `planDate/planStatus`；`unlinkPlan` 三态 |
| Review | ✅ | ✅ | remote 回算 reviewStatus + 删除保护 |
| Portfolio（FIFO 账本） | ✅ 前端纯函数 | ✅ 后端 `FifoCalculatorManager` | mock 必须复刻后端口径（见 §4） |
| Position Snapshot | ✅ | ✅ | remote 含 `comparison`/`reconciliation` |
| Dashboard | ✅ 前端聚合 | ✅ 后端 `/dashboard/today` 聚合 | v0.1.1 remote 直接用后端 todos |
| Risk Calculator | ✅ 前端纯函数 | ⚠️ 后端 `/risk/calculations/position-size` 已实现但**前端页面未接 remote adapter**，当前用本地纯函数（`features/risk/api/riskCalculator.ts`） | |
| Build Status | ✅ 静态 | ✅ 静态 | |
| Market Data（证券/日 K/最新价/同步） | ✅ localStorage；不伪造 provider 执行成功 | ✅ 后端 DB + LongPort | canonical 规则必须一致：`SH/SZ/BJ/HK/US` |
| Market Workbench（行情工作台） | ✅ 计划 CRUD 落 `marketSyncPlans`；`run/getTask` 明确拒绝 | ✅ overview + 手工/盘中执行引擎 | mock 只验表单/状态；真实 task/item/分钟 K/水位必须使用 remote |
| Security Verification（精确代码验证） | ❌ 明确提示切换后端模式，不伪造 LongPort 结果 | ✅ `/market-data/securities/verify` | 验证只读；确认后才进入计划 scope |
| Market Segments（市场板块/自定义分组） | ✅ 市场板块仅演示数据；自定义分组 localStorage | ✅ `/sector-catalog/*` + `/segments/*` | 演示数据必须标 `LOCAL_DEMO`，不得冒充 LongPort |

## 3. localStorage 规则

- **逻辑 key**：`tradeJournals` / `tradePlans` / `watchlist` / `reviews` / `positionSnapshots` / `portfolioPrices` / `marketSyncPlans` / `settings`。
- **物理 key**：统一带 `qta:` 前缀（`shared/api/localStorageClient.ts` `fullKey`，如物理 key 为 `qta:tradeJournals`）；所有读写必须经 `localStorageClient`，禁止直接 `window.localStorage`。`settings` 同时被 `shared/api/client.ts` 读取 `apiBaseUrl`。
- **ID 类型**：mock = `generateId()` UUID **string**；remote = DB **Long**。`EntityId = string | number` 两种兼容，运行时不混用。
- **初始化**：首次访问无数据（空数组）；`settings` 缺失时按 `VITE_DEFAULT_API_MODE` 兜底（dev=mock，prod=remote）。
- **清空**：设置页"清空所有本地数据"（`clearAll`）。
- **导出**：设置页"导出本地配置 / 本地模式数据"——**仅 localStorage，不含后端 MySQL 业务数据**；文件名 `qta-local-export-*.json`。

## 4. 必须一致的计算/业务口径（mock 必须复刻后端）

- **FIFO 持仓**：买入批次 `lotTotalCost = price*qty + totalFee`（totalFee 缺失按 commissionFee+stampTax+transferFee+otherFee 求和，null→0），`unitCost = lotTotalCost/qty`；卖出按 FIFO 从最早批次扣减；超卖即停止后续计算（abnormal）。后端 `FifoCalculatorManager` / 前端 `features/position-snapshot/api/positionSnapshotReconciliation.ts`。
- **快照对比**：仅 `CONFIRMED`；基准严格早于目标；`changeType` NEW/INCREASED/REDUCED/CLOSED/UNCHANGED；排序 = changeType 序 → 目标市值降序 → symbol 升序。
- **快照对账**：截止时点 FIFO 数量（`trade_date < snapshotDate` 全纳入；同日且 tradeTime 空默认纳入 + warning；同日且 tradeTime 非空需 `<= snapshotTime`）；status MATCHED/QUANTITY_MISMATCH/SNAPSHOT_ONLY/LEDGER_ONLY；超卖 → QUANTITY_MISMATCH + warning；同日 tradeTime 空排有时间之后 + ID 稳定排序；空快照 + 纯卖出也必须返回 QUANTITY_MISMATCH。
- **Dashboard 待办**：6 类（PENDING_REVIEW / UNLINKED_TRADE_PLAN / TRADE_AGAINST_PLAN / MISSING_STOP_LOSS / STALE_POSITION_SNAPSHOT / POSITION_RECONCILIATION_MISMATCH）；TRADE_AGAINST_PLAN 触发 = `allowedToTrade=false` **或** `followedPlan=false`；历史日期口径 `trade_date <= date`（不含未来）；STALE 阈值 3 自然日；`targetPath` 用 `/journal*` 或 **`/position-snapshots`（复数）**；count==0 不返回；按 RISK>WARNING>INFO 排序。
- **计划关联校验**：`planId` 非空 → 计划存在 + 未 CANCELLED + symbol 一致；`unlinkPlan=true` → 置空；不传 → 保持原值。

## 5. 已知差异 / 限制

- mock 不持久化到后端；切换模式看到独立数据，不自动同步。
- mock ID（UUID）与 remote ID（Long）不混用；`String(id)` 比较用于跨模式兼容。
- 生产部署默认 remote，`apiBaseUrl` 留空走同源 `/api/v1`（Nginx 反代）；公网页面禁止保存 localhost 后端地址（`settingsApi.isLocalhostUrl` 防误配）。
- mock 模式不触发后端校验（如计划关联/删除保护），仅 remote 模式强制；故 mock 测试通过 ≠ 后端校验通过，关键校验必须后端单测覆盖。

## 6. 行情工作台与板块（P1.2/P1.3）

### localStorage keys

- `marketSegments`：`MarketSegment[]`，板块/自定义分组主表。
- `marketSegmentMembers:{id}`：`MarketSegmentMember[]`，某板块下的成员列表（按板块 id 分桶存储）。

> 物理 key 同样带 `qta:` 前缀（如 `qta:marketSegments`、`qta:marketSegmentMembers:3`），所有读写必须经 `localStorageClient`。

### mock 模式

- 板块 create / list / get / update / delete 持久化到上述 localStorage keys。
- 板块 **delete 级联清理成员**：删除板块时同步移除对应 `marketSegmentMembers:{id}` 桶。
- **addMember 不允许同板块同 symbol 重复**（同 `segmentId + canonicalSymbol` 视为重复，重复添加被拦截）。
- 板块 `memberCount` 必须与 `marketSegmentMembers:{id}` 数组长度一致（增删成员时同步维护）。
- 板块成员支持增删查；移除成员按 `canonicalSymbol` 定位。
- 市场行业排行在 mock 模式只提供带“演示”名称和 `LOCAL_DEMO` providerCode 的界面样例，不持久化、不声称来自外部行情。
- remote 模式行业排行/层级调用 `/api/v1/market-data/sector-catalog/*`；provider 不可用时展示错误和重试，不回退为演示数据。
- 证券与板块成员统一规范化：港股固定五位（`HK.2498 -> HK.02498`），美股统一大写（`us.aapl -> US.AAPL`）；重复判断使用规范化后的标识。
- ID 类型沿用 mock UUID string 规则（§3）。

### remote 模式

- 板块 CRUD 与成员管理调用 `/api/v1/market-data/segments/*`（详见 `../api/MARKET_DATA_API.md` §4）。
- 行情工作台调用 `/api/v1/market-data/workbench/*`（overview 聚合）。
- remote 由后端强制校验（同板块同 symbol 唯一、级联删除、memberCount 一致等），mock 必须复刻同口径，避免双模式行为分叉。
