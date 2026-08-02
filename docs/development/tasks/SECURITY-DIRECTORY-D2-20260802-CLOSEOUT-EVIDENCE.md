# D2 Close-out Evidence (SELF_CHECKED): SECURITY-DIRECTORY-D2-20260802-CLOSEOUT

- Task: SECURITY-DIRECTORY-D2-20260802-CLOSEOUT (L0 close-out on the repaired architecture gate).
- Dispatch / role: dispatch-IMP-D2CO-1 / IMP-D2CO-1 (this SELF_CHECKED implementer slice).
- Parent run: codex-parent-d2-closeout-1.
- Re-used D2 frozen contract: SECURITY-DIRECTORY-D2-20260802-RESLICE
  (contract sha `9170e2a1be874b6cea4ed2bb34554ffaf654f0df67dea23386e7a3ade3e27c67`).
- Repaired detector (DELIVERY_READY): ARCH-GATE-BASELINE-AWARE-REPAIR-20260802.
- This is a VERIFICATION close-out slice: NO source/test/React/Java/scheduler/schema/detector/D2-frontend
  edits were made. The full frontend `npm run test` and full backend `./mvnw test` were NOT re-run here —
  they were already run once by the parent and the budget forbids repeating a full suite.
- Role start: 2026-08-02T12:08:00Z. Runtime enforcement: ADVISORY (bypassPermissions used only to be
  unattended).

## Real cross-repo commit identities (no patch substitute)

Confirmed in this slice by direct inspection of the frontend repo
`/Users/joker/code/quant-trading-assistant-web`:

- Frontend candidate commit: `0cf382fec889bbecb567fd27064040b3901b9c27`
  (tree `32c1dd3a8ef68e55711ca215e9f1a08f8d1b99f2`),
  subject `feat(security-directory): D2 shared SecuritySelector + directory API + four-flow integration
  (RESLICE candidate)`, on branch `codex/security-directory-d2-20260802`.
- Frontend baseline commit: `80c38324f58ba58cf6f96884184e16c86b967f96`
  (tree `f408660a43bdf121713dd7c85b0af3d221b91833`).
- The 7 D2 files exist in the candidate; 2 of them (`securityDirectoryApi.ts`, `SecuritySelector.tsx`)
  are new in the candidate (absent at baseline).

## Check 1 (AC-01) — repaired architecture gate passes for D2 from the BACKEND cwd

Baseline directory built under the backend cwd from the 7 frontend baseline versions; the 2 new D2 files
have no baseline counterpart (absent at baseline). Command (run from backend cwd
`/Users/joker/code/quant-trading-assistant`):

```
WEB=/Users/joker/code/quant-trading-assistant-web
BASELINE_DIR=$(mktemp -d /tmp/d2co-baseline-XXXXXX)   # 5 baseline files written via:
#   git -C "$WEB" show 80c38324…:<rel-path> > "$BASELINE_DIR/<rel-path>"
node scripts/check-ai-architecture.mjs \
  --files "$WEB/src/pages/market-data.tsx" "$WEB/src/pages/market-workspace.tsx" \
          "$WEB/src/pages/market-segments.tsx" \
          "$WEB/src/features/market-data/utils/syncPlanForm.ts" \
          "$WEB/src/shared/types/domain.ts" \
          "$WEB/src/features/market-data/api/securityDirectoryApi.ts" \
          "$WEB/src/shared/components/SecuritySelector.tsx" \
  --candidate-root "$WEB" \
  --baseline "$BASELINE_DIR" \
  --baseline-commit 80c38324f58ba58cf6f96884184e16c86b967f96 \
  --allowed-worsen-delta 20 \
  --candidate-identity 0cf382fec889bbecb567fd27064040b3901b9c27 \
  --json-output /tmp/d2co-archprobe2.json
```

Result: `EXIT=0`, console reported
`Architecture gate (baseline-aware): introduced=0 worsened=0 pre-existing=9 blocking=0` and
`Architecture gate: files=7, additions=0, warnings=9, errors=0`.

JSON output (`/tmp/d2co-archprobe2.json`) key fields:

| field | value |
|---|---|
| status | `PASS` |
| exitCode | `0` |
| errorCount | `0` |
| blockingErrorCount | `0` |
| introducedErrors | `[]` (0) |
| worsenedErrors | `[]` (0) |
| preExistingErrors | 9 entries (3 pre-existing React page `longest method > 100` errors, multi-method details) |
| candidateIdentity | `0cf382fec889bbecb567fd27064040b3901b9c27` |
| baselineCommit | `80c38324f58ba58cf6f96884184e16c86b967f96` |
| allowedWorsenDelta | `20` |
| baselineFileContentsSha256 | `0cf81353b5e3096b4889758b46f55b36ba71d77db3ae31ddec35e3665ea9bdd0` |

The classification (introduced=0, worsened=0, pre-existing=9, blocking=0) is identical to the parent's
recorded run. `baselineFileContentsSha256` matches the committed report exactly when the baseline directory
is constructed the same way the parent did (5 baseline files present; the 2 D2-new files absent at baseline).
This proves AC-01 cwd-independence for the BACKEND cwd.

## Check 2 (AC-02) — frontend candidate exists and its baseline→candidate patch SHA matches RESLICE

Command:

```
git -C /Users/joker/code/quant-trading-assistant-web diff --binary \
  80c38324f58ba58cf6f96884184e16c86b967f96 0cf382fec889bbecb567fd27064040b3901b9c27 | shasum -a 256
```

Result: `479d6bf4b9f2fdce39d90fa49bb6cb251bae55202e0b98027fa21c3674164b06  -`

This is byte-identical to the RESLICE-frozen frontend diff patch SHA recorded in the contract, proving the
real frontend commit `0cf382f` contains exactly the RESLICE-frozen D2 implementation. (The full frontend
`npm run test` / `npm run build` and backend `./mvnw test` / `./mvnw package` were run once by the parent
and are NOT re-run here per the budget; their green results are recorded in the CONTROL.)

## Check 3 (AC-01) — committed D2 architecture report is internally consistent

File: `docs/development/tasks/SECURITY-DIRECTORY-D2-20260802-RESLICE-ARCH-REPORT.json`
(file SHA-256 `8341c075a5c3dc6ab49778ad12a507850c51d20963e0db6f1b818e63281e7ac6`).

| field | value |
|---|---|
| status | `PASS` |
| errorCount | `0` |
| blockingErrorCount | `0` |
| candidateIdentity | `0cf382fec889bbecb567fd27064040b3901b9c27` |
| baselineCommit | `80c38324f58ba58cf6f96884184e16c86b967f96` |
| allowedWorsenDelta | `20` |
| preExistingErrors | 9 entries |
| baselineFileContentsSha256 | `0cf81353b5e3096b4889758b46f55b36ba71d77db3ae31ddec35e3665ea9bdd0` |

These match both the contract's expected values and my own fresh gate run (Check 1).

## Check 4 (AC-03) — full governance STATIC suite green

Command (run from backend cwd):

```
node scripts/run-ai-governance-gates.mjs
```

Result: `EXIT=0`, `QTA AI governance gates passed.`; `tests 50 / pass 50 / fail 0`. This is the STATIC
suite and includes the two binding close-out selectors:
- `candidate-root makes baseline-aware classification cwd-independent` (TEST-D2CO-01, AC-01) PASS.
- `baseline-aware repair keeps the full governance suite and strict no-baseline behavior green`
  (TEST-D2CO-STATIC, AC-03) PASS.

The full frontend `npm run test` (303/303, TEST-D2CO-02, AC-02) was run once by the parent and is not
re-run here per the budget.

## AC mapping summary

| AC | Required evidence | Self-check result |
|---|---|---|
| AC-01 | Repaired arch gate PASS from backend cwd, identical classification, binds to real candidate + baseline. | CONFIRMED: status=PASS, exit=0, blocking=0, pre-existing=9, candidate=0cf382f, baseline=80c38324, delta=20, baselineFileContentsSha256 matches; committed report identical. |
| AC-02 | Real frontend candidate commit recorded; baseline→candidate patch SHA = `479d6bf4…`. | CONFIRMED: patch SHA `479d6bf4b9f2fdce39d90fa49bb6cb251bae55202e0b98027fa21c3674164b06`; candidate tree `32c1dd3a…` matches contract. (Full npm/mvnw gates run once by parent, not repeated.) |
| AC-03 | Governance STATIC suite green (binding close-out selectors). | CONFIRMED: `run-ai-governance-gates.mjs` exit 0, 50/50 tests, incl. TEST-D2CO-01 and TEST-D2CO-STATIC. |

## Out of scope / next

- No business code, D2 frontend implementation, detector, schema, or any source/test edit.
- RUNTIME/DEPLOYMENT: NOT_REQUIRED (close-out; D2 was already SELF_CHECKED by RESLICE).
- Git stage/commit/merge/rebase/push NOT performed (Git is parent-owned).
- Local main integration is the separate Phase 3 work.

SELF_CHECKED
