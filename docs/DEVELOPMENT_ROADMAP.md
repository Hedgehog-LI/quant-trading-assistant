# Development Roadmap

> 路线图。当前事实以 `BUILD_CHECKLIST.md` + `AI_HANDOFF.md` 为准。早期版本的"v0.1 功能清单"中的日 K / 指标 / 策略 / 回测属**未来能力**，非 v0.1.1 已实现范围。

## 已完成

- **v0.1.0** Today MVP + 交易账本 + 持仓快照（Spring Boot 单体 + MyBatis + Flyway + React mock/remote 双模式）。
- **v0.1.1** 基础交易闭环优化（计划关联 / 复盘一致性 / 快照对比 / FIFO 对账 / 工作台待办 / 连接防呆 + 多轮质量收尾）。**已验收**：后端 121、前端 179 测试通过。
- **P1.0** 证券主数据与 CSV 日 K 基础：`stock_basic`、`stock_daily_bar`、CSV 幂等导入、`fetched_at` 已实现。

## 已完成的 P1 行情能力

- **P1.1** LongPort quote-only provider、最新价、历史日 K 同步、异常提醒和前端行情页已完成；A 股真实最小外联已验收，港美股代码链路已实现、真实权限待部署验收。
- **P1.2/P1.3** 行情工作台、LongPort 分钟 K、历史补档、A 股盘中调度、任务明细/水位和板块管理已实现；2026-07-17 通过 Docker MySQL 和最小真实 LongPort 验收。
- **P1.5** CN/HK/US 市场行业发现、关注、手动采集、聚合/成分快照及历史查询已实现；行业接口使用签名 HTTPS，不依赖缺失 JNI。

## 当前并行优先级

### P1.2：分钟行情执行引擎（已完成）

- LongPort SDK 4.3.3 原生分钟 K adapter、分段和限流已实现。
- `MINUTE_BAR_BACKFILL` / `INTRADAY_MINUTE_REFRESH` 与 DB claim、重启收敛已打通。
- A 股交易日/时段/频率 scheduler 已实现，港美股待各自时区/日历后再开放。
- Fake provider Docker 幂等/部分失败与 LongPort SH.601318 单日 5M 已验收。

### P1.4：证券目录与智能检索

设计基线：`features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`、`decisions/ADR-0009-local-first-security-directory.md`。

- 扩展现有 `stock_basic` 为本地统一证券目录，增加名称、别名、交易所、类型和来源治理。
- 建设确定性证券搜索 API，支持名称、代码、别名和拼音。
- 建设共享证券选择器，先接入最新价、历史同步、采集计划和板块成员。
- 用目录 provider 保持元数据新鲜；LongPort Static Info 仅用于已知代码后的补全，不在每次键入时外联。

实施顺序与门禁见 `development/SECURITY_DIRECTORY_SEARCH_IMPLEMENTATION_PLAN.md`。P1.2 执行引擎和 P1.4 本地搜索可以分支独立推进，但不得同时修改相同的行情表单文件后强行合并。

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
