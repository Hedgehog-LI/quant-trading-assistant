# 任务契约：P110-A-BE-MARKET-DISCOVERY-20260812

> 版本：2 · 状态：FROZEN（test-designer amendments A1-A5 已折叠，contract_hash 已冻结）
> Lane：L2 · git_automation：COMMIT · 候选模式：COMMIT
> 基线提交：c941309ccac118e6dc52c42b94cd92d654e5269a（main）
> 任务分支：codex/p110-a-be-market-discovery-20260812

## 1. 任务定位

实现 P1.10-A 市场发现闭环的**后端**最小可验收切片：数据就绪门禁、稳定板块身份、计算运行与血缘、市场雷达与板块详情所需的最小衍生指标和只读 API。本任务是 L2，能在 8 AC、14 role-run、每 slice ≤500 行内完成的最小完整闭环。

### 1.1 权威设计来源（按优先级）

1. `docs/features/MARKET_RESEARCH_DECISION_CENTER_DESIGN.md`（产品语义、漏斗、read model 职责）
2. `docs/decisions/ADR-0013-research-funnel-and-asset-inspection-boundary.md`（边界）
3. `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md`（指标公式、单位、scope、质量状态、数据模型 V19+、发布策略）
4. `docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md`（ST-A1/A2/B1/B2 拆分）
5. `docs/features/MARKET_DATA_ASSET_CENTER_DESIGN.md`（只读查询模式参考）

### 1.2 本任务范围（冻结）

实现 ST-A1 数据就绪门禁与稳定身份、ST-A2 计算运行与血缘、ST-B1 最小衍生指标（相对强弱 + 板块级轮动持续性）、ST-B2 查询 API 与控制器（市场雷达 + 板块详情只读）。这些组合后能支撑市场雷达和板块详情的最小首屏。

### 1.3 本任务明确排除（冻结）

- 不实现候选扫描（P1.10-B）、个股决策台（P1.10-C）、策略信号、回测、自动交易。
- 不实现资金趋势（§5.3.1）、交易集中度（§5.3.2）、量价确认六状态（§5.4）、异动提醒（§5.5）——这些公式在设计中已冻结，但属于市场雷达的次要列，留作后续 P1.10-A 续作任务，本任务只保留 scope 占位和 `NO_DERIVED_DATA`/`UNAVAILABLE` 降级语义，不计算。
- 不修改前端仓库、不修改 `.agents/`、`.zcode/`、治理脚本。
- 不引入新 provider、不真实外联、不伪造全市场或真实资金流。
- 不修改 V1–V18 已发布 migration（只新增 V19+）。
- 不做 Docker、MySQL runtime、服务器部署验收（记录为 NOT_VERIFIED）。

### 1.4 关键自主决策记录（无人值守）

1. **范围收敛**：完整 P1.7（ST-A1/A2/B1/B2 全部指标）超出单 L2 任务边界（8 AC / 14 role-run / 每 slice 500 行）。本任务收敛到 ST-A1 + ST-A2 + RS + 板块级轮动持续性 + 雷达/详情只读 API，为最小可独立验收的市场发现闭环。依据：`MARKET_SECTOR_ANALYTICS_DESIGN.md` §0（P1.7-A 前置门禁通过后才可开发 B；分阶段强制）、`P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md` §3 DAG（ST-A1 → ST-A2 → ST-B1 → ST-B2）。
2. **scope 固定为 RANKED_UNIVERSE**：LongPort 排行上限 100 且无权威总数（§5.1），MVP 固定 `RANKED_UNIVERSE`，不得产 `VERIFIED_FULL_MARKET`。
3. **remove watch cascade**：V14 的 `market_sector_snapshot.fk_sector_snapshot_watch ON DELETE CASCADE` 与 §6.1 "移除 watch 级联删除并改为归档" 冲突。本任务 V19 新增 `sector_identity_id` 列并回填，但**不删除既有 FK**（避免破坏现有 watch 生命周期测试）；改为在 readiness 门禁和衍生查询层只使用 `sector_identity_id` 作为身份，watch 级联删除的迁移作为独立后续任务。记录为 BLOCKING_AMENDMENT_01 的边界说明（非放宽，而是显式缩小本轮 schema 改动面，保持 V1–V18 已发布 migration 不变）。
4. **market_calendar source/verification 字段**：§5 要求增加 `source_code/verification_status`，但现有 V10 已发布 `market_calendar` 且被 scheduler 依赖。本任务 V19 `ALTER TABLE` 增加可空列 `source_code VARCHAR(32) NULL`、`verification_status VARCHAR(24) NOT NULL DEFAULT 'INFERRED'`，默认 `INFERRED`（周末推断）不阻断现有逻辑，readiness 长窗口门禁只接受 `EXCHANGE_FILE/MANUAL_VERIFIED`。
5. **benchmark 固定**：RS 共同基准固定 `RANK_SET_EQUAL_WEIGHT`（§5.1），不引入外部指数。
6. **窗口固定**：RS `window=20`、板块级轮动 `window=5/10/20`，对应雷达首屏默认。
7. **formulaVersion 固定 v1**：端点默认 `formulaVersion=v1`，服务端按窗口和默认阈值计算唯一 `parameterHash`。
8. **薄切片总览**：衍生未发布时市场雷达返回 `THIN` 概览（原始 CLOSE 榜单 + `NO_DERIVED_DATA`），不伪造衍生结论。

## 2. 验收标准（8 AC）

每个 AC 描述一个外部可观察结果，最多 2 类强制证据。

### AC-01：数据就绪门禁返回真实 scope 与质量状态，不伪造全市场
**结果**：`GET /api/v1/market-research/readiness?market=CN` 返回市场、最新成功 CLOSE 批次、研究范围（固定 `RANKED_UNIVERSE`，中文说明"排行样本，不代表全市场"）、样本量、覆盖率、完整性（`is_truncated`/`expected_item_count`/`actual_item_count`）、新鲜度（`asOfDate`/`sourceQuoteTime`）和质量状态。`expected_item_count` 不得来自响应行数（禁止用返回条数反填 expected，伪造 `coverage_rate=1`）。provider quote time 缺失时 `reasonCodes` 含 `SOURCE_TIME_UNKNOWN` 且 `qualityStatus≠OK`。无 CLOSE 批次时返回 `NO_DERIVED_DATA` + 非空 `reasonCodes`，雷达拒绝返回衍生结论。HK/US 长窗口且 `market_calendar.verification_status=INFERRED` 时返回 `INSUFFICIENT_RAW`，不静默接受。
**强制证据**：`STATIC`（MockMvc readiness：有批次/无批次/单批/陈旧/source-time-null/HK-US INFERRED）、`AUTOMATION`（`./mvnw test`）。
**test-designer amendment A1 已折叠**。

### AC-02：稳定板块身份与跨 taxonomy 区间锁，watch_id 不参与历史身份
**结果**：V19 新增 `market_sector_identity`（自然唯一键 `(provider_code, market_code, provider_sector_id, taxonomy_version)`）+ `market_sector_identity_lock` 锚点。READ COMMITTED 下先 `INSERT IGNORE` 锚点再 `SELECT ... FOR UPDATE` 校验区间不重叠。现有 `market_sector_snapshot/member_snapshot` 回填 `sector_identity_id`。衍生表和 API 只使用数值 `sectorId`，`watch_id` 不进入幂等键或跨表连接。**身份稳定性分两层验证**：(a) 功能层 = soft-archive/删除+重建 watch 后衍生 `sectorId` 不变且快照保留（H2 可验证）；(b) 并发层 = 两个 claim 同一锚点只产生一行 identity（断言行数+唯一约束，H2/MODE=MySQL 无法复现 `FOR UPDATE` 锁顺序，真实争用属 RUNTIME NOT_VERIFIED）。
**强制证据**：`STATIC`（功能身份稳定性/并发 claim 单结果/跨 taxonomy 区间/无 watch_id join 单测）、`AUTOMATION`（`./mvnw test`）。

### AC-03：计算运行、血缘 manifest 与原子发布
**结果**：V19 新增 `sector_analytics_calculation_run` + `sector_analytics_publication_batch` + `sector_analytics_publication_member`。单公式 run 以 `(formula_code, formula_version, parameter_hash, source_manifest_hash)` 唯一；结果以 `(calculation_run_id, 业务维度)` 唯一。`required_formula_set_hash` 与 `source_manifest_group_hash` 可复算。DB claim 按 `(provider, market, as_of_date, formula_version, parameter_hash)`。重复运行幂等，参数变化不覆盖，并发 claim 单写者，错误 batch/run 组合受 FK/事务拒绝。
**强制证据**：`STATIC`（幂等/claim/跨市场 FK 拒绝/hash 复算单测）、`AUTOMATION`（`./mvnw test`）。

### AC-04：相对强弱固定窗口 cohort + 等权基准 + RS 百分位，单位端到端正确
**结果**：`sector_relative_strength_snapshot` 只消费通过完整性门禁的 CLOSE 榜单。先冻结窗口内每日稳定身份交集 cohort，共同基准、板块收益和最终排名只使用该 cohort。`sectorReturn(t)=change_rate(t)`（decimal ratio，不再 `/100`）。`rs_rank_percentile ∈ [0,1]`，并列按 `relative_return_n` 平均秩。**单位端到端**：测试必须反序列化真实 LongPort provider fixture（`chg="0.0240"` + `value_data="2.40%"`），断言 `sectorReturn=0.0240`（非 `/100`）且格式化输出 `"2.40%"`；不得硬编码字面值（§5.1 禁止）。任一日缺日或 cohort 低于阈值标 `INSUFFICIENT_SAMPLE`，不补值。
**强制证据**：`STATIC`（真实 fixture 单位端到端/cohort 交集/并列平均秩/缺日 INSUFFICIENT_SAMPLE 单测）、`AUTOMATION`（`./mvnw test`）。

### AC-05：板块级轮动持续性位次指标，区分连续强与一日强
**结果**：`sector_rotation_sector_persistence` 基于每日 `change_rate` 重排的平均秩和 `n_t` 百分位。`mean_rank_percentile`、`rank_percentile_std_dev`、`top_bucket_occupancy_rate`、`consecutive_leading_days`、`consecutive_lagging_days`、`rank_percentile_change` 符合 §5.2.2 公式。**GOLDEN-03 端到端**：测试必须构造冻结的 5 板块×5 交易日 `change_rate` fixture（升序平均秩 `[3,4,4,5,5]`，每日 `n_t=5`），从原始输入断言全部 6 个输出值（mean=0.8、std_dev≈0.18708286933869706、top_bucket=0.4、consecutive_leading=2、consecutive_lagging=0、percentile_change=0.5，容差 ≤1e-9）。硬编码 6 个数字而不连接真实输入是禁止的捷径。缺日中断连续性，taxonomy 变化断档 `ORIGIN_CHANGED`。
**强制证据**：`STATIC`（GOLDEN-03 端到端 fixture/缺日中断/taxonomy 断档单测）、`AUTOMATION`（`./mvnw test`）。
**test-designer amendment A5 已折叠**。

### AC-06：市场雷达只读 read model 一致批次与可解释证据
**结果**：`GET /api/v1/market-research/radar?market=CN&window=20` 返回统一 `asOfDate`、scope（`RANKED_UNIVERSE` 中文说明）、样本量、数据时间、`calculationRunId`/`publicationBatchId`、`formulaCode`/`formulaVersion`/`parameterHash` 和质量状态。四象限轮动状态（`LEADING/IMPROVING/WEAKENING/LAGGING/INSUFFICIENT_DATA`）阈值冻结在 v1 `parameter_hash` 中并在 `MARKET_RESEARCH_API.md` 记录默认值（横轴 20 日 RS 百分位、纵轴 5 日 RS 百分位变化）。每个板块返回稳定 `sectorId`、当前/历史位次、RS 百分位、持续性指标、至少两项可解释证据和 `reasonCodes`。资金/量价列在未计算时返回非空 `flowMetricNature` + null 值（返回 0 是禁止的伪造捷径），并标注 `NO_DERIVED_DATA`/`UNAVAILABLE`。雷达、轮动、排行使用同一发布批次，跨批次 join 被拒绝。
**强制证据**：`STATIC`（MockMvc：四象限分类/降级/资金 UNAVAILABLE 非空 nature+null 值/跨批次拒绝/排序）、`AUTOMATION`（`./mvnw test`）。
**test-designer amendments A2/A3 已折叠**。

### AC-07：板块排行历史与板块详情只读，区分持续强势与单日脉冲
**结果**：`GET /api/v1/market-research/sectors/ranking-history?market=CN&days=20` 返回板块历史位次序列、RS 序列和批次质量，能区分持续强势与单日脉冲。`GET /api/v1/market-research/sectors/{sectorId}?market=CN` 返回板块稳定身份、分类版本、tracking symbol（仅详情对照，不参与公共 RS）、RS 序列、排名轨迹、持续性指标、样本数、覆盖率、数据水位和质量状态。允许范围内的成分/领先证券信息（来自原始快照，不实现完整候选扫描）。无数据返回 `NO_DERIVED_DATA`，不沿用旧值。
**强制证据**：`STATIC`（MockMvc：历史/详情/无数据/tracking symbol 不污染公共基准）、`AUTOMATION`（`./mvnw test`）。

### AC-08：原始事实零污染 + 无 provider 反向调用 + 无禁止产物
**结果**：架构守卫测试断言 3 项冻结规则：(a) `analysis/` 包内无文件对 8 张原始事实表（`stock_daily_bar`/`stock_minute_bar`/`stock_quote_snapshot`/`market_sector_snapshot`/`market_sector_member_snapshot`/`market_sector_ranking_batch`/`market_sector_ranking_item`/`market_sector_watch`）执行 UPDATE/INSERT/DELETE/MERGE；(b) `analysis/` 包内无类 autowire provider client；(c) `watch_id` 不出现在任何衍生幂等键或 JOIN。不出现候选扫描、策略信号、自动交易、收益贡献 MVP 代码。`market_calendar` 新增列默认 `INFERRED` 不阻断现有 scheduler（回归断言）。
**强制证据**：`STATIC`（架构门禁 `check-ai-architecture.mjs` + 冻结 pattern 守卫测试）、`AUTOMATION`（`./mvnw test` + `./mvnw -DskipTests=false package`）。
**test-designer amendment A4 已折叠**。

## 3. 实现切片（5 slice）

每 slice ≤3 ACs、≤8 预期文件、≤500 生产行。

### SLICE-01（AC-01, AC-02）：V19 数据就绪门禁 + 稳定身份
**描述**：新增 V19 migration（`market_sector_identity`、`market_sector_identity_lock`、`market_calendar` source/verification 列、快照回填 `sector_identity_id`），readiness 门禁服务/VO/控制器，稳定身份 manager 与锁，change_rate 单位契约断言。
**预期文件（≤8）**：
1. `src/main/resources/db/migration/V19__add_sector_analytics_identity_and_readiness.sql`
2. `src/main/java/com/quant/trade/marketdata/analysis/model/MarketSectorIdentityDO.java`
3. `src/main/java/com/quant/trade/marketdata/analysis/dao/MarketSectorIdentityMapper.java` + `src/main/resources/mapper/MarketSectorIdentityMapper.xml`
4. `src/main/java/com/quant/trade/marketdata/analysis/manager/SectorIdentityManager.java`
5. `src/main/java/com/quant/trade/marketdata/analysis/readiness/SectorAnalyticsReadinessManager.java` + VO
6. `src/main/java/com/quant/trade/marketdata/analysis/controller/SectorAnalyticsReadinessController.java`
7. `src/main/java/com/quant/trade/marketdata/analysis/enums/SectorAnalyticsQualityStatusEnum.java`
8. `src/test/java/.../SectorIdentityManagerTest.java` + `SectorAnalyticsReadinessManagerTest.java`
**生产行增量目标**：≤500。

### SLICE-02（AC-03）：V19 计算运行与血缘 + 原子发布
**描述**：V19 新增 calculation run / publication batch / member 表，DB claim，hash 复算，原子发布事务，幂等/并发/跨市场 FK 拒绝。
**预期文件（≤8）**：
1. `src/main/resources/db/migration/V20__add_sector_analytics_calculation_run.sql`
2. `src/main/java/com/quant/trade/marketdata/analysis/model/`（CalculationRunDO, PublicationBatchDO, PublicationMemberDO）
3. `src/main/java/com/quant/trade/marketdata/analysis/dao/`（3 Mapper.java）+ `src/main/resources/mapper/`（3 XML）
4. `src/main/java/com/quant/trade/marketdata/analysis/run/CalculationRunManager.java`
5. `src/main/java/com/quant/trade/marketdata/analysis/run/PublicationBatchManager.java`
6. `src/main/java/com/quant/trade/marketdata/analysis/run/SourceManifestHasher.java`
7. `src/main/java/com/quant/trade/marketdata/analysis/constant/SectorAnalyticsConstants.java`
8. `src/test/java/.../CalculationRunManagerTest.java` + `PublicationBatchManagerTest.java`
**生产行增量目标**：≤500。

### SLICE-03（AC-04, AC-05）：衍生指标 RS + 板块级轮动持续性
**描述**：V20 新增 `sector_relative_strength_snapshot`、`sector_rotation_sector_persistence` 表与 Mapper；RS 固定 cohort + 等权基准 + RS 百分位；板块级位次序列指标。共享 model owner 在 `analysis/model/`。
**预期文件（≤8）**：
1. `src/main/resources/db/migration/V21__add_sector_relative_strength_and_rotation.sql`
2. `src/main/java/com/quant/trade/marketdata/analysis/model/`（SectorRelativeStrengthSnapshotDO, SectorRotationSectorPersistenceDO）
3. `src/main/java/com/quant/trade/marketdata/analysis/dao/`（2 Mapper.java）+ `src/main/resources/mapper/`（2 XML）
4. `src/main/java/com/quant/trade/marketdata/analysis/derived/RelativeStrengthCalculator.java`
5. `src/main/java/com/quant/trade/marketdata/analysis/derived/SectorRotationPersistenceCalculator.java`
6. `src/main/java/com/quant/trade/marketdata/analysis/derived/CohortResolver.java`（窗口身份交集）
7. `src/main/java/com/quant/trade/marketdata/analysis/model/RankAverageRanker.java`（change_rate 重排 + 并列平均秩）
8. `src/test/java/.../RelativeStrengthCalculatorTest.java` + `SectorRotationPersistenceCalculatorTest.java`
**生产行增量目标**：≤500。

### SLICE-04（AC-06）：市场雷达只读 read model + 控制器
**描述**：雷达查询 service/manager/VO/控制器，统一批次、四象限轮动状态分类、可解释证据、降级、资金 UNAVAILABLE、跨批次拒绝、排序。
**预期文件（≤8）**：
1. `src/main/java/com/quant/trade/marketdata/analysis/vo/MarketRadarVO.java`（含子 VO）
2. `src/main/java/com/quant/trade/marketdata/analysis/manager/MarketRadarReadModelManager.java`
3. `src/main/java/com/quant/trade/marketdata/analysis/manager/RotationStateClassifier.java`（四象限）
4. `src/main/java/com/quant/trading/analysis/service/MarketRadarQueryService.java`（占位路径修正为 marketdata）
5. `src/main/java/com/quant/trade/marketdata/analysis/controller/MarketRadarController.java`
6. `src/main/java/com/quant/trade/marketdata/analysis/enums/RotationStateEnum.java`
7. `src/main/java/com/quant/trade/marketdata/analysis/enums/FlowMetricNatureEnum.java`（含 UNAVAILABLE）
8. `src/test/java/.../MarketRadarControllerTest.java`（MockMvc）
**生产行增量目标**：≤500。

### SLICE-05（AC-07, AC-08）：板块排行历史 + 板块详情只读 + 架构门禁
**描述**：排行历史和板块详情只读 API，tracking symbol 详情对照不污染公共基准，原始事实零污染/provider 不反向调用/calendar 默认不阻断断言。
**预期文件（≤8）**：
1. `src/main/java/com/quant/trade/marketdata/analysis/vo/SectorRankingHistoryVO.java` + `SectorDetailVO.java`（含子 VO）
2. `src/main/java/com/quant/trade/marketdata/analysis/manager/SectorRankingHistoryManager.java`
3. `src/main/java/com/quant/trade/marketdata/analysis/manager/SectorDetailReadModelManager.java`
4. `src/main/java/com/quant/trade/marketdata/analysis/service/SectorResearchQueryService.java`
5. `src/main/java/com/quant/trade/marketdata/analysis/controller/SectorResearchController.java`
6. `src/main/java/com/quant/trade/marketdata/analysis/manager/TrackingSymbolBenchmarkGuard.java`（详情对照隔离）
7. `src/test/java/.../SectorRankingHistoryControllerTest.java` + `SectorDetailControllerTest.java`（MockMvc）
8. `src/test/java/.../SectorAnalyticsArchitectureGuardTest.java`（零污染/provider 不反向调用/calendar 默认不阻断断言）
**生产行增量目标**：≤500。

## 4. 冻结测试清单（testInventory）

| testId | acIds | kind | required | sourcePath | selector |
| --- | --- | --- | --- | --- | --- |
| TEST-01 | AC-01 | STATIC | true | `src/test/java/.../analysis/readiness/SectorAnalyticsReadinessManagerTest.java` | `class SectorAnalyticsReadinessManagerTest` |
| TEST-02 | AC-01 | STATIC | true | `src/test/java/.../analysis/controller/SectorAnalyticsReadinessControllerTest.java` | `readiness*` |
| TEST-03 | AC-02 | STATIC | true | `src/test/java/.../analysis/SectorIdentityManagerTest.java` | `class SectorIdentityManagerTest` |
| TEST-04 | AC-03 | STATIC | true | `src/test/java/.../analysis/run/CalculationRunManagerTest.java` | `class CalculationRunManagerTest` |
| TEST-05 | AC-03 | STATIC | true | `src/test/java/.../analysis/run/PublicationBatchManagerTest.java` | `class PublicationBatchManagerTest` |
| TEST-06 | AC-04 | STATIC | true | `src/test/java/.../analysis/derived/RelativeStrengthCalculatorTest.java` | `class RelativeStrengthCalculatorTest` |
| TEST-07 | AC-05 | STATIC | true | `src/test/java/.../analysis/derived/SectorRotationPersistenceCalculatorTest.java` | `class SectorRotationPersistenceCalculatorTest` |
| TEST-08 | AC-06 | STATIC | true | `src/test/java/.../analysis/controller/MarketRadarControllerTest.java` | `class MarketRadarControllerTest` |
| TEST-09 | AC-07 | STATIC | true | `src/test/java/.../analysis/controller/SectorRankingHistoryControllerTest.java` | `class SectorRankingHistoryControllerTest` |
| TEST-10 | AC-07 | STATIC | true | `src/test/java/.../analysis/controller/SectorDetailControllerTest.java` | `class SectorDetailControllerTest` |
| TEST-11 | AC-08 | STATIC | true | `src/test/java/.../analysis/SectorAnalyticsArchitectureGuardTest.java` | `class SectorAnalyticsArchitectureGuardTest` |
| TEST-12 | AC-01..08 | AUTOMATION | true | `pom.xml` | `./mvnw -q -Dtest=SectorAnalytics* test` |
| TEST-13 | AC-08 | AUTOMATION | true | `pom.xml` | `./mvnw -q -DskipTests=false package` |

> 注：selector 在 test-designer 挑战后可精确化。最终核验通过 `scripts/run-ai-evidence-command.mjs` 执行。

## 5. Blocking Amendments（≤3）

### BLOCKING_AMENDMENT_01：watch 级联删除不在本轮迁移
**原因**：V14 `market_sector_snapshot.fk_sector_snapshot_watch ON DELETE CASCADE` 与设计 §6.1 冲突，但删除既有 FK 会破坏现有 watch 生命周期测试且超出单 L2 slice 边界。
**处理**：本轮 V19 只新增 `sector_identity_id` 列并回填，不删除既有 FK；衍生层只使用 `sector_identity_id` 身份。watch 级联删除的完整迁移作为独立后续任务。
**影响**：AC-02 验收时"删除/重建 watch 不改变 sectorId"通过衍生层身份独立性证明，不依赖 FK 删除。

### BLOCKING_AMENDMENT_02：资金趋势/集中度/量价/提醒不在本轮计算
**原因**：这些指标公式在设计 §5.3/§5.4/§5.5 已冻结，但市场雷达首屏的必需列是 RS + 轮动状态 + 持续性；次要列（资金、量价）在本轮返回 `NO_DERIVED_DATA`/`UNAVAILABLE` 降级占位，不计算。
**处理**：FlowMetricNatureEnum 包含 `UNAVAILABLE`；雷达 VO 资金/量价字段可空并标注降级。完整计算留作后续 P1.10-A 续作任务。
**影响**：AC-06 资金列返回明确降级而非伪造。

### BLOCKING_AMENDMENT_03：HK/US 交易日历权威性不在本轮补齐
**原因**：§9 要求 HK/US 交易日历 `EXCHANGE_FILE/MANUAL_VERIFIED`，但当前只有周末推断。补齐真实日历属于部署数据治理，非代码任务。
**处理**：V19 `market_calendar` 新增列默认 `INFERRED`，readiness 长窗口门禁（RS window=20）对 HK/US fail closed 为 `INSUFFICIENT_RAW`；CN 既有日历行默认 `INFERRED` 但可被 readiness 接受（既有逻辑不阻断）。真实日历补齐后自动放行。
**影响**：HK/US RS 计算在日历补齐前返回明确降级，不伪造。

## 6. Lane 与角色门禁

- Lane：L2（migration、事务、并发、identity-lock、计算血缘）。
- 必需角色：TEST_DESIGNER → IMPLEMENTER(×5 slice) → CODE_REVIEWER → FINAL_VERIFIER。
- 必需门禁：static + focused test + `./mvnw test` + `./mvnw package` + 架构门禁 + 独立最终核验（disposable worktree）。
- Docker/MySQL RUNTIME/DEPLOYMENT：`NOT_VERIFIED`（任务明确排除）。
- 角色 budget：14 role-run 上限（5 实现 + 2 测试设计/审查 repair + 1 审查 + 1 核验 + 余量）。

## 7. Git 与候选策略

- `git_automation = COMMIT`：父协调者创建 contract、candidate、repair-N、finalization 阶段提交到任务分支。不 push、不 merge main。
- 候选模式：`COMMIT`。每次 SELF_CHECKED 后创建 candidate commit，记录 commit/tree/patch hash，用 `scripts/create-candidate-diff.mjs` 生成 `.qta-governance/candidates/<TASK-ID>/generation-N.patch`。
- 子角色不得操作 Git。

## 8. 停止条件

- 单位、完整性、CLOSE、身份或交易日历任一未冻结。
- 独立 verifier REJECT 且同一 fingerprint 两轮 repair 失败 → BLOCKED。
- 候选越过非目标（候选扫描、策略信号、自动交易、收益贡献 MVP）。
- L2 AC/role-run/blocking-amendment 上限突破且无法在冻结契约内 reslice。

## 9. 交付物

- 代码：V19/V20/V21 migration、MyBatis XML、DTO/VO、Service/Manager、Controller、enums、constants、MapStruct、H2 测试。
- 文档：`docs/api/MARKET_RESEARCH_API.md`（新增）、`docs/api/API_INDEX.md`（更新）、`docs/AI_HANDOFF.md`、`docs/development/DEVELOPMENT_LOG.md`、`docs/acceptance/ACCEPTANCE_LOG.md`、`docs/BUILD_CHECKLIST.md`、`docs/CURRENT_ARCHITECTURE_AND_MODULES.md`（§3/§4 新增 P1.10-A 分析模块与 V19-V21）。
- 机器证据：role artifact、架构门禁报告、最终核验报告、test receipt。
