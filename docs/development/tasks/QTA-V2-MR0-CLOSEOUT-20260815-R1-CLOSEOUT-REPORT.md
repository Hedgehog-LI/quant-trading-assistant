# QTA-V2-MR0-CLOSEOUT-20260815-R1 收口报告（CLOSEOUT-REPORT）

- 任务：QTA-V2-MR0-CLOSEOUT-20260815-R1（L2，contract v1.1，SHA-256 `de5736b8a05558d3b2347bcf98faa6b932a924d7ad1e0263517f89f56c6b6ab7`）
- 基线：`2a7b451`（分支 `codex/qta-v2-mr0-closeout-r1`；contract 提交 `11fe460`）
- 本报告由 SLICE-05 实施者（ROLE-RUN-IMP-S5-G1，dispatch `QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-S5-G1-D1`）于三项门禁 + 一次真实 PoC 运行完成后撰写；实现者结论仅 `SELF_CHECKED`，独立审查与核验由父级另行派发。

## 1. 旧 MR-0 验收被取代声明（REC-04 要素一）

旧任务 `QTA-V2-MR0-DATA-SEMANTICS-POC-20260815`（gen-3 `981cd47`）的 DELIVERY_READY 验收结论 **superseded by QTA-V2-MR0-CLOSEOUT-20260815-R1**。

取代原因（review/verifier 时间重叠）：旧任务 ROLE-RUN-FV-G2（FINAL_VERIFIER gen-3）startedAt=2026-08-14T**19:53:16**Z 早于 ROLE-RUN-CR-G3（CODE_REVIEWER gen-3）finishedAt=2026-08-14T**19:56:00**Z，即 verifier 在 reviewer 完成前约 2m44s 即开始，违反 reviewer→verifier 严格串行要求；当时 `check-ai-task-control.mjs` 仅校验 roleRuns finishedAt 单调，未强制该顺序，因此旧控制文件通过了校验。旧任务证据保留为历史，未篡改、未删除、未复用为本任务验收证据。本任务已通过 SLICE-04（AC-05）为校验器补上时间顺序门禁（含旧任务形态 19:56:00/19:53:16 的拦截回归测试 TD-05-07）。

## 2. SLICE-05 三项门禁精确结果（AC-06）

| 门禁 | 命令 | exit | 关键计数 |
|---|---|---|---|
| MR-0 聚焦回归（TEST-07 selector） | `./mvnw -q test -Dtest=Mr0PocIngestServiceTest,Mr0PocQualityServiceTest,Mr0PocAnalysisServiceTest` | 0 | 18 tests（Ingest 6 + Quality 5 + Analysis 7），0 failures，0 errors |
| 全量测试（TEST-FULL 前半） | `./mvnw -q test` | 0 | surefire XML 汇总：**562 tests / 0 failures / 0 errors / 1 skipped**（基线 538 → +24，符合契约预期 ~562；skipped=既有 `SecurityDirectorySearchBenchmarkTest` 条件跳过基准，非本任务引入） |
| package（TEST-FULL 后半） | `./mvnw -q -DskipTests package` | 0 | BUILD SUCCESS，`target/*.jar` 生成 |
| 一次真实 PoC 运行（TEST-08 / AMD-003） | `bash scripts/run-mr0-poc.sh` | 0 | status=SUCCESS，时长 182s，时区 CST；实现阶段成功运行恰 1 次（verifier artifact-restore 重跑 1 次后全程合计恰 2 次） |

门禁执行时间（UTC）：聚焦 17:13Z；全量+package 17:13:43Z–17:15:08Z；PoC 17:15:09Z–17:18:11Z（本地 CST 2026-08-16 01:15–01:18，跨日为时区换算所致）。

## 3. PoC 运行证据摘要（POC-EVIDENCE.json / POC-REPORT.md 重跑再生）

- 窗口（冻结）：analysisStart=2026-07-01、analysisEnd=2026-07-31、warmupStart=2026-04-01、sampleSize=150。
- 双分析哈希一致：analysisHashRun1 == analysisHashRun2 == `1cb27099b8728b8ae029038886330bde6bd6ec33a47f07301cf078df86ca7e2a`（sha256，字段白名单见证据文件）。
- 二次导入幂等（四表 inserted 全 0）：universe=0、membership=0、dailyBar=0、moneyFlow=0（updated 5543/101/11199/3432 为 ODKU 原地刷新，符合幂等语义）。
- 规模：universeSize=151（样本 150 + 基准 SH.000001）、tradingDays=23（INDEX_KLINE_DERIVED）、bar(分析窗)=3080、membership=101、moneyflow=3432；ingest 失败 0 条。
- 数据边界：脚本仅做幂等 ODKU upsert，未删除、未修改任何既有行。

## 4. data quality boundary（数据质量边界延续，REC-04 要素三）

以下发现为质量引擎真实检出并如实报告，**均保留未清理**，MR-1 前置清理清单见 AI_HANDOFF「MR-1 前置清理」：

1. UNIT_ANOMALY（LONGPORT SH.600519 脏数据）：affectedCount=**8**（本次重跑质量报告实际值，与基线一致；SH.600519 2026-07-01..2026-07-10 vwap≈118k–120k 落在 [low,high] 区间外约 100 倍，即 volume 未×100 的手/股单位错位）。按任务边界禁止删除/篡改，保留原样。
2. SINA 行业成分缺口：coverageGap=**49**（49/150 样本股无行业成分，不入占比分母，单独计入 coverageGap 报告）。
3. market_calendar CN 空：marketCalendarCnRows=**0**（空表，交易日由基准 SH.000001 日 K 推导兜底，INDEX_KLINE_DERIVED）。
4. 时点穿越显式假设：TIME_POINT_LOOKAHEAD——现用 SINA_INDUSTRY 当前成分聚合历史，非 PIT 申万成分，作为显式时点假设报告（PIT 成分在凭据就绪前保持阻断，MR-1-BND-B）。

## 5. MR-0 PoC is not MR-1（底座声明，REC-04 要素四）

**MR-0 PoC is not MR-1**：MR-0 PoC 不等于 MR-1 正式数据底座完成。本 PoC 仅证明样本级（流通市值 Top-150 + 基准）单交易月（2026-07）口径下的公式引擎、公共源可得性、幂等导入与质量检出血缘；MR-1 市场全景 MVP 的输入边界仍以 `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-REPORT.md` §MR-1 输入边界（MR-1-BND-A/B/C/D）为准：可用=样本级公式引擎+公共源日 K+幂等导入；阻断=全市场逐股历史/PIT 申万成分/官方资金流（凭据）；禁用=价量猜资金等伪指标。

## 6. 五个切片摘要

| 切片 | AC | 摘要 | 自检工件 |
|---|---|---|---|
| SLICE-01（ROLE-RUN-IMP-S1-G1） | AC-01 | `Mr0PocAnalysisMapper` 6 处 `@Select` 注解 SQL 全量迁移至 `src/main/resources/mapper/Mr0PocAnalysisMapper.xml`（statement id 与方法名 1:1、语义逐字保持）；新增无上下文架构测试断言 poc 包注解 SQL=0、id 集==方法名集（恰 6） | SELF-CHECK-SLICE-01.md |
| SLICE-02（ROLE-RUN-IMP-S2-G1） | AC-02, AC-03 | 新增 `Mr0PocIngestGate`（开关 true 且 activeProfiles 恰 `{"local"}` 才放行，拒绝 400 BUSINESS_RULE_VIOLATION）与 `Mr0PocParamValidator`（start<=end、warmup<=start、sampleSize∈[1,500]、跨度<=365 天）；Controller+Service 双层防御；新增 22 用例 | SELF-CHECK-SLICE-02.md |
| SLICE-03（ROLE-RUN-IMP-S3-G1） | AC-04 | F-005 修复：校验器删除「selector 必须出现在 sourcePath 内容」误报判定（保留存在性与 receipt 绑定校验）+3 回归用例；删除 pom/run-mr0-poc.sh/3 个 PoC 测试类共 5 处 frozen-selector 注释 | SELF-CHECK-SLICE-03.md |
| SLICE-04（ROLE-RUN-IMP-S4-G1） | AC-05 | 校验器新增 reviewer→verifier 严格串行门禁（同代 verifier.startedAt>=reviewer.finishedAt 相等合法、dispatch observedAt>=REVIEW_CLEAR 迁移 at、缺失回执降级 warning）+ `qta-role-ordering.test.mjs` 7 确定性用例（含旧任务形态拦截）；删除 3 份 MR0 文档与 POC-REPORT 中 selector 注释 | SELF-CHECK-SLICE-04.md |
| SLICE-05（ROLE-RUN-IMP-S5-G1） | AC-06, AC-07, AC-08 | 本切片：三项门禁执行（聚焦 18/0/0、全量 562/0/0、package 0）、一次真实 PoC 成功运行（exit 0、双哈希一致、二次导入 inserted=0）、POC-EVIDENCE/POC-REPORT 重跑再生、撰写本 CLOSEOUT-REPORT（四要素齐备） | 本文件 + 实现者返回载荷 |

## 7. AC-01..08 完成状态表（实现者视角， SELF_CHECKED 语义）

| AC | 内容 | 实现状态 | 证据 |
|---|---|---|---|
| AC-01 | Mapper 注解 SQL 迁移 XML + 架构防回归 | SELF_CHECKED | TEST-01/02（SLICE-01） |
| AC-02 | ingest local-profile 门禁 | SELF_CHECKED | TEST-03（SLICE-02） |
| AC-03 | analyze/ingest 入参双层防御 | SELF_CHECKED | TEST-04（SLICE-02） |
| AC-04 | 治理噪声清理 + F-005 校验修复 | SELF_CHECKED | TEST-05（SLICE-03） |
| AC-05 | reviewer→verifier 串行门禁 | SELF_CHECKED | TEST-06（SLICE-04） |
| AC-06 | 集成回归 + 一次真实 PoC（AMD-003） | SELF_CHECKED | TEST-07/TEST-08/TEST-FULL（本切片 §2/§3；LongPort 脏数据未删改、UNIT_ANOMALY affectedCount=8 与基线一致） |
| AC-07 | 独立审查与核验（流程 AC，父级派发） | NOT_STARTED（实现侧输入就绪：全部切片 SELF_CHECKED，候选待父级冻结） | 待 CODE_REVIEWER / FINAL_VERIFIER 角色运行账本 |
| AC-08 | 文档与交付收口记录 | 实现者部分 SELF_CHECKED（本报告四要素）；父级 finalization（9 份文档同步）待独立验收后执行 | TEST-09 + 本文件 |

## 8. 剩余步骤（父级所有）

1. 冻结单一 cumulative candidate（Git 由父级操作）。
2. 依 AC-05 顺序约束串行派发全新 CODE_REVIEWER，outcome 回执落盘后 REVIEW_CLEAR。
3. 架构门禁（`scripts/check-ai-task-control.mjs` / `check-ai-architecture.mjs`）与全新 FINAL_VERIFIER（干净 worktree，TEST-08 artifact-restore 重跑一次，全程成功恰两次）。
4. 独立验收通过后执行 9 份文档 finalization 同步，并以 finalization.changedPaths 覆盖。
