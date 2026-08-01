# Code Review Artifact: SECURITY-DIRECTORY-D3-20260802 (Generation 2, Repair Round 1)

- **Reviewer role run ID**: CR-20260802-G2-01 (Lane L2)
- **Session**: fresh independent review context
- **Candidate mode/identity**: COMMIT `00703042effef74add6a776647858cad107865f4` (tree `a1d9abce166cc96eee83e4e1a0fda72df7017d59`)
- **Baseline**: `8e4447ed86aa2cf53c0c52388a1b2d592ceb70e0`
- **Frozen diff artifact**: `docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CANDIDATE.patch` (SHA-256 `36c0888a2cd7102159de61e48d49c0d7e66b619040aba945f8e4f1d83d721360`)
- **Contract hash**: `afc854bd205b3c152cc96c25546eac978dd882229edf3136c3987b3748b9e95a`
- **Repair round**: 1 (fingerprint `ARCH-CR1-SERVICE-FILE-PROTOCOL-PERSISTENCE` from G1 architecture gate)
- **Assigned ACs**: AC-01..AC-06
- **Enforcement**: ADVISORY (read-only compensating isolation; no edits, no Git writes; the required
  architecture-gate command and `git diff --check` could not be re-executed without Bash in this role; the
  reviewer performed equivalent static inspection by reading the gate script and the full diff)

## 1. Verdict: CHANGES_REQUESTED

Two blocking data-integrity defects in `SecurityDirectorySyncService` defeat AC-02's core guarantees (atomic
publish / failure-retain), and one contract-mandated quality gate (UNIQUENESS) is unimplemented. The candidate
compiles and the D1 boundary is respected, but it cannot be cleared while these stand.

## 2. Functional Verdict: FAIL

AC-02's defining guarantees — "任一阶段或发布失败整批回滚，保留上一成功目录" and "stock_basic/stock_alias
内容与失败前逐行字节等价" — are not actually enforced. The `@Transactional` on `publish(...)` is inert
because `publish` is invoked by self-invocation from `runSync` with no surrounding `txRequiresNew`. Each
mapper call therefore auto-commits, so a failure mid-publish leaves partial writes that are not rolled back.
The UNIQUENESS quality gate required by A3 is absent from the code.

## 3. Architecture Verdict: FAIL

The generation-1 architecture ERROR ("service combines file/protocol parsing with persistence") is structurally
resolved (the service no longer references CSV/file types). However the "single-transaction publish" layering
rule is violated at runtime (CR-1).

## 4. Findings

### CR-1 — BLOCKING — Atomic-publish / failure-retain is not transactionally enforced (AC-02, AC-04)
- **File:line**: `SecurityDirectorySyncService.java` — the `publish(snapshot, identity)` call in `runSync`
  and the `@Transactional PublishResult publish(...)`.
- **Defect**: `runSync` calls `publish` via direct self-invocation on the same bean. Spring's proxy-based
  `@Transactional` does not intercept self-invocations, so the `@Transactional` on `publish` is inert. The
  surrounding `txRequiresNew.executeWithoutResult(...)` blocks wrap only task-status updates, NOT the publish
  loop. Every mapper write auto-commits.
- **Failure scenario**: a snapshot with N inserts; insert #3 throws → inserts #1/#2 are already committed.
  The catch marks the task FAILED and reports "整批已回滚", but `stock_basic`/`stock_alias` now contain
  #1/#2 — not byte-equal to pre-failure. Contradicts AC-02.
- **Why tests miss it**: `forcedPublishFailurePreservesPreviousCatalog` stubs `updateDirectoryById` to throw
  but the fixture has zero `toInsert` and the only `toUpdate` fails before its write — so nothing partial is
  written. The required "before/after 全表内容快照断言，非 count-only" is absent.
- **Minimal fix**: wrap publish execution in `txRequiresNew` (e.g.
  `PublishResult result = txRequiresNew.execute(status -> publish(snapshot, identity));`) and remove the
  now-redundant `@Transactional`. Add a multi-write-then-late-fail test asserting full byte-equality.

### CR-2 — BLOCKING — UNIQUENESS quality gate is not implemented (AC-02, contract A3)
- **File:line**: `SecurityDirectorySyncService.java` — `validateCandidateUniqueness` only checks required fields.
- **Defect**: contract A3 mandates a UNIQUENESS gate ("候选集内 canonical_symbol 重复或 alias identity 重复 →
  拒绝，lastErrorCode=DAILY_BAR_VALIDATION_ERROR, gate=UNIQUENESS"). `GATE_UNIQUENESS` is declared but never
  referenced. The method named `validateCandidateUniqueness` only does REQUIRED_FIELD.
- **Minimal fix**: collect alias identities across candidate rows; if any `(aliasType, normalizedAlias)` maps
  to more than one stock, throw `gateFailure(DAILY_BAR_VALIDATION_ERROR, GATE_UNIQUENESS,
  {"gate":"UNIQUENESS","conflicts":[...]})`. Add a test.

### CR-3 — MAJOR — Forced-publish-failure test does not assert the contract's byte-equality guarantee
- **File:line**: `SecurityDirectorySyncIntegrationTest.java` (`forcedPublishFailurePreservesPreviousCatalog`).
- **Defect**: asserts only `countAll()` and `getName()`; never snapshots `stock_alias`, never compares full row
  content, never seeds DELISTED/UNKNOWN rows to assert `list_status` immutability.
- **Minimal fix**: after CR-1, snapshot all `stock_basic` + `stock_alias` before; force failure on a LATE
  write; assert full byte-equality; seed DELISTED/UNKNOWN rows and assert `list_status` unchanged.

### CR-4 — MINOR — `SecurityDirectoryProviderException` package/directory mismatch
- The class declares `package ...provider;` while residing under `.../exception/`. Compiles, but a layering
  smell. Move the file to `.../provider/` to match its package.

### CR-5 — MINOR — Scheduler skip on disabled provider records no FAILED task
- `triggerScheduled` throws before creating a task when provider disabled → silent skip + log, no auditable
  FAILED task. Acceptable; document or create a FAILED task for auditability.

## 5. AC Coverage

- AC-01 COVERED with minor gap (no `/stocks` CRUD non-regression / D1-search-under-D3 parametric tests;
  verifier should add).
- AC-02 GAP/FAIL (CR-1 transactionality, CR-2 missing UNIQUENESS, CR-3 weak evidence).
- AC-03 COVERED (disabled 400, 404, no-secret; MockMvc-vs-mock minor).
- AC-04 PARTIALLY COVERED (retry chain tested; explicit multi-thread concurrency test absent).
- AC-05 COVERED (bean absent by default; trigger seams; CR-5 nuance).
- AC-06 CONDITIONAL (forbidden-paths/whitespace PASS by patch inspection; architecture file-protocol ERROR
  resolved; CR-1/CR-2 block clearance).

## 6. Parent-Authorship Independent Assessment

Treated the candidate strictly on its merits. Coherent, well-layered, faithful to most of the contract
(constants centralization, content-identity idempotency, disabled fallback, D1-additive boundary, V18 state
table, no-secret API surface). P2 parser genuinely mirrors D1 frozen semantics. CR-1/CR-2 are ordinary
correctness bugs (Spring self-invocation trap; naming-as-substitute-for-behavior) that the existing tests do
not catch because they align with the code's assumptions rather than the contract's byte-equality bar.
Independence satisfied: both findings derived from reading source against the contract, not from SELF-CHECK
framing.

## Residual Risks

- RUNTIME: Docker/MySQL not exercised (H2 only); `ON DUPLICATE KEY UPDATE` MySQL syntax, `selectForUpdate`
  H2-vs-MySQL lock differences — verifier's job.
- `scope_json` exact string-match idempotency is internally consistent today but fragile (any future scopeJson
  field or Jackson config change breaks it). Non-blocking note.
- No multi-thread concurrency test for AC-04.
