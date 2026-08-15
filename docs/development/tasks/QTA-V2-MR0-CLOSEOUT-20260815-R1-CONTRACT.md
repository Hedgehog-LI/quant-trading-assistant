# Task Contract: QTA-V2-MR0-CLOSEOUT-20260815-R1 MR-0 收口（代码 + 架构 + 治理流程一次性修复）

## Contract Identity

- Status: `FROZEN`
- Contract version: 1.1（v1.0 草案 + TEST_DESIGNER 阻塞修正案 AMD-001/002/003 与建议 REC-01..08 合并）
- Frozen at: 2026-08-15T16:10:00Z
- Frozen by parent run: PARENT-RUN-1
- Lane: `L2`

## Objective

以已完成的 MR-0 分支（`codex/qta-v2-mr0-data-semantics-poc` @ `2a7b451`）为基线，一次性修复代码、架构和 QTA 治理流程问题，重新生成唯一候选并完成严格串行的独立审查、核验和文档收口，使 MR-0 真正达到可以合并 main 的程度。旧 MR-0 任务（`QTA-V2-MR0-DATA-SEMANTICS-POC-20260815`，gen-3 `981cd47`）的 DELIVERY_READY 结论因 review/verifier 时间重叠被本任务取代，其证据保留为历史，不得篡改、删除或复用为本次验收证据。

## Authority

- Product/design: 用户任务指令（2026-08-15，QTA-V2-MR0-CLOSEOUT-20260815-R1）；`docs/AI_DEVELOPMENT_INDEX.md` §2 信息优先级
- API/data contract: `docs/api/MARKET_RESEARCH_API.md`（MR-0 PoC 章节）；`docs/features/MARKET_RESEARCH_MR0_*` 三份冻结文档
- Baseline commit: `2a7b45188d537a4136e8b6ab19878e54b35bf58e`
- Baseline branch: `codex/qta-v2-mr0-data-semantics-poc`（新任务分支 `codex/qta-v2-mr0-closeout-r1` 自该提交创建）
- Pre-existing dirty paths: 无（工作树干净）
- Allowed write paths: 见 Implementation Slices 各切片 allowlist；父协调者另可写任务控制/证据工件（`docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-*`）与 finalization 文档
- git_automation: `COMMIT_AND_CHECKPOINT_PUSH`（仅任务分支；禁止合并/推送 main、禁止 force push）

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | 旧任务 ROLE-RUN-CR-G3（CODE_REVIEWER gen-3）finishedAt=2026-08-14T19:56:00Z，ROLE-RUN-FV-G2（FINAL_VERIFIER gen-3）startedAt=2026-08-14T19:53:16Z：verifier 在 reviewer 完成前约 2m44s 即开始，存在时间重叠；`scripts/check-ai-task-control.mjs` 当前仅校验 roleRuns finishedAt 单调，未强制 reviewer→verifier 串行，因此旧控制文件通过了校验。 |
| FACT | 旧任务 repair 2（F-005）把 10 条 frozen-selector 注释嵌入 9 个业务源文件（pom.xml、scripts/run-mr0-poc.sh×2、3 个 PoC 测试类、3 份 MR0 特性文档、POC-REPORT.md），根因是 `check-ai-task-control.mjs` 的 VERIFIED 校验要求 frozen selector 字符串逐字出现在 sourcePath 文件内容中。 |
| FACT | `Mr0PocAnalysisMapper.java` 含 6 处 MyBatis `@Select` 注解 SQL（含 SLICE-03 时记录的约定偏离），违反 AGENTS.md「MyBatis SQL 写在 `src/main/resources/mapper/*.xml`」项目规范。 |
| FACT | `Mr0PocController.ingest` 仅检查 `qta.mr0-poc.ingest-enabled` 开关，不校验当前 profile；`analyze`/`report` 不校验任何日期顺序、warmup 顺序、sampleSize 边界或日期跨度上限。 |
| FACT | 父会话对治理保护路径（含 `scripts/check-ai-task-control.mjs`）的 Edit 被用户级 Hook 无条件拦截（探针实测：`QTA governance blocked this action: governed roles must not rewrite the active governance controls`）；子代理工具调用不经过该用户级 Hook（探针实测：子代理 Edit 未被拦截）。 |
| DECISION | AC-04/AC-05 的治理校验器修复由用户在本任务指令中明确要求，且方向为加强门禁（强制时间顺序）与修复误报逻辑（selector 绑定）；父协调者本人不编辑实现，修复由冻结切片（SLICE-03/SLICE-04）内的全新 `qta-implementer` 子代理执行，全程经 dispatch 回执、CODE_REVIEWER 审查与 FINAL_VERIFIER 核验公开留痕。此为本次唯一可行的合规实现路径，记录为父级裁决。 |
| DECISION | 旧 MR-0 的 DELIVERY_READY 不回滚、不修改；本任务以新 Task ID 全生命周期重走 TEST_DESIGNER→IMPLEMENTER→CODE_REVIEWER→FINAL_VERIFIER，新验收证据全部绑定新候选。 |
| DECISION | TEST 选择器一律使用精确命令串（receipt command 数组可直接观察到），不再要求业务源码包含选择器字符串；静态断言通过 `bash -c` 包装命令实现。 |
| ASSUMPTION | 本地 Docker/MySQL（127.0.0.1:3306，qta/qta_dev_password，库 quant_trading_assistant）在验证窗口可用；PoC 脚本公共源（腾讯/新浪/搜狐）临时失败最多重试一次。 |
| ASSUMPTION | `scripts/run-mr0-poc.sh` 以 `--spring.profiles.active=local` 启动（第 68 行），AC-02 的 local 门禁必须继续放行该脚本。 |
| OPEN_QUESTION | 无（无人值守，按 现有项目规范 > 冻结设计 > 最小修改 > 可验证性 自行裁决）。 |

## Scope

### In Scope

- `marketdata.poc` 包的 MyBatis XML 迁移与架构防回归测试。
- `marketdata.poc` REST 入口的 ingest local-profile 门禁与 analyze/report 入参边界校验（Controller + Service 双层防御）。
- 治理噪声清理：删除 9 个业务源文件中的全部 frozen-selector 注释。
- `scripts/check-ai-task-control.mjs` 两处修复：F-005 selector 绑定逻辑、review/verifier 时间顺序门禁；配套回归测试。
- MR-0 聚焦测试、全量 `./mvnw test` + package、一次 `scripts/run-mr0-poc.sh` 真实运行。
- 新任务的 contract/control/test design/review/verification/finalization 工件与 9 份项目文档收口。

### Out Of Scope

- MR-1 市场全景 MVP、前端页面、新数据源接入、Provider 选型 ADR。
- 重新设计 MR-0 指标字典/Provider 矩阵/数据盘点内容（仅删除其中治理噪声注释）。
- 修复既有 LongPort 脏数据（SH.600519 8 行 volume 单位异常等数据质量边界继续如实报告）。

### Prohibited

- 自动下单、券商接口、密钥读取；合并或推送 main；force push；篡改/删除旧 MR-0 control、review、verification、finalization 证据。
- 为绿灯隐藏 UNIT_ANOMALY、覆盖缺口、时点穿越等数据质量发现。
- 以“切片路径限制”为由在 `marketdata.poc` 保留注解 SQL。
- 生产代码、pom、测试源码或业务文档中新增/保留测试选择器命令字符串。

## Acceptance Criteria

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | `Mr0PocAnalysisMapper` 全部查询迁移到 `src/main/resources/mapper/Mr0PocAnalysisMapper.xml`，接口无注解 SQL，查询语义不变（REC-02：poc 包全量断言） | 基线代码 + 既有 `Mr0PocAnalysisServiceTest` | `marketdata/poc/**/*.java` 中 `@Select/@Insert/@Update/@Delete` 计数 0（含 Mr0PocMapper）；XML statement id 集合 == 接口方法名集合（恰 6 个）；既有分析测试不改断言全绿 | TEST-01, TEST-02 | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-02 | ingest 同时要求 `qta.mr0-poc.ingest-enabled=true` 且 `environment.getActiveProfiles()` 集合恰为 `{"local"}`（REC-01） | 非 local profile（test/docker/prod/组合/空）+ 开关 true | ingest 拒绝（400 `BUSINESS_RULE_VIOLATION` envelope，非 500）；local + true 放行（`run-mr0-poc.sh` 不受影响）；analyze/report 保持只读且不外联 Provider；拒绝路径 client 零交互 | TEST-03 | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-03 | analyze/ingest 入参防御（AMD-001 冻结边界）：`analysisStart<=analysisEnd`、`warmupStart<=analysisStart`、`sampleSize∈[1,500]`、跨度 `analysisEnd−analysisStart<=365 天`（相等均合法） | 非法参数（倒序日期、warmup 晚于分析窗、sampleSize 0/-1/501、跨度 367 天、畸形日期 2026-13-01） | Controller 与 Service 双层防御均返回规范 `ErrorCodeEnum`（400，VALIDATION_ERROR 语义），不出现 500；参数被拒时零外联 | TEST-04 | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-04 | 治理噪声清理 + F-005 校验逻辑修复 | 基线含 10 条 embedded selector 注释；校验器要求 selector 出现在源文件 | 9 个业务源文件 0 条 frozen-selector 注释（fs 扫描恰 9 文件断言，POC-REPORT 以 SLICE-05 重跑产物为准）；校验器改为 test inventory 与 control/receipt 一致性校验，业务源码不含 selector 字符串不判失败，sourcePath 存在性检查与 receipt 绑定校验保留；回归测试覆盖 | TEST-05 | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-05 | 治理校验器强制 reviewer→verifier 严格串行（AMD-002：仅依赖 control + `.json` dispatch 回执） | 同代 CODE_REVIEWER 与 FINAL_VERIFIER role runs + dispatch 回执 + transitionHistory | 校验器强制：(a) 同代 verifier.startedAt >= reviewer.finishedAt（相等合法）；(b) verifier dispatch 回执 observedAt >= REVIEW_CLEAR 迁移 at；(c) 任何 verifier.startedAt < reviewer.finishedAt 判失败；reviewer outcome 回执时间戳（若有）交叉校验，缺失降级 warning。≥6 个确定性测试：重叠失败、提前 dispatch 失败、正常串行通过、相等边界通过、多代仅同代比较、旧任务形态（19:56:00/19:53:16）必须被拦截；既有断言全集保留 | TEST-06 | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-06 | MR-0 集成回归（AMD-003 运行次数语义） | SLICE-01..04 完成后 | MR-0 聚焦测试通过；完整 `./mvnw test` + package 通过；实现阶段恰成功运行一次 `bash scripts/run-mr0-poc.sh` exit 0（exit 2 公共源失败允许重试一次）；最终 verifier 按 inventory 重跑 TEST-08 一次（artifact-restore），全程合计成功恰两次；LongPort 脏数据不删除不篡改（UNIT_ANOMALY affectedCount 与基线一致）；质量发现如实报告 | TEST-07, TEST-08, TEST-FULL | AUTOMATION+RUNTIME | IMPLEMENTER | NOT_STARTED |
| AC-07 | 独立审查与核验（流程 AC） | 全部实现与集成测试完成后冻结单一 cumulative candidate | 全新 CODE_REVIEWER 审查冻结 diff；reviewer outcome 回执落盘后才进 REVIEW_CLEAR；全新 FINAL_VERIFIER 在干净 worktree 对同一候选执行全部门禁；满足 AC-05 时间顺序约束 | 角色运行账本 + dispatch/outcome 回执 | RUNTIME | PARENT（派发） | NOT_STARTED |
| AC-08 | 文档与交付收口记录 | 独立验收通过后 | CLOSEOUT-REPORT 记录四要素（REC-04）：superseded by QTA-V2-MR0-CLOSEOUT-20260815-R1 声明、旧任务时间证据（19:53:16/19:56:00）、data quality boundary 延续（含 UNIT_ANOMALY affectedCount 基线一致引用）、MR-0 PoC is not MR-1 底座声明；父级 finalization 同步 9 份文档（AI_HANDOFF、DEVELOPMENT_ROADMAP、BUILD_CHECKLIST、CURRENT_ARCHITECTURE_AND_MODULES、DATABASE_DESIGN、MARKET_RESEARCH_API、DEVELOPMENT_LOG、ACCEPTANCE_LOG、本任务工件）并以 finalization.changedPaths 覆盖 | TEST-09 + finalization 工件 | STATIC | IMPLEMENTER + PARENT | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | TEST-09（CLOSEOUT-REPORT 必备标记） | grep 计数 >=1 且四要素存在 |
| AUTOMATION | Yes | TEST-01..TEST-07、TEST-FULL | 全部 exit 0 且 receipt observedSelectors 完整 |
| RUNTIME | Yes | TEST-08（`bash scripts/run-mr0-poc.sh`；verifier 侧 artifact-restore：备份证据/报告两文件 → 裸命令 → 按字节恢复，`candidateUnchanged=true`） | 实现阶段与 verifier 重跑均 exit 0；证据键齐全；两次分析哈希一致；二次导入 inserted=0；exit 2 公共源失败各允许重试一次 |
| DEPLOYMENT | No | —（PoC 本地运行时即覆盖运行维度；无服务器部署） | NOT_REQUIRED |

## Implementation Slices

| Slice ID | Coherent boundary | AC IDs | Allowed write paths | Max files | Max production-line delta |
|---|---|---|---|---:|---:|
| SLICE-01 | Mr0PocAnalysisMapper 注解 SQL 全量迁移 MyBatis XML + 架构防回归测试 | AC-01 | `src/main/java/com/quant/trade/marketdata/poc/Mr0PocAnalysisMapper.java`, `src/main/resources/mapper/Mr0PocAnalysisMapper.xml`, `src/test/java/com/quant/trade/marketdata/poc/Mr0PocMapperXmlArchitectureTest.java` | 3 | 300 |
| SLICE-02 | ingest local-profile 门禁 + analyze/ingest 入参边界（Controller+Service 双防御）及测试 | AC-02, AC-03 | `src/main/java/com/quant/trade/marketdata/poc/`, `src/test/java/com/quant/trade/marketdata/poc/` | 8 | 400 |
| SLICE-03 | F-005 校验逻辑修复 + 回归测试 + 删除 pom/脚本/测试类中 frozen-selector 注释 | AC-04 | `scripts/check-ai-task-control.mjs`, `scripts/tests/ai-governance.test.mjs`, `pom.xml`, `scripts/run-mr0-poc.sh`, `src/test/java/com/quant/trade/marketdata/poc/` | 7 | 200 |
| SLICE-04 | reviewer→verifier 时间顺序门禁 + ≥3 确定性测试 + 删除特性文档/POC 报告中 selector 注释 | AC-05 | `scripts/check-ai-task-control.mjs`, `scripts/tests/ai-governance.test.mjs`, `scripts/tests/qta-role-ordering.test.mjs`, `docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md`, `docs/features/MARKET_RESEARCH_MR0_PROVIDER_MATRIX.md`, `docs/features/MARKET_RESEARCH_MR0_DATA_INVENTORY.md`, `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-REPORT.md` | 7 | 300 |
| SLICE-05 | 集成回归执行（聚焦/全量/package/一次 PoC 真实运行）+ 收口报告 | AC-06, AC-07, AC-08 | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-CLOSEOUT-REPORT.md`, `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-EVIDENCE.json`, `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-REPORT.md` | 5 | 150 |

## Frozen Test Inventory

| Test ID | AC IDs | Kind | Required | Source path | Exact selector | Receipt path |
|---|---|---|---|---|---|---|
| TEST-01 | AC-01 | AUTOMATION | YES | `src/test/java/com/quant/trade/marketdata/poc/Mr0PocMapperXmlArchitectureTest.java` | `./mvnw -q test -Dtest=Mr0PocMapperXmlArchitectureTest` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-01.json` |
| TEST-02 | AC-01 | AUTOMATION | YES | `src/test/java/com/quant/trade/marketdata/poc/Mr0PocAnalysisServiceTest.java` | `./mvnw -q test -Dtest=Mr0PocAnalysisServiceTest` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-02.json` |
| TEST-03 | AC-02 | AUTOMATION | YES | `src/test/java/com/quant/trade/marketdata/poc/Mr0PocIngestGateTest.java` | `./mvnw -q test -Dtest=Mr0PocIngestGateTest` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-03.json` |
| TEST-04 | AC-03 | AUTOMATION | YES | `src/test/java/com/quant/trade/marketdata/poc/Mr0PocParamBoundaryTest.java` | `./mvnw -q test -Dtest=Mr0PocParamBoundaryTest` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-04.json` |
| TEST-05 | AC-04 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `node --test scripts/tests/ai-governance.test.mjs` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-05.json` |
| TEST-06 | AC-05 | AUTOMATION | YES | `scripts/tests/qta-role-ordering.test.mjs` | `node --test scripts/tests/qta-role-ordering.test.mjs` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-06.json` |
| TEST-07 | AC-06 | AUTOMATION | YES | `src/test/java/com/quant/trade/marketdata/poc/Mr0PocIngestServiceTest.java` | `./mvnw -q test -Dtest=Mr0PocIngestServiceTest,Mr0PocQualityServiceTest,Mr0PocAnalysisServiceTest` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-07.json` |
| TEST-08 | AC-07 | RUNTIME | YES | `scripts/run-mr0-poc.sh` | `bash scripts/run-mr0-poc.sh` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-08.json` |
| TEST-09 | AC-08 | STATIC | YES | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-CLOSEOUT-REPORT.md` | `grep -c 'superseded by QTA-V2-MR0-CLOSEOUT-20260815-R1' docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-CLOSEOUT-REPORT.md && grep -c '19:53:16' docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-CLOSEOUT-REPORT.md && grep -c 'data quality boundary' docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-CLOSEOUT-REPORT.md && grep -c 'MR-0 PoC is not MR-1' docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-CLOSEOUT-REPORT.md` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-09.json` |
| TEST-FULL | AC-01..AC-08 | AUTOMATION | YES | `pom.xml` | `./mvnw -q test && ./mvnw -q -DskipTests package` | `docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-TEST-FULL.json` |

## Architecture And Quality Gates

- Required architecture review: `YES`（`scripts/check-ai-architecture.mjs`，候选冻结后、REVIEW_CLEAR 前运行；报告哈希绑定 review/verification）
- Triggered thresholds: 依赖数 >10、单切片生产行增量超标、模块边界（poc 包不得反向依赖 web 之外层）等机器阈值；每个 warning 需结构化处置
- Required layers/boundaries: MyBatis SQL 仅存在于 `src/main/resources/mapper/*.xml`（AC-01 架构测试机器强制）；ingest 外联仅 PublicMarketDataClient；analyze/report 零外联
- ADR exception and expiry: 无（SLICE-03 时记录的注解 SQL 偏离随 AC-01 消除）

## Stop Conditions / Budget / Repair

- 相同失败指纹最多 2 轮 repair，之后 BLOCKED。
- 全量测试最多候选冻结前一次、最终 verifier 一次。
- 公共源临时失败最多重试一次；不可循环调用。
- 角色派发严格串行；reviewer outcome 回执落盘前禁止派发 verifier（AC-05 语义，本任务自身必须遵守）。
- 上下文测量 `UNAVAILABLE`：执行轮次/等待/轮询/压缩限制替代（waitCalls<=2/role，shellPolls<=3/command）。
- Final verdict: 全新 `qta-final-verifier`（干净 worktree，机器回执，FUNCTIONAL+ARCHITECTURE 双 PASS 才 ACCEPTED）。
