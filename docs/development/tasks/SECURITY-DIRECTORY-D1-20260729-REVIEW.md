# Code Review: SECURITY-DIRECTORY-D1-20260729

## Reviewed Identity

- Role: `qta-code-reviewer`
- Role run ID: `CR-20260729-01`
- Contract hash: `0c16a3510ca7e8c34354c42ce78babcd1ffff3f4ffbf83d91debd74a7db6b500`
- Candidate commit: `ec101b3bfe81a117a3024af3d7aa46b683046bfa`
- Candidate tree: `a54dcedf5ba3226a05a8f34493b63f8e23f38288`
- Candidate patch SHA-256: `89270b577d1dd4a48f00a2d089fcfa53e422cd19f7935ce921f45b0fae20e45c`
- Frozen diff: `48b52b9..ec101b3bfe81a117a3024af3d7aa46b683046bfa`
- Repair round reviewed: `0`
- Result: `FINDINGS`; `REVIEW_CLEAR` not issued.

## Findings

### CR-01 — P1 — LIKE literals are not escaped

- AC: AC-03, AC-04, AC-06
- Locations: `SecurityDirectoryService.java:539`; `StockBasicMapper.xml:155,160,188`
- Scenario: `q=%%` or `_` is treated as a SQL wildcard, returns incorrect candidates and can load/score the
  full catalog before the application limit.
- Missing evidence: no `%`, `_` or escape-character query case.
- Required correction: generate one escaped literal before mapper use, use the same explicit SQL `ESCAPE`
  rule everywhere, and add correctness/candidate-size regressions.

### CR-02 — P2 — MVC binding failures fall through to HTTP 500

- AC: AC-03, AC-08
- Location: `SecurityDirectoryController.java:47-51`
- Scenario: missing `q` or `limit=abc` fails before the service and is handled by the generic 500 path.
- Missing evidence: MockMvc does not cover missing request parameters or type conversion.
- Required correction: map relevant Spring binding errors to HTTP 400 and stable `ApiResponse`; add precise
  status/body tests.

### CR-03 — P2 — `source_updated_at` loses fractional seconds

- AC: AC-02, AC-08
- Locations: `SecurityDirectoryService.java:702-705`; `V17__add_security_directory.sql:16`
- Scenario: `.900Z` becomes the whole second, producing incorrect unchanged decisions and a stale boundary up
  to almost one second early.
- Missing evidence: timestamp fixtures are whole seconds.
- Required correction: persist compatible microsecond precision and add offset/fraction import, repeat and
  freshness-boundary tests.

### CR-04 — P2 — Multiple conflicts can report `failed > totalRows`

- AC: AC-02
- Location: `SecurityDirectoryService.java:231-242`
- Scenario: three conflicting normalized rows produce `failed=4` for `totalRows=3`.
- Missing evidence: only a two-row conflict exists and failed count is not asserted.
- Required correction: count unique failed line numbers while retaining explainable errors; test three or more
  conflicts and the error bound.

### CR-05 — P2 — Side-effect snapshot test is vacuous on empty tables

- AC: AC-05
- Locations: `SecurityDirectoryIntegrationTest.java:166-168,299-307`
- Scenario: protected rows are never seeded; an empty before/after equality cannot prove content preservation.
- Required correction: seed and assert non-empty representative protected rows, then compare exact primary keys
  and content while retaining provider zero-interaction.

## Coverage Gaps And Residual Risk

- Add MVC binding and LIKE metacharacter cases.
- Add fractional-second time and three-plus conflict cases.
- Replace empty protected snapshots with non-empty fixtures.
- Benchmark uses repeated hot keys; final report must record this limitation.
- MySQL 8.4 runtime and deployment remain `NOT_VERIFIED`.

## Repair Gate

All five findings are accepted by the parent. Any code/test change invalidates this review and requires a new
candidate identity plus a complete fresh review.
