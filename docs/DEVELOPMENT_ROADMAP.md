# Development Roadmap

> 路线图。当前事实以 `BUILD_CHECKLIST.md` + `AI_HANDOFF.md` 为准。早期版本的"v0.1 功能清单"中的日 K / 指标 / 策略 / 回测属**未来能力**，非 v0.1.1 已实现范围。

## 已完成

- **v0.1.0** Today MVP + 交易账本 + 持仓快照（Spring Boot 单体 + MyBatis + Flyway V1-V4 + React mock/remote 双模式）。
- **v0.1.1** 基础交易闭环优化（计划关联 / 复盘一致性 / 快照对比 / FIFO 对账 / 工作台待办 / 连接防呆 + 多轮质量收尾）。**已验收**：后端 121、前端 179 测试通过。

## 下一阶段（P1）：证券主数据与行情基础

设计基线：`features/MARKET_DATA_FOUNDATION_DESIGN.md`。

- 建 `stock_basic` 与统一证券标识。
- 先做 CSV 日 K 幂等导入。
- 再封装行情 provider，接入一个数据源。
- 明确手工估值、外部价格快照与日 K 的边界。
- API Key 只在服务端配置，不进前端与仓库。

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
