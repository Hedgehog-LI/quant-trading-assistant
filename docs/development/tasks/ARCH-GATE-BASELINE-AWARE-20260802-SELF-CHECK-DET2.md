# Self-Check Artifact — ARCH-GATE-BASELINE-AWARE-20260802 / SLICE-DET (fix continuation)

- Task: ARCH-GATE-BASELINE-AWARE-20260802 (baseline/diff-aware architecture gate)
- Slice: SLICE-DET (continuation/fix within generation 1; prior candidate never frozen/reviewed)
- Dispatch ID: dispatch-imp-agba-det-2
- Role instance policy: FRESH_ONLY (executor type SUBAGENT; no parent substitution)
- Role start time: 2026-08-02 (recorded at dispatch)
- Repair round: 0 (continuation/fix, not a repair round; SLICE-DET candidate was not yet frozen)
- Contract path: `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-CONTRACT.md`
- Contract hash (verified byte-for-byte): `79ec00e82eb4a797fa3767b34f9567ddd56b45507d531d121c36caa6769af148`
- Baseline commit (verified): `979b080f6ccbcf9e2af2b435f360735e130cbff8`
- Assigned AC IDs (unblocked by this fix): AC-01, AC-02, AC-03, AC-05, AC-06
- Allowed write paths (only): `scripts/check-ai-architecture.mjs`, this self-check artifact
- Candidate mode: COMMIT (no Git operations performed; parent owns Git)
- Status: SELF_CHECKED

## The concrete finding (from SLICE-IDX self-check)

`scripts/check-ai-architecture.mjs` computes the local variable `errorCount` (used for console
output, `status`, and `exitCode`) but the JSON `payload` object literal written to `--json-output`
did NOT serialize `errorCount` as a field. Consumers reading the report JSON could not observe
`errorCount`, breaking two acceptance assertions the contract requires verbatim:

- AC-05 / TEST-AG-05 (line 1093): `assert.equal(report.errors.length, report.errorCount)` in the
  no-baseline path.
- AC-06 / TEST-AG-06 (line 1130): `assert.equal(report.errorCount, 0)` for a baseline-aware run
  whose only errors are pre-existing.

## Reproduction of the finding (before the fix)

`node --test --test-name-pattern="baseline-aware|strict behavior|architecture report binds" ...`
produced exactly two failures before the fix:

- TEST-AG-05: `Expected values to be strictly equal: 1 !== undefined`
  (`report.errorCount` was `undefined`; `report.errors.length` was 1).
- TEST-AG-06: `Expected values to be strictly equal: undefined !== 0`
  (`report.errorCount` was `undefined`; expected 0).

This confirmed the exact, single missing field: `errorCount` was never serialized into `payload`.

## Surgical fix

The ONLY behavioral change is that the report JSON now includes a top-level `errorCount` field set
to the already-computed local `errorCount`. This applies to BOTH the baseline-aware path and the
no-baseline path (they share the same `payload` object literal). No classification logic, predicate,
console-output semantics, or exit-code logic was changed; no other fields were added or removed.

### Before / after of the `payload` object literal

Before (single shared literal, around line 408 in the pre-fix file):

```js
const payload = {
  schemaVersion: 1,
  generatedBy: "scripts/check-ai-architecture.mjs",
  generatedAt: new Date().toISOString(),
  candidateIdentity,
  base,
  manifestPath: manifestIndex >= 0 ? process.argv[manifestIndex + 1] : "",
  architectureReviewCount,
  files: reports,
  additions,
  warnings,
  errors,
  status: errorCount === 0 ? "PASS" : "FAIL",
  exitCode: errorCount === 0 ? 0 : 1
};
```

After (one line added: `errorCount,` adjacent to `errors`/`warnings`):

```js
const payload = {
  schemaVersion: 1,
  generatedBy: "scripts/check-ai-architecture.mjs",
  generatedAt: new Date().toISOString(),
  candidateIdentity,
  base,
  manifestPath: manifestIndex >= 0 ? process.argv[manifestIndex + 1] : "",
  architectureReviewCount,
  files: reports,
  additions,
  warnings,
  errorCount,
  errors,
  status: errorCount === 0 ? "PASS" : "FAIL",
  exitCode: errorCount === 0 ? 0 : 1
};
```

The baseline-aware branch still appends `baselineIdentity`, `blockingErrorCount`,
`introducedErrors`, `worsenedErrors`, `preExistingErrors` to the same payload (unchanged).

### Field-set confirmation (unchanged fields)

The pre-existing report fields are all still present and unchanged:
`schemaVersion`, `generatedBy`, `generatedAt`, `candidateIdentity`, `base`, `manifestPath`,
`architectureReviewCount`, `files`, `additions`, `warnings`, `errors`, `status`, `exitCode` — and,
in baseline-aware mode only: `baselineIdentity`, `blockingErrorCount`, `introducedErrors`,
`worsenedErrors`, `preExistingErrors`. The ONLY field added is `errorCount`.

### Diff scope note

`git diff scripts/check-ai-architecture.mjs` shows the full SLICE-DET working-tree change vs the
`979b080` baseline, because the SLICE-DET implementation was already present in the working tree
when this dispatch began (continuation within generation 1; candidate never frozen). The change
made by THIS dispatch is exactly the single line `+      errorCount,` in the `payload` literal
hunk. Everything else in the diff is the pre-existing SLICE-DET implementation, which is in-scope
for the contract (SLICE-DET allowed write path) and outside this dispatch's modification set.

## Required gates

| Gate | Command | Exit code | Result |
|---|---|---:|---|
| focused baseline-aware | `node --test --test-name-pattern="baseline-aware" scripts/tests/ai-governance.test.mjs` | 0 | pass 5 / fail 0 (TEST-AG-01, 02, 03, 04, 07) |
| full regression | `node --test scripts/tests/ai-governance.test.mjs` | 0 | pass 45 / fail 0 (TEST-AG-05/06 now pass; TEST-AG-04/07 already passed) |
| governance suite | `node scripts/run-ai-governance-gates.mjs` | 0 | "QTA AI governance gates passed." (45/45 tests pass) |

### Confirmation TEST-AG-05 / TEST-AG-06 now pass

- TEST-AG-05 (`architecture gate keeps strict behavior when no baseline is supplied`):
  PASS. `report.errors.length === report.errorCount` (1 === 1); no-baseline fields
  (`baselineIdentity`, `blockingErrorCount`, `introducedErrors`, `worsenedErrors`,
  `preExistingErrors`) all `undefined` as required.
- TEST-AG-06 (`architecture report binds candidate and baseline identity and the full governance
  suite passes`): PASS. `report.errorCount === 0`; `report.status === "PASS"`;
  `report.exitCode === 0`; `report.candidateIdentity === "cand-X"`;
  `report.baselineIdentity === baselineAbs`; spawned `run-ai-governance-gates.mjs` exits 0.

## Self-test standard disclosure

Testing done here is SELF_CHECKED only (not independently verified). Exact commands and exit codes
are recorded above. RUNTIME and DEPLOYMENT dimensions are NOT_REQUIRED for this governance-tooling
task per the contract's Verification Plan, and were not exercised.

## AC → evidence map

- AC-01 (TEST-AG-01): pass — baseline-aware gate does not block unchanged pre-existing debt.
- AC-02 (TEST-AG-02): pass — newly introduced over-threshold method blocks as `introduced`.
- AC-03 (TEST-AG-03): pass — worsened beyond delta blocks; within-delta is pre-existing.
- AC-05 (TEST-AG-05): pass — strict no-baseline behavior; `errorCount == errors.length` (this fix).
- AC-06 (TEST-AG-06): pass — identity binding, self-coherent report, suite exit 0 (this fix unblocks
  the `errorCount === 0` assertion).
- (AC-04 / TEST-AG-04 and AC-01,02 / TEST-AG-07 also pass; not the focus of this fix dispatch but
  share the same detector + report.)

## Remaining risks / blockers

- None at the implementer level. The candidate is SELF_CHECKED with all gates green.
- The candidate has NOT been frozen, committed, or pushed (parent owns Git). Candidate handoff
  proceeds to `qta-code-reviewer`; final verification is not dispatched until review is clear.
