# Architecture Gate (generation 1): SECURITY-DIRECTORY-D3-20260802

- Role run ID: `ARCH-G1-20260802-01` (parent-run architecture gate acting as the generation-1 finding role)
- Generation: 1
- Command: `node scripts/check-ai-architecture.mjs --base 8e4447e --architecture-review-count 1`
- Candidate at gate: generation-1 candidate `62a72704583bcce98679b25dd2a9fd5becc459a6`
- Verdict: `FAIL` (one blocking architecture ERROR; candidate requires repair)

## Blocking finding

- **CR-arch-1 (ERROR)**: `SecurityDirectorySyncService.java` combines file/protocol parsing with
  persistence. responsibilities = `transaction|persistence|file-protocol|provider|service-layer`.
  The service referenced the `provider.csv` package (`CsvSnapshotSecurityDirectoryProvider.computeIdentity`,
  `SecurityDirectoryCsvParser.sameDirectoryData`) for snapshot-identity and directory-data comparison, which
  the architecture heuristic flagged as a service layer parsing file/protocol data.

## Non-blocking warnings (repair also addressed)

- `SecurityDirectoryCsvParser.java`: 409 lines / 30 methods / 69-line longest method (informational; the
  parser legitimately mirrors the frozen D1 rule set and is exercised by equivalence tests).
- `SecurityDirectorySyncService.java`: longest method 89 lines (post-repair reduced).

## Repair (generation 2, candidate `0070304`)

Extracted a neutral `util/SecurityDirectoryIdentityCalculator` (computeSnapshotHash, snapshotIdFromHash,
sameDirectoryData) and rewired `CsvSnapshotSecurityDirectoryProvider`, `SecurityDirectoryCsvParser`, and
`SecurityDirectorySyncService` to use it. The service no longer imports `provider.csv.*`; its responsibility
is now `transaction|persistence|provider|service-layer` (no `file-protocol`). Re-run of the architecture gate
on the repaired candidate: `Architecture gate: files=17, additions=1613, warnings=5, errors=1` — the
file-protocol+persistence ERROR is resolved; the remaining single ERROR is the size-based
"requires an independent architecture review", which is exactly the independent `qta-code-reviewer`'s job.

## Honest note

The architecture gate is a parent-run script, not a fresh `qta-code-reviewer` sub-agent. Per the
orchestration rule, the independent code review on the repaired generation-2 candidate still follows
(fresh `qta-code-reviewer` instance, separate context).
