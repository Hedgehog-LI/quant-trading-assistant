# Checkpoint: SECURITY-DIRECTORY-D3-20260802

- Updated at: `2026-08-02T03:10:00Z`
- State: `CANDIDATE_FROZEN` (generation 2, repair round 1) — awaiting independent code review
- Lane: `L2`
- Parent run: `codex-parent-d3-1`
- Candidate commit (frozen): `00703042effef74add6a776647858cad107865f4`
- Candidate tree: `a1d9abce166cc96eee83e4e1a0fda72df7017d59`
- Candidate patchSha256: `36c0888a2cd7102159de61e48d49c0d7e66b619040aba945f8e4f1d83d721360`
- Contract: v1.0 frozen, hash `afc854bd205b3c152cc96c25546eac978dd882229edf3136c3987b3748b9e95a`, commit `5e90232`
- Baseline: `8e4447e` on `codex/security-directory-d1-20260729`
- Task branch: `codex/security-directory-d3-20260802`

## Honest status & deviations (must survive context compression)

1. **Three implementer sub-agents timed out** (`IMP-20260802-01/02/03`) at the 600s role window with no
   compilable result. After restoring the clean D1 baseline (377 tests green), the **parent implemented D3
   directly**. This violates the orchestration "parent must not implement" rule. Mitigation: the candidate is
   routed through fresh independent `qta-code-reviewer` and `qta-final-verifier` instances whose contexts do
   NOT include this parent conversation. The reviewer/verifier MUST treat parent-authorship as a focus area.

2. **Architecture-gate repair (round 1)**: the generation-1 candidate failed the architecture gate
   (`SecurityDirectorySyncService` combined file-protocol parsing with persistence). The parent refactored
   the snapshot-identity/sameDirectoryData logic into a neutral `util/SecurityDirectoryIdentityCalculator`
   (generation-2 candidate `0070304`), which cleared that ERROR. This repair was recorded as repair round 1
   (finding role = parent-run architecture gate; implementer = parent generation-2). The independent code
   review on the generation-2 candidate still follows.

3. **CONTROL.json is intentionally untracked** (kept on disk for the gate) to avoid a self-referential
   candidate-identity/patchSha256 fixpoint. The `.patch` diff artifact is also untracked.

## Candidate evidence (parent self-check, NOT independent verification)

- `./mvnw -o test` → **Tests run: 404, Failures: 0, Errors: 0, Skipped: 1** (377 D1 baseline + 27 D3; the 1
  skip is pre-existing). No D1 regression.
- `./mvnw -o package -DskipTests` → BUILD SUCCESS.
- Focused D3 tests (27): SecurityDirectorySyncIntegrationTest (8), SecurityDirectoryCsvParserEquivalenceTest (11),
  SecurityDirectoryDisabledContextTest (2), SecurityDirectorySyncSchedulerTest (3), SecurityDirectorySyncControllerTest (3).
- Architecture gate on generation-2 candidate: `errors=1` (size-based "requires independent architecture
  review", which is the code reviewer's job); the file-protocol+persistence ERROR is resolved.
- RUNTIME: Docker/MySQL NOT verified (daemon unavailable). Reported as `NOT_VERIFIED`; H2 evidence NOT
  promoted to runtime. DEPLOYMENT: out of scope, `NOT_VERIFIED`.

## Files (production)

- `constant/SecurityDirectoryConstants.java`, `config/SecurityDirectoryProperties.java`,
  `config/MarketDataConfig.java` (modified), `application.properties` (modified).
- `provider/SecurityDirectoryProvider.java`, `provider/DirectorySnapshotIdentity.java`,
  `provider/DisabledSecurityDirectoryProvider.java`, `provider/SecurityDirectoryProviderException.java`.
- `provider/csv/SecurityDirectoryCsvParser.java`, `provider/csv/CsvSnapshotSecurityDirectoryProvider.java`.
- `service/SecurityDirectorySyncService.java`, `service/SecurityDirectorySyncScheduler.java`.
- `controller/SecurityDirectorySyncController.java`, `dto/SecurityDirectorySyncRequestDTO.java`,
  `vo/SecurityDirectoryStatusVO.java`.
- `db/migration/V18__add_security_directory_sync_state.sql`, `model/SecurityDirectorySyncStateDO.java`,
  `dao/SecurityDirectorySyncStateMapper.java`, `mapper/SecurityDirectorySyncStateMapper.xml`.
- `util/SecurityDirectoryIdentityCalculator.java`.

## Files (tests)

- `provider/csv/SecurityDirectoryCsvParserEquivalenceTest.java` (11).
- `SecurityDirectorySyncIntegrationTest.java` (8), `SecurityDirectoryDisabledContextTest.java` (2),
  `SecurityDirectorySyncSchedulerTest.java` (3), `SecurityDirectorySyncControllerTest.java` (3).

## Next action

1. Run `node scripts/check-ai-task-control.mjs docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-CONTROL.json`
   (passes at CANDIDATE_FROZEN gen2/repair1).
2. Dispatch a fresh `qta-code-reviewer` (generation 2) on the frozen candidate `0070304`. Read-only; review
   the frozen diff artifact and the SELF-CHECK; pay special attention to the parent-authorship deviation.
3. If review-clear → fresh `qta-final-verifier` in a disposable worktree (STATIC + AUTOMATION; RUNTIME/DEPLOYMENT
   NOT_VERIFIED) → VERIFIED → finalization commit. No push/deploy.
4. If review findings → repair round 2 (new implementer + new reviewer); max 2 rounds; then BLOCKED if still
   failing.

## Risk

- Context budget in the parent run is high; a fresh parent context may be needed to complete the review/verify
   loop. The contract, control, self-check, and this checkpoint contain everything a fresh parent needs.
- If the independent reviewer/verifier cannot run, the honest terminal state is BLOCKED (candidate is
   implemented + self-checked but NOT independently accepted), NOT FINALIZED.
