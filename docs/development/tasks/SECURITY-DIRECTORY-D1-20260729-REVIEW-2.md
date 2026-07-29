# Code Review Generation 2: SECURITY-DIRECTORY-D1-20260729

## Reviewed Identity

- Role run: `CR-20260729-02`
- Review generation / repair round: `2 / 1`
- Contract hash: `0c16a3510ca7e8c34354c42ce78babcd1ffff3f4ffbf83d91debd74a7db6b500`
- Candidate commit: `483503b3bdb27317cbb3b8c950d5fc547c168adb`
- Candidate tree: `5119591d87cec2cb2b7d69f8da55a33d6bb7b07f`
- Full patch SHA-256: `1a3523b2c3dce268d3c27ce6eea072109044b67722c19dbdda2d6e766efbad11`
- Repair-1 patch SHA-256: `cd84201d0bfdd3272d8e2fc94c4ec4fc12a39a90b3a0370b8511211fa527339b`
- Result: `FINDINGS`; `REVIEW_CLEAR` not issued.

## Previous Finding Closure

- CR-01..CR-04: closed.
- CR-05: original three-table empty-snapshot defect closed; broader AC-05 gap is now CR-10.

## Findings

### CR-06 — P1 — Default MySQL collation can violate normalized alias identity

- AC: AC-01..AC-04
- Locations: `V17__add_security_directory.sql:35,43-44`; `StockBasicMapper.xml:150,155-160`
- Scenario: accent-insensitive MySQL collation may collapse `resume` and `résumé` in uniqueness and may
  misclassify exact/prefix matchedBy, while H2 treats them distinctly.
- Required correction: make alias identity and final match scoring code-point deterministic across H2/MySQL,
  add accent-sensitive contrasts, and provide safe disposable MySQL evidence when available.

### CR-07 — P2 — Conflicting alias metadata is silently deduplicated

- AC: AC-02
- Locations: `SecurityDirectoryService.java:333-363,614-619`
- Scenario: aliases sharing type/normalized text but differing in language/display are silently collapsed in a
  row or across duplicate security rows.
- Required correction: only fully identical persisted alias metadata may deduplicate; metadata conflicts must
  return stable line errors and atomically roll back.

### CR-08 — P2 — Frozen CSV negative matrix lacks isolated coverage

- AC: AC-02 / test design A-04
- Location: `SecurityDirectoryIntegrationTest.java:152-200`
- Missing isolated cases: required header, canonical format, enum branches, date/timestamp, alias grammar/type
  and the 200,001-row boundary.
- Required correction: parameterized single-defect tests must assert code/line/field/envelope and unchanged DB.

### CR-09 — P2 — Ranking fixtures do not isolate tie-break precedence

- AC: AC-04 / test design A-09
- Location: `SecurityDirectoryIntegrationTest.java:241-287`
- Scenario: LISTED and preferred market point to the same row, so a reversed comparator still passes; other
  normalized-name/canonical/repeated-order and full matchedBy precedence cases are missing.
- Required correction: opposing fixtures isolate each comparator level and repeated calls compare full order.

### CR-10 — P2 — Side-effect snapshots omit collection and portfolio price data

- AC: AC-05
- Location: `SecurityDirectoryIntegrationTest.java:394-426`
- Required correction: seed non-empty collection plan/state and `portfolio_price_snapshot`, then compare
  primary keys/content along with existing protected tables and provider zero interaction.

## Residual Risk

- H2 hot-key benchmark is not mixed-key MySQL evidence; report already states the limitation.
- MySQL, Docker HTTP and deployment remain `NOT_VERIFIED` pending safe runtime assessment.

Any repair invalidates this review and requires a complete generation 3 review of the full candidate.
