# AI Governance Close-out Hardening Contract

- Task ID: `AI-GOVERNANCE-CLOSEOUT-HARDENING-20260802`
- Lane: `L1`
- Baseline: `6bdd8d5c26ec76395025d92cbc6deb6fd273801e`
- Branch: `codex/ai-governance-closeout-hardening-20260802`
- Objective: make QTA governed role dispatch, protected-branch enforcement, activity-lock lifecycle, and
  close-out role requirements deterministic enough for unattended ZCode runs.

## Facts

1. The failed D2 reviewer prompt used the canonical Task Packet header but did not contain a standalone
   `- Dispatch ID: ...` field. The Hook therefore rejected the real Agent call correctly.
2. The parent then invoked `scripts/zcode-governance-hook.mjs` manually with a synthetic valid packet. That
   created a dispatch receipt and active lock even though no Agent was launched.
3. `SECURITY-DIRECTORY-D2-20260802-FINALIZE` omitted the implementer while the schema-v3 validator requires
   an accepted implementer for every frozen generation and implementation slice.
4. A governed run could commit documentation directly on `main`; the existing Hook blocked direct pushes
   but did not inspect the current branch before file writes or commits.

## Decisions

1. Keep the validator's implementer requirement. L0 may omit test design and code review where explicitly
   justified, but it may not omit both implementation ownership and independent final verification.
2. A close-out is normally a continuation stage of the existing governed task, not a new task whose only
   implementation is acceptance evidence. A legacy recovery task must still assign an evidence implementer.
3. Keep Task Packet parsing strict. Improve the error with the exact required two-line prefix and prohibit
   direct execution of the Hook from an active governed Bash session.
4. Record Agent dispatch in two phases: `PENDING` at `PreToolUse`, then `SUCCEEDED` or `FAILED` at
   `PostToolUse`/`PostToolUseFailure`. Delivery rejects pending dispatch receipts.
5. While a parent session owns an active governed task, file writes and Git history writes on `main` or
   `master` are blocked. Creating or switching to a `codex/*` task branch remains allowed.
6. A replacement session may explicitly resume the exact non-terminal task with
   `/qta-run --resume <TASK-ID> <objective-or-control-path>`; implicit takeover remains prohibited.

## Scope

### In Scope

- `scripts/zcode-governance-hook.mjs`
- `scripts/check-ai-delivery-ready.mjs`
- `scripts/tests/ai-governance.test.mjs`
- `.zcode/config.json`
- `.zcode/commands/qta-run.md`
- `.agents/skills/qta-development-orchestration/**` and synchronized `.claude` mirror
- `.agents/skills/qta-task-contract/SKILL.md` and synchronized `.claude` mirror
- `docs/ai/SKILL_AND_AGENT_GOVERNANCE.md`
- This task's contract, test design, review, verification, and handoff artifacts

### Out Of Scope

- Trading, market-data, database, API, frontend, or deployment behavior.
- Weakening independent verification or treating parent-run tests as independent acceptance.
- Rewriting historical task controls or pretending the unfinished D2 close-out is delivered.

## Acceptance Criteria

| ID | Observable outcome | Required evidence |
|---|---|---|
| AC-01 | A malformed fixed-role Agent packet is rejected with the exact canonical prefix required for one bounded retry; a valid canonical packet is accepted. | Automated Hook tests |
| AC-02 | Direct/manual Hook execution is blocked in a governed Bash call, and dispatch receipts transition from `PENDING` to `SUCCEEDED` or `FAILED`; delivery rejects `PENDING`. | Automated Hook and delivery tests |
| AC-03 | An active governed task blocks file writes, commits, merges, cherry-picks, and equivalent history writes on `main`/`master`, while task-branch work remains allowed. | Temporary-Git-repository tests |
| AC-04 | L0/close-out documentation, templates, and commands agree that implementer omission is invalid; close-out resumes the original task or assigns a bounded evidence implementer. | Static governance validation |
| AC-05 | `BLOCKED` and valid `DELIVERY_READY` stop events release their activity lock; terminal stale locks are reconciled, non-terminal locks are preserved from implicit takeover, and an exact explicit resume transfers ownership. | Automated lifecycle tests |

## Implementation Slices

### Slice A: Machine enforcement

- ACs: AC-01, AC-02, AC-03
- Write paths: `scripts/zcode-governance-hook.mjs`, `scripts/check-ai-delivery-ready.mjs`,
  `scripts/tests/ai-governance.test.mjs`, `.zcode/config.json`
- Maximum expected files: 4

### Slice B: Workflow contract alignment

- ACs: AC-04, AC-05
- Write paths: `.zcode/commands/qta-run.md`, `.agents/skills/qta-development-orchestration/**`,
  `.agents/skills/qta-task-contract/SKILL.md`, synchronized `.claude` mirrors,
  `docs/ai/SKILL_AND_AGENT_GOVERNANCE.md`, and focused lifecycle tests
- Maximum expected files: 8

## Frozen Verification Inventory

1. Focused Node tests for canonical packets, manual Hook blocking, two-phase receipts, protected branches,
   pending-delivery rejection, and terminal-lock reconciliation.
2. `node --test scripts/tests/ai-governance.test.mjs`
3. `node scripts/validate-ai-governance.mjs`
4. `node scripts/evaluate-skill-triggers.mjs`
5. `node scripts/run-ai-governance-gates.mjs`
6. `git diff --check`

## Stop Conditions

- Pass: all acceptance criteria have machine evidence, a clean independent review, and independent
  verification of the frozen candidate.
- Block: the same failure fingerprint repeats twice, the client lacks a required supported Hook event, or
  fixing the issue would require weakening role independence.
- No Docker, Maven, frontend build, push, deployment, or product-code edits are required.
