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

## 错误码

通用错误码表见 `../API_TODAY_MVP.md`；v0.1.1 新增：`TRADE_PLAN_NOT_FOUND` / `TRADE_PLAN_NOT_LINKABLE` / `TRADE_PLAN_SYMBOL_MISMATCH` / `JOURNAL_REFERENCED_BY_REVIEW` / `POSITION_SNAPSHOT_COMPARISON_INVALID`。

## 维护规则

新增/修改/删除接口时**必须**：① 更新本索引（**完整 `/api/v1/...` 路径**）；② 更新对应详细文档；③ 若影响前端 mock，同步 `../mock/MOCK_REMOTE_CONTRACT.md`。禁止复制多份接口定义。
