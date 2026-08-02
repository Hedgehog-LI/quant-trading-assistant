# Architecture Gate: Baseline-Aware Mode

## Purpose

`scripts/check-ai-architecture.mjs` can run in **baseline-aware** mode so that architecture debt a candidate
does **not** worsen is recorded as debt instead of a hard block, while newly introduced or materially
worsened violations still block. This lets cross-cutting tasks land small integration changes against files
that already carry pre-existing architecture debt, without masking new violations.

## When to use

Pass a baseline when the candidate touches files with **KNOWN, pre-existing** architecture debt (for example,
the three large React page files in the Security Directory work, whose `longest method > 100` errors pre-date
the candidate). Baseline-aware mode is the detector-repair route prescribed by
`GOVERNANCE_V2_POLICY.md` §Architecture Gate for cases where a machine architecture error is pre-existing
rather than introduced by the candidate.

Do **not** use baseline-aware mode to mask new violations. A brand-new offending method/component that crosses
a hard threshold the baseline did not cross always blocks, regardless of delta.

## How to use

Provide `--baseline <dir>`, where `<dir>` is a directory holding the baseline source files at their
repo-relative paths, together with the usual gate options:

```sh
node scripts/check-ai-architecture.mjs \
  --base <git-ref> \
  --files <candidate-files...> \
  --baseline <baseline-dir> \
  --candidate-root <candidate-repo-root> \
  --baseline-commit <frozen-baseline-commit> \
  --candidate-identity <frozen-candidate-id> \
  --json-output <report.json>
```

The baseline directory **must be the real frozen baseline**. Never omit files to hide debt: a candidate file
that is missing from the baseline directory is classified `introduced` (blocking) — a missing baseline file is
never silently dropped. Reuse `--base <git-ref>` for candidate-file selection and additions exactly as in the
strict path; the baseline directory only supplies baseline source for per-file comparison.

When no `--baseline` is supplied, the detector is byte-for-byte unchanged: every error blocks and the report
contains no baseline fields.

### `--candidate-root` (cwd-independent cross-repo baseline path)

By default a candidate file path is mapped to its repo-relative path using `process.cwd()`, so the same
candidate + baseline can classify differently when the gate is run from the backend repo root versus the
frontend repo root. Pass `--candidate-root <dir>` to resolve the candidate file's repo-relative path
lexically against `<dir>` (with `path.resolve`, independent of `process.cwd()`): the candidate's
repo-relative path is `path.relative(path.resolve(candidateRoot), path.resolve(candidateFile))`, and the
baseline file is read at `<baseline-dir>/<repo-relative-path>`. With a fixed `--candidate-root`, the same
candidate + baseline + candidate-root classify identically regardless of the process cwd. When
`--candidate-root` is absent, the legacy `process.cwd()`-relative resolution is used unchanged
(backward compatible).

## Classification semantics

Classification is per **candidate file × error rule** (the granularity `analyzeSource` supports today). For
each candidate file error, the same rule is evaluated against the baseline source of that file:

| Classification | When | Blocking |
|---|---|---|
| `introduced` | The rule fires for the candidate but did **not** fire for the baseline file (band-crossing), **or** the candidate file is absent from the baseline directory (anti-masking). | yes |
| `worsened` | The rule fires for **both** baseline and candidate AND the candidate's offending metric exceeds the baseline metric by more than the allowed delta. Applies to quantifiable metrics (significant lines, longest method, methods, dependencies). | yes |
| `pre-existing` | The rule fires for both and the candidate is within the allowed delta, **or** the rule is a non-quantifiable binary rule (SQL-outside-mapper, layer-crossing, service-file-persist) that fires for both. | no |

A brand-new offending method that causes a file to newly cross a threshold is `introduced` (band-crossing),
never `worsened`, so it blocks regardless of delta.

**Per-method identity for the `longest-method` rule.** When both the baseline and candidate files trip the
`longest-method` rule, the classifier compares the **per-method** over-100 methods, not just the file-level
`longestMethod` aggregate. A candidate over-100 method whose name has no matching over-100 method in the
baseline file is `introduced` (blocking), even if the file-level aggregate stayed within the allowed delta.
This catches the case where an offending baseline method is replaced by a brand-new, different, over-threshold
method: the new method is genuinely new debt and blocks. A candidate over-100 method that matches a baseline
over-100 method (same method identity, allowing for the parser's name extraction) is `worsened` when it grew
by more than the delta, and `pre-existing` when it grew by at most the delta. Band-crossing (baseline had no
over-100 method at all, candidate does) stays `introduced`. Because a single file can now produce multiple
`introduced`/`worsened` longest-method details, the classified splits may contain more than one longest-method
entry per file; each detail still carries `classification`, `candidateMetric`, `baselineMetric` (null when
unmatched/introduced), and `delta` (null when introduced). The coherence invariant below still holds.

**Default allowed worsen delta = 0.** Any growth of an already-firing quantifiable metric (delta > 0) is
classified `worsened` (blocking) unless a run explicitly passes a larger delta via
`--allowed-worsen-delta <int>`. Band-crossing is always `introduced` regardless of delta. To opt in to the
historical tolerance for small integration additions, pass the delta explicitly, e.g.
`--allowed-worsen-delta 20`. Candidate-level errors (review-count thresholds) are always blocking and are not
classified against the baseline.

## Report fields

In baseline-aware mode the JSON report carries, alongside the existing fields:

- `baselineIdentity` — the verbatim `--baseline` argument (preserved for compatibility).
- `baselineCommit` — the verbatim `--baseline-commit` argument, recording the frozen baseline commit the
  baseline directory was extracted from. `""` when `--baseline-commit` was not supplied; the content hash
  below still binds the compared sources either way.
- `baselineFileContentsSha256` — the sha256 of the JSON array of `{file, sha256(content)}` for every file
  that participated in the baseline comparison (every candidate file for which a baseline file was read,
  whether the baseline tripped the rule or not), sorted by repo-relative file path. This makes the baseline
  sources that produced the classification auditable.
- `allowedWorsenDelta` — the worsen delta actually used (the value of `--allowed-worsen-delta`, or `0` when
  the flag was not supplied).
- `blockingErrorCount` — count of blocking errors (`introduced` + `worsened` + candidate-level).
- `introducedErrors[]`, `worsenedErrors[]`, `preExistingErrors[]` — the classified split.
- Each detail records `id`, `file`, `message`, `classification`, `candidateMetric`, `baselineMetric`
  (`null` when the rule did not fire at baseline, i.e. `introduced`), and `delta` (`null` for `introduced`
  and for non-quantifiable rules).

**Coherence invariant:** `errors[]` contains **only** blocking errors; `preExistingErrors[]` holds the
non-blocking pre-existing errors. `errorCount == errors.length == blockingErrorCount`, and
`errorCount == 0` iff `status == "PASS"` iff `exitCode == 0`. The exit code is decided solely by
`blockingErrorCount`.

`check-ai-task-control.mjs` validates the candidate-bound report against `report.errors.length`, so a
candidate records `architectureGate.errorCount = blockingErrorCount`.

## Honesty rule

The baseline must be the real frozen baseline at the candidate's repo-relative paths. Omitting a file from the
baseline directory to suppress its errors classifies every error for that file as `introduced` (blocking) —
debt cannot be hidden by absence. Classification is per-error vs the baseline, never a wholesale file-category
exclusion.

## Motivating case

This mode unblocks `SECURITY-DIRECTORY-D2-20260802-RESLICE`, whose three architecture errors are pre-existing
`longest method > 100` errors in React page files that the D2 candidate touched only with small
`SecuritySelector` integration additions. See `GOVERNANCE_V2_POLICY.md` §Architecture Gate for the detector
repair route and `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-CONTRACT.md` for the governing
contract.
