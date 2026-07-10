# 2026-07-10 LongPort 行情接入研究过程记录

> 本文是产品/架构研究过程沉淀，记录设计来源和裁决。实现事实以后续代码、migration、测试和 API 文档为准。

## 1. 研究背景

用户希望评估是否可以通过 LongPort/长桥 OpenAPI 接入 A 股行情，实现：

- 实时或准实时最新价查询。
- 历史数据存档。
- 异常变动提醒。
- 后续基于成交量、价格、K 线做量化分析。

项目约束不变：Quant Trading Assistant 是个人交易辅助系统，不自动交易、不连接真实券商交易能力、不保存券商密码或交易密钥、不承诺收益。

## 2. 已读取的项目上下文

- `docs/AI_DEVELOPMENT_INDEX.md`
- `docs/PRODUCT_BLUEPRINT.md`
- `docs/BUILD_CHECKLIST.md`
- `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`
- `docs/CURRENT_ARCHITECTURE_AND_MODULES.md`
- `docs/DATABASE_DESIGN.md`
- 实际代码：`src/main/java/com/quant/trade/marketdata`
- 实际 migration：`V5__add_market_data_tables.sql`、`V6__add_fetched_at_to_daily_bar.sql`
- 前端行情页和建设看板：`quant-trading-assistant-web/src/pages/market-data.tsx`、`buildStatusData.ts`

关键发现：部分文档落后于代码事实。实际代码已存在 `stock_basic`、`stock_daily_bar`、CSV 日 K 导入和 `/api/v1/market-data/*` 基础接口。

## 3. 外部资料

- LongPort 行情概览：https://open.longportapp.com/zh-CN/docs/quote/overview
- LongPort 实时行情：https://open.longportapp.com/zh-CN/docs/quote/pull/quote
- LongPort 历史 K 线：https://open.longportapp.com/zh-CN/docs/quote/pull/history-candlestick
- LongPort 实时价格推送：https://open.longportapp.com/zh-CN/docs/quote/push/quote
- LongPort SDK：https://open.longportapp.com/zh-CN/sdk
- LongPort MCP：https://open.longportapp.com/zh-CN/docs/mcp

官方文档显示：

- A 股代码格式为 `ticker.region`，如 `600519.SH`、`399001.SZ`。
- 实时行情接口支持批量查询，单次请求标的上限 500。
- 返回字段包含最新价、昨收、开高低、成交量、成交额、交易状态和时间戳。
- 实时价格推送包含最新价、开高低、成交量、成交额、交易状态、当前增量成交量/成交额。
- Java SDK 可通过 Maven 依赖 `io.github.longport:openapi-sdk:4.0.5` 引入。
- LongPort MCP 覆盖行情、账户、资产组合和交易能力，因此本项目必须在提示词和架构上限定只读行情能力。

## 4. 专家团结论摘要

### 产品/交易员视角

- 后续不是从零做行情基础，而是先修正文档事实，再接 quote-only provider。
- 第一阶段只围绕自选股、持仓股、交易计划股，不做全市场扫描。
- 最有价值提醒：放量跌破、放量突破、冲高回落、缩量反弹、跳空、高振幅、数据质量异常。
- 量比需要按交易时间进度修正。

### 后端架构视角

- LongPort 接入应留在现有 `marketdata` 模块，不另起平行体系。
- 新增 provider 抽象、quote snapshot manager、sync task manager、alert manager。
- 复用 `stock_basic` 和 `stock_daily_bar`。
- 新增 `stock_quote_snapshot`、`market_data_sync_task`、`market_data_alert`。
- 默认关闭 provider，CI 使用 Fake provider，真实凭据只从服务端环境读取。

### 前端/UX 视角

- 在现有 `/market-data` 页面增量扩展，不新增孤立菜单。
- 页面建议 5 个 Tab：行情状态、证券主数据、最新价快照、历史数据同步、异常提醒。
- 设置页不提供 LongPort 密钥输入。
- 建设看板需要新增 LongPort 相关节点并修正 P1.0 已完成事实。

## 5. 最终裁决

采用 `P1.1 LongPort 只读行情源接入` 方案：

- 只接 Quote provider。
- 只读行情，不接 Trade/Account/Order/Position。
- 最新价进入 `stock_quote_snapshot`。
- 历史日 K 进入 `stock_daily_bar`，`data_source=LONGPORT`。
- 同步任务和异常提醒单独建表。
- 前端只展示 provider 状态和脱敏错误，不接触密钥。

## 6. 后续执行入口

实现前必须先读：

- `docs/features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`
- `docs/features/MARKET_ALERT_RULES_DESIGN.md`
- `docs/api/MARKET_DATA_API.md`
- `docs/decisions/ADR-0008-longport-quote-only-provider.md`
- `docs/prompts/LONGPORT_MARKET_DATA_CLAUDE_PROMPT.md`
