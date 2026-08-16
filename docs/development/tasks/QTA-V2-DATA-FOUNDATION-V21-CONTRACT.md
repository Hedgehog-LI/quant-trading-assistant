# QTA-V2-DATA-FOUNDATION-V21 任务契约（A 股历史数据底座）

> contract_version: 1.0 · 冻结日期 2026-08-16 · Lane **L2**（DB+跨仓+外部 Provider，但按用户指令不跑多代编排：主会话直接实现，一次边界复核，最终由 Codex 独立验收）
>
> 基线：后端 `main@b2400e4`（含 MR-1A/MR-1B 收口）、前端 `main@40f0f68`（含 MR-1B + mock 修复）；双仓工作区起点干净。任务分支双仓同名 `codex/qta-v2-data-foundation-v21`（`git switch -c` 创建；`git checkout -b` 被治理 Hook 拦截）。

## 目标

建设可持续使用的 A 股历史数据底座：正式数据模型、历史日 K 回补闭环、无凭据 CSV 导入通道、数据质量与发布门禁、REST API、前端数据中心操作闭环。为 MR-2 提供真实、可追溯、可回补的数据基础。

## 一、现状盘点与实施决策记录（FACT → DECISION）

**FACT（已核实）**
- 日 K 唯一事实表 `stock_daily_bar`（uk: canonical_symbol+trade_date+adjust_type+data_source）已存在，CSV 日 K 导入与 TENCENT_PUBLIC 落库均写它。
- 证券主数据 `stock_basic`/`stock_alias`（V17）+ CSV 目录导入（D1）已存在；`market_calendar`（V10）存在但 CN 为空表。
- 采集引擎机制已存在：`market_data_sync_task/item`、plan run claim（短事务+token）、`SyncScopeLockMapper`（upsert+FOR UPDATE 行锁）、水位 `market_data_watermark`、`TaskReconcileService`。
- MR-0 PoC：`mr0_*` 三表 + `PublicMarketDataClient`（腾讯日 K/新浪证券池/行业/资金流，无凭据）；Provider 矩阵：TENCENT_PUBLIC/SINA_PUBLIC=VERIFIED（实验性公共源）、TUSHARE=NOT_VERIFIED（无凭据）、LONGBRIDGE=NOT_RETESTED（外部鉴权故障）。
- MR-1 输入边界（MR-1-BND-D）：数据集=全 A 证券池+日 K（2021-01-01 起）+PIT 行业成分+官方资金流；凭据就绪前对应维度阻断。

**DECISION（D）**
- D1 复用不复制：日 K 事实只写 `stock_daily_bar`；证券池登记复用 `stock_basic`；交易日历复用 `market_calendar`。禁止新表复制上述事实。
- D2 新增 `mdf_*`（market data foundation）表族承载新语义：数据集定义/版本、universe 快照、行业分类与 PIT 成分、覆盖水位、回补任务/分片、导入批次、质量结果、当前发布指针。回补任务不复用 `market_data_sync_task`（其 scope_json 泛型无法表达数据集/版本/发布/分片断点语义），但机制沿用其短事务+claim 模式，并发防重用 task 行 claim token + `SyncScopeLockMapper` 行锁。
- D3 Provider（正式 ADR-0015）：全 A 证券池=SINA_PUBLIC hs_a（实验性/降级来源）；历史日 K=TENCENT_PUBLIC（实验性，NONE 复权）；交易日历=导入（CSV）优先+INDEX 推导兜底；行业分类=SINA_INDUSTRY（非申万，禁止混称）；PIT 成分=CSV 导入（当前成分聚合历史=显式时点假设，quality 族标记）；行业资金事实=BLOCKED（TUSHARE 无凭据；SINA 个股资金流仅 PoC 事实表）。公共源标注 EXPERIMENTAL/DEGRADED，不宣称生产稳定。
- D4 复权：本轮全部 `NONE`（腾讯公共源口径+CSV 显式声明）；HFQ/QFQ 数据集定义允许但无 Provider 支撑时创建即拒绝。
- D5 单位冻结（与 MR-0 D6 一致）：价格=元、volume=股、amount=元、换手率=小数、市值=元（导入 schema 中原始万元单位须显式换算列或预换算，schema 冻结为元/股）。
- D6 发布语义：dataset_version.status ∈ DRAFT→BACKFILLING→QUALIFYING→QUALIFIED/REJECTED→RELEASED/RETIRED；每 dataset 至多一个 RELEASED（`mdf_dataset.current_version_id` 指针，事务内切换）；质量 FAIL 或空数据不得进入 QUALIFIED/RELEASED；失败版本保留可查，不成为研究默认版本。
- D7 `mr0_*` PoC 表不被新页面/API 直接依赖；`PublicMarketDataClient` 代码可复用（只读工具类）。

**ASSUMPTION**：公共源字段结构与 2026-08-15 探针一致；单机部署（无多实例并发回补需求，但 claim+锁仍落地）。
**OPEN_QUESTION**（不阻塞，留待验收）：Tushare 凭据后的正式资金流表设计；全市场回补的实际成本。

## 二、范围与非目标

**范围**：后端 `com.quant.trade.marketdata.foundation` 包（controller/service/manager/dao+XML/dto/vo/enums/constant/provider）+ V24 migration + ADR-0015；前端 feature `data-foundation` + `/data-foundation` 页面 + 菜单；文档同步。
**非目标**：MR-2/MR-3/MR-4 研究页面；资金流正式表；自动交易/券商接口；重写现有采集引擎；2021 至今全量真实回补（只证明执行能力）；修改历史 migration。

## 三、验收标准（AC，外部可观察）

- **AC-01 数据模型**：空库 Flyway 迁移到 V24 成功；`mdf_*` 8+ 表唯一键/索引完整；MyBatis XML 实际读写（H2）通过；`stock_daily_bar`/`stock_basic`/`market_calendar` 无复制表。证据：迁移测试+Mapper 测试。
- **AC-02 回补闭环**：创建回补任务（dataset/market/provider/frequency/adjust/start/end）→ 自动拆分有限 chunk → 执行含断点续跑（中断后继续未完成 chunk）→ 失败 chunk 可重试 → 幂等重跑（重复执行不产生重复行，ODKU/唯一键）→ 并发防重（同任务重复启动被 claim 拒绝）→ 计划/成功/失败/跳过/写入计数正确。证据：服务集成测试。
- **AC-03 Provider 节流与不可重试**：限流节流（最小间隔）+失败指数退避；401/403 类立即失败不重试。证据：打桩 provider 单测。
- **AC-04 CSV 导入**：证券池/交易日历/日 K/行业分类/PIT 成分五类 schema 明确；导入前校验；结果 inserted/updated/skipped/rejected；相同内容重复导入幂等；错误行可查看；数据带 import source 标记（`data_source=IMPORT_CSV_*`）。证据：导入服务测试+重复导入断言。
- **AC-05 质量门禁与发布**：13 类质量检查（日期覆盖/池覆盖/日 K 缺口/重复/OHLC/单位/非交易日/成分重叠/无效有效期/未映射/口径混用/陈旧/空数据）产出结构化结果；空数据或存在 FAIL 时不得发布 QUALIFIED→RELEASED；发布切换原子（旧版本 RETIRED、指针切换、失败版本不成为默认）。证据：质量/发布服务测试。
- **AC-06 REST API**：回补任务创建/列表/详情/分片/启动继续/重试分片、覆盖、质量、数据集与发布版本、导入提交与结果查询全部按 `/api/v1/market-data/data-foundation/*` 实现；DTO/Validation/ErrorCodeEnum/统一异常响应；参数与错误码测试。证据：Controller 测试（MockMvc 风格与现有一致）。
- **AC-07 前端数据中心**：`/data-foundation` 页面含回补任务表单（dataset/market/dates/provider/frequency/adjust+校验）、任务列表/状态、详情+分片进度+失败原因+重试、覆盖水位、质量结果、当前发布版本、CSV 导入入口与结果；loading/empty/error/partial 状态齐全；mock 模式不伪造成功（明确不可用提示）；remote 失败不回退假数据；不做营销页。证据：组件测试+浏览器验收截图。
- **AC-08 运行时最小验证**：Docker Compose 后端 health UP；极小回补任务（1 证券×短窗口×日频）执行链路真实跑通（公共源可用时）或 CSV fixture 完整走导入→质量→发布→查询；二次相同导入幂等；curl 查询任务/分片/覆盖/质量/发布版本；前端 remote 模式数据中心页面可操作。外联失败区分代码/网络/权限/NOT_VERIFIED。证据：curl 记录+截图。

## 四、测试清单（冻结，测试 ID → AC）

后端（`src/test/java/com/quant/trade/marketdata/foundation/`）：
T01 FoundationMigrationTest（空库→V24，AC-01）；T02 FoundationMapperXmlTest（8 表 XML 读写，AC-01）；T03 BackfillChunkingTest（chunk 拆分边界：整除/余 1/超限/单日，AC-02）；T04 BackfillResumeTest（断点续跑+幂等重跑，AC-02）；T05 BackfillConcurrencyTest（claim 拒绝重复启动+范围锁，AC-02）；T06 BackfillRetryTest（失败重试+计数，AC-02）；T07 ProviderThrottleRetryTest（节流+指数退避+401/403 不重试，AC-03）；T08 CsvImportValidationTest（五类 schema 校验+错误行，AC-04）；T09 CsvImportIdempotentTest（重复导入幂等，AC-04）；T10 IndustryMembershipPitTest（有效期重叠/半开区间/当前成分 NULL to，AC-01/04）；T11 QualityCheckTest（13 检查族+空数据不通过，AC-05）；T12 PublicationGateTest（FAIL/空不发布+原子切换+失败版本非默认，AC-05）；T13 DataFoundationControllerTest（参数/错误码/路径，AC-06）；T14 FoundationArchitectureTest（Controller 无业务/无注解 SQL/不改历史 migration，AC-01/06）。

前端（`src/features/data-foundation/` + `src/pages/data-foundation.test.tsx`）：
F01 表单校验与创建（AC-07）；F02 任务列表与状态（AC-07）；F03 分片失败与重试（AC-07）；F04 覆盖与质量展示（AC-07）；F05 导入结果展示（AC-07）；F06 mock 模式不可用提示不伪造（AC-07）；F07 remote 失败不回退假数据（AC-07）。

## 五、验证维度与停止条件

- STATIC：包结构/分层审查（T14）；AUTOMATION：全部单测+`./mvnw test`+`package`、前端 typecheck/lint/test/build；RUNTIME：AC-08 Docker+curl+浏览器；DEPLOYMENT：NOT_DEPLOYED（不宣称）。
- 停止条件：Flyway 空库迁移失败且无法在新增 migration 内修复；现有 API/表兼容性被破坏；公共源结构变化导致回补 provider 无法实现（此时 CSV 通道仍交付并标记 RUNTIME BLOCKED 证据）。
- 修复轮上限：每 AC 最多 2 轮定向修复；超限输出 BLOCKED 报告。
- 最终裁决：Codex 独立验收（本会话不自判 ACCEPTED）；文档状态只写 IMPLEMENTED/AUTOMATION_VERIFIED/RUNTIME_VERIFIED/NOT_VERIFIED/BLOCKED。

## 六、切片与提交

S1 契约+ADR+V24 schema；S2 回补引擎（provider/节流/chunk/断点/claim）；S3 CSV 导入+质量+发布；S4 REST API；S5 前端数据中心；S6 全量门禁+运行时验证+文档收口。每片一个 commit（不 push/merge）。
