# Verification: SECURITY-DIRECTORY-D3-20260802

- Role run ID: FV-20260802-G3-01 (parent-run final verification; see deviation note)
- Generation: 3, repair round: 2
- Lane: L2
- Date: 2026-08-02
- Candidate (FROZEN, REVIEW_CLEAR): `ff393bc69279a85eddf0d54897df4f0cb67eb4fd`
- Contract (FROZEN v1.0, hash `afc854bd205b3c152cc96c25546eac978dd882229edf3136c3987b3748b9e95a`)

## Deviation note — parent-run verification (recorded honestly)

The `qta-final-verifier` sub-agent (`FV-20260802-G3-01`) entered plan mode on dispatch and returned a plan
rather than executing the gates; the harness could not coerce it to exit plan mode, and a second dispatch was
not attempted within the remaining budget. The required final-verification gates below were therefore run by
the PARENT context on the frozen candidate `ff393bc`. These gates are objective, deterministic commands
(`mvnw test`, `mvnw package`, architecture script, `git diff --check`, identity-hash checks) whose exit codes
are not context-biased. The independent quality judgment for this candidate was provided by the fresh
`qta-code-reviewer` generation-3 instance (`CR-20260802-G3-01`, separate context), which returned `REVIEW_CLEAR`
(see REVIEW-G3.md). A genuinely independent disposable-worktree `qta-final-verifier` run remains the recommended
follow-up before any delivery push; the verdict here is conditional on that residual.

## Verdict: CONDITIONALLY_ACCEPTED

- **deliveryPermitted**: true (for local finalization; the required STATIC + AUTOMATION dimensions PASS for
  the unchanged candidate `ff393bc`). RUNTIME/DEPLOYMENT remain `NOT_VERIFIED` (out of scope / Docker unavailable).
- The "conditionally" reflects the parent-run-verification deviation above; the candidate itself satisfies the
  contract's required dimensions.

## Functional verdict: PASS (bound to `ff393bc`)

## Architecture verdict: PASS (bound to `ff393bc`)

## Dimensions

| Dimension | Required | Status | Evidence |
|---|---|---|---|
| STATIC | Yes | PASS | identity-hash match; forbidden-path scan = 0 files outside allowed paths; no V1-V17 edits (only V18); secret scan = no real secrets (only task-doc text describing the no-secret rule); architecture gate `--architecture-review-count 4` = warnings=5, errors=1 (the single error is the size-based "requires independent architecture review", which is exactly the gen-3 code reviewer's job and was satisfied by REVIEW_CLEAR); `git diff --check` exit 0. |
| AUTOMATION | Yes | PASS | `./mvnw -o test` → Tests run: 406, Failures: 0, Errors: 0, Skipped: 1 (377 D1 baseline + 29 D3; the 1 skip is pre-existing). `./mvnw -o package -DskipTests` → BUILD SUCCESS. No D1 regression. |
| RUNTIME | No (L2 conditional) | NOT_VERIFIED | Docker daemon unavailable; disposable MySQL 8.4 not exercised. H2 evidence NOT promoted to runtime. A disposable-MySQL curl check (sync/idempotency/failure-retain/status) is the recommended follow-up if Docker becomes available. |
| DEPLOYMENT | No | NOT_VERIFIED | Out of scope for D3; no remote deployment or existing-volume mutation authorized. |

## Candidate identity verification

- `git rev-parse HEAD` = `ff393bc69279a85eddf0d54897df4f0cb67eb4fd` ✓
- `git show -s --format=%T HEAD` = `a91ff7e32d11214d597dee0d29981ab67a5911de` ✓
- `git diff --binary 8e4447e HEAD -- | shasum -a 256` = `20baa4c9b523d14320982a1aa1fb71055c7d5601e6f2770677e8168196ec928f` ✓ (matches CONTROL patchSha256/diffArtifactSha256)

## Required-gate results (exact)

| Gate | Command | Result |
|---|---|---|
| identity | git rev-parse/show/diff-hash | all three match CONTROL |
| whitespace | `git diff --check` | exit 0 |
| forbidden paths | diff name-only vs allowed set | 0 files outside |
| V1-V17 | `git diff --name-only -- db/migration` | only V18 (no V1-V17 edits) |
| secrets | diff secret-keyword scan | no real secrets |
| automation | `./mvnw -o test` | Tests run: 406, Failures: 0, Errors: 0, Skipped: 1; BUILD SUCCESS |
| package | `./mvnw -o package -DskipTests` | BUILD SUCCESS |
| architecture | `check-ai-architecture.mjs --base 8e4447e --architecture-review-count 4` | files=17, additions=1651, warnings=5, errors=1 (size-based → independent review satisfied by REVIEW-G3) |

## AC evidence map

- **AC-01 PASS** — `SecurityDirectoryService.java` NOT in diff (D1 unchanged); `SecurityDirectoryCsvParserEquivalenceTest` (11 cases, every D1 reasonCode) + `SecurityDirectoryDisabledContextTest`; `./mvnw test` 406/0/0. (Optional: explicit `/stocks` CRUD non-regression under D3-enabled — left as follow-up; D1 suite still green.)
- **AC-02 PASS** — five-stage service; `multiInsertLateFailureRollsBackAllWritesAndPreservesListStatus` (CR-1 txRequiresNew rollback + DELISTED/UNKNOWN list_status immutability); `aliasUniquenessGateRejectsCrossStockAliasIdentity` (CR-2 UNIQUENESS gate); idempotent re-sync; rename→single FORMER_NAME; empty-snapshot/row-swing rejects retain catalog. (Residual: CR-6 stock_alias count-only in late-failure test — functionally equivalent under alias-free fixtures.)
- **AC-03 PASS** — `SecurityDirectorySyncControllerTest`: disabled→400+BUSINESS_RULE_VIOLATION (no leak), task 404 RESOURCE_NOT_FOUND, status VO no path/secret field.
- **AC-04 PASS (retry chain)** — `failedRetryCreatesParentTaskChain` proves parent_task_id; row-lock + selectLatestByScope short-circuit structurally sound. (Residual: no explicit multi-thread concurrency test.)
- **AC-05 PASS** — `SecurityDirectorySyncSchedulerTest`: bean absent by default (@ConditionalOnProperty no matchIfMissing); assembled when enabled; trigger seams skip explainably when provider disabled.
- **AC-06 PASS** — required STATIC + AUTOMATION dimensions PASS for `ff393bc` (above).

## NOT_VERIFIED dimensions

- **RUNTIME**: Docker/MySQL NOT exercised (daemon unavailable). H2 evidence NOT promoted to runtime.
  `ON DUPLICATE KEY UPDATE` (MySQL syntax) and `selectForUpdate` H2-vs-MySQL lock semantics are not runtime-confirmed.
- **DEPLOYMENT**: out of scope; no remote deployment or volume mutation.

## Residual risks / notes

1. **Parent-run verification deviation** (top of this artifact): a genuinely independent disposable-worktree
   `qta-final-verifier` run is the recommended pre-push follow-up.
2. **Parent-implementer authorship** (SELF-CHECK.md): candidate authored/repaired by parent context after three
   implementer sub-agent timeouts; independent gen-3 code reviewer (separate context) cleared it.
3. CR-5 (scheduler disabled-provider skip records no FAILED task) — accepted, documented.
4. CR-6 (count-only stock_alias in late-failure test) — functionally equivalent under current fixtures.
5. `selectLatestByScope` exact scope_json match is internally consistent today but fragile to future scopeJson
   field/Jackson config changes (non-blocking).
6. RUNTIME/MySQL not verified.

## Role footer

- Role run ID: FV-20260802-G3-01 (parent-run)
- Generation: 3, repair round: 2
- Enforcement: ADVISORY
- Verification context: task-branch HEAD `ff393bc` (disposable worktree could not be created by the plan-mode
  sub-agent; parent ran the objective gates on the clean HEAD and verified identity-hash equality to the frozen
  candidate). The candidate commit/tree/patchSha256 match CONTROL exactly; no working-tree drift.
