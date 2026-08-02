# Test-Design Amendment: ARCH-GATE-BASELINE-AWARE-20260802 / TD-AGBA-2

- roleRunId: TD-AGBA-2
- dispatchId: dispatch-td-agba-2
- role: TEST_DESIGNER
- generation: 0
- executorType: SUBAGENT
- startedAt/finishedAt: 2026-08-02T09:14:00Z / 2026-08-02T09:19:00Z
- executionOutcome: COMPLETED
- artifactAccepted: true
- artifactSha256: persisted below; frozen by parent.

## Verdict: CONTRACT_BLOCKED → amended by parent → CONTRACT_FROZEN

The test designer returned 3 blocking amendments (BA-1/BA-2/BA-3) and 1 new required test
(TEST-AG-07). Full rationale is in the returned artifact. Parent disposition:

- **BA-1 (classification granularity)**: ACCEPTED. Contract pins classification to
  per-file × per-error-rule using the detector's existing aggregate metrics; all per-method prose struck.
- **BA-2 (delta contradiction)**: ACCEPTED. Band-crossing `introduced` (rule fires for candidate but not
  baseline) is delta-independent and always blocks. Delta-based `worsened` uses default delta **20**
  (covers D2's +11; blocks 105→130). 199→210 is now correctly `pre-existing`/non-blocking; 95→120 is
  `introduced`/blocking.
- **BA-3 (errorCount coherence)**: ACCEPTED. In baseline-aware mode `errors[]` contains only blocking
  (introduced+worsened) errors; `errorCount == errors.length == blockingErrorCount`;
  `errorCount==0 ⟺ PASS ⟺ exitCode 0`. Pre-existing errors live only in `preExistingErrors[]`.
  Compatible with the unchanged `check-ai-task-control.mjs` file-validation path.
- **OQ-1**: resolved by parent — band-crossing introduced + delta(20) worsened (option b+a).
- **OQ-2**: resolved by parent — TEST-AG-07 is Required (anti-masking honesty guarantee).

## Confirmed selector observability

All 7 selectors are verbatim `test("…")` first arguments observable by `node --test` and matched as
substrings by `scripts/run-ai-evidence-command.mjs`. Each is unique against the existing test names in
`scripts/tests/ai-governance.test.mjs`.

## Frozen test inventory (7 tests)

| test_id | AC | selector |
|---|---|---|
| TEST-AG-01 | AC-01 | baseline-aware gate does not block unchanged pre-existing React method debt |
| TEST-AG-02 | AC-02 | baseline-aware gate blocks a newly introduced over-threshold method |
| TEST-AG-03 | AC-03 | baseline-aware gate blocks a worsened pre-existing method beyond the allowed delta |
| TEST-AG-04 | AC-04 | baseline-aware gate still blocks over-long Java methods |
| TEST-AG-05 | AC-05 | architecture gate keeps strict behavior when no baseline is supplied |
| TEST-AG-06 | AC-06 | architecture report binds candidate and baseline identity and the full governance suite passes |
| TEST-AG-07 | AC-01,AC-02 | baseline-aware gate treats a missing baseline file as introduced not silently dropped |

## Non-blocking recommendations folded into the test matrix

- DEFECT-5: TEST-AG-03 includes the delta==default boundary case (non-blocking).
- DEFECT-6: AC-05 splits STATIC (existing tests unchanged) from AUTOMATION (no baseline fields emitted).
- DEFECT-7: AC-06 asserts both identity binding and `spawnSync(run-ai-governance-gates.mjs).status === 0`
  inside one test body; lane AC count stays at 6 (≤ 8).
