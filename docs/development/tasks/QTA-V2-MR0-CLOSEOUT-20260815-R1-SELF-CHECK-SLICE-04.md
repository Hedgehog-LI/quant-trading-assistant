# SELF-CHECK — QTA-V2-MR0-CLOSEOUT-20260815-R1 / SLICE-04 / ROLE-RUN-IMP-S4-G1

- Dispatch ID: QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-S4-G1-D1
- Assigned AC: AC-05；verdict: `SELF_CHECKED`（切片级）
- Started: 2026-08-15T17:02:00Z / Finished: 2026-08-15T17:11:15Z

## Changed files（7 个，均在 allowlist 内）

| 文件 | 变更 |
|---|---|
| `scripts/check-ai-task-control.mjs` | +80/−6：结构门禁（同代 verifier.startedAt >= reviewer.finishedAt，相等合法、仅同代、L0 兼容）+ 文件阶段 `validateVerifierDispatchOrdering`（dispatch observedAt >= 按 generation 索引的 REVIEW_CLEAR 迁移 at；>= 同代 reviewer outcome observedAt，缺失/无时间戳降级 warning）；`validateTaskControlFiles` 增加可选第三 warnings 参数（向后兼容） |
| `scripts/tests/ai-governance.test.mjs` | 仅调整 FINAL_VERIFIER fixture 时间戳至 [00:00:30,00:01:00]（AMD-002 授权；断言零改动） |
| `scripts/tests/qta-role-ordering.test.mjs`（新） | 428 行，7 用例 TD-05-01..07（含旧任务形态 19:56:00/19:53:16 复现拦截） |
| 4 份文档（3 份 MR0 特性文档 + POC-REPORT.md） | 各删 1 行 frozen-selector 注释，内容零改动 |

## Self-check commands

| 命令 | exit | 结果 |
|---|---|---|
| `node --test scripts/tests/qta-role-ordering.test.mjs` | 0 | 7/7/0 |
| `node --test scripts/tests/ai-governance.test.mjs` | 0 | 73/73/0 |
| `node scripts/check-ai-task-control.mjs <本任务 CONTROL.json>` | 0 | 仍通过（新门禁在 pre-REVIEW_CLEAR 正确 inactive） |
| `grep -rn "frozen-selector" docs/features/ ...POC-REPORT.md` | 1（0 命中） | 0 残留 |
| `node scripts/run-ai-governance-gates.mjs` | 0 | 全门禁通过 |

## Deviations（实施者披露）

1. ai-governance.test.mjs fixture 时间戳调整（仅 verifier 窗口，授权路径；73 用例全绿证明断言未删）。
2. REVIEW_CLEAR 选择按 generation 索引（第 g 次出现）而非「最后一次出现」——单代行为相同，多代防误伤（TD-05-05 覆盖）。
3. `validateTaskControlFiles` 第三 warnings 参数（暴露 outcome 交叉降级 warning 的最小方式；导出返回形状不变）。

## 角色运行元数据

- roleRunId ROLE-RUN-IMP-S4-G1；dispatch QTA-V2-MR0-CLOSEOUT-20260815-R1-IMP-S4-G1-D1；sessionId agent_a76f13d8-e59f-42e0-94c6-c058854ee86f；executorType SUBAGENT；agentDefinition .zcode/agents/qta-implementer.md；sliceId SLICE-04；generation 1；capability READ_WRITE；executionOutcome COMPLETED；status CLOSED；enforcement ADVISORY；compensatingIsolation: 7-file allowlist（含治理校验器，合同 DECISION 授权）；无 Git、无网络、无密钥；waitCalls 0；maxShellPollsForOneCommand 0；compactionCount 0。
