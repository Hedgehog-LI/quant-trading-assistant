# Market Research API

> 实现状态：P1.10-A/A1 前后端候选已实现（2026-08-14）；`1/5/10/20/50` 查询窗口的自动化与 mock 浏览器通过，真实数据 remote 运行时待验。

## 1. 能力边界

本组接口把已落库的板块收盘排行计算为可解释、可重算的研究结果。它只读原始行情事实，衍生结果写入独立分析表；查询链路不会调用 LongPort，也不会回写原始表。

- 当前范围固定为 `RANKED_UNIVERSE`，表示 provider 返回的排行样本，**不代表全市场**。
- `sourceQuoteTime` 当前来自 LongPort HTTP 响应的 `Date` 头，表示 provider 响应时间，不是交易所逐笔成交时间。
- `flowMetricNature=UNAVAILABLE` 时 `capitalFlow=null`；禁止用 `0` 冒充没有资金流入。
- 雷达只输出相对强弱与轮动状态，不输出买入、卖出或收益预测，不构成投资建议。
- 查询窗口支持 `1/5/10/20/50`。`1` 表示最新合格 CLOSE 批次的当日横截面强度，不生成轮动结论；`5/10/20/50` 使用已发布相对强弱和固定 5 日轮动动量。

## 2. 公式与发布语义

### 2.1 相对强弱

同一窗口使用固定板块 cohort。每日基准收益是 cohort 等权收益，板块相对收益为窗口内对数相对收益：

```text
benchmarkReturn(t) = average(sectorReturn(t))
relativeReturn = exp(sum(log(1 + sectorReturn) - log(1 + benchmarkReturn))) - 1
```

最终按相对收益计算平均名次和 `[0,1]` 百分位；并列值使用平均名次。所有收益字段使用小数比率，例如 `0.024` 表示 `2.4%`。

### 2.2 轮动持续性

固定 5 日窗口计算平均名次百分位、总体标准差、头部占用率、连续领涨/落后天数和窗口首尾位次变化。雷达四象限仅用于观察：

| 状态 | 解释 |
| --- | --- |
| `LEADING` | 强度较高，短期位次仍在改善或持平 |
| `IMPROVING` | 强度尚低，但短期位次改善 |
| `WEAKENING` | 强度较高，但短期位次转弱 |
| `LAGGING` | 强度和短期位次均偏弱 |
| `INSUFFICIENT_DATA` | 样本不足，不能分类 |

### 2.3 一日强度

`window=1` 直接读取最新合格 `CLOSE` 排行事实，使用同一批次板块的等权收益作为共同基准，
计算当日收益、对数相对收益和并列友好的横截面强度百分位。它不创建
`sector_analytics_publication_batch`，响应通过 `analysisMode=ONE_DAY_STRENGTH`、
`rotationAvailable=false` 和原始 `sourceBatchId` 明确来源。

一日模式不输出改善、转弱、连续性或排名动量；所有板块的 `rotationState` 固定为
`INSUFFICIENT_DATA`，原因码包含 `ONE_DAY_STRENGTH_ONLY` 与 `ROTATION_REQUIRES_5_DAYS`。

### 2.4 原子发布

一次雷达发布同时绑定强度 run、5 日动量 run、源批次集合、公式版本和参数哈希。只有两种公式都成功且 scope/market/as-of 一致时才发布；查询端只读取 `PUBLISHED` 批次，不读取计算中的半成品。相同输入重复计算会复用同一发布批次。

## 3. 接口

### 3.1 数据就绪状态

```http
GET /api/v1/market-research/readiness?market=CN
```

`market` 支持 `CN/HK/US`。返回最新成功收盘批次、样本量、固定期望样本量 `100`、覆盖率、来源时间、质量状态和原因码。没有收盘批次时返回 HTTP 200，但 `qualityStatus=NO_DERIVED_DATA`；前端必须展示空态，不得合成研究结论。

### 3.2 手工重算并发布

```http
POST /api/v1/market-research/calculations?market=CN&asOfDate=2026-08-13&window=20
```

请求参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `market` | 否 | 默认 `CN`，支持 `CN/HK/US` |
| `asOfDate` | 否 | 默认当前日期；只使用该日及以前已落库的成功 CLOSE 批次 |
| `window` | 否 | 多日发布窗口，默认 `20`，仅支持 `5/10/20/50`；动量固定为 `5`。一日强度无需调用本接口 |

成功返回：

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "publicationBatchId": 31,
    "asOfDate": "2026-08-13",
    "strengthWindowDays": 20,
    "momentumWindowDays": 5,
    "status": "PUBLISHED",
    "sectorCount": 42,
    "reused": false
  }
}
```

数据不足、来源时间缺失、样本过小或 HK/US 长窗口缺少权威交易日历时，返回 HTTP 400 + `BUSINESS_RULE_VIOLATION`，且不产生发布批次。

### 3.3 市场雷达

```http
GET /api/v1/market-research/radar?market=CN&window=20
```

`window=1` 返回最新合格 CLOSE 原始批次的当日强度；`publicationBatchId`、计算 run ID、
`momentumFormulaCode` 和 `publishedAt` 为 `null`，`sourceBatchId` 指向来源排行批次。
`window=5/10/20/50` 返回最新已发布批次、强度/动量 run ID、公式与参数身份、数据水位、
覆盖率、质量原因以及所有板块的相对收益、百分位、持续性和四象限状态。对应数据不存在时，
沿用当前全局异常映射返回 HTTP 400 + `RESOURCE_NOT_FOUND`。

前端必须展示：

- `asOfDate`、`sourceQuoteTime`、`publishedAt` 和覆盖率。
- `scopeDescription=排行样本，不代表全市场`。
- `flowMetricNature`；为 `UNAVAILABLE` 时显示“暂无真实资金流口径”，不得显示 0。
- 板块级 `evidence` 和 `reasonCodes`，不得只显示颜色或综合分。

### 3.4 排行历史

```http
GET /api/v1/market-research/sectors/ranking-history?market=CN&window=20&days=20
```

`window=1` 返回最多 120 个已落库合格 CLOSE 交易日的当日强度历史点，并使用
`sourceBatchId` 标识来源；多日窗口返回已发布交易日的板块历史点。`days` 小于 1 时按 1，
大于 120 时按 120。

### 3.5 板块详情

```http
GET /api/v1/market-research/sectors/123?market=CN&window=20&days=20
```

`sectorId` 是 `market_sector_identity.id` 稳定内部身份，不是 provider 临时代码。返回板块名称、provider 标识、taxonomy 版本、领先证券、关联跟踪证券、历史轨迹和数据质量。
`window=1` 的历史来自已落库 CLOSE 原始事实；多日窗口来自已发布结果。对应数据不存在时返回
HTTP 400 + `RESOURCE_NOT_FOUND`。

## 4. 自动触发

`MarketSectorCollectionScheduler` 成功保存一个市场的 CLOSE 排行批次后，会尝试为 `5/10/20/50` 强度窗口计算研究结果；每个雷达发布均配套固定 5 日动量。原始样本不足属于可解释业务状态，只记录并跳过，不把已成功的原始采集改成失败。

## 5. 数据表与版本

| Migration | 内容 |
| --- | --- |
| V19 | 稳定板块身份和 readiness 门禁基础（既有） |
| V20 | 计算 run、发布批次、发布成员、排行稳定身份和 provider 来源时间 |
| V21 | 相对强弱与轮动持续性衍生结果 |
| V22 | 强度/动量双窗口发布身份、跨市场复合约束和查询索引 |

生产部署必须让 Flyway 顺序执行 V19-V22。禁止修改已经发布的 migration。

## 6. 当前未完成

- Docker/MySQL、真实 provider 数据、服务器部署和 remote 浏览器验收。
- 真实资金流、成交额集中度、量价确认、板块异动提醒。
- P1.10-B 候选扫描与 P1.10-C 个股决策台。
