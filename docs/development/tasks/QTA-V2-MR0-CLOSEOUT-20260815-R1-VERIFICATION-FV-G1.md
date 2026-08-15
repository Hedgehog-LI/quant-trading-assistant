# VERIFICATION (FV-G1) — QTA-V2-MR0-CLOSEOUT-20260815-R1 / ROLE-RUN-FV-G1

- Dispatch ID: QTA-V2-MR0-CLOSEOUT-20260815-R1-FV-G1-D1
- Candidate: 84094bbe02d0c76fcb9f8f1d02b03a7e9eb013e8（gen-2）
- Mode: 干净一次性 worktree（/tmp/qta-fv-closeout-r1 @ 84094bb，已回收）
- Started: 2026-08-15T17:51:23Z / Finished: 2026-08-15T18:03:36Z
- Verdict: `REJECTED`（FUNCTIONAL=FAIL / ARCHITECTURE=PASS；deliveryPermitted=false）

## receiptSummary（10/10 回执，均绑定 ROLE-RUN-FV-G1 / 84094bb / COMMIT）

| Test | exit | result | candidateUnchanged |
|---|---|---|---|
| TEST-01 | 0 | PASS | true |
| TEST-02 | 0 | PASS | true |
| TEST-03 | 0 | PASS | true |
| TEST-04 | 0 | PASS | true |
| TEST-05 | 0 | PASS | true |
| TEST-06 | 0 | PASS | true |
| TEST-07 | 0 | PASS | true |
| **TEST-08** | **3** | **FAIL（IDEMPOTENCY_VIOLATION）** | true（artifact-restore 按字节恢复） |
| TEST-09 | 0 | PASS | true |
| TEST-FULL | 0 | PASS | true |

失败回执已回收：`docs/development/tasks/QTA-V2-MR0-CLOSEOUT-20260815-R1-RECEIPT-FV1-TEST-08-FAILED.json`（sha 86b2db53f76e2e09444bd2b71634e8b7520aca075d24c1916fa0387244d6db5e）。

## Findings

### F-1 — P1（阻断验收）— 二次导入 membership 误报 inserted=1（计数推导缺陷）

- 现象：TEST-08 verifier 侧重跑 exit 3 `IDEMPOTENCY_VIOLATION`，二次导入 membership 报告 inserted=1, updated=101。
- 根因：`Mr0PocIngestService.TableSummary.accumulate`（L106-123）以「写入条数 − 预查存在行数」推导 inserted，不对 payload 按唯一键去重；SINA 第二次调用返回 102 条映射到 101 个唯一键（uk_mr0_industry_membership = taxonomy+industry_code+symbol+as_of_date）→ 误报 inserted=1。
- DB 取证：实际持久化零插入（verifier 窗口 created_at 新行=0；MAX(id)=607 全部创建于实现期；101 行 as_of=2026-08-16 仅 fetched_at 刷新）。
- 冻结脚本语义「二次导入任何 inserted>0 → exit 3」触发；exit 3 不允许重试。
- repair 建议：accumulate 按唯一键对 payload 去重（或以真实 ODKU 插入分类计数）；新增「payload 含重复唯一键 → inserted=0」回归用例（IngestService 层 mock 源构造）。
- 失败指纹：`second-ingest-membership-false-inserted`（第 1 次出现）。

### F-2 — P3 — CR-G1-5 时间注释照录（非缺陷，见 REVIEW-G2）

### F-3 — P3 — verifier 侧双哈希不可观察（回执仅存 stdoutSha256）；实现期双哈希一致已核实（1cb27099…）

## staticChecks

- 候选身份：worktree HEAD == 84094bb，门禁前后不变；合同哈希一致。
- 治理时序（AC-05 实战）：CR-G2 finishedAt 17:48:15Z → outcome 回执 17:48:37.738Z → REVIEW_CLEAR 17:50:00Z → FV 派发 observedAt 17:51:10.692Z → 首命令 17:51:23Z，严格串行无重叠。
- 架构门禁 G2 报告 sha 一致 PASS；review 绑定同一候选。
- AC-01..05/08 独立静态复核全部 PASS。

## AC-by-AC

AC-01/02/03/04/05/08 PASS；AC-06 FAIL（TEST-08）；AC-07 FAIL（verifier 全绿未满足）。

## Dimensions

STATIC=PASS / AUTOMATION=PASS / RUNTIME=FAIL / DEPLOYMENT=NOT_REQUIRED。

## 角色运行元数据

- roleRunId ROLE-RUN-FV-G1；dispatch QTA-V2-MR0-CLOSEOUT-20260815-R1-FV-G1-D1（observedAt 17:51:10.692Z）；sessionId agent_bd1c12b3-fa6d-4939-a847-64aa792b2b79；executorType SUBAGENT；agentDefinition .zcode/agents/qta-final-verifier.md；sliceId ""；generation 2；capability VERIFY_EXECUTE；executionOutcome COMPLETED；status CLOSED；enforcement ADVISORY；compensatingIsolation: disposable worktree、主仓只读、机器回执、docker exec 只读取证；waitCalls 1；maxShellPollsForOneCommand 2；compactionCount 0。
