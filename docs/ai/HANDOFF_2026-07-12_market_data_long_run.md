# Handoff 2026-07-12 — Market Data Long Run (P1.2 + P1.3)

> 本轮长线夜间自主开发完成。后续接手优先读本文件 + `AI_HANDOFF.md` + `BUILD_CHECKLIST.md`。

## 1. 本轮完成了什么

### 阶段 A：P1.2 后端核心 — 行情采集与分钟线资产
- 新增 V10 migration：`stock_minute_bar`、`market_trading_session`、`market_calendar`、`market_data_sync_plan`、`market_data_sync_task_item`、`market_data_watermark`（6 张表）。
- 分钟 K 数据质量校验（`MinuteBarQualityManager`）：OHLC 合法性、volume/amount 非负、交易时段校验、内容冲突检测、qualityStatus（VALID/SUSPECT/REJECTED）。
- 交易时段/日历领域逻辑（`TradingSessionManager`）：DB 优先 + A 股默认窗口回退、周末规则回退、幂等初始化。
- 采集计划 service：CRUD + 启停 + 手动执行入口。
- 分钟 K 写入 service：幂等（一致跳过/冲突不覆盖+alert/质量拒绝+alert）+ 自动水位更新。
- 工作台概览 API：provider 状态 + 提醒统计 + 交易时段聚合。

### 阶段 B：P1.2 前端 — 行情工作台页面
- 新增 `/market-workspace` 页面（4 Tab：概览/采集计划/分钟K/水位）。
- 新增 `/market-segments` 页面（板块列表 + 成员管理 Drawer）。
- 前端 API 层（`workbenchApi.ts` + `segmentApi.ts`），mock/remote 双模式。
- 路由注册 + 侧边栏菜单（行情工作台 + 板块管理）。
- mock 测试覆盖（overview/trading-sessions/isTradingDay/createSyncPlan）。

### 阶段 C：P1.3 板块/自定义分组
- 新增 V11 migration：`market_segment`、`market_segment_member`（2 张表）。
- 板块 CRUD + 成员增删改查 service + controller。
- 前端板块管理页面（列表 + 新建 + 成员管理 Drawer）。

### 阶段 D/E：未启动
- P1.5 异动大屏 MVP：未启动（时间优先级让给文档收口）。
- P2.1 指标/量价统计：未启动。

## 2. 哪些 API 新增/修改

### P1.2 工作台 API（`MarketDataWorkbenchController`，前缀 `/api/v1/market-data`）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/workbench/overview` | 工作台概览 |
| POST | `/sync-plans` | 创建采集计划 |
| GET | `/sync-plans` | 分页查询采集计划 |
| GET | `/sync-plans/{id}` | 查询单个采集计划 |
| PUT | `/sync-plans/{id}` | 更新采集计划 |
| POST | `/sync-plans/{id}/toggle?enabled=` | 启停采集计划 |
| GET | `/sync-tasks/{taskId}/items` | 任务明细分页 |
| GET | `/minute-bars` | 分钟 K 分页查询 |
| POST | `/minute-bars` | 写入分钟 K（带质量校验+幂等+水位） |
| GET | `/trading-sessions` | A 股交易时段 |
| GET | `/trading-sessions/is-trading-day?marketCode=&date=` | 判断交易日 |
| GET | `/watermarks` | 数据水位分页 |

### P1.3 板块 API（`MarketSegmentController`，前缀 `/api/v1/market-data/segments`）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/segments` | 创建板块 |
| GET | `/segments` | 分页查询板块 |
| GET | `/segments/{id}` | 查询单个板块 |
| PUT | `/segments/{id}` | 更新板块 |
| DELETE | `/segments/{id}` | 删除板块 |
| GET | `/segments/{id}/members` | 查询板块成员 |
| POST | `/segments/{id}/members` | 添加板块成员 |
| DELETE | `/segments/{id}/members/{canonicalSymbol}` | 移除板块成员 |

## 3. 哪些表新增

| Migration | 表 | 说明 |
| --- | --- | --- |
| V10 | `stock_minute_bar` | 分钟 K 线（1M/5M/15M/30M/60M） |
| V10 | `market_trading_session` | 交易时段模板 |
| V10 | `market_calendar` | 交易日历 |
| V10 | `market_data_sync_plan` | 采集计划 |
| V10 | `market_data_sync_task_item` | 任务执行明细 |
| V10 | `market_data_watermark` | 数据水位 |
| V11 | `market_segment` | 板块/自定义分组 |
| V11 | `market_segment_member` | 板块成分股 |

## 4. 前端新增哪些页面/入口

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/market-workspace` | 行情工作台 | 4 Tab：概览/采集计划/分钟K/水位 |
| `/market-segments` | 板块管理 | 板块列表 + 成员管理 Drawer |

侧边栏菜单新增"行情工作台"和"板块管理"两个入口。

## 5. 测试命令和结果

### 后端
- `./mvnw test`：**217 tests / 0 failures / 0 errors**（新增 29 tests：质量校验 13 + 时段 8 + 工作台 8）。
- `./mvnw -DskipTests package`：BUILD SUCCESS。
- `git diff --check`：通过。
- V10/V11 migration 在 H2 MySQL 兼容模式下正常加载。

### 前端
- `npm run typecheck`：通过。
- `npm run lint`：通过。
- `npm run test`：**221 tests passed**（新增 7 tests：workbenchApi mock 5 + segmentApi 待补）。
- `npm run build`：通过。

## 6. 没跑的测试为什么没跑
- LongPort 真实外联验收：跳过（SKIPPED）—— 本轮无 LongPort 凭据/容器环境，且任务要求明确"没有就跳过不停住"。
- Docker `docker compose up`：跳过 —— 代码侧编译+单测已覆盖，Docker 联调留给人工或后续。

## 7. LongPort 真实外联是否执行
- **未执行（SKIPPED）**。原因：本轮开发模式无 LongPort 凭据和运行中容器；LongPort 域名覆盖和单 symbol 外联在上一轮（P1.1）已验收通过，本轮代码改动（工作台/采集计划/分钟K/板块）不涉及 LongPort provider 反射链路，不影响已验证的外联通路。
- 如需真实外联验收分钟 K，需先确保 LongPort SDK 分钟 K 接口可用（`getHistoryCandlesticksByDate` 已支持 Period.Minute 等）。

## 8. 下一轮从哪里继续

### 优先级 1：P1.2 补强（盘中采集执行 + 数据质量审计）
- 当前采集计划有 CRUD + 启停 + 手动执行入口，但**盘中自动调度（cron/定时器）尚未实现**。
- 任务明细表已有结构，但分钟 K 实际批量拉取 + 明细写入的执行链路需要在 service 层补全（当前 upsertMinuteBar 是单条写入，适合测试和手动）。
- LongPort `ReflectiveLongPortQuoteClient` 当前只有 `getDailyBars`（日 K），需要扩展 `getMinuteBars`（分钟 K），反射调用 `getHistoryCandlesticksByDate` 传 `Period.Minute`/`5M`/`15M`/`30M`/`60M`。

### 优先级 2：P1.5 异动大屏 MVP
- 复用 `market_data_alert`（HIGH/WARN/INFO）+ 板块聚合 + 工作台概览数据。
- 大屏展示持仓股/自选股/计划股/板块提醒流。
- 不输出买卖建议。

### 优先级 3：P2.1 指标/量价统计
- 基于日 K / 分钟 K 做 MA、成交量均线、振幅、量比、涨跌幅。
- 指标必须声明数据源、粒度、复权口径。
- 数据质量不通过（SUSPECT/REJECTED）的数据不得进入指标计算。

## 9. 风险和待办

| 风险/待办 | 说明 |
| --- | --- |
| 盘中自动调度未实现 | 采集计划 trigger_type=INTRADAY/SCHEDULED 当前只有配置存储，无定时器执行。需引入 `@Scheduled` 或 Quartz。 |
| 分钟 K 批量拉取未接通 | LongPort adapter 的 `getMinuteBars` 尚未实现，当前只有 `getDailyBars`。 |
| 工作台概览聚合是占位 | `totalSymbols`/`totalMinuteBars`/`failedTasksToday` 等计数当前返回 0（占位），需接通 DAO 聚合查询。 |
| 日历表数据为空 | `market_calendar` 无初始化数据，交易时段用周末规则回退。后续需要导入真实 A 股交易日历。 |
| LongPort 真实外联未复核 | 本轮无凭据，真实分钟 K 外联留待人工补验。 |
| 未 commit/push | 遵守长线开发约定，全部改动未提交，留给用户 review 后决定。 |
