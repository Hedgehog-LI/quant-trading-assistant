# Task Contract: ARCH-GATE-BASELINE-AWARE-20260802 Architecture gate baseline/diff-aware detection

## Contract Identity

- Status: `FROZEN`
- Contract version: 3
- Frozen at: 2026-08-02T10:20:00Z
- Frozen by parent run: codex-parent-arch-gate-baseline-aware-1
- Lane: `L2`
- Test-designer amendment accepted: BA-1/BA-2/BA-3 + TEST-AG-07 (within L2 cap of 3 blocking amendments).
- Version 3 amendment: AC-06 requiredEvidence corrected to AUTOMATION only. The STATIC governance-suite gate
  is asserted inside TEST-AG-06's body (spawnSync run-ai-governance-gates.mjs exit 0), so the AUTOMATION
  receipt already proves the STATIC suite. This corrects a version-2 inventory/evidence mismatch (AC-06
  required STATIC but the inventory had no STATIC test). The candidate code/tests/doc are unchanged.

This is a governance/tooling task. It does **not** change any business code, the three large React page
files, or D2's frontend implementation. It changes the architecture detector so pre-existing baseline debt
that a candidate does not worsen is recorded as debt instead of a hard block, while newly introduced or
worsened errors still block.

## Objective

Make `scripts/check-ai-architecture.mjs` baseline-aware/diff-aware. When an explicit baseline source is
provided, the gate classifies each candidate file's errors into `introducedErrors`, `worsenedErrors`, and
`preExistingErrors`. Exit code is decided solely by `blockingErrorCount` (introduced + worsened). Pre-existing
debt that the candidate does not worsen is recorded but does not block. When no baseline is provided the
detector keeps its current strict behavior (every error blocks).

This unblocks SECURITY-DIRECTORY-D2-20260802-RESLICE, whose 3 architecture errors are pre-existing long React
methods in pages D2 only touched with small integration code. D2 restoration is a **separate** later task.

## Authority

- Product/design: none (governance tooling).
- Architecture/governance authority:
  `.agents/skills/qta-development-orchestration/references/GOVERNANCE_V2_POLICY.md` §Architecture Gate
  ("A reviewer cannot waive a machine architecture error. If the detector itself is wrong, repair and
  validate the detector in a separate governed task, then regenerate the candidate-bound report.").
- Skill authority: `.agents/skills/qta-development-orchestration/SKILL.md` §Architecture Gate.
- Existing detector + tests: `scripts/check-ai-architecture.mjs`, `scripts/tests/ai-governance.test.mjs`.
- Baseline commit (backend/governance): `979b080` (current `codex/security-directory-d2-20260802` HEAD).
- Baseline branch: `codex/security-directory-d2-20260802`.
- Pre-existing dirty paths: `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CANDIDATE.patch`,
  `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CONTROL.json`, and all
  `SECURITY-DIRECTORY-D2-20260802*` task artifacts already present.
- Allowed write paths: `scripts/check-ai-architecture.mjs`, `scripts/tests/ai-governance.test.mjs`,
  `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-*.md|.json|.patch`, and the governance doc
  `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md` (new). No other paths.

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | D2 RESLICE is BLOCKED only by 3 architecture errors, all `longest method > 100` from 3 React page files (`market-data.tsx` 115, `market-workspace.tsx` 210, `market-segments.tsx` 148). D2 added 3–13 lines of SecuritySelector integration to each and did not create those methods. |
| FACT | Measured against the frozen frontend baseline `80c38324`: `market-data.tsx` longest=115 (candidate 115, unchanged), `market-workspace.tsx` longest=199 (candidate 210, +11 within allowed delta, no new error category), `market-segments.tsx` longest=148 (candidate 148, unchanged). All three errors pre-existed; none is newly introduced and none crosses a new hard threshold the baseline did not already cross. |
| FACT | The detector (`analyzeSource`/`main`) currently treats every emitted `error` as blocking regardless of whether it pre-existed. It supports `--base` (git ref) and `--manifest` for selecting candidate files and counting additions, but it does not compare candidate file metrics against baseline metrics. |
| FACT | GOVERNANCE_V2_POLICY explicitly prescribes the detector-repair route for detector-side issues; it forbids reviewer-prose waivers and forbids shrinking the file scan to mask debt. This task is the prescribed detector-repair route. |
| DECISION | Implement baseline-aware classification at **per-file × per-error-rule** granularity (the only granularity the detector supports today: `analyzeSource` emits aggregate metrics per file, not per method). For each candidate file error, compute the same rule against the baseline source and classify as: `introduced` (the rule fires for the candidate file but did NOT fire for the baseline file — e.g., baseline `longest ≤ 100`, candidate `longest > 100`); `worsened` (the rule fires for BOTH baseline and candidate AND the candidate's offending metric exceeds the baseline metric by more than the allowed delta — applies only to quantifiable metrics: significant lines, longest method, methods, dependencies); or `pre-existing` (the rule fires for both and the candidate is not worsened beyond the delta; or the rule is a non-quantifiable binary rule — e.g., SQL-outside-mapper, layer-crossing — that fires for both). A brand-new offending method that causes the file to newly cross a threshold is `introduced` (band-crossing), never `worsened`. Exit code is decided solely by `blockingErrorCount = introduced + worsened`. |
| DECISION | Default allowed worsen delta = **20** quantifiable units (lines or method-line count). This is large enough to permit small integration additions without false "worsened" labels (D2's largest growth is +11: market-workspace.tsx 199→210) and small enough to block material growth (e.g., 105→130 = +25 blocks). Band-crossing (`introduced`) is delta-independent, so a brand-new over-threshold method/component always blocks regardless of delta. This resolves the BA-2 contradiction: 199→210 (+11 ≤ 20) is `pre-existing`/non-blocking, while 95→120 is `introduced`/blocking. |
| DECISION | Backward compatibility: when no baseline source is supplied, behavior is byte-for-byte unchanged (every error blocks, no baseline fields emitted). Baseline is supplied via a new `--baseline <dir>` option pointing at a directory containing baseline source files at their repo-relative paths (clean, read-only, no git writes), plus the existing `--base <git-ref>` is reused for candidate-file selection and additions. This keeps the gate runnable from the backend root without needing the frontend repo on the detector's PATH, and avoids the governed-run shell-substitution restriction. |
| DECISION | Report coherence invariant (BA-3): in baseline-aware mode the JSON report's `errors[]` array contains ONLY blocking errors (introduced + worsened), `preExistingErrors[]` contains the non-blocking pre-existing errors, and `introducedErrors[]`/`worsenedErrors[]` are the blocking split. `errorCount == errors.length == blockingErrorCount` and `status == (errorCount === 0 ? "PASS" : "FAIL")` and `exitCode == (errorCount === 0 ? 0 : 1)`, so `errorCount==0 ⟺ PASS ⟺ exitCode 0`. This keeps the report self-coherent and compatible with the unchanged `check-ai-task-control.mjs` file-validation path (which checks `report.errors.length === control.architectureGate.errorCount` and `report.status === control.architectureGate.status`); D2 will record `architectureGate.errorCount = blockingErrorCount`. `check-ai-task-control.mjs` and `check-ai-delivery-ready.mjs` are NOT modified in this task. |
| DECISION | The classification must be deterministic and evidence-backed: each error detail (in whichever bucket) records `classification`, `candidateMetric`, `baselineMetric` (absent/`null` when the rule did not fire at baseline, i.e., `introduced`), and `delta` so a reviewer/verifier can audit why a pre-existing error did not block. Anti-masking honesty rule: if a candidate file is absent from the `--baseline` directory, every error for that file classifies as `introduced` (blocking) — a missing baseline file is never silently dropped (TEST-AG-07). |
| DECISION | Test-designer amendment BA-1/BA-2/BA-3 + TEST-AG-07 accepted and folded into this contract (within the L2 amendment cap of 3). OQ-1 resolved by parent: band-crossing introduced + delta(20) worsened. OQ-2 resolved by parent: TEST-AG-07 is Required. |
| DECISION | This task does not modify the three React page files or any D2 frontend implementation. It does not merge main, push, deploy, or start any business feature. |
| ASSUMPTION | The detector's existing brace/line accounting is accurate enough to compare two measurements of the same metric; the baseline-aware feature reuses `analyzeSource` for both baseline and candidate, so any pre-existing measurement bias cancels in the diff. |

## Scope

### In Scope

- `scripts/check-ai-architecture.mjs`: add baseline-aware classification + report fields + `--baseline` option;
  preserve strict behavior when no baseline is supplied.
- `scripts/tests/ai-governance.test.mjs`: add deterministic tests for the 6 required scenarios (see Test
  Inventory). Existing tests must continue to pass unchanged.
- `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md`: short governance doc describing when/how to pass a baseline,
  the classification semantics, the default delta, and the honesty rule (baseline must be the real frozen
  baseline; never omit files to mask debt).

### Out Of Scope

- D2 frontend restoration (separate task; uses the new feature after this task is DELIVERY_READY).
- Refactoring the three large React pages.
- Modifying `check-ai-task-control.mjs` or `check-ai-delivery-ready.mjs` (their `errorCount` field semantics
  are reused as "blocking error count").
- Any backend Java business code, MyBatis, Flyway, schedulers, providers.

### Prohibited

- Editing the three React page files or any D2 frontend implementation.
- Any Git merge/rebase/push/deploy, any main-branch write, any force-push.
- Calling `AskUserQuestion`.
- Removing or weakening existing architecture tests.
- Excluding file categories wholesale (e.g., "all React components"); classification is per-error vs baseline.

## Acceptance Criteria

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | Baseline-aware classifier labels each candidate file×rule error against a supplied baseline dir as `introduced`/`worsened`/`pre-existing` and blocks only on introduced+worsened. | Candidate dir where a baseline file already tripping `longest>100` is touched by a small integration that does not increase `longest` beyond baseline+delta (e.g., 199→210, delta 11 ≤ default 20); `--baseline` set. | `status=PASS`, `exitCode=0`, `blockingErrorCount=0`; the pre-existing error is in `preExistingErrors[]` and absent from `introducedErrors[]`/`worsenedErrors[]`; each error detail carries `classification`, `candidateMetric`, `baselineMetric`, `delta`. Classification granularity = per-file×per-error-rule. `errors[]`/`errorCount` contain only blocking errors; `errorCount==0 ⟺ PASS ⟺ exitCode 0`. | AUTOMATION | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-02 | A candidate file crossing a hard threshold the baseline file did not cross blocks as `introduced`. | Candidate dir with a file whose `longest` goes from ≤100 at baseline to >100 at candidate (e.g., 95→120); `--baseline` set. | `status=FAIL`, `exitCode=1`, `blockingErrorCount>=1`; the error is in `introducedErrors[]` with `baselineMetric` absent/`null` and `delta` absent; in `errors[]`/`errorCount`. | AUTOMATION | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-03 | A pre-existing offending metric that the candidate worsens beyond the allowed delta blocks as `worsened`; a worsening within the delta is `pre-existing`. | Candidate dir grows the offending metric beyond baseline+delta (delta=25 case, e.g., 105→130) AND a delta-exactly-equal-to-default case (e.g., 100→120 with default 20). | delta>default ⇒ `status=FAIL`, `exitCode=1`, error in `worsenedErrors[]`/`errors[]` with `delta` recorded; delta==default ⇒ `pre-existing`, non-blocking. | AUTOMATION | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-04 | Java over-long methods still block under baseline mode and the report binds to candidate identity. | Candidate dir adds/grows a >100-line Java method; `--baseline` + `--candidate-identity` set. | Java method error blocks (introduced or worsened per BA-2); `report.candidateIdentity == input`. | AUTOMATION | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-05 | Without `--baseline` the detector keeps strict behavior and emits no baseline fields. | Same `--files`/`--base` invocation as today, no `--baseline`. | Any emitted error ⇒ `exitCode=1`; report has no `baselineIdentity`/`introducedErrors`/`worsenedErrors`/`preExistingErrors` fields (or they are empty); `errorCount == errors.length`; existing architecture tests unchanged. | AUTOMATION+STATIC | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-06 | Report binds candidate+baseline identity, the report is self-coherent, and the full governance suite passes. | Baseline-aware run with `--candidate-identity`, `--baseline`, `--json-output`. | `candidateIdentity`/`baselineIdentity == inputs`; a report with only pre-existing errors has `errorCount==0`; `node scripts/run-ai-governance-gates.mjs` exits 0 (asserted inside the test body via spawnSync status 0). | AUTOMATION | AUTOMATION | IMPLEMENTER | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | `node scripts/run-ai-governance-gates.mjs` (runs `validate-ai-governance.mjs` + `node --test scripts/tests/ai-governance.test.mjs`) | exit 0; all existing + new arch-gate tests pass |
| AUTOMATION | Yes | `node --test scripts/tests/ai-governance.test.mjs` (focused) and the new baseline-aware tests create temp dirs and assert classification/exit codes | all required test IDs pass via `scripts/run-ai-evidence-command.mjs` |
| RUNTIME | No | n/a (governance tooling, no runtime/deployment) | NOT_REQUIRED |
| DEPLOYMENT | No | n/a | NOT_REQUIRED |

## Implementation Slices

| Slice ID | Coherent boundary | AC IDs | Allowed write paths | Max files | Max production-line delta |
|---|---|---|---|---:|---:|
| SLICE-DET | Baseline-aware detector: classify candidate errors vs a baseline directory into introduced/worsened/pre-existing; exit code from blockingErrorCount only; preserve strict behavior with no baseline; tests TEST-AG-01..03 | AC-01, AC-02, AC-03 | `scripts/check-ai-architecture.mjs`, `scripts/tests/ai-governance.test.mjs` | 2 | 450 |
| SLICE-IDX | Identity-binding + suite-pass tests TEST-AG-04 (Java block), TEST-AG-05 (strict no-baseline), TEST-AG-06 (candidate+baseline identity + governance suite) + governance doc | AC-04, AC-05, AC-06 | `scripts/tests/ai-governance.test.mjs`, `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md` | 2 | 300 |

## Frozen Test Inventory

| Test ID | AC IDs | Kind | Required | Source path | Exact selector | Receipt path |
|---|---|---|---|---|---|---|
| TEST-AG-01 | AC-01 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `baseline-aware gate does not block unchanged pre-existing React method debt` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-EVIDENCE-TEST-AG-01.json` |
| TEST-AG-02 | AC-02 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `baseline-aware gate blocks a newly introduced over-threshold method` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-EVIDENCE-TEST-AG-02.json` |
| TEST-AG-03 | AC-03 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `baseline-aware gate blocks a worsened pre-existing method beyond the allowed delta` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-EVIDENCE-TEST-AG-03.json` |
| TEST-AG-04 | AC-04 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `baseline-aware gate still blocks over-long Java methods` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-EVIDENCE-TEST-AG-04.json` |
| TEST-AG-05 | AC-05 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `architecture gate keeps strict behavior when no baseline is supplied` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-EVIDENCE-TEST-AG-05.json` |
| TEST-AG-06 | AC-06 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `architecture report binds candidate and baseline identity and the full governance suite passes` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-EVIDENCE-TEST-AG-06.json` |
| TEST-AG-07 | AC-01, AC-02 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `baseline-aware gate treats a missing baseline file as introduced not silently dropped` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-EVIDENCE-TEST-AG-07.json` |

Selector rule (binding on the implementer): each selector string above is the verbatim first argument to
`test("…", …)` in `scripts/tests/ai-governance.test.mjs`. The evidence runner matches selectors as substrings
of the `node --test` output, so the implementer must use each string verbatim.

## Architecture And Quality Gates

- Required architecture review: `YES` (the detector change is itself architecture-relevant).
- Triggered thresholds: this task's own candidate must not introduce new detector errors. The detector is in
  `scripts/`, so its own file is not analyzed by `analyzeSource` unless explicitly listed; the task runs the
  architecture gate on its candidate to confirm no production-source regressions (none expected, scripts-only).
- Required layers/boundaries: none changed.
- Responsibility-map evidence: classification fields make pre-existing vs blocking auditable per error.
- ADR exception and expiry: none.

## Role Assignments

- Test designer: fresh `qta-test-designer` (challenges ACs/test inventory before freeze).
- Implementer: fresh `qta-implementer` per slice (SLICE-DET then SLICE-IDX; backend/governance tooling).
- Code reviewer: fresh `qta-code-reviewer` (functional + architecture on frozen candidate).
- Final verifier: fresh `qta-final-verifier` (disposable worktree, machine receipts).
- Omitted roles and justification: none (full L2 four-role lifecycle).

## Candidate And Git Policy

- Git automation: `COMMIT` (local stage commits on the existing task branch; no push).
- User authorization evidence: task input explicitly authorizes local commits for the governance fix and D2
  restoration, and explicitly forbids push/merge/deploy.
- Task branch: `codex/security-directory-d2-20260802` (reuse; this is a governance sub-task of the D2 effort).
- Contract commit: `contract(arch-gate): freeze baseline-aware detector governance task`.
- Candidate mode: `COMMIT`.
- Candidate commit: (set at freeze).
- Candidate tree hash: (set at freeze).
- Patch SHA-256: (set at freeze).
- Candidate manifest path/hash: n/a (COMMIT mode).
- Checkpoint push allowed: `NO`.
- Delivery push target: none.
- Protected/default branch direct push: `NO`.

## Checkpoint Policy

- Context budget: `UNAVAILABLE` + null (no reliable runtime telemetry); enforce deterministic limits.
- Persist discoveries at: 25%.
- Stop opening stages at: 40%.
- Mandatory fresh-context handoff at: 60% or first compaction.
- Maximum waits per role run: 2.
- Maximum shell polls per command: 3.
- Automatic compaction policy: first compaction forces handoff; second is prohibited.
- Maximum repair rounds for one failure fingerprint: 2.
- Lane AC cap: 8 (this task uses 6).
- Blocking amendment cap: 3.
- Blocking amendment history: (none yet).
- Stop conditions: `DELIVERY_READY` (`node scripts/check-ai-delivery-ready.mjs <CONTROL>` exit 0) or `BLOCKED`
  with evidence; never ask the user.
