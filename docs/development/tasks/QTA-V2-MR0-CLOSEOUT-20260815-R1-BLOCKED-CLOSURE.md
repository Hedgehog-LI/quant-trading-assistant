# BLOCKED CLOSURE — QTA-V2-MR0-CLOSEOUT-20260815-R1

- 时刻：2026-08-15T18:35:00Z（UTC）
- 终态：`BLOCKED`（自 CANDIDATE_FROZEN gen-3 直接转入；review G3 已 PASS 并完整入账）
- 候选：4736a6c53d47c520e517e2df69fdf4ce39d20d37（gen-3，COMMIT，tree bd7dab34106a5820f37432a94c983d12cc51ea84）

## 1. 阻塞证据（机器可复现）

对工作 control 执行 `node scripts/check-ai-task-control.mjs` 在记录 gen-3 REVIEW_CLEAR 迁移（seq 14，at 2026-08-15T18:28:00Z）后稳定失败：

```
Task control validation failed:
- final verifier ROLE-RUN-FV-G1 dispatch precedes REVIEW_CLEAR formation
```

时间线（全部真实回执/账本值）：

| 事件 | 时刻（UTC） |
|---|---|
| ROLE-RUN-CR-G2（gen-2 reviewer）finishedAt | 2026-08-15T17:48:15Z |
| CR-G2 outcome 回执 observedAt | 2026-08-15T17:48:37.738Z |
| seq 10 CANDIDATE_FROZEN→REVIEW_CLEAR（gen-2 形成） | 2026-08-15T17:50:00Z |
| FV-G1 派发回执 observedAt | 2026-08-15T17:51:10.692Z |
| FV-G1 首命令（startedAt） | 2026-08-15T17:51:23Z |
| seq 14 CANDIDATE_FROZEN→REVIEW_CLEAR（gen-3 形成） | 2026-08-15T18:28:00Z |

FV-G1 派发（17:51:10）**晚于** 其同代（gen-2）REVIEW_CLEAR 形成（17:50:00）——真实顺序完全合规。

## 2. 根因（gen-3 候选内 AC-05 实现缺陷）

`scripts/check-ai-task-control.mjs` 的 `reviewClearTransitionAt(control, generation)`（约 L794-800）用 `occurrences[generation - 1]` 把「第 g 个 REVIEW_CLEAR 出现」映射到「generation g 的 verifier」。该启发式假设每个 generation 恰好形成一次 REVIEW_CLEAR。本任务历史是反例：**gen-1 review FAIL（未形成 REVIEW_CLEAR）→ repair → gen-2 形成第 1 次 REVIEW_CLEAR → FV-G1 REJECTED → repair → gen-3 形成第 2 次 REVIEW_CLEAR**。于是 gen-2 的 FV-G1 被误配到第 2 次出现（gen-3 的 18:28:00），产生误报。

`validateVerifierDispatchOrdering` 的调用（L910）无状态门槛，任何 lifecycle 状态都会触发；历史 roleRuns 与迁移不可改写（哈希锚定）；因此只要 control 同时含有 (a) 已接受的 gen-2 verifier、(b) 两次 REVIEW_CLEAR 形成，校验永不通过。

## 3. 为何不能在 本任务 内修复（机器约束）

1. 修复必须修改 `scripts/check-ai-task-control.mjs`（治理保护路径；按合同 DECISION 只能由冻结切片实施者修改）→ 产生新候选（gen-4）。
2. 冻结 schema 硬性上限：`maxRepairRounds` 恒为 2；`candidate.generation` 必须等于 `repairRound + 1` ≤ 3。repair round 1/2 已用（REVIEW-G1 findings、F-1），gen-4 冻结在机器上不可表达。
3. 任何未经新候选的校验器改动 = 候选漂移/内联重释，治理明令禁止。
4. 同会话新任务也被 Hook 阻止（父会话 active lock 绑定本 taskId；`recordDispatchReceipt` 拒绝不同 taskId 的派发，且 reconcile 仅在新 UserPromptSubmit 时发生）。

结论：按「相同预算耗尽 → BLOCKED + 新有界任务」的既有治理模式（参见 P110-A R1 两超时 BLOCKED 后以新 Task ID 重试先例），本任务以 BLOCKED 终结。

## 4. R2 修复设计（一处函数 + 测试）

**修复**（`reviewClearTransitionAt`）：把每个 REVIEW_CLEAR 出现映射到形成它的 generation——generation = 该迁移之前 `to === "CANDIDATE_FROZEN"` 迁移的个数（每次冻结递增一代）。对 generation g 的 verifier 取 generation == g 的形成；等价且更简的语义：**verifier 派发回执 observedAt 之前最近一次 REVIEW_CLEAR 形成必须存在**（不存在 = 提前 dispatch = 失败；存在 = 通过）。该语义同时满足 AMD-002/TD-05-02 意图并天然兼容多重 repair 历史。

**回归测试**（`scripts/tests/qta-role-ordering.test.mjs` 增补）：构造多周期 fixture——gen-1 review FAIL（无 REVIEW_CLEAR）→ repair → gen-2 REVIEW_CLEAR(t1) → gen-2 verifier dispatch(t1+ε) → FV REJECTED → repair → gen-3 REVIEW_CLEAR(t2)——断言 gen-2 verifier 不被 t2 误伤（当前实现失败的用例），并保留既有 7 用例。

**R2 任务建议**：
- Task ID：`QTA-V2-MR0-CLOSEOUT-20260815-R2`
- 基线：分支 `codex/qta-v2-mr0-closeout-r1` @ `4736a6c53d47c520e517e2df69fdf4ce39d20d37`（gen-3 候选，产品代码无需再动——562 测试/包/PoC 证据链已在案）
- 范围：仅 `scripts/check-ai-task-control.mjs`（一处函数）+ `scripts/tests/qta-role-ordering.test.mjs`（多周期用例）+ 新任务工件；L1 lane 即可（无 migration/provider）
- PoC 运行次数语义需在 R2 合同重述（实现期成功运行已有 1 次；R2 verifier 重跑 1 次；FV-G1 的失败运行不计成功）
- 本任务 BLOCKED 控制文件保留为审计事实，不修改为通过。

## 5. 已完成并保留的有效成果（全部绑定 gen-3 候选 4736a6c）

- AC-01/02/03/05/08 完整实现与机器证据；AC-04 完整实现（F-005 修复 + 噪声清零 + fs 扫描防回归）；AC-06 实现期证据完整（562/0/0 + package + PoC SUCCESS 双哈希/二次导入 inserted=0/UNIT_ANOMALY=8 保留）。
- AC-05 时间顺序门禁本身已实战拦截旧任务形态（TD-05-06 用例 + 本任务全程严格串行的真实账本），其缺陷仅为多周期历史下的误报（本文件 §2）。
- review G3（FUNCTIONAL/ARCHITECTURE PASS）与全部治理账本、回执、锚定链完整入账。
