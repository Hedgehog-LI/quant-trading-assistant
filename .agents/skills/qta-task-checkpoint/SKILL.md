---
name: qta-task-checkpoint
description: Use to persist resumable QTA task state when a slice ends, context grows, quota or interruption approaches, a blocker appears, or ownership changes. It records evidence and the next action but never claims acceptance or delivery.
when_to_use: Use at contract checkpoints, candidate commits, repair rounds, external blockers, tool switches, compaction, or handoff. Do not use as routine self-test output or as a substitute for finalization.
---

# QTA Task Checkpoint

## Purpose

Make interrupted or long-running work resumable from files without replaying the conversation.

## Trigger Conditions

Invoke when:

- Context reaches the budget defined by the task contract.
- A meaningful implementation slice finishes.
- The same failure repeats twice.
- External credentials, provider permissions, deployment, or user input blocks progress.
- The user pauses, changes tools, starts a new conversation, or requests handoff.
- Before compaction or after detecting that prior context was compressed.

Do not wait for the context window to become nearly full.

## State Model

Track every acceptance criterion as exactly one:

- `NOT_STARTED`
- `IN_PROGRESS`
- `IMPLEMENTED`
- `SELF_CHECKED`
- `BLOCKED`
- `INDEPENDENTLY_VERIFIED`

Only an independent verifier may set `INDEPENDENTLY_VERIFIED`.

## Checkpoint Process

1. Re-read the task contract and current diff.
2. Confirm the lifecycle state, contract hash, candidate hash, repair round, and failure fingerprint.
3. Validate the task control file and record role-run, wait, poll, context, compaction, and optional usage
   counters.
4. Record what changed since the previous checkpoint.
5. Map files and evidence to acceptance criteria.
6. Record commands actually run and exact outcomes without pasting unchanged logs.
7. Separate code defects, architecture debt, policy violations, and environment/external blockers.
8. State the next smallest executable action and the fresh role instance required.
9. Update only the task-local contract, control, state, and continuation handoff.
10. Leave project-level status and acceptance documents unchanged until finalization.

## Required Artifacts

Use:

- `assets/TASK_STATE_TEMPLATE.md` for machine-friendly current state.
- `assets/HANDOFF_TEMPLATE.md` for a compact continuation brief.

Store task-local state beside the active task contract. Update `docs/AI_HANDOFF.md` only for project-level
facts through `$qta-delivery-finalization`, never from an implementation checkpoint.

## Idempotency Rules

- Replace current-state sections instead of endlessly appending duplicate summaries.
- Link to evidence files rather than pasting logs.
- Do not rewrite product or architecture documents merely to narrate progress.
- Do not update `docs/AI_HANDOFF.md`, `docs/development/DEVELOPMENT_LOG.md`,
  `docs/acceptance/ACCEPTANCE_LOG.md`, or `docs/BUILD_CHECKLIST.md`.
- Preserve unresolved blockers exactly until new evidence changes them.
- Invalidate review and verification whenever the candidate hash changes.
- Persist repair counts across clean contexts; a new conversation does not reset a failure fingerprint.
- Never reuse a prior implementer, reviewer, or verifier role run after checkpoint.
- A compaction or prohibited tool call invalidates that role artifact and must be recorded as
  `POLICY_VIOLATION`.

## Context Budget

Default checkpoint thresholds:

- At 25% of a large context window: summarize discoveries and freeze scope.
- At 40%: checkpoint before any new workstream.
- At 60%: stop implementation, write handoff, continue in a clean context.
- At the first automatic compaction: checkpoint immediately and end the role run.
- Record no more than two Agent waits per role and three shell polls per command.

Tool-specific limits may require earlier checkpoints.

## Required Output

State:

- Last completed atomic step
- Current AC status table
- Changed files
- Verified and unverified dimensions
- Blockers with ownership
- Exact next action and command
- Contract/candidate/patch hashes and repair round
- Stage commit and checkpoint-push status

Never say “complete” unless `$qta-independent-verification` has produced an accepted verdict.
