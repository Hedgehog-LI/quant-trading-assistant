# Task Contract: ARCH-GATE-BASELINE-AWARE-REPAIR-20260802 Architecture gate baseline-aware detector repair

## Contract Identity

- Status: `FROZEN`
- Contract version: 1
- Frozen at: 2026-08-02T15:05:00Z
- Frozen by parent run: codex-parent-arch-gate-repair-1
- Lane: `L0`
- Test designer: omitted by parent authorization under the L0 contract-lite path
  (orchestration §Parent State Machine: "L0 may omit test design/review through an explicit omission
  record"). This is a narrow governance-tooling repair with acceptance criteria frozen by the parent from
  concrete detector defects; there is no product-design ambiguity to challenge. See CONTROL
  `review.testDesignerOmissionReason` and `review.omissionReason`. The parent has frozen a verbatim test
  inventory below in lieu of a test-designer role run.

This is a governance/tooling task. It does **not** change any business code, the three large React page
files, or D2's frontend implementation. It repairs four concrete defects in the existing baseline-aware
mode of `scripts/check-ai-architecture.mjs` that the prior `ARCH-GATE-BASELINE-AWARE-20260802` task left
behind, and tightens the worsen-delta default so debt cannot drift upward across tasks.

## Objective

Repair the baseline-aware architecture detector so that:

1. **Cross-repo baseline path resolution is deterministic and cwd-independent.** An explicit
   `--candidate-root` maps candidate files to their repo-relative paths and the baseline directory is read
   at those same repo-relative paths. Running the gate from the backend cwd or the frontend cwd produces
   byte-identical classification for the same candidate + baseline.
2. **Method identity is preserved.** When a baseline file's offending long method is replaced by a
   brand-new long method, the new method is classified `introduced` (blocking), not `pre-existing`.
   Classification is no longer solely a file-level `longestMethod` aggregate comparison.
3. **The baseline report records the frozen baseline commit identity and the sorted content hash of the
   files that participated in the comparison.**
4. **The default allowed worsen delta is 0.** A non-zero delta is permitted only when the frozen task
   contract explicitly supplies `allowedWorsenDelta`; the report records the value actually used.

## Authority

- Architecture/governance authority:
  `.agents/skills/qta-development-orchestration/references/GOVERNANCE_V2_POLICY.md` §Architecture Gate
  ("A reviewer cannot waive a machine architecture error. If the detector itself is wrong, repair and
  validate the detector in a separate governed task, then regenerate the candidate-bound report.").
- Skill authority: `.agents/skills/qta-development-orchestration/SKILL.md` §Architecture Gate.
- Existing detector + tests: `scripts/check-ai-architecture.mjs`, `scripts/tests/ai-governance.test.mjs`.
- Existing governance doc: `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md` (updated by this task).
- Baseline commit (backend/governance): `5aceef0aeb77b701c1f34dc0ee96b4c30ba404af`
  (current `codex/security-directory-d2-20260802` HEAD; the prior `ARCH-GATE-BASELINE-AWARE-20260802`
  candidate commit).
- Baseline branch: `codex/security-directory-d2-20260802`.
- Pre-existing dirty paths (declared, not modified by this task):
  `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CANDIDATE.patch`,
  `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CONTROL.json`, and every
  `ARCH-GATE-BASELINE-AWARE-20260802-*` and `SECURITY-DIRECTORY-D2-20260802-*` task artifact already present
  on disk before this task's contract commit.
- Allowed write paths: `scripts/check-ai-architecture.mjs`, `scripts/tests/ai-governance.test.mjs`,
  `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md`, and
  `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-REPAIR-20260802-*.md|.json|.patch`. No other paths.

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | The prior detector's `baselineReportFor` resolves the baseline file by `fs.realpathSync(process.cwd())` + `path.relative(...)`. That only maps against the *process* cwd, so the same candidate+baseline classifies differently when the gate is run from the backend root versus the frontend root. For D2 the gate must run from both cwds and agree. |
| FACT | The prior `classifyError` compares only the per-file aggregate `longestMethod` metric for the `longest-method` rule. If a baseline file had a 199-line method (rule fires, baseline metric 199) and the candidate replaces it with a brand-new 210-line method that crosses no new aggregate band the baseline did not already cross, the candidate is labelled `pre-existing` because the aggregate metric 199→210 is within the default delta. The brand-new method is genuinely new debt and must be `introduced`. |
| FACT | The prior report's `baselineIdentity` is just the verbatim `--baseline` directory argument. It does not record the frozen baseline *commit* the baseline directory was extracted from, nor a content hash of the files actually compared, so a reviewer cannot audit which baseline sources produced the classification. |
| FACT | The prior `ALLOWED_WORSEN_DELTA = 20` is a process-wide constant with no opt-out. Every baseline-aware run silently tolerates up to +20 lines/method-lines of growth, so debt can drift upward across tasks without a contract decision. |
| DECISION | Add `--candidate-root <dir>`. When supplied, a candidate file path is normalized to its path relative to `candidateRoot` (resolved lexically against the *supplied* root, independent of process cwd); the baseline file is read at `<baselineDir>/<repo-relative-path>`. When `--candidate-root` is not supplied, behavior falls back to the current `process.cwd()`-relative resolution (backward compatible). The same candidate + baseline + candidateRoot must classify identically regardless of process cwd (AC-01). |
| DECISION | Method identity: extend `analyzeSource` so the `longest-method` rule, when both baseline and candidate trip it, compares the *set of per-method line counts* rather than only the file aggregate. A candidate long method whose line count has no counterpart (within `allowedWorsenDelta`) among the baseline file's long methods is `introduced`, even if the file aggregate stayed within delta. Concretely: compute the multiset of method lengths > threshold-to-classify for each side; the candidate's over-100 methods that cannot be matched to a baseline over-100 method within delta are `introduced`; matched pairs within delta are `pre-existing`; matched pairs exceeding delta are `worsened`. Band-crossing (baseline had no over-100 method, candidate does) remains `introduced`. This preserves the honesty rule (AC-02). |
| DECISION | Baseline report records `baselineCommit` (verbatim `--baseline-commit` arg, optional) and `baselineFileContentsSha256` (sha256 of the JSON-array of `{file, sha256(content)}` for every file that participated in the baseline comparison, sorted by file path). When `--baseline-commit` is absent, `baselineCommit` is `""` and the content hash still binds the compared sources. The existing `baselineIdentity` (the `--baseline` dir arg) is preserved for compatibility (AC-03). |
| DECISION | Worsen delta default is `0`. Add `--allowed-worsen-delta <int>`; only a non-negative integer is accepted. The report records `allowedWorsenDelta` (the value actually used). A run that does not pass the flag uses `0`, so any growth of an already-firing quantifiable metric is `worsened` (blocking) unless the contract explicitly authorizes a larger delta. The constant `ALLOWED_WORSEN_DELTA = 20` is removed (AC-04). |
| DECISION | The existing tests that hard-coded the default delta of 20 (`baseline-aware gate blocks a worsened pre-existing method beyond the allowed delta`, and the boundary/delta assertions inside it) are updated to pass `--allowed-worsen-delta 20` explicitly and to assert the reported `allowedWorsenDelta === 20`, preserving their original semantics under the new opt-in default. The strict no-baseline test (`architecture gate keeps strict behavior when no baseline is supplied`) is unchanged in behavior (no `allowedWorsenDelta` field emitted without `--baseline`). This is a documented behavior change to the default, not a weakening of any test's *intent*. |
| DECISION | This task does not modify the three React page files, any D2 frontend implementation, `check-ai-task-control.mjs`, `check-ai-delivery-ready.mjs`, `run-ai-evidence-command.mjs`, the schema, or any Java/MyBatis/Flyway code. It does not merge main, push, deploy, or start any business feature. |
| ASSUMPTION | The detector's existing brace/line accounting is accurate enough for per-method comparison; the repair reuses `methodBraceIndexes`/`methodMetrics` machinery for both baseline and candidate, so measurement bias cancels. |

## Scope

### In Scope

- `scripts/check-ai-architecture.mjs`: add `--candidate-root`, `--baseline-commit`, `--allowed-worsen-delta`;
  per-method longest-method classification; `baselineCommit` + `baselineFileContentsSha256` + used
  `allowedWorsenDelta` in the report; preserve strict behavior with no `--baseline`.
- `scripts/tests/ai-governance.test.mjs`: add the new tests below; update the delta-default tests to pass an
  explicit delta and assert the recorded value; keep all other existing tests passing.
- `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md`: document `--candidate-root`, the default-0 delta, the
  opt-in `--allowed-worsen-delta`, `--baseline-commit`, the new report fields, and the method-identity rule.

### Out Of Scope

- D2 frontend restoration (separate Phase 2 work that consumes this repaired gate).
- Refactoring the three large React pages.
- Modifying `check-ai-task-control.mjs`, `check-ai-delivery-ready.mjs`, `run-ai-evidence-command.mjs`, or
  the task-control JSON schema.
- Any backend Java business code, MyBatis, Flyway, schedulers, providers.

### Prohibited

- Editing the three React page files or any D2 frontend implementation.
- Any Git merge/rebase/push/deploy, any main-branch write, any force-push.
- Calling `AskUserQuestion`.
- Removing or weakening existing architecture tests beyond the documented delta-default update.
- Excluding file categories wholesale; classification remains per-error vs baseline.

## Acceptance Criteria

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | Cross-repo baseline path is cwd-independent with `--candidate-root`. | Same candidate file + same baseline dir + same `--candidate-root`; gate invoked once from backend cwd and once from frontend cwd (different process cwds). | Both runs produce identical `status`/`exitCode`/`blockingErrorCount` and identical classification of every error (`introduced`/`worsened`/`pre-existing`), and the baseline file is read at the repo-relative path under the baseline dir. | AUTOMATION | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-02 | A brand-new over-threshold method that replaces a baseline over-threshold method is `introduced` even when the file-level `longestMethod` aggregate stays within delta. | Candidate file where baseline had one >100 method (e.g. 120 lines) and candidate replaces it with a different >100 method (e.g. 122 lines) and adds nothing else; `--baseline` set, `--allowed-worsen-delta 50`. | `status=FAIL`, `exitCode=1`, `blockingErrorCount>=1`; the new method is in `introducedErrors[]` (not `preExistingErrors[]`); `errors[]`/`errorCount` contain only blocking. | AUTOMATION | AUTOMATION | IMPLEMENTER | NOT_STARTED |
| AC-03 | The baseline-aware report records the frozen baseline commit, the sorted content hash of compared files, and the actually-used `allowedWorsenDelta`; the default delta is 0 (growth with no explicit delta blocks as `worsened`); the full governance suite and strict no-baseline behavior stay green. | (a) Baseline-aware run with `--baseline`, `--baseline-commit <id>`, `--allowed-worsen-delta 20`, `--candidate-identity`, `--json-output`; (b) baseline-aware run that grows a firing metric by +1 with NO `--allowed-worsen-delta`; (c) `node scripts/run-ai-governance-gates.mjs`; (d) strict run with no `--baseline`. | (a) report has `baselineCommit == input`, `allowedWorsenDelta == 20`, `baselineFileContentsSha256` matching the sha256 of the JSON array of `{file,sha256(content)}` sorted by file for the files compared, `baselineIdentity` preserved; (b) `status=FAIL`, error in `worsenedErrors[]` with `delta=1`, report `allowedWorsenDelta === 0`; (c) suite exit 0; (d) any emitted error ⇒ `exitCode=1` and report has no baseline/`allowedWorsenDelta` fields. | AUTOMATION | AUTOMATION | IMPLEMENTER | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | `node scripts/run-ai-governance-gates.mjs` (runs `validate-ai-governance.mjs` + `node --test scripts/tests/ai-governance.test.mjs`) | exit 0; all existing + new arch-gate tests pass |
| AUTOMATION | Yes | `node --test scripts/tests/ai-governance.test.mjs` (focused) and the new tests create temp dirs and assert classification/exit codes/reported fields | all required test IDs pass via `scripts/run-ai-evidence-command.mjs` |
| RUNTIME | No | n/a (governance tooling, no runtime/deployment) | NOT_REQUIRED |
| DEPLOYMENT | No | n/a | NOT_REQUIRED |

## Implementation Slices

| Slice ID | Coherent boundary | AC IDs | Allowed write paths | Max files | Max production-line delta |
|---|---|---|---|---:|---:|
| SLICE-REPAIR | Repair baseline-aware detector: cwd-independent `--candidate-root`, per-method identity, baseline commit + content hash + used delta in report, default-0 delta; update/add tests; update governance doc. | AC-01, AC-02, AC-03 | `scripts/check-ai-architecture.mjs`, `scripts/tests/ai-governance.test.mjs`, `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md` | 3 | 450 |

Single slice. One fresh implementer instance.

## Frozen Test Inventory

| Test ID | AC IDs | Kind | Required | Source path | Exact selector | Receipt path |
|---|---|---|---|---|---|---|
| TEST-AGR-01 | AC-01 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `candidate-root makes baseline-aware classification cwd-independent` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-REPAIR-20260802-EVIDENCE-TEST-AGR-01.json` |
| TEST-AGR-02 | AC-02 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `baseline-aware gate classifies a replacement over-threshold method as introduced` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-REPAIR-20260802-EVIDENCE-TEST-AGR-02.json` |
| TEST-AGR-03 | AC-03 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `baseline-aware report records baseline commit content hash and used worsen delta` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-REPAIR-20260802-EVIDENCE-TEST-AGR-03.json` |
| TEST-AGR-04 | AC-03 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `baseline-aware gate defaults worsen delta to zero and blocks any growth` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-REPAIR-20260802-EVIDENCE-TEST-AGR-04.json` |
| TEST-AGR-STATIC | AC-03 | AUTOMATION | YES | `scripts/tests/ai-governance.test.mjs` | `baseline-aware repair keeps the full governance suite and strict no-baseline behavior green` | `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-REPAIR-20260802-EVIDENCE-TEST-AGR-STATIC.json` |

Selector rule (binding on the implementer): each selector string above is the verbatim first argument to
`test("…", …)` in `scripts/tests/ai-governance.test.mjs`. The evidence runner matches selectors as substrings
of the `node --test` output, so the implementer must use each string verbatim. TEST-AGR-STATIC's body asserts
the governance-suite spawnSync exit 0 (so its AUTOMATION receipt also proves the STATIC suite) and the
strict no-baseline unchanged behavior.

## Architecture And Quality Gates

- Required architecture review: `YES` (the detector change is itself architecture-relevant).
- Triggered thresholds: this task's own candidate must not introduce new detector errors. The detector is in
  `scripts/`, not analyzed by `analyzeSource` unless listed; the task runs the architecture gate on its
  candidate to confirm no production-source regressions (none expected, scripts-only). The architecture gate
  run for THIS task uses the strict path (no `--baseline`): a scripts-only candidate has zero production
  source errors, so it passes without baseline-aware mode.
- Required layers/boundaries: none changed.
- Responsibility-map evidence: per-method classification fields + recorded baseline content hash make
  introduced vs pre-existing auditable per method.
- ADR exception and expiry: none.

## Role Assignments

- Test designer: OMITTED under the L0 contract-lite path (parent-authorized; narrow governance-tooling
  repair with frozen concrete defects; the user instruction explicitly forbids re-dispatching a test
  designer; see CONTROL `review.testDesignerOmissionReason`). The parent has frozen a verbatim test
  inventory above in lieu of a test-designer role run.
- Implementer: fresh `qta-implementer` for SLICE-REPAIR (backend/governance tooling).
- Code reviewer: fresh `qta-code-reviewer` (functional + architecture on frozen candidate). Required by the
  user instruction (one independent code reviewer).
- Final verifier: fresh `qta-final-verifier` (disposable worktree, machine receipts).
- Omitted roles and justification: test-designer omitted under L0 contract-lite as above (recorded).
  Implementer, code-reviewer, and final-verifier are all present per the user instruction.

## Candidate And Git Policy

- Git automation: `COMMIT` (local stage commits on the existing task branch; no push).
- User authorization evidence: task input explicitly authorizes local commits for the governance repair and
  the Phase-2 D2 close-out, and explicitly forbids push/merge/deploy.
- Task branch: `codex/security-directory-d2-20260802` (reuse; this is a governance sub-task of the D2 effort).
- Contract commit: `contract(arch-gate-repair): freeze baseline-aware detector repair governance task`.
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
- Lane AC cap: 3 (this task uses 3).
- Blocking amendment cap: 1.
- Blocking amendment history: (none yet).
- Stop conditions: `DELIVERY_READY` (`node scripts/check-ai-delivery-ready.mjs <CONTROL>` exit 0) or `BLOCKED`
  with evidence; never ask the user.
