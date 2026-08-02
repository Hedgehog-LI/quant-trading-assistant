# Finalization: ARCH-GATE-BASELINE-AWARE-REPAIR-20260802

- Task: ARCH-GATE-BASELINE-AWARE-REPAIR-20260802 (L0 governance-tooling repair).
- Candidate identity: `b1fc6993073b4541029e2a7837b2473b6c054caf`
  (tree `fcb79cf8e8a6bc2fe6c0ffd497e7bc8a999172ae`, patch SHA-256
  `1fdcb30a68585fad551fe094cdd6bc74689120406be82cf80bcece9b41358909`).
- Verification verdict: ACCEPTED (deliveryPermitted true; FUNCTIONAL=PASS, ARCHITECTURE=PASS;
  STATIC=PASS, AUTOMATION=PASS; RUNTIME/DEPLOYMENT NOT_REQUIRED).
- Independent review: REVIEW_CLEAR (FUNCTIONAL=PASS, ARCHITECTURE=PASS; 3 advisory P3 notes).
- Architecture gate: PASS, errorCount 0, candidate-bound
  (`docs/development/tasks/ARCH-GATE-BASELINE-AWARE-REPAIR-20260802-ARCH-REPORT.json`).

## What this task delivered

Repaired four concrete defects in the baseline-aware mode of `scripts/check-ai-architecture.mjs`:

1. Cross-repo baseline path is now cwd-independent via an explicit `--candidate-root` (the same candidate +
   baseline + candidateRoot classifies identically from the backend cwd and the frontend cwd). Backward
   compatible: without `--candidate-root` the legacy `process.cwd()`-relative resolution is preserved.
2. Per-method identity for the `longest-method` rule: a brand-new over-threshold method that replaces a
   baseline over-threshold method is classified `introduced` (blocking), not `pre-existing`.
3. The baseline-aware report now records the frozen `baselineCommit`, the sorted content hash
   `baselineFileContentsSha256` of the files compared, and the `allowedWorsenDelta` actually used.
4. The default worsen delta is now 0 (debt cannot drift upward silently); a non-zero delta is opt-in via
   `--allowed-worsen-delta`.

This unblocks SECURITY-DIRECTORY-D2-20260802-RESLICE: confirmed by the parent with a probe run against the
real frontend baseline `80c38324` from BOTH the backend cwd and the frontend cwd — the 3 pre-existing React
page `longest method > 100` errors classify as pre-existing/non-blocking (9 pre-existing details, 0 blocking),
identical from both cwds.

## Changed paths (candidate source)

- `scripts/check-ai-architecture.mjs`
- `scripts/tests/ai-governance.test.mjs`
- `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md`

Plus task artifacts (contract, control, self-check, self-arch-report, review, verification, finalization,
diff artifact, arch report, 5 evidence receipts). No business code, React page, Java, MyBatis, Flyway,
scheduler, schema, or D2 frontend change. No push/merge/deploy (local only, per instruction).

## Out of scope / next

- RUNTIME/DEPLOYMENT: NOT_REQUIRED (governance tooling).
- D2 close-out and local main integration are separate Phase 2 / Phase 3 work that consume this repaired gate.

## Status

- FINALIZED. Delivery-ready gate `node scripts/check-ai-delivery-ready.mjs` exits 0.
