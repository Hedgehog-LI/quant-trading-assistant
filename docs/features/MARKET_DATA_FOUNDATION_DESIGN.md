# Market Data Foundation Design

> 状态：P1.0 已实现证券主数据 + CSV 日 K；P1.1 设计 LongPort 只读行情源。
> 目的：为查询股票价格并落库确定数据边界，避免把手工估值、外部实时快照和历史 K 线混在一起。

## 1. 产品目标

后续系统需要回答：

1. 证券代码对应哪个公司、哪个交易所？
2. 某一时刻查询到的价格是多少，来自哪里？
3. 某个交易日的开高低收、成交量和成交额是多少？
4. 数据什么时候抓取、是否重复、是否缺失？
5. 交易账本使用的是手工估值还是外部行情？

## 2. 明确区分的数据

| 数据 | 建议表 | 用途 |
| --- | --- | --- |
| 证券基础信息 | `stock_basic` | 代码、名称、交易所、上市状态 |
| 手工当前价 | `portfolio_price_snapshot` | 现有功能，用户手工估算持仓盈亏 |
| 外部价格快照 | `stock_quote_snapshot` | 某次查询获得的价格和来源 |
| 日 K 行情 | `stock_daily_bar` | 指标、策略和回测 |
| 同步任务 | `market_data_sync_task` | 记录任务状态、范围和错误摘要 |

现有 `portfolio_price_snapshot` 不重命名、不冒充真实行情，也不直接被外部行情覆盖。

当前实现事实：

- `stock_basic` 已由 `V5__add_market_data_tables.sql` 实现。
- `stock_daily_bar` 已由 `V5__add_market_data_tables.sql` 实现。
- `stock_daily_bar.fetched_at` 已由 `V6__add_fetched_at_to_daily_bar.sql` 实现。
- CSV 日 K 幂等导入已实现，`data_source=CSV`。
- LongPort provider facade、stock_quote_snapshot、market_data_sync_task、market_data_alert 已实现（V7-V9 migration）。真实 LongPort SDK 凭据联调待完成。

## 3. 证券主数据

建议第一步实现 `stock_basic`：

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `symbol` | 原始证券代码，如 `600519` |
| `exchange` | `SH` / `SZ` / `BJ` / 其他 |
| `canonical_symbol` | 统一标识，如 `SH.600519` |
| `name` | 当前证券名称 |
| `market` | A 股、港股、美股等市场分类 |
| `currency` | 币种 |
| `list_status` | 上市、停牌、退市等基础状态 |
| `list_date` | 上市日期 |
| `data_source` | 基础信息来源 |
| `source_updated_at` | 来源更新时间 |
| `created_at` / `updated_at` | 系统时间 |

唯一约束建议使用 `canonical_symbol`。现有业务表继续保留 `symbol` 和 `name`，它们是交易发生时的业务快照，不要求立即增加外键。

## 4. 外部价格快照

`stock_quote_snapshot` 建议字段：

- `id`
- `canonical_symbol`
- `quote_time`
- `current_price`
- `open_price`
- `high_price`
- `low_price`
- `pre_close_price`
- `volume`
- `amount`
- `data_source`
- `fetched_at`
- `raw_hash`
- `created_at`

价格、金额使用 `DECIMAL`，成交量使用 `BIGINT`。同一来源、证券和报价时间应幂等写入。

## 5. 日 K 行情

`stock_daily_bar` 沿用现有数据库设计方向，并补充：

- `canonical_symbol`
- `trade_date`
- `open_price` / `high_price` / `low_price` / `close_price`
- `pre_close_price`
- `volume` / `amount`
- `turnover_rate`
- `adjust_type`：不复权、前复权、后复权
- `data_source`
- `fetched_at`

唯一约束至少包含：证券、交易日、复权类型和数据来源。指标与回测必须明确使用哪一种复权口径。

## 6. 数据源适配边界

后端通过 provider 接口隔离具体数据源：

```text
MarketDataProvider
├── queryStockBasic(...)
├── queryLatestQuote(...)
└── queryDailyBars(...)
```

规则：

- Controller 不直接调用第三方 SDK。
- API Key 只存在服务器环境变量或密钥管理中，不进入前端和 Git。
- Provider 返回统一领域对象，第三方字段转换放 adapter/convert。
- 原始响应默认不长期保存；需要审计时只保存脱敏摘要或 hash。
- 限流、重试、超时和熔断必须由数据源适配层统一处理。

LongPort 特别约束：

- LongPort 只作为只读行情 provider，见 `../decisions/ADR-0008-longport-quote-only-provider.md`。
- 只允许使用 Quote 相关能力，不允许接入交易、订单、账户资金、真实持仓能力。
- LongPort 标的代码使用 `ticker.region`，系统内部使用 `region.ticker`，例如内部 `SH.600519` 映射为 provider `600519.SH`。
- LongPort 凭据只存在服务端环境变量或安全配置中，不进入前端、DB、日志或 Git。

## 7. 推荐实施顺序

1. 完成当前交易闭环优化。
2. 已完成 `stock_basic`、代码规范化和查询 API。
3. 已完成 CSV 日 K 导入，验证表结构和幂等规则。
4. 接入 LongPort 只读行情 provider 的最新价查询。
5. 增加 `stock_quote_snapshot`、同步任务、失败重试和数据质量检查。
6. 仅在用户明确选择时，让交易账本参考外部最新价展示估值来源；不得自动覆盖手工价。
7. 最后建设指标、策略和回测。

## 8. 验收原则

- [x] 手工价格与日 K 来源清楚区分。
- [x] 股票代码有统一、无歧义的规范化方式。
- [x] CSV 日 K 不会重复写入。
- [x] CSV 日 K 可追溯数据来源和抓取时间。
- [ ] LongPort API Key 不出现在前端、日志和仓库。
- [ ] 外部最新价快照不覆盖用户手工数据。
- [ ] 行情仅用于辅助分析，不包装成确定性交易建议。
