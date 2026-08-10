# Task Contract: MARKET-DATA-ASSET-CENTER-P19A 个股行情资产查看器

## Contract Identity

- Status: `FROZEN`
- Contract version: `1.0`
- Frozen at: `2026-08-10`
- Frozen by parent run: `Codex product/architecture design`
- Lane: `L2`

## Objective

基于已落库的日 K、分钟 K、最新价、水位和采集任务，交付 `/market-assets` 个股行情资产查看器，使用户能查看 K 线、成交量、区间摘要、质量与相关采集记录。

## Authority

- Product/design: `docs/features/MARKET_DATA_ASSET_CENTER_DESIGN.md`
- API/data contract: `docs/api/MARKET_DATA_API.md §6`、`docs/DATABASE_DESIGN.md`
- Architecture decision: `docs/decisions/ADR-0012-market-data-asset-read-model-and-chart-library.md`
- Backend baseline commit: `813862d` on `main`
- Frontend baseline commit: `40eae51` on `main`
- Pre-existing dirty paths: backend `qta-ai-governance-portable.zip`（无关，禁止读取、修改、暂存或提交）；设计文档由本契约前置阶段产生
- Backend allowed write paths: `src/main/java/com/quant/trade/marketdata/**`、`src/main/resources/mapper/**`、对应测试、API/架构/Mock/交付文档
- Frontend allowed write paths: `package.json`、`package-lock.json`、`src/features/market-assets/**`、`src/pages/market-assets.tsx`、路由/菜单、market-data/workspace 的跳转入口、对应测试与文档

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | 日 K、分钟 K、最新价、水位和采集任务已经落库并有基础分页 API。 |
| FACT | 当前 bar 表没有 task_id，相关任务不能冒充精确行级血缘。 |
| DECISION | 新增只读聚合 read model，不写库、不调用 provider、不新增 migration。 |
| DECISION | 单次最多 2000 bars，SQL 用范围 + LIMIT 2001 判断截断。 |
| DECISION | 前端使用 lightweight-charts 5.2.x 且保留 attribution。 |
| DECISION | P1.9-B/C、P1.7 和 P2 指标不在本任务。 |

## Scope

### In Scope

- availability、series、related-tasks 三个只读 API。
- `/market-assets` 页面、证券选择、日/分钟 K、成交量、摘要、质量、水位、原始表格、相关任务。
- 从采集计划、日 K 和分钟 K 入口跳转。
- mock/remote、自动化、Docker/curl 和浏览器验收。

### Out Of Scope

- 板块历史视图、多证券对比、导出、保存视图。
- MA/MACD/RSI/BOLL、策略、信号、回测。
- P1.7 板块衍生分析与提醒。

### Prohibited

- 修改原始行情表、V1-V18 migration 或采集 scheduler。
- 调用 LongPort/provider 或触发采集。
- 混合 dataSource、adjustType、币种或日/分钟粒度。
- 前端循环分页、后端全表加载后截断、伪造 HK/US 缺口。
- 自动交易、券商账户、订单或真实持仓能力。

## Acceptance Criteria

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | availability 返回真实可用组合 | A/H/US 证券，有/无 bars | 只返回存在的 interval/source/adjust；空资产可解释 | controller/service/mapper tests | AUTOMATION | backend | NOT_STARTED |
| AC-02 | series 有界且口径单一 | 合法与超限范围 | 时间升序、最多 2000、2001 判 truncated、无来源混合 | mapper + service tests | AUTOMATION | backend | NOT_STARTED |
| AC-03 | 摘要、质量和水位正确 | daily/minute fixture | BigDecimal 计算正确；CN 覆盖可算，HK/US UNKNOWN | golden tests | AUTOMATION | backend | NOT_STARTED |
| AC-04 | 页面可交互查看 K 线与成交量 | 选择有数据证券 | 图表非空，切换粒度/范围无旧数据残留 | component/browser evidence | AUTOMATION/RUNTIME | frontend | NOT_STARTED |
| AC-05 | 空、错、可疑、截断、陈旧可区分 | 各状态 fixture | 页面状态符合设计且可重试 | component tests | AUTOMATION | frontend | NOT_STARTED |
| AC-06 | 入口和 URL 状态闭环 | 计划/日K/分钟K跳转 | 自动带入 symbol/interval/source/adjust/range | route tests | AUTOMATION | frontend | NOT_STARTED |
| AC-07 | 金融与数据边界不误导 | 不同来源/复权/市场 | 不混算、不称实时、不把区间涨跌称持仓收益 | code review + tests | STATIC/AUTOMATION | shared | NOT_STARTED |
| AC-08 | 本地运行链路可用 | Docker MySQL 有日/分钟样本 | health、curl、桌面/窄屏图表和控制台通过 | runtime receipts/screenshots | RUNTIME | verifier | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | `git diff --check` + architecture review | 无越界、无原始表写入、无 provider 调用 |
| AUTOMATION | Yes | `./mvnw test && ./mvnw package`；前端四项门禁 | 全部 exit 0 |
| RUNTIME | Yes | Docker、5 类 curl、桌面/窄屏浏览器 | health UP、响应口径正确、canvas 非空、0 console error |
| DEPLOYMENT | No | 服务器部署后另验 | 本任务不冒充 DEPLOYED |

## Implementation Slices

| Slice ID | Coherent boundary | AC IDs | Allowed write paths | Max files | Max production-line delta |
|---|---|---|---|---:|---:|
| BE-01 | availability + 参数/枚举契约 | AC-01 | backend marketdata.asset + focused tests | 8 | 500 |
| BE-02 | series + summary/quality/watermark/related tasks | AC-02, AC-03 | backend asset manager、mapper/xml、tests | 8 | 500 |
| FE-01 | API/model/hook/chart adapter | AC-04, AC-05 | frontend market-assets feature | 8 | 500 |
| FE-02 | page、toolbar、chart、summary/health/table | AC-04, AC-05 | frontend components/page/tests | 8 | 500 |
| INT-01 | route/menu/three entry links + runtime | AC-06, AC-07, AC-08 | both repos bounded integration paths | 8 | 400 |

## Frozen Test Inventory

| Test ID | AC IDs | Kind | Required | Source path | Exact selector | Receipt path |
|---|---|---|---|---|---|---|
| TEST-BE-FOCUSED | AC-01..03 | AUTOMATION | YES | backend tests | asset controller/service/manager/mapper focused suite | runtime artifact |
| TEST-BE-FULL | AC-01..03,07 | AUTOMATION | YES | backend | `./mvnw test && ./mvnw package` | runtime artifact |
| TEST-FE-FOCUSED | AC-04..07 | AUTOMATION | YES | frontend tests | market-assets focused suite | runtime artifact |
| TEST-FE-FULL | AC-04..07 | AUTOMATION | YES | frontend | typecheck/lint/test/build | runtime artifact |
| TEST-RUNTIME | AC-08 | RUNTIME | YES | Docker/browser | health + 5 curl + desktop/mobile | runtime artifact |

## Architecture And Quality Gates

- Required architecture review: `YES`
- Required layers/boundaries: controller → service → manager → mapper/xml；前端 page → feature hook → API/adapter
- Hard blocks: provider 调用、DB 写入、新 migration、无界查询、前端分页拼接、图表 attribution 关闭

## Role Assignments

- Product/architecture owner: Codex（设计已冻结）
- Implementer: DeepSeek/Claude Code，只能 `SELF_CHECKED`
- Code reviewer/final verifier: Codex 干净验收上下文
- Implementer不得自行升级设计或给出 ACCEPTED

## Candidate And Git Policy

- Git automation: `COMMIT`
- Task branch: `codex/market-data-asset-center-p19a`（两仓同名）
- Candidate mode: `COMMIT`
- Checkpoint push allowed: `NO`
- Protected/default branch direct push: `NO`

## Checkpoint Policy

- 只按 BE-01 → BE-02 → FE-01 → FE-02 → INT-01 顺序推进。
- 同一 failure fingerprint 最多两轮修复。
- 禁止递归子代理、专家团重做设计和短间隔轮询。
- 外部凭据、LongPort 或服务器不是本任务前置条件；本地已有 DB fixture 可完成开发。
- 任何设计语义冲突、超出 P1.9-A 或需要新表时立即停止并记录 BLOCKED。
