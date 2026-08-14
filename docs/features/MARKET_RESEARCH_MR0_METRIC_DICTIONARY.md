# MR-0 指标数据字典（冻结）

> 状态：`FROZEN FOR MR-0 PoC`（任务 QTA-V2-MR0-DATA-SEMANTICS-POC-20260815，AC-01）
>
> 冻结日期：2026-08-15
>
> 上位权威：`docs/features/QTA_V2_INSTITUTIONAL_MARKET_RESEARCH_DESIGN.md` §8（公式）、§9（数据需求）、§11（MR-0/MR-1 边界）；
> 契约决策 D5-D9、AMD-3、REC-10。本字典冻结 PoC 级计算细节（种子、自由度、容差、覆盖域、单位、缺失与失效语义），
> 不改变 V2 设计公式的含义；两者冲突时以设计文档为准并停止实现。
>
> 本字典只描述 MR-0 PoC 口径。PoC 数据源选型（D1：公共无凭据源）不构成 MR-1 生产选型决策。

## 1. 冻结口径总则（D5/D6/D7/D8/D9）

- 市场：CN（A 股）。
- 基准：SH.000001（上证指数）。
- 分析窗口：2026-07-01..2026-07-31（完整交易月）；预热抓取窗口 2026-04-01..2026-07-31（满足 MA20/MA60 与 20 日波动率预热）。
- 样本池：新浪证券池 `hs_a` 按流通市值（nmc）降序前 150 只 ∪ 基准指数 SH.000001；样本随抓取日快照确定，
  as-of 日期入库。不声称可跨日复现同一 Top150；可复现性定义为"同库重算一致 + 重导入幂等"。
- 行业口径：新浪行业分类，记为 `SINA_INDUSTRY`（互斥行业，非申万）。禁止与申万（SW）混称、混算、混表。
- 单位冻结（D6）：`amount` = 元（源数据万元 ×10000）；`volume` = 股（源数据手 ×100）；
  `turnover_rate` = 小数比例（源数据百分数 ÷100）；新浪资金流 `netamount` 源单位已是元，直接入库。
- 复权冻结（D7）：PoC 全部事实 `adjust_type=NONE`（不复权；腾讯日 K 探针取无 fq 参数原始价）。
  前复权（qfq）可得但本任务不启用、不与 NONE 混存。NONE 口径下除权日收益率失真是已知失效条件（见各条目）。
- 交易日历（D8）：PoC 交易日集合由 SH.000001 指数日 K 日期推导，记为 `INDEX_KLINE_DERIVED`；
  `market_calendar` CN 空表记为陈旧度发现，本任务不回填。
- Provider 单一来源红线（D9 / 契约 Prohibited）：每个指标的事实字段只有一个 Provider、一个口径。
  腾讯日 K 与新浪资金流可以并存，但同一指标不得混源计算；混用必须被质量检查标记而不是静默合并。
  资金净流入是 Provider 事实，QTA 永远不从价量（涨跌幅×成交额）猜测或推导资金净流入。
- 输出纪律（REC-10）：空有效股票池输出原因码 `EMPTY_VALID_UNIVERSE`；任何指标输出禁止出现 `NaN`、`Infinity`
  字符串；预热不足输出 `INSUFFICIENT_WARMUP` 且不得输出任何部分数值。

## 2. 公式冻结规则

- V2 设计 §8 已给出公式的指标：本字典原样引用（`activityRatio`、`sectorTurnoverShare`、广度三式、
  `flowIntensity`、`illiquidityProxy`），并补充 PoC 级细节。
- V2 设计只给文字描述、未给公式的指标（成交扩散、价格冲击代理分位、覆盖率、陈旧度等）：本字典给出唯一
  PoC 冻结公式，实现不得自行变体。
- 数值计算：金额与比率聚合使用 BigDecimal；行业占比求和容差 ε=1e-6（AMD-3 冻结）。

## 3. 单位自检规则（VWAP）

对任一导入日 K 行，按冻结单位换算后自检：

```text
vwap = amount(元) / volume(股)
约束：low <= vwap <= high（同一行 low_price/high_price，adjust_type=NONE 口径）
违反 → 单位异常（quality family=UNIT_ANOMALY），该行不得进入指标计算
```

该自检同时约束成交额（M-03/M-11）与换手率（M-05）条目的单位口径：万元未乘 10000 或手未乘 100 时，
vwap 通常落到 [low,high] 之外，可被检出。

## 4. 指标条目（M-01..M-23）

### M-01 基准收盘价

- 名称：基准收盘价（benchmarkClose）
- 金融含义：市场趋势与风险状态的第一事实；判断大盘向上、震荡或向下的价格锚点。
- 公式：`benchmarkClose(t) = close(SH.000001, t)`，直接取基准指数日 K 收盘价，不做任何变换。
- 单位：指数点
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（单一来源；指数日 K）
- 原始字段：`proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get` 返回 data 数组每行第 0 列日期、第 2 列收盘
- 窗口：分析窗口 2026-07-01..2026-07-31；展示与均线预热 2026-04-01 起
- 复权：NONE（指数无复权概念；个股复权口径见总则 D7）
- 交易日历：INDEX_KLINE_DERIVED（即由本指标自身的日 K 日期集合推导，D8）
- 缺失语义：某日期在指数日 K 中不存在 → 判定为非交易日，不计为缺口；分析窗口内基准自身缺行 → 覆盖率指标降级并输出原因码
- 失效条件：基准日 K 行数不足以覆盖分析窗口；来源行结构变化（列位移）；出现 NaN/Infinity

### M-02 基准均线趋势位置

- 名称：基准均线趋势位置（benchmarkMaPosition）
- 金融含义：中期趋势证据：收盘价相对 MA20/MA60 的位置与均线斜率，回答"趋势向上、震荡还是向下"。
- 公式：`MA_n(t) = mean(close[benchmark][t-n+1..t])`，n∈{20,60}；趋势位置 = `close(t) > MA_n(t)` 与 `MA_n(t) - MA_n(t-1)` 斜率符号
- 单位：指数点（位置与斜率均为点值比较，不做成百分比）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（单一来源；与 M-01 同源同口径）
- 原始字段：同 M-01（指数日 K 收盘列）
- 窗口：MA20 需 20 个收盘观测、MA60 需 60 个收盘观测；PoC 预热自 2026-04-01，MA60 不足 60 观测时按实际可得历史计算并标注（新指数不适用；SH.000001 历史充足）
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：预热不足 20 个观测 → `INSUFFICIENT_WARMUP`，不输出部分均值；MA60 预热不足单独标注，不静默用 MA20 替代
- 失效条件：基准收盘序列中断；用当前成分/价格回填历史；输出 NaN/Infinity

### M-03 样本域市场成交额

- 名称：样本域市场成交额（marketTurnoverAmount）
- 金融含义：市场交易活跃度的金额事实；判断成交活跃还是流动性收缩（样本域口径，非全市场）。
- 公式：`marketTurnover(t) = Σ amount(i, t)`，i 遍历当日有有效日 K 的样本股票（元；源数据万元 ×10000 后求和）
- 单位：元（D6 冻结；源字段为万元，入库前 ×10000）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（成交额事实单一来源）
- 原始字段：日 K 行第 8 列成交额（万元）
- 窗口：逐日；20/60 日基线见 M-04
- 复权：NONE（金额不受复权影响，但与价格行同批导入，保持同 adjust_type 便于对齐）
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：样本股票当日无日 K（停牌/未抓取）→ 不计入分子，计入当日覆盖率分母统计；禁止填 0 冒充无成交
- 失效条件：单位未按万元 ×10000 换算（被 M-3 节 VWAP 自检捕获）；跨 Provider 混拼成交额；把样本域成交额宣称为全市场成交额

### M-04 成交活跃度比值

- 名称：成交活跃度比值（activityRatio）
- 金融含义：当前成交额相对 20 日基线的扩张/收缩；只表征活跃度，不得单独称为流动性。
- 公式：`activityRatio(t) = marketTurnover(t) / median(marketTurnover[t-19:t])`（V2 设计 §8.1 冻结原文；窗口含 t 当日，共 20 个观测）
- 单位：无量纲比值（1 = 与 20 日中位数持平）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（与 M-03 同源；单一来源）
- 原始字段：同 M-03（日 K 成交额列）
- 窗口：20 个交易日（含 t）；60 日中位数作为补充基线时可另列，不覆盖 20 日口径
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：窗口内不足 20 个观测 → `INSUFFICIENT_WARMUP`，不输出部分比值；分母为 0 → 输出原因码，禁止除零
- 失效条件：窗口内混入非交易日；用均值替代中位数；输出 NaN/Infinity

### M-05 换手率

- 名称：换手率（turnoverRate）
- 金融含义：股份换手的活跃程度，交易活跃度与拥挤度证据之一。
- 公式：`turnoverRate(i, t) = 源换手率字段(%) / 100`（入库=小数比例，D6 冻结）
- 单位：小数比例（0.0034 = 0.34%）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（日 K 行内换手率字段，单一来源）
- 原始字段：日 K 行第 7 列换手率（百分数）
- 窗口：逐日
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：源字段为空 → 该股当日换手率置空并计入缺失统计，禁止填 0；新浪证券池快照的 `turnoverratio` 字段仅是抓取时点快照值，只用于样本池元数据，不得拼入日频序列（单一来源红线）
- 失效条件：百分数未 ÷100（出现 0.34 与 34 混存）；与新浪快照换手率混算；单位异常行（M-3 节 VWAP 自检失败）参与统计

### M-06 上涨/下跌/平家数

- 名称：上涨/下跌/平家数（advanceDeclineCounts）
- 金融含义：市场广度的原始计数事实；识别"指数上涨但多数股票下跌"的窄幅上涨。
- 公式：对每只样本股票 i：`adv(t) = #{i: close(i,t) > close(i,t-1)}`；`dec(t) = #{i: close(i,t) < close(i,t-1)}`；`flat(t) = #{i: close(i,t) = close(i,t-1)}`（仅统计 t 与 t-1 均有有效日 K 的股票）
- 单位：家（整数计数）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（收盘价事实单一来源；成分归属用 SINA_INDUSTRY，仅作分组标签，不改价量口径）
- 原始字段：日 K 行第 2 列收盘价
- 窗口：相邻两个交易日（t-1 → t）
- 复权：NONE（除权日 close 跳变会造成假上涨/假下跌，见失效条件）
- 交易日历：INDEX_KLINE_DERIVED（t-1 为指数日 K 的前一交易日）
- 缺失语义：t-1 无日 K 的股票（新股/停牌复牌首日）不进入分子，单独计数；不强行填充
- 失效条件：NONE 复权下除权除息日收益失真（已知失效条件，D7）；把指数涨跌当作个股涨跌；计数混入非样本股票

### M-07 上涨占比

- 名称：上涨占比（advanceRatio）
- 金融含义：当日上涨股票占有效股票的比例；广度的比率形式，便于跨日比较。
- 公式：`advanceRatio(t) = advancingStocks(t) / validStocks(t)`（V2 设计 §8.3 冻结；validStocks = 当日有有效日 K 且 t-1 亦有收盘的样本股票数，即 M-06 三类计数之和）
- 单位：小数比例（0..1）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（单一来源）
- 原始字段：日 K 行收盘价列
- 窗口：相邻两个交易日
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：validStocks(t) = 0（空有效池）→ 输出原因码 `EMPTY_VALID_UNIVERSE`，禁止 0/0 与 NaN/Infinity
- 失效条件：分母用全样本数而非有效数（停牌股拉低占比制造假广度）；除权失真（D7）；混源收盘价

### M-08 累计涨跌 A/D 线

- 名称：累计涨跌 A/D 线（adLine）
- 金融含义：广度的累积形式；识别指数横盘但内部逐步改善的"广度先行"与指数新高但广度走弱的背离。
- 公式：递推 `adLine(t) = adLine(t-1) + adv(t) - dec(t)`；首日种子冻结（AMD-3）：`adLine(t0) = adv(t0) - dec(t0)`（t0 为分析窗口首个交易日；种子为首个交易日净涨跌家数，不置 0）
- 单位：家（累计净家数，无量纲整数）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（单一来源，与 M-06 同源同口径）
- 原始字段：日 K 行收盘价列（经由 M-06 计数）
- 窗口：自分析窗口首日 t0 起逐日累计；展示窗口 20/60 日
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：某交易日 adv/dec 缺失（该日无任何有效对）→ 该日 A/D 线不产出并输出原因码；不得跳日外推
- 失效条件：首日种子改为 0（违背 AMD-3 冻结值）；窗口中途更换样本池不重新标注；除权失真（D7）；输出 NaN/Infinity

### M-09 MA20/MA60 覆盖率

- 名称：MA20/MA60 覆盖率（aboveMaRatio）
- 金融含义：站上自身中/长期均线的股票比例；趋势广度证据。
- 公式：`aboveMa20Ratio(t) = stocksAboveMa20(t) / stocksWithEnoughHistory(t)`（V2 设计 §8.3 冻结）；MA60 同式。`stocksAboveMa20(t) = #{i: close(i,t) > MA20(i,t)}`；`stocksWithEnoughHistory(t) = #{i: i 有 ≥20 个连续收盘观测}`（MA60 用 60）
- 单位：小数比例（0..1）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（单一来源）
- 原始字段：日 K 行收盘价列
- 窗口：个股 20/60 个交易日；预热自 2026-04-01（MA60 所需 60 观测按实际可得历史计算并标注覆盖样本数）
- 复权：NONE（除权造成的均线断裂是已知失真源）
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：个股历史不足 20（或 60）个观测 → 不进入该比率的分子与分母，另计"历史不足"计数；分母为 0 → `EMPTY_VALID_UNIVERSE`
- 失效条件：用不满足预热的历史硬算（未来函数）；分母混入历史不足股票；新股强行填充；输出 NaN/Infinity

### M-10 20 日新高/新低家数

- 名称：20 日新高/新低家数（newHigh20 / newLow20）
- 金融含义：突破与破位广度；辅助识别趋势健康度与极端状态。
- 公式：`newHigh20(t) = #{i: close(i,t) = max(close(i)[t-19..t])}`；`newLow20(t) = #{i: close(i,t) = min(close(i)[t-19..t])}`（含 t 当日共 20 个观测；并列计入）
- 单位：家（整数计数）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（单一来源）
- 原始字段：日 K 行收盘价列
- 窗口：含当日的 20 个交易日
- 复权：NONE（除权日会出现假新低/假新高，已知失效条件）
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：不足 20 个观测的股票不参与；两计数之和不得超过有效样本数（自检）
- 失效条件：用前复权价与 NONE 价混算；窗口两端不闭合（未来函数）；输出 NaN/Infinity

### M-11 行业成交额

- 名称：行业成交额（industryTurnoverAmount）
- 金融含义：交易活动向哪些行业集中；行业资金池明细表的金额事实（样本域、SINA_INDUSTRY 口径）。
- 公式：`industryTurnover(s, t) = Σ amount(i, t)`，i ∈ 行业 s 在 as_of_date 的成分股票（成分来自 `mr0_industry_membership` 快照，互斥）
- 单位：元（同 M-03）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（成交额事实）+ SINA_INDUSTRY（成分归属；分类事实，非价量口径；质量引擎必须显式标注该复合归属）
- 原始字段：腾讯日 K 成交额列（万元 ×10000）；新浪行业成分（newSinaHy 目录 + 行业 node 成分）
- 窗口：逐日；20 日均值用于"较 20 日变化"
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：行业内当日无有效日 K 的成分股不计入；行业当日无任何有效成分行情 → 该行业该日不产出，进入缺口报告
- 失效条件：用当前成分回填历史（非 point-in-time）；概念板块与互斥行业混加；跨 Provider 混拼金额；宣称全市场口径

### M-12 行业成交占比

- 名称：行业成交占比（industryTurnoverShare）
- 金融含义：市场交易注意力在行业间的分布与迁移；堆叠面积图的层厚度。
- 公式：`industryTurnoverShare(s, t) = industryTurnover(s, t) / coverageTurnover(t)`（V2 设计 §8.2 `sectorTurnoverShare` 的 PoC 覆盖域冻结版）；求和校验 `|Σ_s share(s,t) - 1| <= ε`，ε=1e-6（BigDecimal，AMD-3 冻结）
- 单位：小数比例（0..1，各行业之和为 1±ε）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（金额事实）+ SINA_INDUSTRY（成分归属，显式复合标注）
- 原始字段：同 M-11
- 窗口：逐日；迁移观察 20/60 日
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：覆盖域冻结（AMD-3）——分母 `coverageTurnover(t)` = 有 as_of_date 成分的样本股票当日成交额合计；无成分映射的样本股票不计入分母，计入 `coverageGap` 单独报告（列出股票与缺失原因），禁止静默丢弃或强行归入"其他"行业
- 失效条件：Σ share 偏离 1 超过 ε=1e-6；把无成分股票塞进分母或某个行业；概念板块占比求和成 100%（V2 设计 §12 红线）；输出 NaN/Infinity

### M-13 成交扩散

- 名称：成交扩散（turnoverDiffusion）
- 金融含义：成交活跃是普遍扩散还是集中在少数股票；活跃度的横截面广度。
- 公式：`turnoverDiffusion(t) = #{i ∈ valid(t): amount(i,t) > median(amount(i)[t-20..t-1])} / |valid(t)|`（基线为该股自身不含当日的过去 20 个交易日成交额中位数，严格大于；本字典 PoC 冻结，V2 设计 §4.5 只有文字描述）
- 单位：小数比例（0..1）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（单一来源）
- 原始字段：日 K 行成交额列（元）
- 窗口：基线 20 个交易日（不含 t）；基线观测不足 20 → 该股当日不参与（计入历史不足计数）
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：`|valid(t)| = 0` → `EMPTY_VALID_UNIVERSE`；中位数基线无观测 → 原因码，禁止除零
- 失效条件：基线窗口含 t（自我稀释）；用均值替代中位数；混入万元未换算行（VWAP 自检捕获）；输出 NaN/Infinity

### M-14 个股主力资金净流入

- 名称：个股主力资金净流入（stockNetInflow，Provider 事实）
- 金融含义：供应商口径的主动资金方向证据；是 Provider 报告的事实，不是 QTA 的推导结论。
- 公式：`stockNetInflow(i, t) = netamount(i, t)`（直接取新浪资金流接口字段，源单位已是元；不做任何加工）
- 单位：元
- 频率：日频（盘后）
- 市场：CN
- Provider：SINA_PUBLIC（`MoneyFlow.ssl_qsfx_zjlrqs`，单一来源；D9 红线：资金净流入不从价量猜测）
- 原始字段：`opendate`（日期）、`netamount`（主力净流入，元）、`ratioamount`（主力净占比）、`r0_net`（超大单净额）、`cate_ra`/`cate_na`（新浪行业口径涨/净流入参考）
- 窗口：逐日；探针实测覆盖 2010-03-01 起全历史（SH.600519 3991 行）
- 复权：不适用（金额事实）
- 交易日历：INDEX_KLINE_DERIVED（资金流行情日期与交易日集合对齐；多出的非对齐日期进入缺口报告）
- 缺失语义：某日无记录 → 该日资金事实缺失，计数报告；禁止用 0 或价量推导值填充
- 失效条件：从价量（|r|×amount 或类似）猜测净流入（契约 Prohibited）；与任何其他 Provider 的资金口径混算成同一序列；输出 NaN/Infinity

### M-15 行业资金净流入

- 名称：行业资金净流入（industryNetInflow）
- 金融含义：行业级主动资金的聚合证据；回答"主动资金偏向哪里"（样本域、SINA_INDUSTRY 口径）。
- 公式：`industryNetInflow(s, t) = Σ stockNetInflow(i, t)`，i ∈ s 的 as_of_date 成分；一致性报告：与新浪行业口径 `cate_na` 的偏差 `deviation(s,t) = industryNetInflow(s,t) - cate_na(s,t)`，只报告偏差与容忍度，不做跨口径等式断言（D9）
- 单位：元；偏差同为元
- 频率：日频（盘后）
- 市场：CN
- Provider：SINA_PUBLIC（个股聚合与 cate_na 同一 Provider、同一分类体系）
- 原始字段：`netamount`（个股聚合）、`cate_na`（新浪行业参考值）
- 窗口：逐日；5/10/20 日累计用于连续性观察
- 复权：不适用
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：成分股资金记录缺失 → 聚合值标注覆盖股票数与缺失数；行业无任何成分记录 → 缺口报告
- 失效条件：把个股聚合与 cate_na 的偏差隐藏或断言为零；跨分类体系（如混入申万）聚合；输出 NaN/Infinity

### M-16 资金强度

- 名称：资金强度（flowIntensity）
- 金融含义：单位成交额承载的主动资金净流入；避免大行业天然金额大造成的误读。
- 公式：`flowIntensity(s, w) = Σ providerNetFlow(s, w) / Σ sectorTurnover(s, w)`（V2 设计 §8.4 冻结；w 为窗口，分子分母同窗口同成分域）
- 单位：小数比例（无量纲，元/元）
- 频率：日频（盘后；窗口 1/5/20 日）
- 市场：CN
- Provider：SINA_PUBLIC（净流入事实）+ TENCENT_PUBLIC（成交额事实）——两个不同事实字段、各自单一来源；质量引擎必须输出双 Provider 归属标注，任何一方缺源则该指标阻断
- 原始字段：`netamount`（新浪，元）；日 K 成交额（腾讯，元）
- 窗口：w ∈ {1, 5, 20} 交易日；分子分母必须同窗口、同覆盖域
- 复权：不适用（金额事实）
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：分母 Σ=0（行业当日无成交）→ 输出原因码，禁止除零；分子或分母覆盖股票集不一致 → 阻断并标注
- 失效条件：分子分母窗口错位；不同覆盖域相除；输出 NaN/Infinity

### M-17 相对强弱

- 名称：相对强弱（relativeStrength）
- 金融含义：行业（或个股）相对基准的阶段强弱；四象限图的 X 轴。
- 公式：`relativeReturn(s, t, W) = [Π(1 + r(i or s, τ))] / [Π(1 + r(benchmark, τ))] - 1`，τ 遍历窗口 W 个交易日（几何相对收益；PoC 冻结为简单收益乘积比，与 P1.7 对数口径等价排序，不混用）
- 单位：小数比例（相对收益）
- 频率：日频序列；周频（5 日）用于中期轮动观察（V2 设计 §5.6）
- 市场：CN；基准 SH.000001
- Provider：TENCENT_PUBLIC（个股/指数收盘价单一来源；行业口径为成分等权聚合时另标注聚合规则）
- 原始字段：日 K 行收盘价列
- 窗口：W = 20（中期）；5（短频补充）
- 复权：NONE（除权失真直接进入相对收益，为已知失效条件；不与 qfq 混存）
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：窗口内任一日缺收盘 → 该窗口相对强弱不产出（不部分计算）；行业缺成分 → 缺口报告
- 失效条件：窗口两端不闭合（未来函数）；基准与个股日历不一致时静默对齐；NONE 与 qfq 混算；输出 NaN/Infinity

### M-18 相对动量

- 名称：相对动量（relativeMomentum）
- 金融含义：相对强弱的变化速度；四象限图的 Y 轴，区分领先/转弱/落后/改善。
- 公式：`relativeMomentum(s, t) = relativeReturn(s, t, 5) - relativeReturn(s, t-5, 5)`（5 日相对强强的 5 日变化；动量窗口冻结为 5，与 V22 `momentum_window_days` 缺省一致）
- 单位：小数比例（相对收益之差）
- 频率：日频序列
- 市场：CN；基准 SH.000001
- Provider：TENCENT_PUBLIC（单一来源，与 M-17 同源同口径）
- 原始字段：日 K 行收盘价列（经由 M-17）
- 窗口：动量窗 5 日 + 回看 5 日（合计需 ≥10 个交易日预热）
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：所需任一窗口缺观测 → `INSUFFICIENT_WARMUP`，不输出部分差值
- 失效条件：动量窗与强弱窗口径不一致；用未来数据修正；输出 NaN/Infinity

### M-19 20 日实现波动率

- 名称：20 日实现波动率（realizedVolatility20）
- 金融含义：近端风险状态；判断波动是否放大。
- 公式：`rv20(i, t) = stdev(r(i)[t-19..t], ddof=1)`，其中 `r(i, τ) = close(i, τ)/close(i, τ-1) - 1` 为简单收益率（非对数收益）；样本标准差自由度 ddof=1（n-1）；需要 20 个收益观测（21 个收盘价）
- 单位：小数比例（日频波动；**PoC 输出不年化**，并在所有展示与报告中原样标注"未年化"——AMD-3 冻结）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（单一来源）
- 原始字段：日 K 行收盘价列
- 窗口：20 个收益率观测（21 个收盘价）；预热自 2026-04-01
- 复权：NONE（除权日会产生虚假极端收益，污染波动率——已知失效条件，D7）
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：收益观测 <20 个 → `INSUFFICIENT_WARMUP` 且不输出任何部分数值（边界两侧可测：恰好 20 = 成功，19 = 阻断）
- 失效条件：用总体标准差（ddof=0）；对数收益与简单收益混用；年化后不加标注；除权失真；输出 NaN/Infinity

### M-20 流动性代理

- 名称：流动性代理（illiquidityProxy）
- 金融含义：日频价格冲击代理：单位成交额引起的价格变动幅度；只是日频代理，不替代买卖价差与订单簿深度（V2 设计 §2.4/§8.5 边界）。
- 公式：`illiquidityProxy(i, t) = abs(return(i, t)) / turnoverAmount(i, t)`（V2 设计 §8.5 冻结；return 为简单收益率，amount 单位元）
- 单位：1/元（每元成交额的收益比例）
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（价格与成交额同源单一来源）
- 原始字段：日 K 行收盘价列、成交额列（万元 ×10000 后参与）
- 窗口：逐日；不合格股票（当日停牌、amount=0、单位异常）剔除
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：`turnoverAmount(i,t) = 0` → 该股该日不参与（除零守卫）；t-1 收盘缺失 → 不参与
- 失效条件：amount 未换算成元（数值放大 1e4 倍）；称之为"流动性"而非代理；输出 NaN/Infinity

### M-21 价格冲击代理分位

- 名称：价格冲击代理分位（illiquidityProxyQuantile）
- 金融含义：全市场（样本域）日频非流动性分布的位置；观察流动性收缩是普遍还是局部。
- 公式：`illiquidityProxyMedian(t) = median({illiquidityProxy(i,t)})`；`illiquidityProxyP90(t) = quantile_p90({illiquidityProxy(i,t)})`，横截面取当日全部合格样本股票（本字典 PoC 冻结：中位数 + P90 两个分位，线性插值法）
- 单位：1/元
- 频率：日频（盘后）
- 市场：CN
- Provider：TENCENT_PUBLIC（单一来源，经由 M-20）
- 原始字段：同 M-20
- 窗口：逐日横截面；20 日趋势用于活跃度对照
- 复权：NONE
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：合格样本数 < 最低样本门槛 → 输出原因码（样本不足）而非强行计算；空集 → `EMPTY_VALID_UNIVERSE`
- 失效条件：混入不合格股票（amount=0/单位异常/停牌）；分位方法未冻结就混用（最近邻 vs 插值）；输出 NaN/Infinity

### M-22 数据覆盖率

- 名称：数据覆盖率（coverageRate / coverageGap）
- 金融含义：结论建立在多大比例的真实数据上；发布门禁的输入。
- 公式：`barCoverage(t) = #{i ∈ universe: 有当日有效日 K} / |universe|`；`membershipCoverage = #{i ∈ universe: 有 as_of_date 行业成分} / |universe|`；`coverageGap` = 无成分映射的样本股票清单（AMD-3：单独报告，不并入占比分母）
- 单位：小数比例（0..1）；coverageGap 为股票清单 + 计数
- 频率：日频（盘后）+ 快照日（样本池）
- 市场：CN
- Provider：TENCENT_PUBLIC（日 K 覆盖事实）+ SINA_PUBLIC（证券池与成分覆盖事实）——两类覆盖分别归属、分别报告
- 原始字段：日 K 行存在性；`hs_a` 证券池；行业成分记录
- 窗口：分析窗口逐日；样本池 as-of 快照日
- 复权：不适用（存在性事实）
- 交易日历：INDEX_KLINE_DERIVED（交易日集合是"应覆盖"的分母日期来源）
- 缺失语义：空样本池 → `EMPTY_VALID_UNIVERSE`；覆盖不足冻结阈值 → 对应图形/结论阻断，不用上一指标替代（V2 设计 §9.3）
- 失效条件：把覆盖率不足的数据集展示为完整；隐藏 coverageGap；输出 NaN/Infinity

### M-23 数据陈旧度

- 名称：数据陈旧度（staleness）
- 金融含义：数据距"现在"的滞后程度；盘中/隔夜结论的数据新鲜度边界。
- 公式：`staleness(t_ref) = t_ref - max(trade_date of 最新有效日 K)`（按 INDEX_KLINE_DERIVED 交易日计数）；`fetchedLag = fetched_at - quote_time`（抓取延迟，供参考）。`market_calendar` CN 空表与 `verification_status=INFERRED` 作为陈旧度发现单独记录（D8），不回填
- 单位：交易日（整数）；fetchedLag 为时长
- 频率：每次分析时点计算
- 市场：CN
- Provider：TENCENT_PUBLIC（日 K 日期事实）；日历来源标注 INDEX_KLINE_DERIVED（非权威日历，PoC 已知限制）
- 原始字段：日 K 日期列、`fetched_at`
- 窗口：分析窗口末日至参照日
- 复权：不适用
- 交易日历：INDEX_KLINE_DERIVED
- 缺失语义：无任何有效日 K → 陈旧度不可计算，输出原因码；权威日历缺失本身计入质量报告"陈旧"族
- 失效条件：把 INFERRED 日历冒充权威日历；用自然日混淆交易日；输出 NaN/Infinity

## 5. 类别覆盖对照（供审查）

| 契约规定类别 | 对应指标 |
| --- | --- |
| 基准走势 | M-01、M-02 |
| 成交额 | M-03（活跃度比值 M-04） |
| 换手率 | M-05 |
| 市场广度：上涨/下跌家数 | M-06 |
| 市场广度：advanceRatio | M-07 |
| 市场广度：A/D 线 | M-08（首日种子 AMD-3 冻结） |
| 市场广度：MA20/MA60 覆盖 | M-09 |
| 市场广度：20 日新高/新低 | M-10 |
| 行业成交额 | M-11 |
| 行业成交占比 | M-12（ε=1e-6、覆盖域 AMD-3 冻结） |
| 成交扩散 | M-13 |
| 资金净流入 | M-14（个股）、M-15（行业） |
| 资金强度 | M-16 |
| 相对强弱 | M-17 |
| 相对动量 | M-18 |
| 20 日实现波动率 | M-19（简单收益、ddof=1、不年化，AMD-3 冻结） |
| 流动性代理（\|r\|/amount） | M-20 |
| 价格冲击代理分位 | M-21 |
| 数据覆盖率 | M-22（含 coverageGap 冻结口径） |
| 陈旧度 | M-23 |
<!-- frozen-selector: grep -c '^### M-' docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md -> >=15 -->
