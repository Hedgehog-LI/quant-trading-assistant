# Position Snapshot 持仓快照 API

> 持仓快照用于记录某个时点券商账户中实际显示的持仓盘点。本模块不连接券商、不自动同步账户，也不会根据快照反推交易流水。金额仅供记录和复盘参考，不构成投资建议。

基础路径：`/api/v1/position-snapshots`

## 1. 数据口径

### 1.1 状态

| 状态 | 说明 | 可编辑 |
| --- | --- | --- |
| `DRAFT` | 草稿，可继续修改 | 是 |
| `CONFIRMED` | 已确认的正式历史快照 | 否 |
| `CANCELED` | 已作废，默认历史列表不展示 | 否 |

允许的状态流转：

```text
DRAFT -> CONFIRMED
DRAFT -> CANCELED
CONFIRMED -> CANCELED
```

### 1.2 来源

| 来源 | 说明 |
| --- | --- |
| `MANUAL` | 手工录入，当前阶段使用 |
| `IMAGE_RECOGNITION` | 图片识别，预留给后续自动填表 |
| `CSV_IMPORT` | CSV 导入，预留 |

### 1.3 后端计算字段

客户端只提交数量、成本价和当前价。以下字段由后端统一重新计算：

```text
costAmount = holdingQuantity * costPrice
marketValue = holdingQuantity * currentPrice
unrealizedPnl = marketValue - costAmount
pnlRate = unrealizedPnl / costAmount
positionRatio = marketValue / totalMarketValue
```

所有金额和比例保留 6 位小数，使用 `HALF_UP` 舍入。`pnlRate` 和 `positionRatio` 使用小数表示，例如 `0.050000` 表示 5%。

## 2. POST / 创建快照

可创建 `DRAFT` 或直接创建 `CONFIRMED` 快照。不能直接创建 `CANCELED` 快照。

请求示例：

```json
{
  "snapshotDate": "2026-06-27",
  "snapshotTime": "2026-06-27T15:05:00",
  "snapshotName": "2026-06-27 收盘持仓",
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

响应示例：

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "id": 1,
    "snapshotDate": "2026-06-27",
    "snapshotTime": "2026-06-27T15:05:00",
    "snapshotName": "2026-06-27 收盘持仓",
    "sourceType": "MANUAL",
    "snapshotStatus": "DRAFT",
    "totalCostAmount": 21000.000000,
    "totalMarketValue": 22000.000000,
    "totalUnrealizedPnl": 1000.000000,
    "totalPnlRate": 0.047619,
    "positionCount": 1,
    "remark": "手工根据券商持仓页录入",
    "createdAt": "2026-06-27T15:06:00",
    "updatedAt": "2026-06-27T15:06:00",
    "items": [
      {
        "id": 1,
        "snapshotId": 1,
        "symbol": "300750",
        "name": "宁德时代",
        "marketType": "SZ",
        "holdingQuantity": 100,
        "availableQuantity": 100,
        "costPrice": 210.000000,
        "currentPrice": 220.000000,
        "costAmount": 21000.000000,
        "marketValue": 22000.000000,
        "unrealizedPnl": 1000.000000,
        "pnlRate": 0.047619,
        "positionRatio": 1.000000,
        "sortOrder": 0,
        "remark": "观察做 T"
      }
    ]
  },
  "timestamp": "2026-06-27T15:06:00"
}
```

业务规则：

- `snapshotDate` 必须与 `snapshotTime` 中的日期一致。
- `items` 必传，但允许空数组，用于记录空仓状态。
- 同一快照内 `symbol` 标准化后不能重复。
- `holdingQuantity` 必须大于 0。
- `availableQuantity` 为空时按 `holdingQuantity` 处理，且不能超过持仓数量。
- `costPrice`、`currentPrice` 必须大于等于 0。
- `marketType` 支持 `SH`、`SZ`、`BJ`、`UNKNOWN`；为空时按 `UNKNOWN` 处理。

## 3. PUT /{id} 更新草稿

整批覆盖草稿的基础信息和全部持仓明细。只有 `DRAFT` 可更新，来源和状态不能通过本接口修改。

请求示例：

```json
{
  "snapshotDate": "2026-06-27",
  "snapshotTime": "2026-06-27T15:10:00",
  "snapshotName": "2026-06-27 收盘持仓修正版",
  "remark": "重新核对数量",
  "items": []
}
```

响应结构与创建接口相同。

## 4. PATCH /{id}/confirm 确认草稿

将 `DRAFT` 流转为 `CONFIRMED`。确认后不能再通过普通更新接口编辑。

请求体：无。

响应：确认后的快照详情。

## 5. PATCH /{id}/cancel 作废快照

将 `DRAFT` 或 `CONFIRMED` 流转为 `CANCELED`。本模块不提供硬删除接口。

请求体：无。

响应：作废后的快照详情。

## 6. GET / 查询历史快照

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fromDate` | date | 否 | 快照日期起始，包含边界 |
| `toDate` | date | 否 | 快照日期结束，包含边界 |
| `status` | string | 否 | `DRAFT` / `CONFIRMED` / `CANCELED` |
| `sourceType` | string | 否 | 来源筛选 |
| `includeCanceled` | boolean | 否 | 默认 `false`；未传 status 时是否包含作废记录 |

说明：显式指定 `status=CANCELED` 时，即使 `includeCanceled=false` 也会返回已作废记录。

响应 `data` 为快照汇总数组，不包含 `items`。按 `snapshotTime DESC, id DESC` 排序。

## 7. GET /latest 查询最近快照

返回 `snapshotTime` 最新的一条 `CONFIRMED` 快照及全部明细。草稿和已作废快照不参与查询。

尚无已确认快照时请求成功，`data` 为空。

## 8. GET /{id} 查询详情

返回指定快照的汇总字段和全部明细，明细按 `sortOrder ASC` 排序。

## 9. 主要错误码

| 错误码 | 场景 |
| --- | --- |
| `VALIDATION_ERROR` | Bean Validation 入参错误 |
| `INVALID_ENUM_CODE` | 状态、来源或市场编码错误 |
| `POSITION_SNAPSHOT_NOT_FOUND` | 快照 ID 不存在 |
| `POSITION_SNAPSHOT_NOT_EDITABLE` | 尝试编辑非草稿快照 |
| `POSITION_SNAPSHOT_INVALID_TRANSITION` | 非法状态流转 |
| `POSITION_SNAPSHOT_DUPLICATE_SYMBOL` | 同一快照存在重复股票代码 |
| `POSITION_SNAPSHOT_INVALID_ITEM` | 数量、价格或可用数量不合法 |

## 10. GET /comparison 两次已确认快照对比

仅支持 `CONFIRMED` 快照，且基准快照时间必须严格早于目标快照时间。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `baseSnapshotId` | long | 是 | 基准快照 ID（较早） |
| `targetSnapshotId` | long | 是 | 目标快照 ID（较晚） |

响应示例（节选）：

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "baseSnapshotId": 1,
    "targetSnapshotId": 2,
    "baseSnapshotTime": "2026-07-04T15:00:00",
    "targetSnapshotTime": "2026-07-05T15:00:00",
    "baseStatus": "CONFIRMED",
    "targetStatus": "CONFIRMED",
    "totalCostDelta": 1000.000000,
    "totalMarketValueDelta": 1500.000000,
    "totalUnrealizedPnlDelta": 500.000000,
    "positionCountDelta": 1,
    "items": [
      {
        "symbol": "A",
        "name": "A",
        "changeType": "INCREASED",
        "baseQuantity": 100,
        "targetQuantity": 200,
        "quantityDelta": 100,
        "baseCostPrice": 10.000000,
        "targetCostPrice": 10.000000,
        "marketValueDelta": 1000.000000,
        "unrealizedPnlDelta": 500.000000
      }
    ]
  }
}
```

变化类型 `changeType`：`NEW` 基准无目标有 / `INCREASED` 加仓 / `REDUCED` 减仓 / `CLOSED` 清仓 / `UNCHANGED` 未变化。

排序：变化类型（NEW → INCREASED → REDUCED → CLOSED → UNCHANGED）→ 目标市值降序 → symbol 升序。金额均 `BigDecimal`，缺失侧按 0 参与 delta。结果实时计算，不新增结果表，不构成投资建议。

## 11. GET /{snapshotId}/reconciliation 与 FIFO 账本对账

仅支持 `CONFIRMED` 快照。以数量为核心一致性判断，成本差异只展示不判错；对账只读，不会自动修改交易流水。

FIFO 纳入口径：

- `trade_date < snapshotDate` 全部纳入。
- 同日且 `trade_time` 为空默认纳入，并在 `warnings` 中提示时间精度不足。
- 同日且 `trade_time` 非空，仅 `trade_time <= snapshot_time` 纳入。

响应示例（节选）：

```json
{
  "data": {
    "snapshotId": 2,
    "snapshotTime": "2026-07-05T15:00:00",
    "matchedCount": 1,
    "mismatchCount": 1,
    "hasMismatch": true,
    "warnings": [],
    "items": [
      {
        "symbol": "A",
        "name": "A",
        "status": "QUANTITY_MISMATCH",
        "snapshotQuantity": 200,
        "ledgerQuantity": 100,
        "quantityDifference": 100,
        "snapshotCostPrice": 10.000000,
        "ledgerAverageCost": 10.000000
      }
    ]
  }
}
```

对账状态 `status`：`MATCHED` 一致 / `QUANTITY_MISMATCH` 数量不一致 / `SNAPSHOT_ONLY` 仅快照有 / `LEDGER_ONLY` 仅账本有。结果不替代券商正式对账单，不构成投资建议。

## 12. 当前阶段边界

- 已完成：DB、手工数据 API、草稿/确认/作废、历史列表、详情、最近已确认快照。
- 已完成：React 手工录入页、mock/remote adapter、页面联调、桌面与窄屏视觉验收。
- 后续能力：截图识别只生成可编辑草稿，必须由用户确认后才能成为正式快照。
