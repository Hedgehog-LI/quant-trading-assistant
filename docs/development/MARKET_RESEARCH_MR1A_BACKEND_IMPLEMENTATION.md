# MR-1A 市场全景后端实现说明

> 日期：2026-08-16 · 分支：`codex/qta-v2-mr1-market-overview-backend`（基于 main `9cd37ae`）
> 范围：正式只读 API `GET /api/v1/market-research/overview`；不接入新 Provider、不回补全市场历史、
> 不开发前端、不新增数据库表、零外部网络调用。
> 契约文档：`docs/api/MARKET_RESEARCH_API.md` §8。本文只记录实现结构与决策，验收记录见
> `docs/acceptance/ACCEPTANCE_LOG.md`（独立验收后追加，本轮不写）。

## 1. 分层结构

| 组件 | 位置 | 职责 |
| --- | --- | --- |
| `MarketOverviewController` | `marketdata/analysis/controller` | 协议与参数绑定；畸形日期 → 400 `VALIDATION_ERROR`（controller 局部 handler，先例 Mr0PocController） |
| `MarketOverviewService` | `marketdata/analysis/service` | 参数边界（market 必填且仅 CN、start/end 必填、顺序、跨度 ≤365）、Mapper 装载（含 300 自然日预热，见 §3 冗余说明）、样本快照/成分视图组装、metadata/quality 外壳（limitations 四条、assumptions、providerAttribution、qualityStatus 判定） |
| `MarketOverviewCalculationManager` | `marketdata/analysis/manager` | 纯计算：基准趋势/回撤、成交活跃度、广度、流动性代理、行业迁移、覆盖率/缺口与质量发现；无 DB/无状态 |
| `MarketOverviewMapper` + XML | `marketdata/analysis/dao`、`mapper/MarketOverviewMapper.xml` | 3 条只读 SELECT（日 K/证券池快照/行业成分），不写任何事实表 |
| `MarketOverviewVO` | `marketdata/analysis/vo` | 正式契约（Java record；不暴露任何 PoC 内部类型） |
| `MarketDerivedCalculators` | `marketdata/analysis/derived` | MR-0 冻结公式唯一实现（样本派生/广度计数/行业覆盖域聚合/占比/收益率/流动性代理/线性插值分位），PoC 与 MR-1A 共同委托 |

## 2. 复用决策（禁止两套算法）

- `Mr0PocAnalysisService` 重构为委托 `MarketDerivedCalculators`（样本派生、广度计数、行业日聚合、
  illiquidity、priceRatio、percentile），数值行为逐位不变——`Mr0PocAnalysisServiceTest` 7/7 保持
  通过即兼容证明；`/mr0-poc/**` 响应与 `analysisContentHash` 不受影响。
- MR-1A 新增公式（MA/中位基线、activeStockRatio、drawdown、迁移前值/中位变化）仅存在于 Manager，
  其中分位/占比/收益率原语仍来自共享计算器。
- liquidity 口径差异是冻结公式差异而非算法复制：PoC 是"逐股窗口均值再横截面"（M-20 汇总），
  MR-1A 是"逐日横截面中位数/P90"（M-21 日频），二者共用 `illiquidityValue` 原语。

## 3. 数据边界与门禁实现

- 样本：与 MR-0 CR-3 同口径（最新档快照流通市值 Top-150 ∪ 基准，as_of 无上界），因此
  `dataScope=SAMPLE`；样本域成交额（M-03，全部样本股）与行业覆盖域成交额（M-12，仅有映射样本股）
  是两个分母，coverageGap（未映射证券数量+成交额+清单）只进 quality，绝不入占比分母。
- 交易日：INDEX_KLINE_DERIVED（基准日 K 推导）；`market_calendar` CN 空表以 assumption 声明，不回填。
- 资金流：不读取任何资金流表；`unavailableMetrics=["OFFICIAL_MONEY_FLOW"]` 于 metadata 与 quality
  双声明，响应不含任何推算资金流字段。
- **M-22 覆盖门禁**（`MarketOverviewCalculationManager` 冻结阈值常量，测试逐档覆盖）：

| 常量 | 冻结值 | 触发行为 |
| --- | --- | --- |
| `BAR_COVERAGE_WARN_THRESHOLD` | 0.90 | `barCoverage` 低于即 `LOW_BAR_COVERAGE` WARN（沿用 MR-0 PoC LOW_COVERAGE 同值） |
| `MEMBERSHIP_COVERAGE_WARN_THRESHOLD` | 0.90 | `membershipCoverage` 低于即 `LOW_MEMBERSHIP_COVERAGE` WARN，整体 `DEGRADED` |
| `MEMBERSHIP_COVERAGE_BLOCK_THRESHOLD` | 0.50 | 低于即追加 `INDUSTRY_MIGRATION_BLOCKED` WARN，`industryTurnoverMigration` 强制为空（沿用板块分析"低于预期成分 50% 即样本不足"纪律） |

  全部缺失（coverage=0）记 `INDUSTRY_MAPPING_MISSING` WARN 并同样阻断；真实 PoC 水平
  （101/150=0.673333）落在告警档（WARN、迁移仍输出），绝不返回 `OK`。
- **预热门禁**：`MID_TERM_MIN_QUALIFIED_TRADING_DAYS=120`（设计 §9.2"中期结论至少 120 个合格交易
  日"）。`qualifiedTradingDays` 按真实合格日计算：当日存在基准日 K 且当日样本日 K 覆盖率 ≥0.90
  （`BAR_COVERAGE_WARN_THRESHOLD`）；空样本恒为 0，不得以基准 K 线数量冒充样本市场合格。
  不足即 `INSUFFICIENT_WARMUP` WARN + `DEGRADED`，短期序列保留，MA60/60 日基线等继续 null 不填 0。
  预热读取 = 窗口前 300 自然日（`MarketOverviewService.WARMUP_LOOKBACK_DAYS`；最坏日历比 ~1.5 下
  可覆盖 ≥190 个交易日，明确冗余，不依赖"180 自然日恰够"假设）。测试交易日历自 2026-06-01（周一）
  起跳过周末构造。
- 质量状态：`NO_DATA`（窗口无基准）> `DEGRADED`（任一 WARN）> `OK`；发现码全集
  `BENCHMARK_DATA_MISSING`/`EMPTY_SAMPLE`/`LOW_BAR_COVERAGE`/`LOW_MEMBERSHIP_COVERAGE`/
  `INDUSTRY_MIGRATION_BLOCKED`/`INSUFFICIENT_WARMUP`/`EMPTY_VALID_TRADING_DAY`/
  `INDUSTRY_MAPPING_MISSING`/`PARTIAL_INDUSTRY_MAPPING`(INFO，仅 coverage≥0.90 的轻微缺口)。

## 4. 测试

聚焦测试合计 **31 个**：MarketOverview 24 个 + `Mr0PocAnalysisServiceTest` 回归 7 个。

- `MarketOverviewCalculationManagerTest`（13）：MA 预热两侧/返回与回撤/中位基线/成交扩散/广度与
  A/D 种子/aboveMa20 分母/流动性分位（11 样本整数序位避免插值歧义）/Top8+OTHER 与前值、中位
  变化/coverageGap 排除/无数据与空样本 + 收口新增：覆盖门禁五档（全 1.0/边界 0.90/低 0.80/
  阻断 0.40/全缺失 0）、真实 101/150=0.673333 不返回 OK、预热门禁四档（19/30/90/120 真实合格
  交易日，交易日历跳过周末）+ 合格日定义（基准 120 天空样本=0、样本仅 40 天=40、覆盖恰
  0.90×120 天通过）。
- `MarketOverviewServiceTest`（7）：120 个交易日门禁全过的完整响应与标签、覆盖域缺口不入分母
  （2/3 覆盖 → DEGRADED）、NO_DATA、空样本、映射全缺失（MISSING+BLOCKED）、短预热
  INSUFFICIENT_WARMUP + 短期序列保留、8 类参数异常（全部 `VALIDATION_ERROR`）。
- `MarketOverviewControllerTest`（4）：MockMvc 200（含 barCoverage/membershipCoverage/
  qualifiedTradingDays）/400/畸形日期/NO_DATA。期望值按冻结公式手工推导。

## 5. 明确不做（本轮边界）

无全市场或资金流误导；不修改 MR-0 指标口径与历史行情数据；不动 `/mr0-poc/**` 行为；不新增
migration；不触碰前端仓库；MR-2（资金地图/四象限）及 P1.10-A 运行时验收均不在本轮范围。

## 6. 遗留事项

- 真实数据验证：本地库已有 2026-07 月度 PoC 数据，未在本轮执行真实 curl（部署验收归独立验收）。
- `CURRENT_ARCHITECTURE_AND_MODULES.md`/`AI_HANDOFF.md`/`BUILD_CHECKLIST.md` 待独立验收通过后
  按交付流程同步（本轮明确不更新）。

## 7. 交付状态（finalization，2026-08-16）

- **独立验收结论：通过**（维护者指令记录，2026-08-16）。能力状态 `VERIFIED`（静态 + 自动化证据
  齐备）；`DEPLOYED` 未达成——Docker/MySQL 真实数据 curl 与 remote 运行时 **NOT_VERIFIED**。
- 验证证据：聚焦 31/31（MarketOverview 24 + Mr0Poc 回归 7）；全量 `./mvnw test`
  **588 tests / 0 failures / 0 errors / 1 skipped**（1 skipped 为既有基线项）；`./mvnw -DskipTests
  package` 通过；`git diff --check` 通过。
- 前端 MR-1B（市场全景页面消费 `/api/v1/market-research/overview`）**尚未开发**；本接口在前端
  接入前无可视消费方。
- 交付同步范围：`AI_HANDOFF.md`、`DEVELOPMENT_LOG.md`、`ACCEPTANCE_LOG.md`、
  `CURRENT_ARCHITECTURE_AND_MODULES.md`（模块表）、`BUILD_CHECKLIST.md`（MR-1A 小节）、
  `docs/api/*`（已在候选内更新）。业务代码在验收后零改动（No-Code Rule）。
