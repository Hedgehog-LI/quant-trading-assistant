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
  --candidate-identity <frozen-candidate-id> \
  --json-output <report.json>
```

The baseline directory **must be the real frozen baseline**. Never omit files to hide debt: a candidate file
that is missing from the baseline directory is classified `introduced` (blocking) — a missing baseline file is
never silently dropped. Reuse `--base <git-ref>` for candidate-file selection and additions exactly as in the
strict path; the baseline directory only supplies baseline source for per-file comparison.

When no `--baseline` is supplied, the detector is byte-for-byte unchanged: every error blocks and the report
contains no baseline fields.

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

**Default allowed worsen delta = 20** quantifiable units (lines or method-line count). This permits small
integration additions without false `worsened` labels, while blocking material growth. Candidate-level errors
(review-count thresholds) are always blocking and are not classified against the baseline.

## Report fields

In baseline-aware mode the JSON report carries, alongside the existing fields:

- `baselineIdentity` — the verbatim `--baseline` argument.
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
