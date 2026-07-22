# API Index

> 所有已实现 REST API 的索引（**完整 `/api/v1/...` 路径**）。详细请求/响应见对应文档，本文件不复制定义。

统一响应 `ApiResponse<T>` = `{ success, code, data, message?, timestamp }`。

## 接口清单

| 模块 | 方法 | 完整路径 | 状态 | 详细文档 |
| --- | --- | --- | --- | --- |
| Dashboard | GET | `/api/v1/dashboard/today[?date=]` | 已实现（v0.1.1 含 todos + 历史日期口径） | `../API_TODAY_MVP.md` |
| Watchlist | GET / POST | `/api/v1/watchlist` | 已实现 | `../API_TODAY_MVP.md` |
| Watchlist | GET / PUT / DELETE | `/api/v1/watchlist/{id}` | 已实现 | `../API_TODAY_MVP.md` |
| Watchlist | PATCH | `/api/v1/watchlist/{id}/enabled` | 已实现 | `../API_TODAY_MVP.md` |
| Trade Plan | GET / POST | `/api/v1/trade-plans` | 已实现 | `../API_TODAY_MVP.md` |
| Trade Plan | GET / PUT / DELETE | `/api/v1/trade-plans/{id}` | 已实现 | `../API_TODAY_MVP.md` |
| Trade Plan | PATCH | `/api/v1/trade-plans/{id}/status` | 已实现 | `../API_TODAY_MVP.md` |
| Risk Calculator | POST | `/api/v1/risk/calculations/position-size` | 已实现（纯计算；**前端页面当前用本地纯函数，未接 remote adapter**） | `../API_TODAY_MVP.md` |
| Trade Journal | GET / POST | `/api/v1/trade-journals` | 已实现（v0.1.1：planId 校验） | `../API_TODAY_MVP.md` |
| Trade Journal | GET / PUT / DELETE | `/api/v1/trade-journals/{id}` | 已实现（v0.1.1：unlinkPlan 三态 + 删除保护） | `../API_TODAY_MVP.md` |
| Trade Journal | PATCH | `/api/v1/trade-journals/{id}/review-status` | 已实现 | `../API_TODAY_MVP.md` |
| Review | GET / POST | `/api/v1/reviews` | 已实现（v0.1.1：一致性回算 + 不存在关联拒绝） | `../API_TODAY_MVP.md` |
| Review | GET / PUT / DELETE | `/api/v1/reviews/{id}` | 已实现 | `../API_TODAY_MVP.md` |
| Portfolio | GET | `/api/v1/portfolio/summary` | 已实现（FIFO） | `PORTFOLIO_API.md` |
| Portfolio | GET | `/api/v1/portfolio/positions` | 已实现 | `PORTFOLIO_API.md` |
| Portfolio | GET | `/api/v1/portfolio/closed-trades` | 已实现 | `PORTFOLIO_API.md` |
| Portfolio | GET | `/api/v1/portfolio/symbol/{symbol}` | 已实现 | `PORTFOLIO_API.md` |
| Portfolio | POST / GET | `/api/v1/portfolio/prices` | 已实现（upsert + 列表） | `PORTFOLIO_API.md` |
| Position Snapshot | POST / GET | `/api/v1/position-snapshots` | 已实现 | `POSITION_SNAPSHOT_API.md` |
| Position Snapshot | GET | `/api/v1/position-snapshots/latest` | 已实现 | `POSITION_SNAPSHOT_API.md` |
| Position Snapshot | GET / PUT | `/api/v1/position-snapshots/{id}` | 已实现 | `POSITION_SNAPSHOT_API.md` |
| Position Snapshot | GET | `/api/v1/position-snapshots/comparison?baseSnapshotId=&targetSnapshotId=` | 已实现（v0.1.1） | `POSITION_SNAPSHOT_API.md` |
| Position Snapshot | GET | `/api/v1/position-snapshots/{id}/reconciliation` | 已实现（v0.1.1） | `POSITION_SNAPSHOT_API.md` |
| Position Snapshot | PATCH | `/api/v1/position-snapshots/{id}/confirm` | 已实现 | `POSITION_SNAPSHOT_API.md` |
| Position Snapshot | PATCH | `/api/v1/position-snapshots/{id}/cancel` | 已实现 | `POSITION_SNAPSHOT_API.md` |
| Market Data | GET / POST | `/api/v1/market-data/stocks` | 已实现 | `MARKET_DATA_API.md` |
| Market Data | GET / DELETE | `/api/v1/market-data/stocks/{canonicalSymbol}` | 已实现 | `MARKET_DATA_API.md` |
| Market Data | PUT | `/api/v1/market-data/stocks/{id}` | 已实现 | `MARKET_DATA_API.md` |
| Market Data | POST | `/api/v1/market-data/securities/verify` | 已实现（A/H/US 精确代码 + LongPort 静态信息/报价只读验证） | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/daily-bars` | 已实现 | `MARKET_DATA_API.md` |
| Market Data | POST | `/api/v1/market-data/daily-bars/import` | 已实现 | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/daily-bars/template` | 已实现 | `MARKET_DATA_API.md` |
| Market Data | GET / POST | `/api/v1/market-data/providers/LONGPORT/status`、`/health-check` | 已实现；LongPort 真实外联已验收（2026-07-12） | `MARKET_DATA_API.md` |
| Market Data | POST / GET | `/api/v1/market-data/quotes/latest`、`/quote-snapshots` | 已实现；latest quote 可落 `stock_quote_snapshot(dataSource=LONGPORT)` | `MARKET_DATA_API.md` |
| Market Data | POST / GET | `/api/v1/market-data/sync-tasks/daily-bars`、`/sync-tasks`、`/sync-tasks/{id}` | 已实现；daily bar 可落 `stock_daily_bar(data_source=LONGPORT)` | `MARKET_DATA_API.md` |
| Market Data | GET / PATCH | `/api/v1/market-data/alerts`、`/alerts/{id}/resolve` | 已实现 | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/workbench/overview` | 已实现（P1.2 行情工作台概览） | `MARKET_DATA_API.md` |
| Market Data | POST / GET / PUT / DELETE | `/api/v1/market-data/sync-plans[/{id}]` | 已实现（P1.2 采集计划 CRUD） | `MARKET_DATA_API.md` |
| Market Data | POST | `/api/v1/market-data/sync-plans/{id}/toggle` | 已实现（启停采集计划） | `MARKET_DATA_API.md` |
| Market Data | POST | `/api/v1/market-data/sync-plans/{id}/run` | 已实现（`DAILY_BAR_BACKFILL` + `MINUTE_BAR_BACKFILL`；父任务/item/水位/计数闭环） | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/sync-tasks/{taskId}/items` | 已实现（任务执行明细） | `MARKET_DATA_API.md` |
| Market Data | POST | `/api/v1/market-data/sync-tasks/{taskId}/reconcile` | 已实现（幂等收敛主任务与逐标的子任务状态/计数） | `MARKET_DATA_API.md` |
| Market Data | GET / POST | `/api/v1/market-data/minute-bars` | 已实现（P1.2 分钟 K 查询/写入，带质量 + 交易日/时段校验 + 幂等 + 水位） | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/trading-sessions` | 已实现（A 股交易时段，启动时幂等初始化） | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/trading-sessions/is-trading-day` | 已实现（判断是否交易日） | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/watermarks` | 已实现（数据水位查询） | `MARKET_DATA_API.md` |
| Market Data | POST / GET / PUT / DELETE | `/api/v1/market-data/segments[/{id}]` | 已实现（P1.3 板块 CRUD） | `MARKET_DATA_API.md` |
| Market Data | GET / POST / DELETE | `/api/v1/market-data/segments/{id}/members[/{canonicalSymbol}]` | 已实现（板块成员增删查） | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/sector-catalog/industry-rankings` | 已实现（LongPort 行业排行，只读） | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/sector-catalog/industry-peers` | 已实现（LongPort 行业层级摘要，只读） | `MARKET_DATA_API.md` |
| Market Data | POST / GET / DELETE | `/api/v1/market-data/sector-catalog/watches[/{id}]` | 已实现（行业关注、手工快照和历史） | `MARKET_DATA_API.md` |
| Market Data | PUT | `/api/v1/market-data/sector-catalog/watches/{id}/collection` | 已实现（关注板块自动采集频率） | `MARKET_DATA_API.md` |
| Market Data | GET / PUT | `/api/v1/market-data/sector-catalog/ranking-configs[/{market}]` | 已实现（CN/HK/US 全市场榜单采集配置） | `MARKET_DATA_API.md` |
| Market Data | POST | `/api/v1/market-data/sector-catalog/ranking-configs/{market}/run` | 已实现（立即采集全市场榜单） | `MARKET_DATA_API.md` |
| Market Data | GET | `/api/v1/market-data/sector-catalog/ranking-history[/{batchId}/items]` | 已实现（历史榜单批次和明细） | `MARKET_DATA_API.md` |

## 错误码

通用错误码表见 `../API_TODAY_MVP.md`；v0.1.1 新增：`TRADE_PLAN_NOT_FOUND` / `TRADE_PLAN_NOT_LINKABLE` / `TRADE_PLAN_SYMBOL_MISMATCH` / `JOURNAL_REFERENCED_BY_REVIEW` / `POSITION_SNAPSHOT_COMPARISON_INVALID`。行情错误码见 `MARKET_DATA_API.md` 和实际 `ErrorCodeEnum`。

## 维护规则

新增/修改/删除接口时**必须**：① 更新本索引（**完整 `/api/v1/...` 路径**）；② 更新对应详细文档；③ 若影响前端 mock，同步 `../mock/MOCK_REMOTE_CONTRACT.md`。禁止复制多份接口定义。
