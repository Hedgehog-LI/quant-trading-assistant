# P1.2/P1.3 多轮开发交付总结（2026-07-12 至 2026-07-16）

## 1. 这几天完成了什么

本阶段把行情能力从“LongPort 单股票接口验证”推进到“可配置、可追踪、可查询的数据资产工作台”，并经过六轮验收修复解决状态失真、统计口径、事务边界、页面可达性和伪行为测试问题。

### 产品能力

- 行情工作台：概览、采集计划、分钟 K、数据水位 4 个视图。
- 采集计划：创建、查询、修改、启停和手工执行；当前真实执行范围为 `DAILY_BAR_BACKFILL`。
- 任务追踪：父任务、逐标的 item、子任务 ID、错误留痕、真实行数统计、主动/懒收敛。
- 分钟线资产：1M/5M/15M/30M/60M 存储、查询、手工写入、质量校验、幂等和水位。
- 板块管理：板块 CRUD、成员增删查、成员数一致性，mock/remote 双模式。
- 页面能力矩阵：同步行情工作台、采集任务、分钟线和板块的真实完成度及后续风险。

### 后端交付

- V10：分钟线、交易时段/日历、采集计划、任务明细、水位。
- V11：板块与板块成员。
- V12：`market_data_sync_task_item.sub_task_id`，建立父任务、item 与逐标的子任务追踪关系。
- `runPlan` 改用 Jackson 解析 scope，支持 symbol 列表、日期范围、去重和格式校验。
- 不支持的任务类型直接返回业务错误，不再创建看似成功的空壳任务。
- 父任务状态严格按子任务状态收敛；六类 count 使用行情行数单位并直接汇总真实子任务字段。
- 独立 `TaskReconcileService` 提供事务收敛，避免 self-invocation；全量读取 item，不再受 500 条分页截断影响。
- 新增 `POST /api/v1/market-data/sync-tasks/{taskId}/reconcile`，任务明细查询支持安全懒收敛。

### 前端交付

- 采集计划新增任务明细入口，展示逐标的状态、计数、子任务、错误和开始/结束时间。
- Drawer 支持分页、刷新/收敛、loading/error/retry 和重复点击保护。
- 按 `lastTaskId` 重建内部状态，request-id/active guard 防止切换任务时旧请求覆盖。
- 板块 mock 使用 localStorage 持久化，支持 UUID、级联删除、重复成员校验和 memberCount 同步。
- 板块创建/删除失败、成员加载/重试、添加/移除 pending 均有真实交互测试。

## 2. 六轮质量收口解决的问题

1. 修复 mock 板块刷新丢数据、remote API 测试覆盖不足和页面错误态缺失。
2. 修复计划执行虚报成功、scope 字符串解析脆弱、日期未透传和状态映射错误。
3. 统一 symbol 状态与行情行数统计单位，补 `sub_task_id` 追踪。
4. 消除任务明细 500 条截断，父任务 count 直接使用子任务真实字段。
5. 把收敛事务抽到独立 Bean，并提供普通用户可达的任务明细/收敛页面。
6. 修复 Drawer 切换任务分页竞态，把浅层/伪行为测试改为真实 API 交互断言。

## 3. 最终验证结果

- 后端：`./mvnw test`，250 tests / 0 failures / 0 errors；package 成功。
- 前端：typecheck、lint、32 files / 261 tests、production build 全部通过。
- 两仓库：`git diff --check` 通过。
- 本轮未执行：Docker、浏览器端到端、LongPort 真实外联；历史 P1.1 单股票真实外联验收仍保留。

## 4. 当前明确未完成

- LongPort 分钟 K `getMinuteBars` 批量 adapter。
- `MINUTE_BAR_BACKFILL` / `INTRADAY_MINUTE_REFRESH` 执行链路。
- 遵守交易日历和交易时段的 scheduler。
- 异动大屏、量价衍生指标、多数据源扩展。

下一阶段必须先完成分钟线采集执行引擎，并用单标的、小时间范围完成端到端验收，再进入异动大屏或指标/策略/回测。

## 5. 事实来源

- 当前接手：`../AI_HANDOFF.md`
- 产品设计：`../PRODUCT_BLUEPRINT.md`
- 行情设计：`../features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`
- API：`../api/MARKET_DATA_API.md`
- 数据库：`../DATABASE_DESIGN.md`
- 架构：`../CURRENT_ARCHITECTURE_AND_MODULES.md`
- 逐轮开发记录：`DEVELOPMENT_LOG.md`
- 逐轮验收记录：`../acceptance/ACCEPTANCE_LOG.md`
