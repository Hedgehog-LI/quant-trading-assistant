# Today MVP API 文档

> **重要声明：** 本系统是交易辅助记录和复盘工具，不自动交易，不连接券商，不保存真实密钥，不输出"稳赚""必涨""无风险"结论。所有交易相关输出仅为辅助信号 + 风险提示 + 人工确认。

基础路径：`/api/v1`

---

## 1. Dashboard 今日工作台

### GET /api/v1/dashboard/today

聚合查询今日看板数据。

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| date | date (yyyy-MM-dd) | 否 | 默认当天 |

**响应示例：**

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "date": "2026-06-08",
    "enabledWatchlistCount": 5,
    "activePlanCount": 3,
    "todayJournalCount": 2,
    "pendingReviewCount": 4,
    "todayReviewCount": 1,
    "riskWarnings": [
      "有 4 条交易记录待复盘"
    ],
    "highAttentionStocks": [
      {
        "id": 1,
        "symbol": "300750",
        "name": "宁德时代",
        "attentionLevel": "HIGH",
        "enabled": true
      }
    ],
    "todayPlans": [],
    "pendingReviewJournals": [],
    "todos": [
      {
        "code": "PENDING_REVIEW",
        "level": "WARNING",
        "title": "待复盘交易",
        "description": "存在尚未复盘的交易记录，及时复盘可沉淀经验",
        "count": 4,
        "targetPath": "/journal?reviewStatus=PENDING"
      }
    ]
  },
  "timestamp": "2026-06-08T09:00:00"
}
```

**`todos` 字段（v0.1.1 新增）：** 结构化待办列表，由后端聚合，remote 模式直接使用；mock 模式由前端按相同口径纯函数计算。每项含 `code` / `level` / `title` / `description` / `count` / `targetPath`，`count==0` 不返回。级别 `level`：`INFO` 提示 / `WARNING` 关注 / `RISK` 风险。待办码 `code`：

| code | level | 说明 |
|------|-------|------|
| `PENDING_REVIEW` | WARNING | 存在待复盘交易 |
| `UNLINKED_TRADE_PLAN` | INFO | 交易未关联计划 |
| `TRADE_AGAINST_PLAN` | WARNING | 关联计划 allowedToTrade=false |
| `MISSING_STOP_LOSS` | RISK | 今日买入交易未记录计划止损 |
| `STALE_POSITION_SNAPSHOT` | INFO | 最新已确认快照超过 3 个自然日 |
| `POSITION_RECONCILIATION_MISMATCH` | WARNING | 最新快照与截止时点 FIFO 账本数量不一致 |

待办只表达记录、数据质量和纪律事项，**不包含买卖建议**；点击跳转 `targetPath` 对应页面。

**日期口径（v0.1.1 最终修复）**：`date` 参数为空时按当天；非空时 `pendingReviewCount`、`pendingReviewJournals`、`riskWarnings` 以及 `PENDING_REVIEW` 待办均只统计 `trade_date <= date` 的交易，**绝不混入未来交易**；`STALE_POSITION_SNAPSHOT` / `POSITION_RECONCILIATION_MISMATCH` 待办取 `snapshot_date <= date` 的最新已确认快照。

---

## 2. Watchlist 自选股

### GET /api/v1/watchlist

查询自选股列表。

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| enabled | boolean | 否 | 按启用状态过滤 |
| keyword | string | 否 | 按名称或代码模糊搜索 |
| tradeStyle | string | 否 | SHORT_TERM / DO_T / SWING / OBSERVE |

### POST /api/v1/watchlist

新增自选股。symbol 会自动 trim 并转大写。

**请求示例：**

```json
{
  "symbol": "300750",
  "name": "宁德时代",
  "market": "A_SHARE",
  "groupName": "新能源",
  "watchReason": "关注锂电池龙头反弹",
  "tradeStyle": "DO_T",
  "attentionLevel": "HIGH",
  "supportPrice": 210.00,
  "resistancePrice": 240.00,
  "stopLossPrice": 200.00,
  "riskNote": "注意大盘系统性风险"
}
```

**业务规则：**
- `symbol` 必填，自动 trim + 大写，唯一。
- `name` 必填。
- `market` 必须为合法枚举值（A_SHARE / HK / US / ETF / OTHER）。
- `tradeStyle` 必须为合法枚举值。
- `attentionLevel` 必须为合法枚举值（HIGH / MEDIUM / LOW）。
- `supportPrice`、`resistancePrice`、`stopLossPrice` 大于 0。
- 如果同时填支撑位和压力位，压力位必须大于支撑位。

### GET /api/v1/watchlist/{id}

查询单条自选股。

### PUT /api/v1/watchlist/{id}

更新自选股。

### PATCH /api/v1/watchlist/{id}/enabled

启用/停用自选股。

```json
{ "enabled": false }
```

### DELETE /api/v1/watchlist/{id}

软删除（设置 enabled=false）。

---

## 3. Trade Plan 盘前计划

### GET /api/v1/trade-plans

查询交易计划列表。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| date | date | 否 | 按计划日期过滤 |
| symbol | string | 否 | 按股票代码过滤 |

### POST /api/v1/trade-plans

新增交易计划。

```json
{
  "planDate": "2026-06-08",
  "symbol": "300750",
  "name": "宁德时代",
  "planStatus": "DRAFT",
  "buyCondition": "突破220且放量",
  "sellCondition": "跌破210止损",
  "stopLossPrice": 210.00,
  "takeProfitPrice": 240.00,
  "plannedPositionRatio": 0.10,
  "maxLossAmount": 2000.00,
  "allowedToTrade": false,
  "riskNote": "注意量能配合",
  "notes": "观察分时走势再决定"
}
```

**业务规则：**
- 同一 `symbol + planDate` 只能有一条计划。
- `plannedPositionRatio` 范围 [0, 1]。
- `allowedToTrade=true` 时必须填写 `buyCondition`、`stopLossPrice`、`plannedPositionRatio`。
- `takeProfitPrice` 如果填写，必须大于 `stopLossPrice`。
- `planStatus` 必须为合法枚举值（DRAFT / ACTIVE / DONE / CANCELLED）。
- **计划只是用户的盘前纪律记录，不是交易建议。**

### GET /api/v1/trade-plans/{id}

### PUT /api/v1/trade-plans/{id}

### PATCH /api/v1/trade-plans/{id}/status

```json
{ "planStatus": "ACTIVE" }
```

---

## 4. Risk Calculator 风控计算器

### POST /api/v1/risk/calculations/position-size

计算建议仓位大小。

```json
{
  "totalCapital": 100000,
  "riskPercent": 0.01,
  "buyPrice": 50.00,
  "stopLossPrice": 48.00,
  "maxPositionRatio": 0.20,
  "lotSize": 100
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| totalCapital | decimal | 是 | 总资金，>0 |
| riskPercent | decimal | 是 | 单笔风险比例，(0, 0.1] |
| buyPrice | decimal | 是 | 计划买入价，>0 |
| stopLossPrice | decimal | 是 | 止损价，必须低于买入价 |
| maxPositionRatio | decimal | 是 | 单票最大仓位比例，[0, 1] |
| lotSize | int | 否 | 最小交易单位，默认 100 |

**计算公式：**

```text
riskAmount = totalCapital × riskPercent
perShareRisk = buyPrice − stopLossPrice
riskBasedQuantity = floor(riskAmount / perShareRisk)
positionCapQuantity = floor(totalCapital × maxPositionRatio / buyPrice)
finalQuantity = min(riskBasedQuantity, positionCapQuantity)
finalQuantity = floor(finalQuantity / lotSize) × lotSize
estimatedLoss = finalQuantity × perShareRisk
positionAmount = finalQuantity × buyPrice
positionRatio = positionAmount / totalCapital
```

**响应示例：**

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "riskAmount": 1000.000000,
    "perShareRisk": 2.000000,
    "riskBasedQuantity": 500,
    "positionCapQuantity": 400,
    "finalQuantity": 400,
    "estimatedLoss": 800.000000,
    "positionAmount": 20000.000000,
    "positionRatio": 0.200000,
    "riskLevel": "LOW",
    "warnings": [],
    "disclaimer": "本计算结果仅为辅助参考，不构成任何投资建议。..."
  },
  "timestamp": "2026-06-08T09:00:00"
}
```

**风险等级（riskLevel）：**

| 等级 | 含义 |
|------|------|
| LOW | 仓位和风险在合理范围 |
| MEDIUM | 存在需要关注的风险因素 |
| HIGH | 资金或止损距离不满足交易条件 |

---

## 5. Trade Journal 交易记录

### GET /api/v1/trade-journals

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| date | date | 否 | 按交易日期过滤 |
| symbol | string | 否 | 按股票代码过滤 |
| reviewStatus | string | 否 | PENDING / REVIEWED |

### POST /api/v1/trade-journals

```json
{
  "tradeDate": "2026-06-08",
  "symbol": "300750",
  "name": "宁德时代",
  "side": "BUY",
  "price": 220.50,
  "quantity": 100,
  "positionRatio": 0.10,
  "reason": "突破220放量买入",
  "planStopLoss": 210.00,
  "planTakeProfit": 240.00,
  "followedPlan": true,
  "emotionTags": ["CALM"],
  "mistakeTags": []
}
```

**业务规则：**
- `side` 必须为 BUY 或 SELL。
- `price > 0`，`quantity >= 1`。
- `amount` 自动计算 = `price × quantity`。
- `side=BUY` 且未填 `planStopLoss` → 返回 warning（不阻断）。
- `reviewStatus` 必须为合法枚举值（PENDING / REVIEWED）。
- 交易记录为手工录入，不连接券商。

**情绪标签：** CALM / FOMO / FEAR / REVENGE / HESITATION

**错误标签：** CHASE_HIGH / PANIC_SELL / NO_STOP_LOSS / OVERSIZED_POSITION / NO_PLAN / BROKE_RULE

### GET /api/v1/trade-journals/{id}

### PUT /api/v1/trade-journals/{id}

### PATCH /api/v1/trade-journals/{id}/review-status

```json
{ "reviewStatus": "REVIEWED" }
```

---

## 6. Review 盘后复盘

### GET /api/v1/reviews

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| date | date | 否 | 按复盘日期过滤 |
| symbol | string | 否 | 按股票代码过滤 |

### POST /api/v1/reviews

```json
{
  "reviewDate": "2026-06-08",
  "symbol": "300750",
  "title": "宁德时代 6月8日复盘",
  "marketContext": "大盘震荡，锂电池板块整体强势",
  "planSummary": "突破220买入，止损210",
  "actionSummary": "按计划执行，220.50买入",
  "rightThings": "严格执行了止损纪律",
  "wrongThings": "没有等待更好的入场价位",
  "ruleChanges": "突破确认后再入场，不追高超过3%",
  "nextActions": "明日关注230压力位",
  "linkedJournalIds": [1, 2]
}
```

**业务规则：**
- `reviewDate` 必填，`title` 必填。
- `symbol` 可为空（表示每日总复盘）。
- `linkedJournalIds` 中的 ID 必须存在，否则报 RESOURCE_NOT_FOUND。
- 创建复盘后，关联的交易记录自动标记为 REVIEWED。
- v0.1.1 一致性回算：新增/编辑/删除复盘后，对受影响交易记录（旧关联 ∪ 新关联）重新计算 reviewStatus——仍被任意复盘引用为 REVIEWED，否则恢复 PENDING。
- v0.1.1 删除保护：被任意复盘引用的交易记录禁止删除，返回 `JOURNAL_REFERENCED_BY_REVIEW`，需先在相关复盘中移除关联。
- v0.1.1 计划关联校验（POST/PUT `/trade-journals`）：`planId` 非空时计划必须存在（`TRADE_PLAN_NOT_FOUND`）、未取消（`TRADE_PLAN_NOT_LINKABLE`）、证券代码一致（`TRADE_PLAN_SYMBOL_MISMATCH`）；`planId` 为空允许保存；`allowedToTrade=false` 不阻断记录，由工作台单独提醒。交易记录响应新增可空展示字段 `planDate`、`planStatus`。
- v0.1.1 解除计划关联（PUT `/trade-journals/{id}`）：请求体新增 `unlinkPlan`（Boolean）实现三态更新。`unlinkPlan=true` → `plan_id` 置 NULL（解绑）；`unlinkPlan!=true` 且 `planId!=null` → 更新为新计划；其余情况保持原值（部分更新语义不变）。禁止用 0/-1 等魔法值表达解绑。

### GET /api/v1/reviews/{id}

### PUT /api/v1/reviews/{id}

---

## 统一错误响应

```json
{
  "success": false,
  "code": "DUPLICATE_RESOURCE",
  "message": "Symbol already exists in watchlist: 300750",
  "data": null,
  "timestamp": "2026-06-08T09:00:00"
}
```

**错误码（ErrorCodeEnum）：**

| code | 场景 |
|------|------|
| SUCCESS | 成功 |
| PARAM_ERROR | 参数错误 |
| VALIDATION_ERROR | Spring Validation 校验失败 |
| RESOURCE_NOT_FOUND | 资源不存在 |
| DUPLICATE_RESOURCE | 唯一键冲突 |
| BUSINESS_RULE_VIOLATION | 违反业务规则 |
| INVALID_ENUM_CODE | 无效的枚举值 |
| RISK_CALCULATION_ERROR | 风控计算异常 |
| TRADE_PLAN_NOT_FOUND | 关联的交易计划不存在 |
| TRADE_PLAN_NOT_LINKABLE | 交易计划已取消，不可关联 |
| TRADE_PLAN_SYMBOL_MISMATCH | 交易记录与计划的证券代码不一致 |
| JOURNAL_REFERENCED_BY_REVIEW | 交易记录被复盘引用，不可删除 |
| POSITION_SNAPSHOT_COMPARISON_INVALID | 持仓快照对比参数非法（非 CONFIRMED 或时间顺序非法） |
| INTERNAL_ERROR | 系统内部错误 |

---

## 枚举值速查

| 枚举 | code 值 | 描述 |
|------|---------|------|
| MarketTypeEnum | A_SHARE, HK, US, ETF, OTHER | 市场类型 |
| TradeStyleEnum | SHORT_TERM, DO_T, SWING, OBSERVE | 交易风格 |
| AttentionLevelEnum | HIGH, MEDIUM, LOW | 关注等级 |
| PlanStatusEnum | DRAFT, ACTIVE, DONE, CANCELLED | 计划状态 |
| TradeSideEnum | BUY, SELL | 交易方向 |
| ReviewStatusEnum | PENDING, REVIEWED | 复盘状态 |
| RiskLevelEnum | LOW, MEDIUM, HIGH | 风险等级 |
