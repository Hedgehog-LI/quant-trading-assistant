# Portfolio 持仓账本 API

> **重要声明：** 本系统是交易辅助记录和复盘工具，不自动交易，不连接券商，不保存真实密钥，不接实时行情，不输出"稳赚""必涨""无风险"结论。持仓账本基于手工录入的交易流水按 FIFO 规则实时计算，当前价为手工维护，所有盈亏数据仅用于复盘参考，不构成任何投资建议。

基础路径：`/api/v1/portfolio`

本模块依赖 `trade_journal`（交易流水）与 `portfolio_price_snapshot`（手工当前价）。类比：买入 = 进货、卖出 = 出货、当前持仓 = 库存。

---

## 1. GET /summary 汇总统计

返回全部持仓账本的汇总：累计已实现/浮动盈亏、胜率、平均收益率、平均持仓天数。

**响应示例：**

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "realizedPnl": 200.000000,
    "unrealizedPnl": 400.000000,
    "totalPnl": 600.000000,
    "currentCost": 4000.000000,
    "currentMarketValue": 4400.000000,
    "closedTradeCount": 1,
    "winCount": 1,
    "lossCount": 0,
    "winRate": 1.000000,
    "averageReturnPoint": 20.000000,
    "averageHoldingDays": 10.000000,
    "warnings": [],
    "disclaimer": "持仓账本基于手工录入的交易记录按 FIFO 规则实时计算，仅供复盘参考..."
  },
  "timestamp": "2026-06-21T18:00:00"
}
```

| 字段 | 说明 |
|------|------|
| realizedPnl | 已实现盈亏合计（FIFO 买卖配对闭环） |
| unrealizedPnl | 浮动盈亏合计（未维护当前价的股票不计入） |
| totalPnl | realizedPnl + unrealizedPnl |
| currentCost | 当前持仓成本合计 |
| currentMarketValue | 当前持仓市值合计（未维护当前价的股票不计入） |
| closedTradeCount | 已结算交易数 |
| winCount / lossCount | 盈利 / 亏损交易数（盈亏平衡不计入） |
| winRate | winCount / closedTradeCount（0~1） |
| averageReturnPoint | 已结算交易平均收益率，百分点（按交易笔数等权） |
| averageHoldingDays | 已结算交易平均持仓天数（按交易笔数等权） |
| warnings | 告警提示（超持仓异常、缺价、无已结算交易等） |
| disclaimer | 免责声明 |

---

## 2. GET /positions 当前持仓

返回当前仍有持仓的股票列表（已清仓的不返回）。

**响应示例：**

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": [
    {
      "symbol": "000002",
      "name": "万科A",
      "quantity": 200,
      "averageCost": 20.000000,
      "costAmount": 4000.000000,
      "currentPrice": 22.000000,
      "marketValue": 4400.000000,
      "unrealizedPnl": 400.000000,
      "unrealizedReturnPoint": 10.000000,
      "firstBuyDate": "2026-01-01",
      "holdingDays": 171,
      "warnings": []
    }
  ],
  "timestamp": "2026-06-21T18:00:00"
}
```

| 字段 | 说明 |
|------|------|
| averageCost | 平均成本（含买入费用摊） |
| costAmount | 持仓成本合计 |
| currentPrice / marketValue / unrealizedPnl / unrealizedReturnPoint | 未维护当前价时为 null，并在 warnings 提示 |
| unrealizedReturnPoint | 浮动收益率，百分点 |
| firstBuyDate | 剩余持仓中最早买入日期 |
| holdingDays | today − firstBuyDate |

---

## 3. GET /closed-trades 已结算交易

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| symbol | string | 否 | 按股票代码过滤 |
| fromDate | date | 否 | 卖出日期起始（按 sellDate 过滤） |
| toDate | date | 否 | 卖出日期截止 |

**响应示例：**

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": [
    {
      "symbol": "000001",
      "name": "平安银行",
      "buyDate": "2026-01-01",
      "sellDate": "2026-01-11",
      "holdingDays": 10,
      "quantity": 100,
      "buyAveragePrice": 10.000000,
      "sellAveragePrice": 12.000000,
      "costAmount": 1000.000000,
      "sellAmount": 1200.000000,
      "totalFee": 0.000000,
      "realizedPnl": 200.000000,
      "returnPoint": 20.000000,
      "profitable": true,
      "buyJournalIds": [1],
      "sellJournalId": 2
    }
  ],
  "timestamp": "2026-06-21T18:00:00"
}
```

| 字段 | 说明 |
|------|------|
| buyDate | 买入日期（多批次时取最早） |
| holdingDays | sellDate − buyDate |
| buyAveragePrice | 买入均价（含买入费用摊） |
| sellAveragePrice | 卖出均价 |
| costAmount | 配对买入成本合计 |
| sellAmount | 卖出毛收入（price × quantity） |
| totalFee | 本笔交易总费用（买入费用按比例摊 + 卖出费用） |
| realizedPnl | 已实现盈亏（卖出净收入 − 配对买入成本） |
| returnPoint | 收益率，百分点（realizedPnl / costAmount × 100） |
| profitable | 是否盈利 |
| buyJournalIds | 配对的买入交易记录 ID 列表（可多个） |
| sellJournalId | 卖出交易记录 ID |

---

## 4. GET /symbol/{symbol} 单股票详情

返回某只股票的当前持仓 + 已结算交易 + 原始交易流水。

若该股票存在卖出超过持仓的异常数据，返回 `INSUFFICIENT_HOLDING` 业务错误。

**响应示例：**

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "position": { "...PositionVO" : "..." },
    "closedTrades": [ { "...ClosedTradeVO" : "..." } ],
    "flows": [ { "...TradeJournalVO" : "..." } ],
    "warnings": []
  },
  "timestamp": "2026-06-21T18:00:00"
}
```

| 字段 | 说明 |
|------|------|
| position | 当前持仓（无持仓时为 null） |
| closedTrades | 已结算交易列表 |
| flows | 原始交易流水（trade_journal 记录） |
| warnings | 告警提示 |

---

## 5. POST /prices 新增或更新手工当前价

相同 symbol + priceDate 视为同一条，覆盖价格（upsert）。

**请求示例：**

```json
{
  "symbol": "000001",
  "name": "平安银行",
  "currentPrice": 11.50,
  "priceDate": "2026-01-12",
  "note": "收盘价"
}
```

**业务规则：**

- `symbol` 必填，自动 trim + 大写。
- `currentPrice` 必填，> 0。
- `priceDate` 必填。
- 相同 symbol + priceDate 已存在则更新价格，否则新增。

**响应示例：**

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "id": 1,
    "symbol": "000001",
    "name": "平安银行",
    "currentPrice": 11.500000,
    "priceDate": "2026-01-12",
    "note": "收盘价",
    "createdAt": "2026-06-21T18:00:00",
    "updatedAt": "2026-06-21T18:00:00"
  },
  "timestamp": "2026-06-21T18:00:00"
}
```

---

## 6. GET /prices 查询手工当前价

返回全部手工当前价（按 priceDate 倒序）。响应为 `PriceSnapshotVO` 列表，结构同 POST /prices 的响应 data。

---

## FIFO 计算说明

买卖配对采用 **FIFO（先进先出）**：卖出时优先消耗最早买入的批次。

- **买入批次**：买入成本 = `price × quantity + totalFee`；单股成本 = 买入成本 / quantity。
- **卖出配对**：卖出收入 = `price × quantity − totalFee`；按 FIFO 从最早买入批次依次扣减。
- **已实现盈亏**：`realizedPnl = 卖出净收入 − 对应买入成本`。
- **收益率**：`returnPoint = realizedPnl / 买入成本 × 100`（百分点）。
- **持有天数**：`holdingDays = sellDate − buyDate`；多批次配对时取最早买入日期。
- **手续费**：`totalFee = 买入费用（按数量比例摊销）+ 卖出费用`。

**时间排序**：流水按 `trade_date ASC`、`trade_time`（空值排到当日最晚）`ASC`、`id ASC` 排序，保证 FIFO 顺序稳定。

---

## 三个关键行为

1. **未维护当前价**：该股票浮动盈亏相关字段（currentPrice / marketValue / unrealizedPnl / unrealizedReturnPoint）返回 null，并在 warnings 提示；不计入 summary 的浮动盈亏与市值合计。
2. **卖出超过持仓**（数据不一致）：
   - 聚合查询（/summary、/positions）→ 该股票标记异常加入 warnings，不参与胜率/均值/已实现盈亏统计，其余股票正常计算（不静默、不整体崩溃）。
   - 单股票详情（GET /symbol/{symbol}）→ 直接抛 `INSUFFICIENT_HOLDING` 业务错误，精确定位。
3. **统计口径**：胜率、平均收益率、平均持仓天数均按已结算交易**笔数等权**（符合"每次交易"的复盘直觉）。

---

## 已知限制

- **当前价为手工录入**，不是实时行情，可能不准确或缺失。
- **交易流水为手工录入**，不连接券商，不自动同步真实交易。
- **FIFO 为默认规则**，未来可扩展手工配对。
- **不考虑**融资融券、做空、分红送转、除权除息等复杂场景（这些会显著影响成本与盈亏，需单独设计）。
- **不考虑**多账户、多币种。
- **实时计算**：每次查询全量 FIFO，数据量到万级时可能变慢，未来可引入物化持仓快照表（每日收盘批算好持仓与已实现盈亏）。
- 本功能仅用于记录和复盘，**不构成投资建议**。

---

## 错误码

| code | 场景 |
|------|------|
| SUCCESS | 成功 |
| PARAM_ERROR | 参数错误（如 currentPrice <= 0、symbol 为空） |
| VALIDATION_ERROR | Spring Validation 校验失败 |
| INSUFFICIENT_HOLDING | 卖出数量超过持仓（GET /symbol/{symbol}） |
| PORTFOLIO_CALCULATION_ERROR | 持仓账本计算异常 |
| PRICE_SNAPSHOT_NOT_FOUND | 当前价快照不存在 |
| INTERNAL_ERROR | 系统内部错误 |

---

## 前端对接说明

- 本模块为纯查询 + 价格维护，无写交易流水接口（交易流水仍走 `/api/v1/trade-journals`）。
- 前端建议页面：持仓列表（/positions）、已结算交易（/closed-trades）、汇总看板（/summary）、单股票详情（/symbol/{symbol}）、手工当前价维护（/prices）。
- 所有金额字段精度 6 位小数（DECIMAL_SCALE=6），前端展示时按需格式化。
- 盈亏收益率字段（returnPoint / unrealizedReturnPoint / averageReturnPoint）为**百分点**（如 20.0 表示 20%），前端展示时加 `%`。
- **交易记录费用字段为全量编辑语义**（PUT `/api/v1/trade-journals/{id}`）：更新时清空费用字段（请求体传 `null`）会归一为 `0.000000` 落库，不会残留旧费用；`totalFee` 传值时以其为准，为 `null` 时按 `commissionFee + stampTax + transferFee + otherFee` 求和（缺失项按 0）。所有 null 费用统一按 0 处理，更新后 VO 与 DB 一致。
- **前端 remote 模式当前仅交易账本（portfolio）已接入**：持仓 / 已结算交易 / 汇总 / 手工当前价走后端 REST API；交易记录、自选股、交易计划、盘后复盘等页面仍以浏览器 localStorage 为主。交易记录的 remote 写入不属于当前范围，后续再做。
- 当前价与交易流水均为手工录入，不连接券商、不接实时行情，所有盈亏仅用于复盘，不构成投资建议。
