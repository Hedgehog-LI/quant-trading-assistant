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

## 当前最高优先级

### QTA V2 MR-1：市场全景 MVP（下一项；MR-0 及收口直接修复均已独立验收，可合并 main）

产品基线：`features/QTA_V2_QUANT_RESEARCH_PLATFORM_PRD.md`、
`features/QTA_V2_INSTITUTIONAL_MARKET_RESEARCH_DESIGN.md`、
`decisions/ADR-0014-institutional-market-research-and-data-first-quant-workflow.md`。

- **MR-0 收口直接修复已完成并独立验收（2026-08-16）**：R1 收口任务 `QTA-V2-MR0-CLOSEOUT-20260815-R1` 的 `BLOCKED` 终态保留为历史事实；其暴露的时间门禁代际误报已直接修复（REVIEW_CLEAR 绑定 `CANDIDATE_FROZEN` 代数、缺同代 CLEAR 显式拒绝、多周期/跨代/缺同代三个回归）。Codex 独立验收：排序 **10/10**、治理 **84/84**、后端 **564 tests**、package、真实 PoC **SUCCESS/213s**（双哈希一致、二次导入四表 `inserted=0`、`failures=[]`）。**MR-0 代码可合并 main**；MR-0 仍只是样本级 PoC，不等于 MR-1 全市场数据底座。入口：`development/tasks/QTA-V2-MR0-DIRECT-REPAIR-VERIFICATION-20260816.md`。
- **MR-0 数据与语义 PoC 已完成（2026-08-15，VERIFIED/ACCEPTED）**：指标数据字典/Provider 能力矩阵/现状盘点已冻结；V23 `mr0_` 事实表 + `marketdata.poc` 分析/质量引擎 + 一键 PoC 脚本；2026-07 完整交易月真实 PoC 证明样本级广度/行业占比/波动/流动性/资金口径可重算（双哈希一致+二次导入幂等）。入口：`features/MARKET_RESEARCH_MR0_*.md`、`development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-REPORT.md`。
- MR-1 按冻结输入边界实施：先交付基准走势、成交量、流动性/活跃度、市场广度和行业成交占比迁移，不一次承包全部 V2；全市场逐股历史、point-in-time 申万成分、官方口径资金流在凭据/选型 ADR 就绪前保持阻断标注。
- MR-1 前置清理：本地 LONGPORT SH.600519 8 行 volume 手/股脏数据；生产 Provider 选型 ADR（MR-0 公共源探针仅为 PoC 证据）。
- 不建设脱离数据能力的新展示型大屏，不把排行样本、成交活跃度或价格相对强弱包装为全市场资金流。
- 现有 P1.10-A、P1.7、P1.9 和行情采集能力继续维护和复用，不推倒重写。

## 已实现/待验证的并行基线

### P1.10：市场研究与个股决策中心（A 阶段全栈候选已完成）

设计基线：`features/MARKET_RESEARCH_DECISION_CENTER_DESIGN.md`、`features/MARKET_SECTOR_ANALYTICS_DESIGN.md`、`decisions/ADR-0013-research-funnel-and-asset-inspection-boundary.md`。

- P1.10-A 前后端候选已完成：V19-V22、稳定板块身份、数据门禁、相对强弱、固定 5 日轮动、原子发布、自动触发、readiness/radar/history/detail API，以及 `/market-research` 雷达和板块详情。下一步是 Docker/MySQL/真实样本与 remote 页面验收；真实资金流、量价证据仍未实现。
- P1.10-B：建设板块内候选扫描，支持可解释候选表和最多 16 图的统一尺度多股网格。
- P1.10-C：建设个股决策台，叠加真实成交、持仓均价、计划止损止盈、板块背景和数据质量。
- 原 `/market-assets` 改为数据详情和原始事实追溯，不再承担研究首页职责。
- 策略候选点后置到 P2，必须有版本化规则、回测证据和风险门禁。

> V2 继承说明：以上 P1.10 内容是现有实现和兼容基线；后续产品导航、页面图形与交付顺序以 V2 冻结设计为准。

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
