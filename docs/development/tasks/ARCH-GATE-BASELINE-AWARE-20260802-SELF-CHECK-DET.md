# Self-Check Artifact: ARCH-GATE-BASELINE-AWARE-20260802 / SLICE-DET

- Status: `SELF_CHECKED`
- Slice ID: `SLICE-DET`
- Dispatch ID: `dispatch-imp-agba-det-1`
- Role instance policy: `FRESH_ONLY`
- Lane: `L2`
- Assigned AC IDs: AC-01, AC-02, AC-03
- Contract hash: `79ec00e82eb4a797fa3767b34f9567ddd56b45507d531d121c36caa6769af148`
- Baseline commit: `979b080` (branch `codex/security-directory-d2-20260802`)
- Repair round: 0
- Role started-at: `2026-08-02T09:25:00Z` (approx; role session start)
- Role finished-at: `2026-08-02T09:38:42Z`
- Executor type: `SUBAGENT`
- Enforcement level: `ADVISORY` (self-tests are SELF_CHECKED only; not independent acceptance)
- Runtime receipt path: created by the workspace hook for this dispatch session
- Wait calls: 0
- Shell polls for one command: at most 2 (focused test runs)
- Context/compaction status: no compaction occurred; well under threshold

## Files changed

1. `scripts/check-ai-architecture.mjs` — added baseline-aware classification, `--baseline`
   CLI option, baseline report lookup, and the baseline-aware JSON report shape; strict
   no-baseline behavior is byte-for-byte preserved.
2. `scripts/tests/ai-governance.test.mjs` — added tests TEST-AG-01, TEST-AG-02, TEST-AG-03
   using the verbatim selectors from the Frozen Test Inventory.

## Behavioral summary

### New module-level constant
- `ALLOWED_WORSEN_DELTA = 20` (significant lines / method-line count). A quantifiable
  candidate metric that exceeds the baseline metric by strictly more than this delta is
  classified `worsened` (blocking); a delta `<= 20` with the rule firing on both sides is
  `pre-existing` (non-blocking).

### New helpers (added before `gitOutput`)
- `errorRuleKind(message)` maps a detector error string to its rule kind:
  `longest-method`, `class-module`, `controller-cross`, `service-file-persist`,
  `sql-outside-mapper`. Returns `null` for unknown messages.
- `errorMetricForKind(message)` parses the leading integer metric from the two
  quantifiable templates (`longest method <N> lines > 100` → `N`; `class/module <lines>
  lines, ...` → `<lines>`). Returns `null` for binary rules.
- `classifyError(message, baselineReport)` implements the exact predicate from the
  contract:
  - `baselineReport === null` (file absent from `--baseline` dir) → `introduced`
    (anti-masking; a missing baseline file is never silently dropped).
  - Rule did not fire on the baseline file (no baseline error of the same kind) →
    `introduced` (band-crossing; delta-independent; always blocking).
  - Rule fired on both:
    - quantifiable: `delta = candidateMetric - baselineMetric`; `delta > ALLOWED_WORSEN_DELTA`
      → `worsened` (blocking); else → `pre-existing` (non-blocking).
    - binary rule: → `pre-existing` (non-blocking).
  Each verdict returns `{ classification, candidateMetric, baselineMetric, delta }`.
- `baselineReportFor(file, baselineDir)` resolves the candidate file to its repo-relative
  path and reads `<baselineDir>/<repo-relative-path>`. If the file is missing from the
  baseline dir it returns `null` (→ all candidate errors for that file are `introduced`).
  The repo-relative path is computed with `fs.realpathSync` on both the candidate file
  and `process.cwd()` so symlinked temp dirs (e.g. macOS `/tmp` ↔ `/private/tmp`) do not
  cause the baseline lookup to fall back onto the candidate file.

### CLI / main changes
- New `--baseline <dir>` option. `baselineDir = path.resolve(argv)`.
- In baseline-aware mode, each candidate file×rule error is classified; introduced and
  worsened (plus candidate-level errors like the >1500/>3000 review-count rules, which
  are ALWAYS blocking and never classified against baseline) populate the top-level
  `errors[]`; pre-existing errors appear ONLY in `preExistingErrors[]`.
- Report coherence invariant (BA-3): `errorCount == errors.length == blockingErrorCount`
  and `status == (errorCount === 0 ? "PASS" : "FAIL")` and
  `exitCode == (errorCount === 0 ? 0 : 1)`. Thus `errorCount == 0 ⟺ PASS ⟺ exitCode 0`.
- Added report fields (baseline-aware mode only): `baselineIdentity` (the verbatim
  `--baseline` argument), `blockingErrorCount`, `introducedErrors[]`, `worsenedErrors[]`,
  `preExistingErrors[]`. Each detail carries `id`, `file`, `message`, `classification`,
  `candidateMetric`, `baselineMetric`, `delta`.
- Console output: in baseline-aware mode prints `BASELINE-INTRODUCED`, `BASELINE-WORSENED`,
  and `BASELINE-PRE-EXISTING <file>: <message>` lines, keeps existing `ERROR`/`WARN`
  lines, keeps the existing summary line, and adds
  `Architecture gate (baseline-aware): introduced=<n> worsened=<n> pre-existing=<n> blocking=<n>`.
- Exit code: baseline-aware → `process.exit(1)` iff `blockingErrorCount > 0`.

### No-baseline path (backward compatibility, AC-05 reserved for SLICE-IDX)
When `--baseline` is absent, EVERYTHING is unchanged: same console output, same
`errors[]`/`errorCount`/`status`/`exitCode`, and the report has none of the
`baselineIdentity`/`introducedErrors`/`worsenedErrors`/`preExistingErrors`/`blockingErrorCount`
fields. The only refactor in the no-baseline branch is that `errorCount` is now computed
as `errors.length` instead of `reports.reduce(sum, report.errors) + candidateErrors.length`;
these are algebraically equal because `errorDetails` already accumulates every report
error plus every candidate error. The existing SNAPSHOT-manifest test (which exercises
candidateErrors) passes unchanged, confirming equivalence.

## Exact diff applied to scripts/check-ai-architecture.mjs (added/changed functions)

```diff
@@ -1,6 +1,7 @@
 #!/usr/bin/env node

 import { createHash } from "node:crypto";
+import fs from "node:fs";
 import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
 import { execFileSync } from "node:child_process";
 import path from "node:path";
@@ -147,6 +148,68 @@ export function analyzeSource(file, content) {
   };
 }

+const ALLOWED_WORSEN_DELTA = 20;
+
+function errorRuleKind(message) {
+  if (message.startsWith("longest method ")) return "longest-method";
+  if (message.startsWith("class/module ")) return "class-module";
+  if (message === "controller crosses transaction/persistence boundary") return "controller-cross";
+  if (message === "service combines file/protocol parsing with persistence") return "service-file-persist";
+  if (message === "SQL appears outside mapper/persistence boundary") return "sql-outside-mapper";
+  return null;
+}
+
+function errorMetricForKind(message) {
+  if (message.startsWith("longest method ")) {
+    const match = message.match(/^longest method (\d+) lines/);
+    return match ? Number.parseInt(match[1], 10) : null;
+  }
+  if (message.startsWith("class/module ")) {
+    const match = message.match(/^class\/module (\d+) lines/);
+    return match ? Number.parseInt(match[1], 10) : null;
+  }
+  return null;
+}
+
+function classifyError(message, baselineReport) {
+  const kind = errorRuleKind(message);
+  const candidateMetric = errorMetricForKind(message);
+  if (!baselineReport) {
+    return { classification: "introduced", candidateMetric, baselineMetric: null, delta: null };
+  }
+  const baselineMatch = baselineReport.errors.find((entry) => errorRuleKind(entry) === kind);
+  if (!baselineMatch) {
+    return { classification: "introduced", candidateMetric, baselineMetric: null, delta: null };
+  }
+  const baselineMetric = errorMetricForKind(baselineMatch);
+  if (candidateMetric !== null && baselineMetric !== null) {
+    const delta = candidateMetric - baselineMetric;
+    if (delta > ALLOWED_WORSEN_DELTA) {
+      return { classification: "worsened", candidateMetric, baselineMetric, delta };
+    }
+    return { classification: "pre-existing", candidateMetric, baselineMetric, delta };
+  }
+  return { classification: "pre-existing", candidateMetric: null, baselineMetric: null, delta: null };
+}
+
+async function baselineReportFor(file, baselineDir) {
+  let relativeFile = file;
+  if (path.isAbsolute(file)) {
+    const candidateRoot = fs.realpathSync(process.cwd());
+    const resolvedFile = fs.realpathSync(file);
+    const fromResolved = path.relative(candidateRoot, resolvedFile);
+    relativeFile = fromResolved && !fromResolved.startsWith("..") ? fromResolved : path.relative(process.cwd(), file);
+  }
+  const baselinePath = path.join(baselineDir, relativeFile);
+  try {
+    const content = await readFile(baselinePath, "utf8");
+    return analyzeSource(file, content);
+  } catch (error) {
+    if (error.code === "ENOENT") return null;
+    throw error;
+  }
+}
+
 function gitOutput(args, options = {}) {
   return execFileSync("git", args, { encoding: "utf8", ...options });
 }
```

(`main` is changed as described in Behavioral summary: parse `--baseline`, classify
per-file×per-rule, build baseline-aware report, preserve strict no-baseline path.)

## Tests added (verbatim selectors)

- `baseline-aware gate does not block unchanged pre-existing React method debt` (TEST-AG-01, AC-01)
- `baseline-aware gate blocks a newly introduced over-threshold method` (TEST-AG-02, AC-02)
- `baseline-aware gate blocks a worsened pre-existing method beyond the allowed delta` (TEST-AG-03, AC-03)

Fixture pattern: `export function Comp() {` + N `const aN = 0;` body lines + `}`. The
detector's brace counter reports `longestMethod = N + 2`. TEST-AG-01's candidate also
appends 5 `useSecuritySelector(...)` integration lines + `return null;` so the longest
method grows by only a few lines (well within delta 20) while baseline already fires.

Note on TEST-AG-03 Case B (delta-equal-boundary): the task packet's literal numbers
"baseline longest=100, candidate longest=120" are inconsistent with the detector's strict
`longest > 100` predicate, because longest=100 does NOT fire an error and so could not be
"pre-existing". To honor the authoritative contract predicate (`delta > ALLOWED_WORSEN_DELTA
→ worsened; else pre-existing`, with both sides firing), Case B uses baseline longest=110
(n=108, fires) and candidate longest=130 (n=128, fires), delta=20 which is NOT `> 20`, so
the classification is `pre-existing` and the gate passes. This exercises the exact
boundary the contract specifies (delta == default ⇒ pre-existing).

## Required gates

| Gate | Command | Result |
|---|---|---|
| focused | `node --test --test-name-pattern="baseline-aware" scripts/tests/ai-governance.test.mjs` | exit 0; tests 3, pass 3, fail 0 |
| focused no-baseline regression | `node --test scripts/tests/ai-governance.test.mjs` | exit 0; tests 41, pass 41, fail 0 |
| governance suite | `node scripts/run-ai-governance-gates.mjs` | exit 0; "QTA AI governance gates passed." |

## Changed-path manifest

- `scripts/check-ai-architecture.mjs` (modified; +128 / -3 lines per `git diff --stat`)
- `scripts/tests/ai-governance.test.mjs` (modified; +172 / -0 lines per `git diff --stat`)
- `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-SELF-CHECK-DET.md` (new; this artifact)

Production-line delta in the detector is 131 added lines — within the SLICE-DET cap of 450.

## Remaining risks / blockers

- None blocking SELF_CHECKED. The detector change is scripts-only; no runtime/deployment
  dimensions apply (governance tooling). RUNTIME/DEPLOYMENT are NOT_REQUIRED per the
  contract Verification Plan and are not exercised here.
- The TEST-AG-03 Case B fixture numbers deviate from the packet's literal "100/120"
  because those numbers are incompatible with the detector's strict `> 100` predicate;
  the contract predicate is authoritative and the chosen 110/130 (delta 20) exercises the
  exact boundary. Flagged for the reviewer.
- AC-04, AC-05, AC-06 and TEST-AG-04/05/06/07 belong to SLICE-IDX and are intentionally
  not implemented in this slice.

## Proposed commit message for the parent

```
feat(governance): baseline-aware architecture gate classification (SLICE-DET)

Add --baseline <dir> to scripts/check-ai-architecture.mjs. When supplied, each
candidate file x rule error is classified introduced / worsened / pre-existing
against the same file's baseline analyzeSource report; exit code is decided
solely by blockingErrorCount (introduced + worsened + candidate errors). The
JSON report gains baselineIdentity, blockingErrorCount, introducedErrors,
worsenedErrors, preExistingErrors and keeps errors[] == blocking only
(report coherence invariant). Strict no-baseline behavior is byte-for-byte
unchanged. Adds TEST-AG-01..03 (AC-01..03).
```

## Candidate handoff

Candidate ready for `qta-code-reviewer` (functional + architecture on the frozen
candidate). Final verification is NOT dispatched until review is clear.
