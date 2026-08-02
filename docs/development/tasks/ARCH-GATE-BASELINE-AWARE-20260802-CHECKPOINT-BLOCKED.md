# Checkpoint: ARCH-GATE-BASELINE-AWARE-20260802 → BLOCKED (ledger-reconciliation)

- recordedAt: 2026-08-02T10:20:00Z
- actor: codex-parent-arch-gate-baseline-aware-1
- taskId: ARCH-GATE-BASELINE-AWARE-20260802
- previous lifecycleState: VERIFIED (substantive)
- new lifecycleState: BLOCKED (machine-ledger reconciliation only)

## 一、实质交付完成且独立验收 ACCEPTED（非 ledger 阻塞）

治理任务 ARCH-GATE-BASELINE-AWARE-20260802 的工程目标 100% 完成并被独立最终核验者接受：

- 候选 `5aceef0aeb77b701c1f34dc0ee96b4c30ba404af`（tree `8c97abc4…`），仅改
  `scripts/check-ai-architecture.mjs`、`scripts/tests/ai-governance.test.mjs`、
  `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md` + 任务工件。
- 新增 `--baseline <dir>` 基线感知分类：per-file×per-error-rule 把候选错误分为
  `introduced`（新跨阈值或 baseline 缺文件）/`worsened`（两边都触发且可量化指标增量 > 默认 20）/
  `pre-existing`（其余）。exit code 仅由 `blockingErrorCount = introduced + worsened` 决定。
  报告新增 `baselineIdentity`/`blockingErrorCount`/`introducedErrors`/`worsenedErrors`/
  `preExistingErrors`，且 `errors[]` 只含 blocking、`errorCount==errors.length==blockingErrorCount`、
  `errorCount==0 ⟺ PASS ⟺ exitCode 0`。无 `--baseline` 时行为严格不变。
- 7 个新测试 TEST-AG-01..07（45/45 全过）；治理 suite `run-ai-governance-gates.mjs` exit 0。
- 角色（全新 session）：test-designer TD-AGBA-2（accepted，BA-1/2/3 + TEST-AG-07）；implementer
  IMP-AGBA-DET-1/DET-2/IDX-3（accepted）；code-reviewer REV-AGBA-1（REVIEW_CLEAR，
  FUNCTIONAL=PASS/ARCHITECTURE=PASS，0 blocking findings）；final-verifier VER-AGBA-1（disposable
  worktree `/tmp/ver-agba-1`，ACCEPTED，7 个机器 receipt，candidate 前后未变）。

## 二、D2 已被实质解除（验证）

用真实前端 baseline `80c38324` 抽取 market-workspace.tsx 到 `/tmp/d2baseline` 实测：检测器现在把
3 个既有 React 长 method 错误归类为 pre-existing/non-blocking，D2 新增文件无 blocking error。
即本治理修复达成了它的产品目标——让 D2 架构门禁可通过（待 D2 续作任务正式重跑并记录）。

## 三、阻塞根因（仅机器 ledger，非工程/非产品）

冻结并 anchor 时的 contract 存在一处 ledger 笔误：AC-06 `requiredEvidence=["AUTOMATION","STATIC"]`，
但冻结的 test inventory（7 项）没有 `kind:"STATIC"` 的测试，导致控制校验
`node scripts/check-ai-task-control.mjs` 报 `AC-06 is missing STATIC evidence for the frozen candidate`。
`check-ai-task-control.mjs` 的 STATIC 证据匹配要求 `testCase.kind === "STATIC"`（见源码第 539 行）。

按治理单调锁（`validateMonotonicControl`，第 904-910 行）：CONTRACT_FROZEN 之后
`contract.{acceptanceCriteria,testInventory,...}` 不可原地修改；任何修改都被判
`frozen contract.<field> changed after anchoring`。因此不能在不触发 contract 版本 bump +
重新 anchor 的前提下原地补一个 STATIC 测试或把 AC-06 改成 AUTOMATION-only。contract 版本 bump 又
会使已绑定的 review/verification 证据（绑在 v2 hash `79ec00e8…`）失效，需要按治理规则重新派发
review + final-verifier（候选 `5aceef0a` 代码不变，属快速重新绑定）。

此为 governance-meta 阻塞，不影响候选代码/测试/文档的正确性，也不影响 D2 解除（检测器已可用）。
`DELIVERY_READY` 机器门（`check-ai-delivery-ready.mjs`）依赖 VERIFIED 通过，故本任务停在 BLOCKED。

## 四、唯一恢复路径（下一受控会话）

1. 将 contract bump 到 v3：AC-06 `requiredEvidence` 改为 `["AUTOMATION"]`（STATIC suite 已由
   TEST-AG-06 的 spawnSync 断言覆盖），或冻结一个 `kind:"STATIC"` 的 TEST-AG-STATIC（selector
   `QTA AI governance gates passed`，source `scripts/run-ai-governance-gates.mjs`）并产出其 receipt。
   推荐 v3 = AC-06 AUTOMATION-only（最小改动）。
2. 重新计算 contract hash；candidate 保持 `5aceef0a`，generation 升到 2（同 commit、新 generation），
   以满足 `validateMonotonicControl` 第 913-916 行（candidate identity 不变但需新 generation）。
3. 重新 anchor（`check-ai-task-control.mjs` 成功校验会追加新 anchor 快照）。
4. 重新派发全新 qta-code-reviewer（绑 v3 hash + gen2 候选）与全新 qta-final-verifier（重跑 7 测试
   receipt + STATIC suite receipt）。候选代码不变，两个角色应快速返回 REVIEW_CLEAR / ACCEPTED。
5. 达 VERIFIED → finalization → `node scripts/check-ai-delivery-ready.mjs` exit 0 → DELIVERY_READY。

## 五、未验证维度 / 下一步业务

- RUNTIME / DEPLOYMENT：NOT_REQUIRED（governance tooling，scripts-only）。
- D2 恢复（Phase 5）：本治理修复 DELIVERY_READY 后，重新运行 D2 架构门禁（显式传前端 baseline
  `80c38324` 抽取目录），确认 3 既有页面错误记为 pre-existing debt、D2 新增无 blocking，再派发
  全新 reviewer + final-verifier，前端 typecheck/lint/test/build + git diff --check，形成前后端
  双候选提交并写入 D2 CONTROL。
- 未 push、未合并 main、未部署、未改三个 React 页面、未动 D2 前端改动。
