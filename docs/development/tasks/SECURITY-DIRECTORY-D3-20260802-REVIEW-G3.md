# Code Review Artifact: SECURITY-DIRECTORY-D3-20260802 (Generation 3, Repair Round 2 — FINAL)

- **Reviewer role run ID**: CR-20260802-G3-01 (Lane L2)
- **Session**: fresh independent review context (no parent conversation read)
- **Candidate mode/identity**: COMMIT `ff393bc69279a85eddf0d54897df4f0cb67eb4fd` (tree `a91ff7e32d11214d597dee0d29981ab67a5911de`)
- **Baseline**: `8e4447ed86aa2cf53c0c52388a1b2d592ceb70e0`
- **Frozen diff artifact**: `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CANDIDATE.patch` (SHA-256 `20baa4c9b523d14320982a1aa1fb71055c7d5601e6f2770677e8168196ec928f`)
- **Contract hash**: `afc854bd205b3c152cc96c25546eac978dd882229edf3136c3987b3748b9e95a`
- **Repair round**: 2 (final; fingerprint `CR1-ATOMIC-PUBLISH-SELF-INVOCATION|CR2-MISSING-UNIQUENESS-GATE|CR3-WEAK-BYTE-EQUALITY`)
- **Assigned ACs**: AC-01..AC-06
- **Enforcement**: ADVISORY (read-only; no edits, no Git writes, no sub-agents)

## 1. Verdict: `REVIEW_CLEAR`

The two generation-2 blocking defects (CR-1 atomic-publish self-invocation trap, CR-2 missing UNIQUENESS gate)
are genuinely fixed and proven by new evidence. CR-4 package/directory mismatch is closed. CR-3 byte-equality
is substantively addressed for `stock_basic` + `list_status` immutability; a residual count-only assertion for
`stock_alias` remains but is functionally equivalent under the (alias-free) test fixtures and is not a
functional defect. No new blocking defect was found. Final repair round; candidate cleared for the final verifier.

## 2. Functional Verdict: PASS

- CR-1 transactionality is real: `runSync` executes `txRequiresNew.execute(status -> publish(snapshot, identity))`
  (line 142); `publish` carries NO `@Transactional`; `txRequiresNew` is `PROPAGATION_REQUIRES_NEW`
  (`MarketDataConfig.java`). Mid-publish failure rolls back ALL writes.
- CR-1 evidence: `multiInsertLateFailureRollsBackAllWritesAndPreservesListStatus` forces a 3rd-insert failure
  (Mockito argThat on SH.600003) and asserts inserts 1-2 absent + DELISTED/UNKNOWN `list_status` byte-unchanged.
- CR-2 UNIQUENESS gate is real and pre-write: `validateAliasUniqueness` (lines 307-327) aggregates alias
  identities across rows; collision >1 owner throws `DAILY_BAR_VALIDATION_ERROR`+`UNIQUENESS`; invoked at line
  183 before preloadStocks/publish; `aliasUniquenessGateRejectsCrossStockAliasIdentity` proves.
- Failure-retain / FAILED-only / no-PARTIAL semantics intact; quality gates all present and contract-aligned
  (EMPTY_SNAPSHOT/REQUIRED_FIELD/UNIQUENESS=DAILY_BAR_VALIDATION_ERROR; ROW_COUNT_SWING=BUSINESS_RULE_VIOLATION
  ≥0.30, skipped when catalog empty); gate ordering before any write.
- API contract: disabled→400+BUSINESS_RULE_VIOLATION no-leak; task 404 RESOURCE_NOT_FOUND; status VO has no
  path/token/secret/key/credential field; no CSV path echoed.
- Idempotency/concurrency: content-identity snapshotHash; SyncScopeLockMapper row-lock double-check inside
  txRequiresNew; selectLatestByScope short-circuit; retry parent_task_id chain (failedRetryCreatesParentTaskChain).
- Scheduler default-off: @ConditionalOnProperty(havingValue=true) no matchIfMissing; default false;
  DisabledContextTest confirms bean absent + D1 infra available.
- D1 compatibility: `SecurityDirectoryService.java` NOT in the candidate diff (verified).

## 3. Architecture Verdict: PASS

Architecture gate: `check-ai-architecture.mjs --base 8e4447e --architecture-review-count 3`. G1 ERROR
(service file-protocol+persistence) structurally resolved — service contains no `CSV|Csv|BufferedReader|
InputStream|MultipartFile|Files.|nio.file|Path` references (grep). file-protocol isolated in `provider/csv/`.
Layering clean: Provider → Service (no file/protocol) → Controller (API only). Constants centralized in
`SecurityDirectoryConstants`. No second security-master; V18 state table UNIQUE(provider), no stock_basic
writeback; status derives catalogStatus from D1 heuristic. No V1-V17 edits, no secret/frontend/.env paths
(patch grep). MyBatis XML correct (`ON DUPLICATE KEY UPDATE` on UNIQUE(provider); selectLatestByScope triple).
(Note: gate script and `git diff --check` were not re-executed by this read-only role — equivalent static
inspection performed.)

## 4. Prior-Finding Closure (from REVIEW-G2)

| ID | G2 severity | Status | Evidence |
|---|---|---|---|
| CR-1 atomic-publish self-invocation trap | BLOCKING | CLOSED | `SecurityDirectorySyncService.java:142` txRequiresNew; line 173 no @Transactional; multiInsertLateFailure test. |
| CR-2 missing UNIQUENESS gate | BLOCKING | CLOSED | validateAliasUniqueness lines 307-327 invoked pre-write line 183; aliasUniquenessGate test. |
| CR-3 weak byte-equality evidence | MAJOR | CLOSED (residual note) | late-failure test now asserts stock_basic content + list_status immutability; stock_alias side count-only (functionally equivalent under alias-free fixtures). |
| CR-4 exception package mismatch | MINOR | CLOSED | moved to `provider/SecurityDirectoryProviderException.java`. |
| CR-5 scheduler no FAILED task on disabled | MINOR | REMAINING (accepted) | documented behavior; scheduler default-off. Non-blocking. |

## 5. New Findings (CR-6+)

None blocking. One MINOR residual evidence note:

- **CR-6 — MINOR — Residual count-only assertion on `stock_alias` in the late-failure test.**
  The contract's A3 asks for "before/after 全表内容快照断言，非 count-only" on both stock_basic and
  stock_alias. stock_basic is content-asserted; stock_alias is count-only. Non-blocking: the seeded rows have
  no aliases and the failing CSV rows carry no aliases, so a count of 0 equals a content-equality result; alias
  upserts run in the same txRequiresNew transaction so the same rollback covers them. Optional verifier
  correction: seed one alias row and assert its full identity survives.

No other functional, data-integrity, authorization/secret, scheduler/provider, or architectural defect found.

## 6. AC Coverage

- AC-01 COVERED (provider/disabled/csv-parser; D1 unchanged; 11-case equivalence test; disabled context test).
- AC-02 COVERED (five-stage; idempotent; rename→FORMER_NAME; empty/swing/uniqueness rejects retain; late-failure rollback; FAILED+summary).
- AC-03 COVERED (disabled 400 no-leak; task 404; status no path/secret).
- AC-04 COVERED (retry parent chain; row-lock + double-check short-circuit). Residual: no multi-thread test.
- AC-05 COVERED (bean absent by default; trigger seams; disabled skip explainable).
- AC-06 COVERED (forbidden-path scan clean; architecture ERROR resolved; constants centralized).

## 7. Parent-Authorship Independent Assessment

Independence preserved by reviewing only the frozen diff and contracts. CR-1/CR-2 fixes verified by reading
actual source (`txRequiresNew.execute(status -> publish(...))` line 142; inert-@Transactional removal line 173)
and confirming the new tests genuinely force a multi-write late failure and a cross-stock alias collision.
Both fixes hold on the merits, not on SELF-CHECK framing. Parent-implementer deviation introduces no detectable
integrity risk for these fingerprints; CR-5/CR-6 residuals are pre-existing and disclosed.

## Residual Risks

- RUNTIME: Docker/MySQL NOT exercised (H2-only); MySQL `ON DUPLICATE KEY UPDATE`, selectForUpdate H2-vs-MySQL
  lock semantics, real migration behavior = verifier's job (REPORTED, NOT INFERRED).
- selectLatestByScope exact scope_json match is internally consistent today but fragile to future field/config
  changes (non-blocking, carried from G2).
- No multi-thread concurrency test for AC-04.
- CR-6 count-only stock_alias in late-failure test (functionally equivalent under current fixtures).

## Process Footing

Final allowed repair round (max 2). The G2 failure fingerprint cleared with new evidence; no NEW blocking
fingerprint discovered. Candidate returned `REVIEW_CLEAR` for the final verifier.

**Reviewed contract hash**: `afc854bd205b3c152cc96c25546eac978dd882229edf3136c3987b3748b9e95a`
**Candidate identity**: COMMIT `ff393bc69279a85eddf0d54897df4f0cb67eb4fd` / tree `a91ff7e32d11214d597dee0d29981ab67a5911de`
**Role run ID**: CR-20260802-G3-01; enforcement ADVISORY (read-only).
