# Product Blueprint

> 本文件沉淀 Quant Trading Assistant 的产品设计。实现细节以 API 文档和代码为准；产品方向以本文件为准。

## 1. 产品定位

Quant Trading Assistant 是个人交易辅助工作台。它帮助用户把短线交易和做 T 过程记录化、规则化、可复盘。

它不是自动交易系统，也不是荐股系统。

## 2. 核心用户问题

1. 我今天关注哪些股票，为什么关注？
2. 盘前计划是什么，允许交易的条件是什么？
3. 我实际买卖了什么，是否按计划执行？
4. 当前持仓和已结算交易到底赚亏多少？
5. 每次持仓变化、浮盈亏变化能否留档？
6. 哪些错误反复出现，如何复盘改进？

## 3. 模块地图

| 模块 | 用户价值 | 当前形态 | 数据要求 |
| --- | --- | --- | --- |
| Dashboard 工作台 | 一屏看今日状态和风险提醒 | 聚合自选、计划、交易、复盘 | remote 模式落 DB |
| Watchlist 自选股 | 管理关注池和交易风格 | CRUD + 启用/停用 | remote 模式落 DB |
| Trade Plan 交易计划 | 盘前纪律和允许交易条件 | CRUD + 状态流转 | remote 模式落 DB |
| Risk Calculator 风控计算 | 计算仓位和单笔风险 | 纯计算 | 可不落库 |
| Trade Journal 交易记录 | 记录真实买卖流水 | CRUD + 复盘状态 | remote 模式落 DB |
| Portfolio Ledger 交易账本 | 根据交易流水计算持仓、已结算盈亏 | FIFO 计算 + 当前价维护 | 交易流水和价格快照落 DB |
| Position Snapshot 持仓快照 | 记录某时点实际券商持仓 | 前后端完成并已联调 | remote 模式落 DB |
| Review 盘后复盘 | 复盘计划执行、错误、改进 | CRUD + 关联交易 | remote 模式落 DB |
| Workflow Optimization 基础闭环优化 | 串联计划、交易、账本、快照和复盘 | v0.1.1 已完成 | 原始数据落 DB，差异实时计算 |
| Market Data Foundation 行情基础 | 统一证券代码并沉淀价格和日 K | P1.0 证券主数据 + CSV 日 K 已完成；P1.1 LongPort 只读行情源真实外联已完成 | 行情需记录来源和抓取时间 |
| Market Data Workbench 行情工作台 | 一屏看重点股票、板块、采集任务和异动提醒 | P1.2 工作台、LongPort 分钟 K、手工补档与 A 股盘中 scheduler 已通过自动化、Docker 和真实外联验收 | 最新价、分钟线、任务、水位、提醒落 DB |
| Security Directory 证券目录与智能检索 | 输入代码或名称选择 A/H/US 证券并自动填充 | P1.4a 精确代码 + LongPort 静态信息/报价确认已完成；P1.4b 本地目录与模糊搜索待开发 | 精确验证只读；本地目录后续负责名称/拼音搜索 |
| Market Sector Catalog 市场板块目录 | 查看 A/H/US 行业排行，关注采集板块/成分资金快照并衔接 ETF 跟踪 | P1.5 发现、关注、手动采集、历史与成分数据资产已完成 | 市场板块与自定义分组分离，资金字段保留 provider 口径和延迟标志 |
| AI Image Import 图片识别导入 | 从券商截图生成持仓草稿 | 暂缓 | 识别草稿需人工确认 |

## 4. 核心业务语言

| 业务概念 | 类比 | 说明 |
| --- | --- | --- |
| 交易记录 | 进货/出货流水 | 每一笔买入和卖出 |
| 交易账本 | 基于流水自动算库存和利润 | FIFO 计算持仓、已结算交易、盈亏 |
| 持仓快照 | 盘点库存 | 某一时刻券商账户实际持仓 |
| 当前价快照 | 手工估价 | 不连接实时行情，用户自己维护 |
| 复盘 | 经营总结 | 为什么买、为什么卖、错在哪里 |

## 5. 数据模式产品原则

正式使用时，核心业务数据要落 DB。`localStorage` 只保留三个用途：

1. 前端本地开发。
2. 离线或临时演示。
3. 老数据迁移前的临时备份。

页面文案必须清楚说明：

- 本地模式的数据只在当前浏览器。
- 后端模式的数据通过 REST API 写入后端 DB。
- 两种模式不会自动同步。
- 如果要迁移，需要专门的导入流程。

## 6. 当前产品建设重点

### P0: 稳定核心记录闭环

- 自选股、交易计划、交易记录、复盘、交易账本都能走 remote 模式。
- 生产部署默认推荐 remote + 同源 `/api/v1`。
- 后端 API、前端 adapter、文档保持一致。

### 已完成 P0: 持仓快照

后端已完成：

- DB 表。
- 草稿、确认、作废状态流转。
- 历史、详情、最近快照 API。
- 金额和比例后端统一计算。

前端已完成：

- 手工录入页面。
- 历史快照列表。
- 快照详情。
- 最近一次快照。
- 草稿编辑、确认与作废。
- mock/remote 双模式。

### 已完成并验收: v0.1.1 基础交易闭环优化

按 `docs/features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md` 实施：

- 交易计划与交易记录强关联。
- 复盘关联的删除保护和状态回算。
- 两次已确认持仓快照差异对比。
- 实际持仓快照与指定时点 FIFO 理论持仓对账。
- 工作台升级为数据质量和纪律待办中心。
- 生产环境同源 `/api/v1` 和 localhost 防误配。

本阶段不接 AI，不接行情源，不自动修正交易数据。

### 当前 P1: 证券主数据、行情工作台与采集任务

按 `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md` 逐步建设：

- P1.0 已完成：`stock_basic`、统一证券标识、CSV 日 K 导入和幂等写入。
- P1.1 已完成：LongPort provider facade + DB + API + 前端已完成；后端反射式 SDK adapter 已完成；官方 Java SDK 已装入 `runtime-libs/`（`io.github.longportapp:openapi-sdk:4.3.3`，vendor jar 被 gitignore），真实单 symbol 外联已于 2026-07-12 验收通过（latest quote + daily bar 落 `dataSource=LONGPORT`）。部署需配 `LONGPORT_HTTP_URL=https://openapi.longbridge.cn` + `LONGPORT_QUOTE_WEBSOCKET_URL=wss://openapi-quote.longbridge.cn/v2`（SDK 默认域名已废弃）。
- P1.2/P1.3 已实现并验收：行情工作台、结构化采集计划、LongPort 分钟 K、历史补档、A 股盘中自动调度、任务明细/水位、板块/自定义分组。异动观察尚未完成；港美股盘中任务在时区/日历补齐前明确禁用。详见 `docs/features/MARKET_DATA_WORKBENCH_AND_COLLECTION_DESIGN.md`。
- P1.4a 已实现并真实验收：采集计划支持 A/H/US 市场 + 精确代码，通过 LongPort Static Info + Quote 展示名称、统一代码、当前价和报价时间，用户确认后加入 scope；验证过程不落库。P1.4b 本地证券目录 + 名称/拼音模糊检索仍待开发。详见 `docs/features/EXACT_SECURITY_VERIFICATION_DESIGN.md` 与 `docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`。
- 手工估值与外部行情严格分离：外部行情不得自动覆盖 `portfolio_price_snapshot`。
- LongPort 仅作为行情 provider，不接交易、账户、订单、真实持仓能力。
- P1.5 已建设 CN/HK/US 行业排行/层级/成分、行业关注、手动采集与历史快照；ETF 和指数作为可选关联证券复用现有采集计划。下一步基于快照开发相对强弱、资金趋势和异动解释。
- 指标、策略和回测要等分钟线/日线质量治理和采集水位稳定后再推进。

### P2: 指标、策略和回测

- 日 K / 分钟线数据质量治理。
- MA、MACD、RSI、BOLL。
- 简化策略信号。
- 回测引擎。

## 7. 图片识别导入原则

图片识别当前暂缓，不是 v0.1.1 的开发范围。未来恢复时继续遵循以下原则。

图片识别只能作为自动填表，不直接写正式数据。

流程必须是：

```text
上传/粘贴截图
-> 后端临时接收图片
-> OCR/视觉模型解析
-> 生成持仓快照草稿
-> 用户逐行确认/修改
-> 确认后写入 DB
```

隐私要求：

- 不上传账号、姓名、资金账号等敏感信息。
- 前端提示用户先遮挡隐私。
- 后端日志不得打印图片内容、API Key 或完整识别原文。
- 原图默认不长期保存。

## 8. 产品验收标准

一个交易辅助功能上线前必须回答：

1. 这个功能解决哪一个交易记录或复盘问题？
2. 数据落在哪里？是否能跨设备读取？
3. 用户是否能人工确认关键结果？
4. 错误输入或 AI 识别错误是否可修正？
5. 是否有“不构成投资建议”的风险提示？
6. 是否避免了自动下单或真实券商连接？
