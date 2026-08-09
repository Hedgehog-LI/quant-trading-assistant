# P17 板块分析层实现计划与并行开发顺序

> 关联设计：`docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md`（可开发设计，规划/未实现）。
> 关联契约：`docs/development/tasks/P17-SECTOR-ANALYTICS-DESIGN-20260802-CONTRACT.md`。
> 范围：本计划只描述后续实现的子任务拆分、写路径、依赖、AC、测试与合并顺序；本设计任务不实现任何子任务代码。

## 概述

板块分析层（相对强弱 / 轮动持续性 / 收益贡献与交易集中度 / 量价确认 / 异动提醒）拆为 4 个子任务，遵循独占写路径两两无前缀重叠原则。所有子任务只读原始事实表，禁止写回 `market_sector_*`/`market_sector_ranking_*`/`stock_*` 原始事实表；衍生结果写入 V19+ 新表（统一含 `formula_code`/`formula_version`/`parameter_hash`/血缘/质量列，幂等键含 `formula_version`），异动提醒复用 `market_data_alert`。

### 前缀重叠消除要点（核心修复）

- **ST-1** 独占 `marketdata/analysis/derived/**`（衍生计算）与**共享枚举/模型的唯一所有权** `marketdata/analysis/model/**`；ST-1 不进入 `analysis/alert/**`。
- **ST-3** 独占 `marketdata/analysis/alert/**`（异动评估）与 `marketdata/scheduler/**`。
- `analysis/derived/**`、`analysis/model/**` 与 `analysis/alert/**` 是 **package 下的兄弟目录**：`analysis/derived`、`analysis/model`、`analysis/alert` 三者两两互不为前缀（`analysis/derived` 不是 `analysis/alert` 的前缀，反之亦然；`analysis/model` 同理）。
- ST-1 是 `analysis/model/**`（共享枚举/模型，如 `SectorFormulaVersion`、`RelativeReturnResult`）的**唯一**所有者；ST-2/ST-3 只**消费**（只读引用）ST-1 的模型，不向 `analysis/model/**` 写文件，因此与 ST-1 在该路径上无写重叠。

### 子任务 1（ST-1）：数据模型 + 衍生计算服务 + 共享模型 + DTO/Mapper

写路径：

- `src/main/resources/db/migration/V19__sector_analytics_derived_tables.sql`（及后续 V20+ 如需拆分）
- `src/main/resources/mapper/SectorRelativeStrengthSnapshotMapper.xml`
- `src/main/resources/mapper/SectorRotationMarketStabilityMapper.xml`
- `src/main/resources/mapper/SectorRotationSectorPersistenceMapper.xml`
- `src/main/resources/mapper/SectorMemberReturnContributionMapper.xml`
- `src/main/resources/mapper/SectorTurnoverConcentrationMapper.xml`
- `src/main/resources/mapper/SectorVolumeConfirmationSnapshotMapper.xml`
- `src/main/java/com/quant/trade/marketdata/analysis/derived/`（六大衍生指标计算服务、读服务、Mapper 接口、derived 内部 dto；不进入 `analysis/alert/`、`analysis/model/` 之外的兄弟目录、`scheduler/`）
- `src/main/java/com/quant/trade/marketdata/analysis/model/`（**共享枚举/模型的唯一所有者**：`SectorFormulaCode`、`SectorFormulaVersion`、`LineageColumns`、各衍生结果模型等；ST-2/ST-3 只读消费，不向此目录写文件）

依赖：无前置子任务；依赖已落库的 P1.5/P1.6 原始事实表（`market_sector_snapshot`、`market_sector_member_snapshot`、`market_sector_ranking_batch`、`market_sector_ranking_item`、`stock_daily_bar`）。

AC：实现六大衍生指标的 V19+ 表 Flyway 迁移成功（相对强弱、市场级稳定性、板块级持续性、收益贡献、交易集中度、量价确认六状态）；衍生计算服务按设计闭式公式产出幂等快照；每张表含统一版本血缘列且幂等键含 `formula_version`；只读原始事实表，禁止写回原始事实表；样本不足/停牌/口径变更/零分母按 `quality_status` 降级。

测试：`./mvnw test`（含新增 Mapper 与衍生计算单元测试，验证幂等键含版本、`INSUFFICIENT_SAMPLE` 降级、零分母不除零、跨市场 ZoneId 对齐、版本升级旧行不覆盖）。

合并顺序：第 1 批（串行前置，必须先合并，为 ST-2/ST-3 提供表、服务与共享模型）。

### 子任务 2（ST-2）：板块分析 REST API + VO

写路径：

- `src/main/java/com/quant/trade/marketdata/controller/SectorAnalyticsController.java`（analytics 控制器）
- `src/main/java/com/quant/trade/marketdata/vo/SectorRelativeStrengthVO.java`
- `src/main/java/com/quant/trade/marketdata/vo/SectorRotationMarketStabilityVO.java`
- `src/main/java/com/quant/trade/marketdata/vo/SectorRotationSectorPersistenceVO.java`
- `src/main/java/com/quant/trade/marketdata/vo/SectorMemberReturnContributionVO.java`
- `src/main/java/com/quant/trade/marketdata/vo/SectorTurnoverConcentrationVO.java`
- `src/main/java/com/quant/trade/marketdata/vo/SectorVolumeConfirmationVO.java`
- `docs/api/MARKET_DATA_API.md`（§5 由“规划”转“已实现”的接口事实更新）

依赖：ST-1（衍生计算服务、DTO/Mapper 与 `analysis/model/**` 共享模型必须先就位）。ST-2 只读消费 ST-1 的 `analysis/model/**`，不向其写文件。

AC：实现 `/api/v1/market-data/sector-analytics/*` 端点（相对强弱、市场级稳定性、板块级持续性、收益贡献、交易集中度、量价确认六状态、异动复用 `/alerts`）；统一响应 `ApiResponse<T>` 并携带 `formulaCode`/`formulaVersion`/`qualityStatus`/`qualityReason`；分析 API 不返回 provider 鉴权失败码，错误码按 `VALIDATION_ERROR`/规划 `MARKET_SECTOR_ANALYTICS_FORMULA_VERSION_NOT_FOUND`/`MARKET_SECTOR_ANALYTICS_DATA_UNAVAILABLE` + 200/`quality_status` 区分；样本不足返回 200 + 字段降级标注。

测试：`./mvnw test`（含 Controller MockMvc 用例，覆盖六状态、拆分贡献/集中度、版本化响应）。

合并顺序：第 2 批（ST-1 之后，与 ST-3 并行）。

### 子任务 3（ST-3）：异动提醒评估器 + market_data_alert 写入 + Scheduler

写路径：

- `src/main/java/com/quant/trade/marketdata/analysis/alert/SectorAnomalyAlertEvaluator.java`
- `src/main/java/com/quant/trade/marketdata/analysis/alert/SectorAlertType.java`
- `src/main/java/com/quant/trade/marketdata/scheduler/SectorAnalyticsScheduler.java`
- `docs/features/MARKET_ALERT_RULES_DESIGN.md`（追加 `SECTOR_*` 提醒类型说明）

依赖：ST-1（衍生指标快照表、读服务与 `analysis/model/**` 共享模型）；不依赖 ST-2（评估器读取衍生表，不依赖 REST）。ST-3 只读消费 ST-1 的 `analysis/model/**`，不向其写文件。

AC：异动评估器按阈值 + Z-score 计算异常并写入 `market_data_alert`（`alert_type=SECTOR_*`、`severity=INFO/WARN/HIGH`、`trigger_value_json` 含 `formula_code`/`formula_version`）；Scheduler 在各市场收盘后（按 ZoneId）触发衍生重算与提醒评估；样本不足时不产 HIGH 提醒。

测试：`./mvnw test`（含评估器单元测试，验证 Z-score、阈值、severity 分级、`trigger_value_json` 内容、样本不足降级）。

合并顺序：第 2 批（ST-1 之后，与 ST-2 并行）。

### 子任务 4（ST-4）：前端页面 + 图表 + mock 契约

写路径：

- `docs/mock/MOCK_REMOTE_CONTRACT.md`（新增 `sector-analytics/*` mock 契约建议）
- 前端实际代码写在独立前端仓库（不在本仓库；本仓库仅更新 mock 契约建议）

依赖：ST-2（REST API 契约冻结后才能定 mock）；不依赖 ST-3（页面读 API，不直接读提醒写入逻辑，提醒经 `/alerts` 查询）。

AC：前端独立仓库实现“板块分析”页面与图表（相对强弱热力/排名、轮动位次带/市场级 Spearman 时序、收益贡献堆叠柱、交易集中度饼/柱、量价六状态散点、异动流）；mock 契约与后端 VO 对齐（含 `formulaCode`/`formulaVersion`）；每页标注“不构成投资建议”。

测试：前端独立仓库 `typecheck`/`lint`/`test`/`build`（不在本仓库执行）。

合并顺序：第 3 批（API 冻结后并行；前端独立仓库 PR 独立合并，本仓库仅合并 mock 契约更新）。

## 并行与串行合并顺序（DAG）

```
ST-1 (数据模型 + 衍生计算服务 + 共享 model/**)
 |
 +-- 串行前置 --> ST-2 (REST API + VO，消费 ST-1 model)   \
 |                                                          } 第2批，ST-2 与 ST-3 可并行
 +-- 串行前置 --> ST-3 (异动评估器 + alert + scheduler，消费 ST-1 model) /
                        |
                        +-- API 冻结后 --> ST-4 (前端页面 + mock 契约，并行)
```

阻塞条件：

- **串行**：ST-1 必须先于 ST-2、ST-3 合并（提供 V19+ 表、衍生服务与共享 `analysis/model/**`）；ST-2 必须先于 ST-4（提供冻结的 REST 契约）。
- **并行**：ST-2 与 ST-3 在 ST-1 之后可并行开发与合并（写路径两两无前缀重叠，且都不向 `analysis/model/**` 写文件）；ST-4 在 API 冻结后可并行（前端独立仓库）。
- 合并顺序总结：第 1 批 = ST-1（串行前置）；第 2 批 = ST-2 ∥ ST-3（并行）；第 3 批 = ST-4（前端并行，本仓库仅 mock 契约）。

写路径独占性核查（两两不前缀重叠）：

- ST-1 = `db/migration`、`mapper/*.xml`、`marketdata/analysis/derived/**`、`marketdata/analysis/model/**`。
- ST-2 = `marketdata/controller/**`、`marketdata/vo/**`、`docs/api/MARKET_DATA_API.md`。
- ST-3 = `marketdata/analysis/alert/**`、`marketdata/scheduler/**`、`docs/features/MARKET_ALERT_RULES_DESIGN.md`。
- ST-4 = `docs/mock/MOCK_REMOTE_CONTRACT.md` 与前端独立仓库。

核查结论：`analysis/derived`、`analysis/model`、`analysis/alert` 是 `marketdata/analysis` 下的兄弟目录，两两互不为前缀；ST-1 唯一拥有 `analysis/model/**`，ST-2/ST-3 只读消费不写入；ST-1 的 `analysis/derived/**` 与 ST-3 的 `analysis/alert/**` 互不为前缀（兄弟目录）。各子任务写路径前缀两两不重叠。
