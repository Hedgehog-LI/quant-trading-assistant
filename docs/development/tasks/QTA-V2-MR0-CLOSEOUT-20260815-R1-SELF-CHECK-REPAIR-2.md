# SELF-CHECK (REPAIR-2) — QTA-V2-MR0-CLOSEOUT-20260815-R1 / ROLE-RUN-IMP-R2-G3

- Dispatch ID: QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-R2-G3-D1
- Assigned AC: AC-06（连带 AC-07 流程位）；finding F-1；verdict: `SELF_CHECKED`
- Started: 2026-08-15T18:10:00Z / Finished: 2026-08-15T18:17:48Z

## Changed files（2 个，均在 REPAIR-2 allowlist 内）

| 文件 | 变更 |
|---|---|
| `Mr0PocIngestService.java` | 新增泛型静态 `dedupeByUniqueKey`（后到覆盖）；4 个 accumulate 调用点（membership/universe/dailyBar/moneyFlow）批内按表唯一键去重后再写入与计数；javadoc 口径说明（净 ~17 行代码） |
| `Mr0PocIngestServiceTest.java` | 新增第 7 用例 `ingestMembershipDuplicateUniqueKeysInBatchReportNoFalseInserted`；fixture 桩扩展 duplicateIndustryMember（复制既有成员对象，真实 SINA 重复形态）；既有 6 用例断言零改动 |

## Self-check commands

| 命令 | exit | 结果 |
|---|---|---|
| `./mvnw -q test -Dtest=Mr0PocIngestServiceTest` | 0 | 7/0/0 |
| `./mvnw -q test -Dtest=Mr0PocQualityServiceTest,Mr0PocAnalysisServiceTest` | 0 | Quality 5/5（XML 口径）+ Analysis 7/7 |

## findingResolution（F-1）

- 方案选择：批内去重（而非 ODKU 真实插入分类计数）——后者需改四表 mapper 返回值并处理 H2/MySQL ODKU 方言不一致，超出最小修改。
- 去重语义 last-wins：与 MySQL ODKU 逐条执行后行覆盖的写库终态逐字节一致；upsert 写入语义未变。
- 键构成以 V23 实际 uk 为准：universe=canonicalSymbol、membership=industryCode+canonicalSymbol、dailyBar/moneyFlow=tradeDate（批内常量列不进键，注释说明）。
- 回归证明：新用例复现 F-1 形态（同批同成员两次）：首次 inserted=2==COUNT（修复前 written=3 虚增）；二次 inserted=0/updated=2/计数不变（修复前误报 inserted=1）。
- 过程披露：首跑 2 失败（新用例 fixture 桩把裸字符串追加进成员数组导致解析中断），隔离定位后一次修复全绿（fixture 修正，非生产代码二改）。

## Deviations

- checkpoint 文本载荷交付（写路径冻结）；无 Hook runtime receipt；未跑 PoC 脚本/全量（AMD-003 语义，运行时证明由 verifier TEST-08 承担）。

## 角色运行元数据

- roleRunId ROLE-RUN-IMP-R2-G3；dispatch QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-R2-G3-D1；sessionId agent_b158c366-20a2-44c5-a6aa-ad3c9ae1552e；executorType SUBAGENT；agentDefinition .zcode/agents/qta-implementer.md；sliceId REPAIR-2；generation 3；capability READ_WRITE；executionOutcome COMPLETED；status CLOSED；enforcement ADVISORY；compensatingIsolation: 2-file allowlist、无 Git、无网络；waitCalls 0；maxShellPollsForOneCommand 0；compactionCount 0。
