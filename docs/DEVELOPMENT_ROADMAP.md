# Development Roadmap

> 路线图。当前事实以 `BUILD_CHECKLIST.md` + `AI_HANDOFF.md` 为准。早期版本的"v0.1 功能清单"中的日 K / 指标 / 策略 / 回测属**未来能力**，非 v0.1.1 已实现范围。

## 已完成

- **v0.1.0** Today MVP + 交易账本 + 持仓快照（Spring Boot 单体 + MyBatis + Flyway + React mock/remote 双模式）。
- **v0.1.1** 基础交易闭环优化（计划关联 / 复盘一致性 / 快照对比 / FIFO 对账 / 工作台待办 / 连接防呆 + 多轮质量收尾）。**已验收**：后端 121、前端 179 测试通过。
- **P1.0** 证券主数据与 CSV 日 K 基础：`stock_basic`、`stock_daily_bar`、CSV 幂等导入、`fetched_at` 已实现。

## 下一阶段（P1.1）：LongPort 只读行情源

设计基线：`features/MARKET_DATA_FOUNDATION_DESIGN.md`、`features/LONGPORT_MARKET_DATA_PROVIDER_DESIGN.md`、`features/MARKET_ALERT_RULES_DESIGN.md`。

- 封装 LongPort quote-only provider，只读行情，不接交易/账户/订单/真实持仓。
- 新增 `stock_quote_snapshot` 外部最新价快照。
- 新增 `market_data_sync_task` 历史日 K 同步任务。
- 新增 `market_data_alert` 数据质量和量价观察提醒。
- 前端 `/market-data` 扩展为行情状态、证券主数据、最新价快照、历史同步、异常提醒。
- LongPort token/app secret 只在服务端配置，不进前端、DB、日志与仓库。

## 后续（P2+）：指标、策略、回测

- 日 K 导入完成后：MA / MACD / RSI / BOLL 指标。
- 策略信号（均线趋势 + 成交量过滤），统一表达为"辅助信号 + 风险提示 + 人工确认"。
- 简化回测（手续费 / 滑点 / T+1）。
- 早期 `BACKTEST_ENGINE_DESIGN.md` / `STRATEGY_PLUGIN_DESIGN.md` / `ARCHITECTURE.md` 已标 **Historical**，仅作参考；落地时按当时事实重新设计。

## 暂缓

- AI 图片识别 / OCR 截图导入（持仓快照草稿流程已就绪，识别能力后置）。

## 每次开发验收

- 后端 `./mvnw test` + `package` 通过；前端 `typecheck` / `lint` / `test` / `build` 通过。
- 新 DB 走更高版本 Flyway migration；新接口同步 `api/API_INDEX.md`。
- 涉及交易信号必须有风险提示；不连券商 / 不自动下单 / 不存密钥。
