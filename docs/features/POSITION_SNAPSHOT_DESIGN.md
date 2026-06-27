# Position Snapshot Design

> 持仓快照 P0 已完成。第一版包含 DB + 手工录入 + 历史查询；图片识别只作为后续自动填表能力。

当前进度（2026-06-27）：后端 DB、REST API、状态流转、金额计算和集成测试已完成；前端手工录入、历史查询、详情、双模式和响应式验收已完成，并已使用真实 MySQL 完成前后端联调。接口契约见 `docs/api/POSITION_SNAPSHOT_API.md`。

## 1. 业务定义

持仓快照表示某个时间点用户券商账户中实际显示的持仓盘点。

它回答：

1. 我在某天某时刻实际持有哪些股票？
2. 每只股票数量、成本、当前价、市值、浮盈亏是多少？
3. 今天和历史某天相比，持仓发生了什么变化？

## 2. 与现有模块的关系

| 模块 | 数据来源 | 作用 |
| --- | --- | --- |
| Trade Journal 交易记录 | 用户逐笔录入买卖 | 形成交易流水 |
| Portfolio Ledger 交易账本 | 根据交易流水 FIFO 计算 | 算理论持仓和已结算盈亏 |
| Position Snapshot 持仓快照 | 用户手工录入或截图识别 | 留存实际券商持仓盘点 |

持仓快照不自动反推交易流水。若快照和交易账本不一致，第一版只提示用户核对，不自动修正。

## 3. 第一版范围

### 做

- 新建持仓快照。
- 手工录入多只持仓明细。
- 保存草稿。
- 修改草稿。
- 确认快照。
- 作废快照。
- 查询历史快照列表。
- 查看快照详情。
- 查看最近一次已确认快照。

### 不做

- 不接券商。
- 不自动同步真实账户。
- 不根据快照自动生成买卖流水。
- 不直接接 GLM 图片识别。
- 不自动把 AI 识别结果写入正式数据。

## 4. 状态设计

| 状态 | code | 说明 | 是否可编辑 |
| --- | --- | --- | --- |
| 草稿 | `DRAFT` | 可继续录入和修正 | 是 |
| 已确认 | `CONFIRMED` | 正式历史快照 | 否，后续可考虑更正流程 |
| 已作废 | `CANCELED` | 不参与默认统计 | 否 |

状态流转：

```text
DRAFT -> CONFIRMED
DRAFT -> CANCELED
CONFIRMED -> CANCELED
```

不允许：

- `CANCELED` 回到其他状态。
- 默认硬删除 `CONFIRMED` 快照。

## 5. 来源设计

| 来源 | code | 第一版 |
| --- | --- | --- |
| 手工录入 | `MANUAL` | 支持 |
| 图片识别 | `IMAGE_RECOGNITION` | 预留 |
| CSV 导入 | `CSV_IMPORT` | 预留 |

## 6. DB 设计

### 6.1 `portfolio_position_snapshot`

一次持仓盘点批次。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `snapshot_date` | DATE NOT NULL | 快照日期 |
| `snapshot_time` | DATETIME NOT NULL | 快照时间 |
| `snapshot_name` | VARCHAR(128) | 名称，如 2026-06-26 收盘持仓 |
| `source_type` | VARCHAR(32) NOT NULL | MANUAL / IMAGE_RECOGNITION / CSV_IMPORT |
| `snapshot_status` | VARCHAR(32) NOT NULL | DRAFT / CONFIRMED / CANCELED |
| `total_cost_amount` | DECIMAL(20,6) | 总成本 |
| `total_market_value` | DECIMAL(20,6) | 总市值 |
| `total_unrealized_pnl` | DECIMAL(20,6) | 总浮盈亏 |
| `total_pnl_rate` | DECIMAL(20,6) | 总盈亏比例 |
| `position_count` | INT | 持仓股票数 |
| `remark` | VARCHAR(1024) | 备注 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

建议索引：

- `idx_position_snapshot_date(snapshot_date)`
- `idx_position_snapshot_status(snapshot_status)`
- `idx_position_snapshot_source(source_type)`

### 6.2 `portfolio_position_snapshot_item`

某次快照中的单只股票持仓。

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | BIGINT PK | 主键 |
| `snapshot_id` | BIGINT NOT NULL | 快照 ID |
| `symbol` | VARCHAR(32) NOT NULL | 股票代码 |
| `name` | VARCHAR(128) | 股票名称 |
| `market_type` | VARCHAR(32) | SH / SZ / BJ / UNKNOWN |
| `holding_quantity` | BIGINT NOT NULL | 持仓数量 |
| `available_quantity` | BIGINT | 可用数量 |
| `cost_price` | DECIMAL(20,6) NOT NULL | 成本价 |
| `current_price` | DECIMAL(20,6) NOT NULL | 当前价 |
| `cost_amount` | DECIMAL(20,6) | 成本金额 |
| `market_value` | DECIMAL(20,6) | 当前市值 |
| `unrealized_pnl` | DECIMAL(20,6) | 浮动盈亏 |
| `pnl_rate` | DECIMAL(20,6) | 盈亏比例 |
| `position_ratio` | DECIMAL(20,6) | 仓位占比 |
| `sort_order` | INT | 排序 |
| `remark` | VARCHAR(512) | 备注 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

建议约束：

- `uk_position_snapshot_item_symbol(snapshot_id, symbol)`

建议索引：

- `idx_position_snapshot_item_snapshot(snapshot_id)`
- `idx_position_snapshot_item_symbol(symbol)`

## 7. 计算规则

后端保存前必须重新计算，前端计算只用于实时预览。

```text
cost_amount = holding_quantity * cost_price
market_value = holding_quantity * current_price
unrealized_pnl = market_value - cost_amount
pnl_rate = unrealized_pnl / cost_amount
position_ratio = market_value / total_market_value
```

边界：

- `holding_quantity` 必须大于 0。
- `cost_price`、`current_price` 必须大于等于 0。
- `available_quantity` 不能大于 `holding_quantity`。
- `cost_amount = 0` 时 `pnl_rate` 返回 0，避免除零。
- 总市值为 0 时 `position_ratio` 返回 0。

## 8. API 草案

基础路径：`/api/v1/position-snapshots`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/position-snapshots` | 查询快照列表 |
| `GET` | `/api/v1/position-snapshots/latest` | 最近一次已确认快照 |
| `GET` | `/api/v1/position-snapshots/{id}` | 快照详情 |
| `POST` | `/api/v1/position-snapshots` | 新建草稿或直接确认 |
| `PUT` | `/api/v1/position-snapshots/{id}` | 更新草稿 |
| `PATCH` | `/api/v1/position-snapshots/{id}/confirm` | 确认草稿 |
| `PATCH` | `/api/v1/position-snapshots/{id}/cancel` | 作废快照 |

### 创建请求示例

```json
{
  "snapshotDate": "2026-06-26",
  "snapshotTime": "2026-06-26T15:05:00",
  "snapshotName": "2026-06-26 收盘持仓",
  "sourceType": "MANUAL",
  "snapshotStatus": "DRAFT",
  "remark": "手工根据券商持仓页录入",
  "items": [
    {
      "symbol": "300750",
      "name": "宁德时代",
      "marketType": "SZ",
      "holdingQuantity": 100,
      "availableQuantity": 100,
      "costPrice": 210.00,
      "currentPrice": 220.00,
      "remark": "观察做 T"
    }
  ]
}
```

## 9. 前端页面设计

建议新增独立菜单：`持仓快照`。

页面区块：

1. 最近快照汇总卡片。
2. 历史快照列表。
3. 新建/编辑快照抽屉或页面。
4. 快照详情表格。

新建快照表格列：

- 股票代码
- 股票名称
- 持仓数量
- 可用数量
- 成本价
- 当前价
- 持仓成本
- 当前市值
- 浮动盈亏
- 盈亏比例
- 仓位占比
- 备注

UI 约定：

- A 股习惯：盈利红色，亏损绿色。
- 计算字段只读展示。
- 保存草稿和确认快照使用明确按钮。
- 确认前弹窗提示“确认后默认不可编辑”。

## 10. 后续图片识别接入点

图片识别不新增新的正式业务表，而是生成持仓快照草稿。

后续可新增：

- `ai_import_task`
- `PositionImageRecognitionService`
- `MockPositionRecognitionProvider`
- `GlmPositionRecognitionProvider`

图片识别完成后调用同一个创建快照草稿 API。

## 11. 验收清单

- [x] 新建草稿时后端重新计算所有金额字段。
- [x] 修改草稿时覆盖明细并重新计算汇总。
- [x] 确认后不能普通编辑。
- [x] 作废后默认列表不展示或以筛选展示。
- [x] 最近快照只返回最新 `CONFIRMED`。
- [x] 同一快照内不允许重复股票代码。
- [x] 前端 remote 模式可落 DB。
- [x] 前端 mock 模式不影响后端数据。
- [x] 文档包含请求/响应示例。
