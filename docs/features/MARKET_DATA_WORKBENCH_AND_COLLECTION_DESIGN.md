# Market Data Workbench And Collection Design

> 状态：P1.2 设计完成，待开发。  
> 关联：`MARKET_DATA_FOUNDATION_DESIGN.md`、`LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`、`MARKET_ALERT_RULES_DESIGN.md`、`../api/MARKET_DATA_API.md`、`../BUILD_CHECKLIST.md`。

## 1. 背景

P1.1 已完成 LongPort 只读行情源的真实外联验收，系统已经能把单股票最新价和日 K 写入行情域表。下一阶段不能直接跳到指标、策略和回测，否则会出现数据资产不完整、分钟线缺失、采集任务不可控、板块和异动没有来源的问题。

本阶段目标是把行情能力从“单次接口验证”升级为“可配置、可追踪、可复用的数据资产建设流程”：

- 用户能在工作台下看到行情状态、重点股票、板块和异常提醒。
- 用户能创建历史补档任务，例如“贵州茅台，2025-01 至今，30min 粒度”。
- 用户能创建盘中采集任务，例如“五粮液，A 股交易时段内按指定频率采集并落库”。
- 系统能沉淀分钟线、日线、最新价、任务执行、数据质量、板块归属和后续异动事件。
- 行情数据只用于辅助观察、复盘、指标、回测和风险提示，不输出买卖指令。

## 2. 专家团结论

### 产品经理视角

页面应分为三个入口：

1. 工作台下的“行情工作台”：高频查看今日重点、provider 状态、重点标的、任务失败和未处理提醒。
2. 行情数据配置中心：管理数据源、标的池、板块池、采集任务、数据浏览、提醒规则和任务日志。
3. 异动大屏：盘中展示持仓股、自选股、计划股、重点板块和提醒流，适合全屏盯盘。

第一版不要做全市场扫描，优先围绕用户自己的持仓股、自选股、交易计划股和自定义板块。

### 理财经理视角

提醒分三层，不混在一起：

| 类型 | 示例 | 页面表达 |
| --- | --- | --- |
| 数据质量 | provider 未配置、行情过旧、权限不足、同步失败 | 阻断分析或提示修复数据 |
| 风险观察 | 放量下跌、冲高回落、接近止损、持仓股高振幅 | HIGH/WARN 提醒，优先展示 |
| 机会观察 | 放量突破、缩量企稳、板块联动、相对强势 | 只写“观察”，不写“买入/卖出” |

### 技术架构视角

行情资产分三层：

1. 原始行情事实：证券主数据、最新价、分钟 K、日 K、复权因子。
2. 可复用衍生统计：量比、振幅、成交额变化、板块宽度、相对强弱、筹码类快照。
3. 任务和质量治理：采集计划、执行任务、任务明细、水位、数据质量提醒、异常事件。

保留现有 `stock_basic`、`stock_daily_bar`、`stock_quote_snapshot`、`market_data_sync_task`、`market_data_alert`，新增表只补缺口，不推倒重来。

### 量化基金经理视角

先建设数据资产和质量治理，再建设策略。策略、信号、回测只能消费通过质量校验的数据。数据质量失败时只产生质量提醒，不产生交易信号。

第一批量化观察场景：

- 量价异常：放量、缩量、高振幅、冲高回落、跳空。
- 板块联动：自定义板块内上涨家数、成交额变化、龙头股变化。
- 持仓风险：持仓股接近计划止损、价格过期、成交量异常。
- 数据质量：缺 bar、旧 quote、重复冲突、provider 失败。

## 3. 产品信息架构

```text
工作台
├── 今日纪律总览
├── 行情工作台
│   ├── Provider 状态
│   ├── 持仓股
│   ├── 自选股
│   ├── 计划股
│   ├── 重点监控
│   ├── 数据质量
│   └── 采集任务状态
└── 异动大屏

行情数据配置
├── 数据源状态
├── 标的池 / 板块池
├── 采集任务配置
│   ├── 历史 K 线补档
│   ├── 盘中定时监控
│   ├── 最新价快照刷新
│   └── CSV 导入
├── 数据浏览
│   ├── 最新价快照
│   ├── 日 K
│   └── 分钟 K
├── 提醒规则
└── 任务日志
```

行情工作台首屏建议：

| 区域 | 内容 |
| --- | --- |
| 顶部状态条 | LongPort 是否可用、最近成功抓取时间、过期行情数量、未处理 HIGH/WARN 提醒 |
| 左侧分组 | 持仓股、自选股、计划股、重点监控、自定义板块 |
| 中央股票矩阵 | 股票名、代码、最新价、涨跌幅、成交量、更新时间、数据源、是否过期 |
| 右侧提醒流 | 高优先级提醒、数据质量提醒、接近计划价或止损价提醒 |
| 底部任务条 | 正在运行任务、失败任务、今日落库数量、最近水位 |

## 4. 关键用户故事

### 4.1 历史 30min 数据补档

用户输入：

```text
股票：贵州茅台
时间：2025-01 至今
K 线粒度：30min
数据源：LongPort
复权：不复权
执行方式：手动执行
```

系统行为：

1. 将 `贵州茅台` 识别或选择为 `SH.600519`。
2. 将“至今”解释为最近可用交易日。若当前日期为 2026-07-12 周日，应默认落到 2026-07-10。
3. 预估请求次数、预计落库行数、provider 权限和限流风险。
4. 创建采集计划和执行任务。
5. 拉取 provider 的历史分钟 K 或历史 K 能力，按 `30M` 粒度写入分钟线资产表。
6. 更新任务明细、水位和数据质量提醒。

注意：历史 30min K 线不能由零散最新价快照拼出来冒充，应优先使用 provider 的历史 K 线能力。

### 4.2 盘中定时监控

用户输入：

```text
股票：五粮液
标识：SZ.000858
采集窗口：09:15-11:30、13:00-15:00
采集频率：60 秒
K 线粒度：1min 或 5min
执行方式：交易日自动执行
```

系统行为：

1. 交易日内按配置窗口触发，不做 24/7 请求。
2. 9:15-9:25 属于集合竞价观察窗口，UI 需要提供“包含集合竞价”开关。
3. 默认连续竞价窗口可使用 09:30-11:30、13:00-15:00。
4. 采集频率和 K 线粒度必须分开配置：
   - 采集频率：多久请求一次，例如 30 秒、60 秒、5 分钟。
   - K 线粒度：写入或拉取几分钟 K，例如 1M、5M、15M、30M、60M、1D。
5. 任务失败不覆盖旧数据；失败可重试，并保留父任务和错误摘要。

### 4.3 异动大屏

用户能一屏看到：

- 持仓股风险提醒。
- 自选股和计划股最新行情。
- 自定义板块或行业板块的涨跌、成交额、上涨家数。
- provider 心跳、行情延迟和失败任务。
- 未处理的 HIGH/WARN 提醒。

异动大屏是展示模式，不是配置模式。

## 5. 数据模型设计

### 5.1 已有表保留

| 表 | 定位 | 后续动作 |
| --- | --- | --- |
| `stock_basic` | 证券主数据 | 补 exchange、currency、security_type、list_status、source_updated_at 等字段时走新 migration |
| `stock_daily_bar` | 日 K 事实表 | 继续承接 CSV 和 LongPort 日 K |
| `stock_quote_snapshot` | 最新价快照 | 继续存单次 quote 结果 |
| `market_data_sync_task` | 任务执行主表 | 可扩展 task_type、scope、granularity、trigger_type |
| `market_data_alert` | 数据质量和观察提醒 | 先承接质量提醒，量价异动可拆 `market_abnormal_event` |
| `portfolio_price_snapshot` | 用户手工估值 | 外部行情不得自动覆盖 |

### 5.2 新增表优先级

| 优先级 | 表 | 用途 |
| --- | --- | --- |
| P1.2 | `stock_minute_bar` | 存 1M/5M/15M/30M/60M 分钟 K，支撑历史补档、盘中采集、量价统计 |
| P1.2 | `market_trading_session` | 存市场/交易所交易时段、集合竞价、连续竞价、盘前盘后 |
| P1.2 | `market_calendar` | 存交易日、节假日、半日市、时区 |
| P1.2 | `market_data_sync_plan` | 存可复用采集配置，不直接等同一次执行 |
| P1.2 | `market_data_sync_task_item` | 存单次任务按标的/时间段拆分后的执行明细 |
| P1.2 | `market_data_watermark` | 存每个标的、粒度、来源的最新成功水位 |
| P1.3 | `market_segment` | 存行业、概念、自定义板块、指数成分等分组 |
| P1.3 | `market_segment_member` | 存板块和标的关系 |
| P1.4 | `market_abnormal_event` | 存量价异动和板块异动事件 |
| P2 | `stock_intraday_stat`、`segment_daily_stat` | 存衍生统计，供大屏、复盘、策略消费 |

### 5.3 `stock_minute_bar` 建议字段

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `canonical_symbol` | 内部统一代码，如 `SH.600519` |
| `trade_date` | 交易日 |
| `bar_start_time` | K 线开始时间 |
| `bar_end_time` | K 线结束时间 |
| `interval_type` | `1M`、`5M`、`15M`、`30M`、`60M` |
| `session_type` | `PRE_MARKET`、`AM`、`PM`、`REGULAR`、`AFTER_HOURS` 等 |
| `open_price` / `high_price` / `low_price` / `close_price` | OHLC |
| `volume` | 成交量 |
| `amount` | 成交额 |
| `turnover_rate` | 换手率，可为空 |
| `adjust_type` | `NONE`、`QF`、`HF`，LongPort 当前继续拒绝 `HF` |
| `data_source` | `LONGPORT`、`CSV`、`TUSHARE`、`AKSHARE` 等 |
| `fetched_at` | 抓取时间 |
| `raw_hash` | 原始数据摘要，辅助幂等和冲突检测 |
| `quality_status` | `VALID`、`SUSPECT`、`REJECTED` |
| `created_at` / `updated_at` | 系统时间 |

幂等键：

```text
canonical_symbol + bar_start_time + interval_type + adjust_type + data_source
```

核心索引：

```text
(canonical_symbol, interval_type, adjust_type, data_source, bar_start_time)
(trade_date, interval_type)
```

第一版不急着做数据库分区，等分钟线数据量上来后再评估按 `trade_date` 月分区。

## 6. 采集任务模型

### 6.1 任务类型

| 任务类型 | 用途 |
| --- | --- |
| `SECURITY_MASTER_SYNC` | 同步证券主数据 |
| `DAILY_BAR_BACKFILL` | 历史日 K 补档 |
| `MINUTE_BAR_BACKFILL` | 历史分钟 K 补档 |
| `INTRADAY_QUOTE_REFRESH` | 盘中最新价刷新 |
| `INTRADAY_MINUTE_REFRESH` | 盘中分钟线刷新 |
| `CALENDAR_SYNC` | 交易日历同步 |
| `SEGMENT_SYNC` | 板块/行业/概念同步 |
| `QUALITY_AUDIT` | 数据质量审计 |

### 6.2 范围类型

| 范围 | 示例 |
| --- | --- |
| `SYMBOLS` | `SH.600519`、`SZ.000858` |
| `MARKET` | `CN_A`、`HK`、`US` |
| `SEGMENT` | 白酒板块、自定义 AI 算力 |
| `WATCHLIST` | 当前启用自选股 |
| `PORTFOLIO` | 当前持仓股 |
| `TRADE_PLAN` | 仍有效的交易计划股 |

### 6.3 执行链路

```text
market_data_sync_plan
-> market_data_sync_task
-> market_data_sync_task_item
-> stock_quote_snapshot / stock_minute_bar / stock_daily_bar
-> market_data_watermark
-> market_data_alert / market_abnormal_event
```

### 6.4 调度原则

- 历史补档默认手动执行，后续支持暂停、恢复和失败重试。
- 盘中任务只在交易日和配置时段执行。
- A 股优先只对持仓股、自选股、计划股和重点监控股执行，不做全市场扫描。
- 最新价可按 30-60 秒级刷新；分钟线可按 1-5 分钟级刷新。
- 收盘后延迟执行日 K 补档，避免尾盘数据未稳定。
- 所有 provider 限流、超时和权限错误必须转为可读任务状态和提醒。

## 7. 多市场和板块设计

### 7.1 市场标识

| 市场 | 内部示例 | 注意点 |
| --- | --- | --- |
| A 股 | `SH.600519`、`SZ.000858`、`BJ.430047` | 午休、集合竞价、涨跌停、ST、交易日历 |
| 港股 | `HK.00700` | 保留前导零，半日市、台风休市、收盘竞价、HKD |
| 美股 | `US.AAPL` | 时区和夏令时，盘前/盘后，拆股复权 |

### 7.2 板块

板块不要硬塞到 `stock_basic`，使用：

```text
market_segment
market_segment_member
```

板块分两类：

- Provider 原生板块或指数，例如行业、概念、指数成分。
- 用户自定义板块，例如“白酒观察池”“银行高股息”“AI 算力”。

第一版可先做自定义板块，专业行业/概念源后续接入。

## 8. 数据源策略

| 数据源 | 角色 | 适合场景 | 风险 |
| --- | --- | --- | --- |
| LongPort / Longbridge | 第一只读行情源 | 最新价、历史 K、交易时段 | 需要 SDK、凭据、权限、限流治理 |
| Tushare Pro | 候选研究/补档源 | 股票、指数、ETF、分钟线、基础面和衍生数据 | 权限和积分限制，`pro_bar` 属 SDK 能力 |
| AKShare | 候选研究/板块源 | 东方财富行业/概念板块分钟历史、快速原型 | 非正式金融数据服务，接口稳定性需额外兜底 |
| BaoStock | 候选低成本历史源 | A 股历史行情研究 | 覆盖粒度与维护状态需二次验证 |
| Wind / Choice / iFinD | 专业数据候选 | 机构级数据、行业分类、因子和宏观数据 | 授权成本高，第一阶段不接 API，最多支持手工导入 |

资料依据：

- LongPort 历史 K 线接口支持 symbol、period、adjust type、日期范围，并返回 OHLC、volume、turnover、timestamp；官方说明 A 股分钟 K 可追溯到 2022-08-25，历史 K 线限频每 30 秒 60 次。参考：https://open.longportapp.com/zh-CN/docs/quote/pull/history-candlestick
- LongPort 交易时段接口返回市场和交易 session，CN 示例包含 09:30-11:30、13:00-14:57。参考：https://open.longportapp.com/zh-CN/docs/quote/pull/trade-session
- Tushare `pro_bar` 提供股票、指数、ETF、期货、期权等综合行情数据，并提供分钟数据能力，但不同数据有权限和积分要求。参考：https://tushare.pro/document/2?doc_id=109
- AKShare 提供东方财富行业/概念板块分钟历史接口，period 包含 `1`、`5`、`15`、`30`、`60`。参考：https://akshare.akfamily.xyz/data/stock/stock.html

第一阶段结论：继续 LongPort 主线，不把系统锁死在 LongPort。Provider 抽象必须支持后续多源字段映射、优先级、fallback、来源可信度和冲突提醒。

## 9. 数据质量规则

第一版必须校验：

- OHLC 合法：`high >= open/close/low`，`low <= open/close/high`。
- 成交量和成交额非负。
- `bar_start_time` 必须落在交易日历和允许交易时段内。
- 同一幂等键内容冲突时记录提醒，不静默覆盖。
- quote 超过阈值未更新时触发 `QUOTE_STALE`。
- 分钟线聚合结果与日线差异超过阈值时标记 `SUSPECT`。
- 数据质量为 `SUSPECT` 或 `REJECTED` 时，不能进入策略信号和回测基准数据集。

## 10. 路线图

### P1.2 行情工作台 MVP

- 工作台下增加“行情工作台”入口。
- 聚合 provider 状态、重点标的、最近同步、失败任务、未处理提醒。
- 支持搜索股票名/代码并进入股票观察详情。
- 复用现有最新价快照、日 K、同步任务和提醒。
- 建设看板新增本阶段节点。

### P1.3 采集任务配置中心

- 新增采集计划表和任务明细表。
- 支持历史日 K/分钟 K 补档任务。
- 支持盘中定时监控任务。
- 支持交易时段模板和集合竞价开关。
- 支持任务暂停、恢复、重试、查看错误摘要。

### P1.4 分钟线数据资产

- 新增 `stock_minute_bar`。
- 接入 LongPort 历史 K 线分钟粒度。
- 支持 1M/5M/15M/30M/60M。
- 支持按标的、日期范围、粒度查询。
- 支持数据质量审计和水位。

### P1.5 异动大屏

- 展示持仓股、自选股、计划股、自定义板块。
- 聚合 HIGH/WARN 提醒。
- 增加量价观察规则。
- 支持把提醒转为盘后复盘线索。

### P2 多源与量化分析

- 调研并接入 Tushare/AKShare/BaoStock 的只读 provider 或导入桥。
- 计算 MA、MACD、RSI、BOLL。
- 建设策略信号和回测，但只消费质量通过的数据。

## 11. 验收标准

- [ ] 用户能从工作台进入行情工作台，不需要先理解底层接口。
- [ ] 用户能配置“贵州茅台，2025-01 至今，30min”的历史补档任务。
- [ ] 用户能配置“五粮液，交易时段内按指定频率采集”的盘中任务。
- [ ] 系统能展示任务预估请求量、落库量、限流风险和 provider 权限状态。
- [ ] 任务执行状态、明细、失败原因、水位可查。
- [ ] 分钟线与日线、最新价快照边界清晰。
- [ ] 数据质量提醒、风险观察、机会观察在 UI 上分开。
- [ ] 外部行情不覆盖 `portfolio_price_snapshot`。
- [ ] 不接交易、订单、账户、真实持仓能力。
- [ ] 新增表使用 Flyway migration，SQL 写 MyBatis XML。

