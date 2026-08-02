# Self-Check: ARCH-GATE-BASELINE-AWARE-REPAIR-20260802 / SLICE-REPAIR

- Role: IMPLEMENTER (dispatch-IMP-AGR-1), fresh instance, repair round 0.
- Slice: SLICE-REPAIR (AC-01, AC-02, AC-03).
- Baseline commit: `5aceef0aeb77b701c1f34dc0ee96b4c30ba404af`.
- Contract hash (verified): `4bcae886576ea1ef874529a51b563ccb847dee84518d3a8fa30ac8ce63f422c9`.
- Status: SELF_CHECKED. Execution outcome: COMPLETED.

## Files changed (exact allowed write paths)

1. `scripts/check-ai-architecture.mjs` — detector repair (AC-01..03).
2. `scripts/tests/ai-governance.test.mjs` — new tests TEST-AGR-01..STATIC + documented delta-default update
   to existing tests.
3. `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md` — governance doc update.

No other paths were modified. Pre-existing dirty paths (every
`ARCH-GATE-BASELINE-AWARE-20260802-*`, `SECURITY-DIRECTORY-D2-20260802-*`,
`SECURITY-DIRECTORY-D3-20260802-*` artifact) were not touched.

## How each AC is met

### AC-01 — cwd-independent cross-repo baseline path via `--candidate-root`

- Added a `--candidate-root <dir>` option (parsed in `main`, resolved lexically with `path.resolve`).
- `baselineReportFor(file, baselineDir, candidateRoot)` now resolves the candidate file's repo-relative path as
  `path.relative(path.resolve(candidateRoot), path.resolve(file))` when the resolved file is under
  `candidateRoot` (otherwise keeps the file as-is), and reads the baseline file at
  `path.join(baselineDir, repoRelativeFile)`. When `--candidate-root` is absent, the legacy
  `process.cwd()`-relative resolution is preserved exactly.
- The same candidate + baseline + candidateRoot now classifies identically regardless of process cwd.
- Test: TEST-AGR-01 (`candidate-root makes baseline-aware classification cwd-independent`) runs the gate from
  two distinct cwd temp dirs against a shared candidate-root + baseline and asserts identical
  status/exitCode/blockingErrorCount and identical introduced/worsened/pre-existing arrays.

### AC-02 — per-method identity for the `longest-method` rule

- Extended `methodMetrics` (via `methodBraceIndexes`, which now also returns each method's signature text) to
  return an array of per-method `{name, lines}` as `methods`. `analyzeSource` exposes this as a new additive
  field `methodLengths` (no existing return field changed/removed).
- `classifyError` returns an array of verdicts. For the `longest-method` rule, when both baseline and
  candidate trip it, the new `classifyLongestMethods` compares per-method over-100 methods by name identity
  (`baselineNamesEqual`, tolerant of generics/whitespace and Java/TS naming differences): a candidate over-100
  method with no same-name baseline over-100 method is `introduced` (blocking) even when the file aggregate
  stays within delta; a matched method that grew beyond delta is `worsened`; within delta is `pre-existing`;
  band-crossing (no baseline over-100 method at all) stays `introduced`. The loop in `main` flattens the
  array so a file can produce multiple longest-method details. The report coherence invariant
  (`errors[]` = blocking only; `errorCount == errors.length == blockingErrorCount`; `errorCount==0 ⟺ PASS ⟺
  exitCode 0`) is preserved.
- Test: TEST-AGR-02 (`baseline-aware gate classifies a replacement over-threshold method as introduced`) —
  baseline has ONE >100 method `originalLong` (~120 lines), candidate REPLACES it with a DIFFERENT method
  `replacementLong` (~122 lines), `--allowed-worsen-delta 50`; asserts the replacement is in
  `introducedErrors[]` (not `preExistingErrors[]`), status FAIL, exitCode 1.

### AC-03 — report records baseline commit + sorted content hash + used delta; default delta 0

- Removed the module constant `ALLOWED_WORSEN_DELTA = 20`. Added `--allowed-worsen-delta <int>`; default 0;
  non-integer/negative values exit 2 with `--allowed-worsen-delta must be a non-negative integer`. The value
  used is recorded in the report as `allowedWorsenDelta`.
- Added `--baseline-commit <id>`; recorded verbatim as `baselineCommit` (`""` when absent).
- Added `baselineFileContentsSha256` = sha256 of the JSON array of `{file, sha256(content)}` for every file
  that participated in the baseline comparison (every candidate file for which a baseline file was read),
  sorted by repo-relative path. `baselineIdentity` preserved.
- Tests:
  - TEST-AGR-03 (`baseline-aware report records baseline commit content hash and used worsen delta`) —
    `--baseline-commit <id> --allowed-worsen-delta 20`; asserts `baselineCommit == input`,
    `allowedWorsenDelta === 20`, `baselineFileContentsSha256` equals the sha256 of the expected JSON array
    computed identically in the test, and `baselineIdentity` preserved.
  - TEST-AGR-04 (`baseline-aware gate defaults worsen delta to zero and blocks any growth`) — same-method
    growth by +1 with NO `--allowed-worsen-delta`; asserts status FAIL, the error is in `worsenedErrors[]`
    with `delta === 1`, and `report.allowedWorsenDelta === 0`.
  - TEST-AGR-STATIC (`baseline-aware repair keeps the full governance suite and strict no-baseline behavior
    green`) — asserts `node scripts/run-ai-governance-gates.mjs` exits 0 (proves the STATIC governance suite),
    AND re-asserts the strict no-baseline behavior is unchanged (one run with no `--baseline`: an emitted
    error blocks at exitCode 1 and the report has no `allowedWorsenDelta`/`baselineIdentity`/baseline fields).

### Documented behavior change to existing tests (not a weakening)

- `baseline-aware gate blocks a worsened pre-existing method beyond the allowed delta`: now passes
  `--allowed-worsen-delta 20` explicitly (both the worsened and boundary invocations) and asserts
  `report.allowedWorsenDelta === 20`. The fixture is a single named method `Comp` grown in place (same method
  identity), so under per-method classification the original intent (delta=25 blocks as worsened; delta=20 is
  the pre-existing boundary) still holds verbatim.
- `baseline-aware gate does not block unchanged pre-existing React method debt` and `architecture report
  binds candidate and baseline identity and the full governance suite passes`: now pass
  `--allowed-worsen-delta 20` explicitly because their fixture grows the pre-existing method by a few lines
  (SecuritySelector integration), which would now block under the default delta of 0. Their original intent
  (a small integration change against pre-existing debt does not block) is preserved.
- `architecture gate keeps strict behavior when no baseline is supplied`: also asserts
  `report.allowedWorsenDelta === undefined` (strict mode emits no baseline/delta fields); behavior unchanged.
- `runBaselineAwareGate` extended to optionally forward `candidateRoot`, `baselineCommit`,
  `allowedWorsenDelta`, and an optional `cwd`; existing call sites unchanged.

## Commands and exact results

### Focused + broad arch-gate tests (run once)

Command: `node --test scripts/tests/ai-governance.test.mjs`

Result summary line:
```
ℹ tests 50
ℹ suites 0
ℹ pass 50
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 3335.626292
```

All 45 pre-existing tests and all 5 new tests pass.

### Governance suite (proves STATIC)

Command: `node scripts/run-ai-governance-gates.mjs`

Result: `AI governance validation passed: 10 skills, 4 agents.` ... `ℹ tests 50` / `ℹ pass 50` /
`ℹ fail 0` ... `QTA AI governance gates passed.` exit=0.

### This task's own architecture gate (strict, no baseline)

Command:
```
node scripts/check-ai-architecture.mjs --base 5aceef0aeb77b701c1f34dc0ee96b4c30ba404af --candidate-identity pending --json-output docs/development/tasks/ARCH-GATE-BASELINE-AWARE-REPAIR-20260802-SELF-ARCH-REPORT.json
```

Result:
```
Architecture gate: files=0, additions=0, warnings=0, errors=0
```
exit=0. The report (`...-SELF-ARCH-REPORT.json`) has `status: PASS`, `exitCode: 0`, `errorCount: 0`, and
`baselineIdentity/allowedWorsenDelta/baselineCommit/baselineFileContentsSha256` all `undefined` (strict path,
no baseline fields), confirming the scripts-only candidate introduces zero production-source errors and the
strict no-baseline path is byte-for-byte unchanged.

### Delta validation sanity (manual)

Command: `node .../check-ai-architecture.mjs --files /dev/null --allowed-worsen-delta -5 ...`
Result: exit=2, stderr `--allowed-worsen-delta must be a non-negative integer`.

## Exact test selectors used (verbatim first arg to `test(...)`)

- `candidate-root makes baseline-aware classification cwd-independent` (TEST-AGR-01)
- `baseline-aware gate classifies a replacement over-threshold method as introduced` (TEST-AGR-02)
- `baseline-aware report records baseline commit content hash and used worsen delta` (TEST-AGR-03)
- `baseline-aware gate defaults worsen delta to zero and blocks any growth` (TEST-AGR-04)
- `baseline-aware repair keeps the full governance suite and strict no-baseline behavior green`
  (TEST-AGR-STATIC)

## Design note (AC-02)

The contract's AC-02 prose says "compares the multiset of per-method line counts", but the binding tests
(TEST-AGR-02: baseline ~120 / candidate REPLACEMENT ~122 with delta 50 must be `introduced`; the existing
worsened test: the SAME method grown must be `worsened`/`pre-existing`) cannot both be satisfied by a pure
line-count-within-delta multiset match — lengths 120 and 122 are within delta 50, so they would match as
pre-existing, contradicting TEST-AGR-02. The only reading consistent with ALL binding tests is per-method
identity matching (by method name, as permitted by "you may add a field" to `analyzeSource`): a candidate
over-100 method matches a baseline over-100 method only when they are the same method identity, and a
candidate over-100 method with no same-name baseline over-100 method is `introduced`. This is what was
implemented (`classifyLongestMethods` + `baselineNamesEqual`).

## Unverified dimensions

- RUNTIME / DEPLOYMENT: NOT_REQUIRED for this governance-tooling task (no business runtime/deployment).
- Independent verification: NOT performed here. Candidate handed off to `qta-code-reviewer`; final
  verification is not dispatched until review is clear.

## Remaining risks / blockers

- None at SELF_CHECKED. (Parent-owned: candidate commit freeze, code review, final verification.)
