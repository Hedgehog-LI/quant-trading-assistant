# Market Research API

> 实现状态：P1.10-A/A1 前后端候选已实现（2026-08-14）；`1/5/10/20/50` 查询窗口的自动化与 mock 浏览器通过，真实数据 remote 运行时待验。MR-1A 市场全景只读后端 API 已实现（2026-08-16，见 §8；前端尚未接入）。

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

## 7. MR-0 数据与语义 PoC（QTA-V2-MR0-DATA-SEMANTICS-POC-20260815）

PoC 入口，不承诺 MR-1 稳定契约。公式、单位与缺失语义冻结于
`docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md`；表结构见 V23 migration。
分析/报告只读本地库，不外联任何公共源。

### 7.1 POST /api/v1/market-research/mr0-poc/ingest（受控写入口）

仅当 `qta.mr0-poc.ingest-enabled=true`（默认 `false`，`application.properties` 声明；本地运行由
脚本用 `--qta.mr0-poc.ingest-enabled=true` 或环境变量 `QTA_MR0_POC_INGEST_ENABLED` 开启）时执行，
否则 HTTP 400 `BUSINESS_RULE_VIOLATION`。body 与 SLICE-02 `IngestCommand` 同构，可省略。

```http
POST /api/v1/market-research/mr0-poc/ingest
Content-Type: application/json

{ "analysisStart": "2026-07-01", "analysisEnd": "2026-07-31",
  "warmupStart": "2026-04-01", "sampleSize": 150 }
```

未启用时响应（不泄露内部细节）：

```json
{ "success": false, "code": "BUSINESS_RULE_VIOLATION",
  "message": "MR-0 PoC ingest 未启用", "data": null, "timestamp": "2026-08-15T10:00:00" }
```

启用时返回 `data.universe/membership/dailyBar/moneyFlow` 各表 `inserted/updated/skipped` 计数、
`failures` 明细与 `sampleSymbols`（结构同 SLICE-02 `IngestResult`）。ingest 恒抓基准 SH.000001
日 K（样本循环后追加，指数点位非个股价格域，免字典 §3 个股 VWAP 自检；失败仅记入 `failures`
`stage=BENCHMARK_DAILY_BAR` 不中断）；基准不参与资金流/成分/`sampleSymbols`/`stock_basic` 身份回填。

### 7.2 GET /api/v1/market-research/mr0-poc/analyze（只读分析）

`start`/`end` 缺省为 `2026-07-01`/`2026-07-31`（D5 冻结窗口）。只读库、无进程内结果缓存，
每次调用重新查询；结果含 `analysisContentHash`（字段白名单规范化 JSON 的 sha256，
排除 `runId/generatedAt/durationMs/fetchedAt` 等运行元数据）。

```http
GET /api/v1/market-research/mr0-poc/analyze?start=2026-07-01&end=2026-07-31
```

```json
{ "success": true, "code": "SUCCESS", "data": {
  "runId": "6f9c…", "generatedAt": "2026-08-15T10:00:00", "durationMs": 812,
  "analysisStart": "2026-07-01", "analysisEnd": "2026-07-31",
  "universe": { "asOfDate": "2026-08-15", "universeSize": 151, "sampleSymbols": 150,
    "sampleSymbolList": ["BJ.430047", "…（Top-150 全列表，symbol 升序）"],
    "benchmarkSymbol": "SH.000001", "universeSymbolsSha256": "ab12…", "status": "OK",
    "providers": ["SINA_PUBLIC"],
    "caliber": "分析时点可见最新档快照（as_of 无上界；时点穿越由 TIME_POINT_LOOKAHEAD 族显式标记）；基准恒入快照不算样本" },
  "tradingDays": { "calendar": "INDEX_KLINE_DERIVED", "dates": ["2026-07-01", "…"], "count": 23,
    "providers": ["TENCENT_PUBLIC"] },
  "breadth": { "caliber": "adv/dec/flat 需 t 与 t-1 两根 bar，首日 t-1 取预热窗；adLine 首日种子=adv−dec",
    "providers": ["TENCENT_PUBLIC"],
    "daily": [ { "date": "2026-07-01", "advancing": 78, "declining": 61, "flat": 11,
      "validStocks": 150, "advanceRatio": 0.5200000000, "adLine": 17, "status": "OK" } ] },
  "industryTurnover": { "byIndustry": [ { "industryCode": "new_blhy", "industryName": "玻璃行业",
      "days": [ { "date": "2026-07-01", "sectorTurnover": 1234567890.00,
        "share": 0.0312000000, "lookaheadAffected": true } ] } ],
    "dailyMarket": [ { "date": "2026-07-01", "marketTurnover": 39580000000.00, "sumShare": 1.0000000000 } ],
    "coverageGap": { "count": 2, "symbols": ["BJ.920099", "…"] },
    "providers": ["TENCENT_PUBLIC"] },
  "volatility": { "asOfDate": "2026-07-31", "annualized": false, "status": "OK",
    "qualifiedStocks": 147, "excludedForWarmup": 3,
    "marketMedian": 0.018765432109, "marketP90": 0.031234567890, "providers": ["TENCENT_PUBLIC"] },
  "liquidityProxy": { "unit": "1/元", "status": "OK", "qualifiedStocks": 150, "zeroAmountRows": 0,
    "marketMedian": 0.000000031234, "marketP90": 0.000000112345, "providers": ["TENCENT_PUBLIC"] },
  "moneyFacts": { "byIndustry": [ { "industryCode": "new_blhy", "days": [ { "date": "2026-07-01",
        "sumMainNetInflow": -32102345.67, "cateNaValue": -23456789.01,
        "deviation": -8645556.66, "inconsistentCateNa": false } ] } ],
    "inconsistentCateNaDays": 0,
    "flowIntensity": { "providers": ["SINA_PUBLIC", "TENCENT_PUBLIC"], "window": "analysisWindow",
      "windowNetInflow": -1234567890.12, "windowTurnover": 912345678901.23,
      "value": -0.001353384702, "status": "OK" },
    "providers": ["SINA_PUBLIC"] },
  "analysisContentHash": "9a8b7c…",
  "metricAttributions": [ { "metric": "flowIntensity", "providers": ["SINA_PUBLIC", "TENCENT_PUBLIC"],
    "caliber": "Σ净流入/Σ成交额 同窗口同覆盖域（混源）" } ],
  "mixedMetrics": ["flowIntensity"] }, "timestamp": "2026-08-15T10:00:01" }
```

语义要点：样本=分析时点可见最新档快照按流通市值降序 Top-150（`sampleSize` 可调，默认 150；排除基准
与 null 市值行），`universeSize=Top-N+1`（恒含基准），`universeSymbolsSha256`=(Top-N 符号 ∪ 基准)
排序后逗号拼接的 sha256；全池快照行仅作事实保留，不进任何分母。预热不足（不足 21 根收盘，或 asOf
末交易日当日无 bar）时 `volatility.status=INSUFFICIENT_WARMUP` 且不输出任何部分数值；空有效池输出
原因码 `EMPTY_VALID_UNIVERSE`（`advanceRatio`/`adLine` 为 `null`，禁止 NaN/Infinity）；
`industryTurnover.share` 分母=覆盖域（有成分样本股），无成分股票计入 `coverageGap` 不入分母，
逐日 `sumShare=1±1e-6`，`share`/`sumShare` 输出 10 位小数；`moneyFacts.deviation` 为绝对差
（元）`Σ成员main_net_inflow − cate_na`（字典 M-15，cate_na 为 `null` 时 deviation 为 `null`），
只报告不判等；`flowIntensity` 为混源指标（新浪净流入 + 腾讯成交额），在 `mixedMetrics` 中显式列出。

### 7.3 GET /api/v1/market-research/mr0-poc/report（只读质量报告）

先执行 7.2 的分析，再生成八检查族质量报告。`format=markdown` 返回 `text/markdown` 文本，
其余返回 ApiResponse 包装 JSON。

```http
GET /api/v1/market-research/mr0-poc/report?start=2026-07-01&end=2026-07-31&format=json
```

```json
{ "success": true, "code": "SUCCESS", "data": { "families": [ {
  "family": "UNIT_ANOMALY", "status": "OK", "reasonCode": "NONE", "affectedCount": 0,
  "details": [] }, { "family": "TIME_POINT_LOOKAHEAD", "status": "WARN",
  "reasonCode": "CURRENT_MEMBERSHIP_FOR_HISTORY", "affectedCount": 3450,
  "details": ["当前成分聚合历史=时点穿越风险，PoC 显式假设", "…"] } ] },
  "timestamp": "2026-08-15T10:00:02" }
```

八检查族固定顺序：`COVERAGE`、`GAPS`、`DUPLICATES`、`STALENESS`、`TIME_POINT_LOOKAHEAD`、
`PROVIDER_MIXING`、`UNIT_ANOMALY`、`RECOMPUTE_CONSISTENCY`；每族为结构化对象
（`family/status/reasonCode/affectedCount/details`），`status∈OK/WARN/FAIL/BLOCKED`。
`DUPLICATES` 为信息级（uk 保证同源无重复，报告跨 data_source 并存事实）；
`STALENESS` 在 `market_calendar` CN 空表时记 WARN（本地事实，不外联）；
`RECOMPUTE_CONSISTENCY` 抽窗口中位日用原始行重算 advancing 数比对（重算总体=分析样本
`universe.sampleSymbolList`，排除基准与非样本符号，与广度分母同口径），`COVERAGE`/`GAPS` 同以
该样本清单为分母口径；基准 SH.000001 指数行免字典 §3 个股 VWAP 单位自检；跨进程重算一致性由
PoC 脚本两次运行（TEST-07）与"每次调用重读存储"用例证明。

## 8. MR-1A 市场全景（正式只读 API，2026-08-16）

`GET /api/v1/market-research/overview?market=CN&start=YYYY-MM-DD&end=YYYY-MM-DD`。首期仅支持
`CN`；三个参数全部必填；`start<=end` 且跨度 ≤365 天，否则 400 `VALIDATION_ERROR`（畸形日期同样
400）。只读已落库事实（TENCENT_PUBLIC 日 K + SINA_PUBLIC 证券池快照/行业成分），零外联、零写库、
不读取资金流。数据不足（窗口无基准、空样本、行业映射缺失）返回 200 + `metadata.qualityStatus`
（`NO_DATA`/`DEGRADED`）+ `quality.qualityFindings`，不抛 500。

数据边界（`metadata` 固定声明，前端必须展示）：`dataScope=SAMPLE`（最新快照流通市值 Top-150
样本，不是全市场）；行业分类 `SINA_INDUSTRY` 不是 PIT 申万；当前成分聚合历史存在时点穿越假设；
不提供官方口径全市场资金流（`unavailableMetrics=["OFFICIAL_MONEY_FLOW"]`，禁止由价量推算）。

覆盖与预热门禁（M-22，冻结阈值）：

- `barCoverage` = 窗口样本日 K 覆盖率（有效收盘对数 / 窗口交易日×样本数）；低于 **0.90** 记
  `LOW_BAR_COVERAGE` WARN。
- `membershipCoverage` = 有行业成分映射的样本证券占比；低于 **0.90** 记 `LOW_MEMBERSHIP_COVERAGE`
  WARN（整体 `qualityStatus=DEGRADED`）；低于 **0.50** 视为严重不足，追加
  `INDUSTRY_MIGRATION_BLOCKED` WARN 并将 `industryTurnoverMigration` 阻断为空——不得按极少数
  映射股票输出误导性行业图；全部缺失记 `INDUSTRY_MAPPING_MISSING` WARN。
- 预热门禁（设计 §9.2）：市场全景中期结论至少需要 **120** 个合格交易日；不足记
  `INSUFFICIENT_WARMUP` WARN 并整体 `DEGRADED`。短期可计算序列照常返回；`MA60`、60 日成交额
  中位数等预热不足指标保持 `null`，不得填 0。
  `metadata.qualifiedTradingDays` = 真实合格交易日数（含预热），**合格日 = 当日存在基准日 K 且
  当日样本日 K 覆盖率 ≥ 0.90**；空样本恒为 0，不得以基准 K 线数量冒充样本市场合格。预热读取为
  窗口前 300 个自然日（A 股最坏日历比约 1.5 自然日/交易日，120 交易日 ≤ 约 185 自然日；300 日
  提供明确冗余，最坏仍可覆盖 ≥190 个交易日）。
- 任何 WARN 发现使 `qualityStatus=DEGRADED`；窗口无基准交易日为 `NO_DATA`。真实 PoC 数据
  （barCoverage≈0.89、membershipCoverage≈0.67）必须返回 `DEGRADED`，不得返回 `OK`。

五类核心证据（公式冻结于 MR-0 指标字典 M-01..M-13/M-20/M-21）：

- `benchmarkSeries`：基准收盘、`dailyReturn`、成交额、`ma20`/`ma60`（读窗口前最多 180 自然日
  预热，观测不足为 null）、`drawdown`（相对窗口内累计峰值，≤0）。
- `activitySeries`：样本域成交额（M-03，非全市场）、20/60 日成交额中位数（含 t；观测不足为
  null）、`activityRatio=marketTurnover/turnoverMedian20`（M-04）、`activeStockRatio` 成交扩散
  （M-13：当日成交额严格大于自身前 20 个交易日中位数的证券占比）。只称"成交活跃度"，不称完整
  市场流动性。
- `breadthSeries`：adv/dec/flat/valid（M-06）、`advanceRatio`（M-07）、`adLine`（M-08 首日种子
  adv−dec，空有效池后中断）、`aboveMa20Stocks`/`aboveMa20Ratio`（M-09：历史不足 20 个收盘观测
  的证券不入分母）。
- `liquidityProxySeries`：`unit="1/元"`，逐日横截面 `medianIlliquidity`/`p90Illiquidity`（M-20
  `|r|/amount`，M-21 线性插值分位，12 位小数）、`qualifiedStocks`、`zeroAmountRows`（除零守卫）。
  只是日频价格冲击代理，不冒充买卖价差、盘口深度或真实交易冲击成本。
- `industryTurnoverMigration`：每日成交额降序前 8 行业单独返回，其余合并 `OTHER`（rank=null）；
  `turnoverShare` 分母仅为覆盖域（有行业映射的样本股，M-12），未映射证券数量与成交额只进
  `quality.coverageGap` 不入分母；`previousDayShareChange`=实体自身占比前值变化（OTHER 用其自身
  序列）、`median20Share(Change)`=近 20 个交易日（含 t）占比观测中位数及其偏差；占比是"交易
  注意力"，不得命名为资金流入。

`quality` 另含 `providerAttribution`（benchmarkDailyBar/sampleDailyBar=TENCENT_PUBLIC，
sampleUniverseSnapshot/industryMembership=SINA_PUBLIC）、`assumptions`（INDEX_KLINE_DERIVED
日历、时点穿越、NONE 复权除权失真、样本口径）与 `qualityFindings`（`BENCHMARK_DATA_MISSING`/
`EMPTY_SAMPLE`/`LOW_BAR_COVERAGE`/`LOW_MEMBERSHIP_COVERAGE`/`INDUSTRY_MIGRATION_BLOCKED`/
`INSUFFICIENT_WARMUP`/`EMPTY_VALID_TRADING_DAY`/`INDUSTRY_MAPPING_MISSING`/
`PARTIAL_INDUSTRY_MAPPING`；WARN 使整体状态降为 DEGRADED）。

请求与响应示例：

```http
GET /api/v1/market-research/overview?market=CN&start=2026-07-01&end=2026-07-31
```

```json
{ "success": true, "code": "SUCCESS", "data": {
  "metadata": { "market": "CN", "startDate": "2026-07-01", "endDate": "2026-07-31",
    "dataAsOf": "2026-07-31", "dataScope": "SAMPLE", "sampleSize": 150,
    "benchmarkSymbol": "SH.000001", "providerCodes": ["SINA_PUBLIC", "TENCENT_PUBLIC"],
    "taxonomyCode": "SINA_INDUSTRY", "barCoverage": 0.893333, "membershipCoverage": 0.673333,
    "qualifiedTradingDays": 131, "qualityStatus": "DEGRADED",
    "limitations": [
      "当前为 Top-N 样本（最新快照流通市值前 150 只），不是全市场",
      "行业分类为 SINA_INDUSTRY 快照，不是 PIT 申万行业",
      "行业成分按抓取日快照聚合历史，存在时点穿越假设",
      "不提供官方口径全市场资金流（OFFICIAL_MONEY_FLOW=UNAVAILABLE）"],
    "unavailableMetrics": ["OFFICIAL_MONEY_FLOW"] },
  "benchmarkSeries": [ { "tradeDate": "2026-07-01", "closePrice": 3050.00,
    "dailyReturn": 0.0012345678, "amount": 300000.00, "ma20": 3011.500000, "ma60": 2980.250000,
    "drawdown": 0.0000000000 } ],
  "activitySeries": [ { "tradeDate": "2026-07-01", "marketTurnover": 3958000000.00,
    "turnoverMedian20": 3900000000.00, "turnoverMedian60": 3880000000.00, "activityRatio": 1.0148717949,
    "activeStockRatio": 0.4000000000, "validStocks": 150 } ],
  "breadthSeries": [ { "tradeDate": "2026-07-01", "advancingStocks": 78, "decliningStocks": 61,
    "flatStocks": 11, "validStocks": 150, "advanceRatio": 0.5200000000, "adLine": 17,
    "aboveMa20Stocks": 60, "aboveMa20Ratio": 0.4000000000 } ],
  "liquidityProxySeries": { "unit": "1/元",
    "caliber": "日频价格冲击代理 illiquidity=|close(t)/close(t−1)−1|/amount(t)……",
    "days": [ { "tradeDate": "2026-07-01", "medianIlliquidity": 0.000000031234,
      "p90Illiquidity": 0.000000112345, "qualifiedStocks": 150, "zeroAmountRows": 0 } ] },
  "industryTurnoverMigration": [ { "tradeDate": "2026-07-01", "industryCode": "new_blhy",
    "industryName": "玻璃行业", "turnover": 123456789.00, "turnoverShare": 0.0312000000,
    "previousDayShareChange": 0.0005000000, "median20Share": 0.030000000000,
    "median20ShareChange": 0.0012000000, "rank": 3, "coveredStocks": 8 } ],
  "quality": {
    "coverageGap": { "uncoveredSampleStocks": 49, "uncoveredTurnoverAmount": 1234567.00,
      "symbols": ["BJ.920099"] },
    "providerAttribution": [
      { "dataset": "benchmarkDailyBar", "providers": ["TENCENT_PUBLIC"] },
      { "dataset": "sampleDailyBar", "providers": ["TENCENT_PUBLIC"] },
      { "dataset": "sampleUniverseSnapshot", "providers": ["SINA_PUBLIC"] },
      { "dataset": "industryMembership", "providers": ["SINA_PUBLIC"] }],
    "qualityFindings": [ { "code": "LOW_BAR_COVERAGE", "severity": "WARN",
      "message": "窗口样本日 K 覆盖率 0.893333（存在单日覆盖低于 0.90 的交易日）", "affectedCount": 23 },
      { "code": "LOW_MEMBERSHIP_COVERAGE", "severity": "WARN",
      "message": "行业映射覆盖率 0.673333 低于告警阈值 0.90（未映射 49/150 只）", "affectedCount": 49 } ],
    "assumptions": [
      "交易日集合由基准指数日 K 推导（INDEX_KLINE_DERIVED）；market_calendar CN 为空表未回填",
      "证券池与行业成分取分析时点可见最新档快照（as_of 无上界），当前成分聚合历史为显式时点穿越假设",
      "全部价格事实为 NONE 复权（adjust_type=NONE），除权日收益率失真是已知失效条件（字典 D7）",
      "样本域成交额为 Top-N 样本股合计（字典 M-03），不可宣称为全市场成交额"],
    "unavailableMetrics": ["OFFICIAL_MONEY_FLOW"] } }, "timestamp": "2026-08-16T12:00:00" }
```

（示例为真实 PoC 覆盖水平口径演示：`barCoverage=0.893333`、`membershipCoverage=0.673333` 均低于
0.90 告警阈值，故 `qualityStatus=DEGRADED` 且携带两条 WARN 发现——该覆盖水平绝不返回 `OK`。
`ma60`/`turnoverMedian60` 在预热不足（合格交易日 &lt;60）时为 null，`adLine` 为窗口内累计值。
响应所有 `null` 表示"该日该指标不可计算"，前端不得以 0 展示。）
