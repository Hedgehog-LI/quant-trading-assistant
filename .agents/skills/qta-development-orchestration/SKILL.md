---
name: qta-development-orchestration
description: Use as the parent-only controller for a bounded QTA development lifecycle spanning contract, fixed roles, candidate commits, review, verification, and finalization. It coordinates work but never replaces specialist roles.
when_to_use: Use for autonomous, standard, cross-repository, long-running, high-risk, or explicitly end-to-end QTA delivery. Do not invoke inside a child agent, for a read-only question, or for a trivial edit that needs no multi-role lifecycle.
---

# QTA Development Orchestration

## Purpose

Run a deterministic development lifecycle from the parent context without replaying full history, allowing
child roles to redefine completion, or accepting a candidate that differs from the reviewed revision.

This Skill is a controller. It does not implement, review, or verify work itself.

## Trigger Conditions

Invoke when:

- The user requests end-to-end, autonomous, goal-mode, overnight, or multi-stage delivery.
- A standard or high-risk task needs fixed role separation.
- Prior attempts looped without a stable candidate or evidence-backed verdict.
- Backend and frontend, database, provider, scheduler, security, deployment, or financial semantics interact.

Do not invoke from a child Agent or merely because the user mentioned experts, agents, or teams.

## Lane Selection

Select exactly one lane before creating a role:

| Lane | Use when | Required roles |
|---|---|---|
| `TRIVIAL` | One low-risk behavior, narrow diff, explicit expected result | implementer + final verifier; contract-lite |
| `STANDARD` | Multi-file feature or defect with bounded risk | test designer + implementer + code reviewer + final verifier |
| `LONG_HIGH_RISK` | Cross-repository, data migration, scheduler, provider, security, financial, deployment, or long-running work | all four roles + dedicated branch/worktree + checkpoints |

Record any omitted role and why. A task cannot be downgraded after a failed gate merely to obtain a pass.

## Parent State Machine

Run these states in order:

```text
CONTEXT_READY
  -> CONTRACT_DRAFTED
  -> TEST_DESIGN_READY
  -> CONTRACT_FROZEN
  -> IMPLEMENTING
  -> SELF_CHECKED
  -> CANDIDATE_FROZEN
  -> REVIEW_CLEAR
  -> VERIFIED
  -> FINALIZED
```

Allowed backward transitions:

- `REVIEW_CLEAR` failure -> `IMPLEMENTING` with a numbered finding set.
- `VERIFIED` failure -> `IMPLEMENTING` with verifier findings.
- Any state -> `BLOCKED` when the same failure fingerprint repeats twice without new evidence.

Never run code review and final verification in parallel. Never finalize before verification.

## Task Packet

Give every role only:

- `task_id`, `lane`, `role_run_id`, and assigned AC IDs.
- Contract path and `contract_hash`.
- Authority paths, not copied document contents.
- Baseline commit, branch, pre-existing dirty-path manifest, and allowed write paths.
- Candidate identity: immutable commit/tree/patch hashes, or snapshot manifest path/hashes when Git writes
  are not authorized.
- Previous finding IDs and `repair_round` when repairing.
- Required output artifact and stop conditions.

Do not pass the complete parent conversation or unrelated repository history.

## Role Dispatch

1. Parent drafts the contract with `$qta-task-contract`.
2. `qta-test-designer` challenges the draft and returns an artifact payload.
3. Parent persists accepted amendments and freezes `contract_hash`.
4. `qta-implementer` changes only assigned paths and returns `SELF_CHECKED` evidence.
5. Parent freezes candidate identity:
   - `COMMIT`: create the candidate commit and record commit/tree/patch hashes.
   - `SNAPSHOT`: create a deterministic allowlisted candidate manifest and record manifest/entry-set hashes.
6. `qta-code-reviewer` reviews exactly that frozen candidate.
7. Parent sends findings to a new implementer repair round; any new candidate invalidates prior review.
8. After `REVIEW_CLEAR`, `qta-final-verifier` verifies the same candidate in a disposable worktree.
9. Parent persists the verdict. Only permitted acceptance routes to `$qta-delivery-finalization`.

Read-only roles return structured artifact payloads. The parent writes those payloads to task-local files.

## Git And Push Policy

The parent is the sole Git owner. Child roles must not stage, commit, rebase, merge, or push.

Before any Git write, read the frozen contract's `git_automation` value:

- `NONE`: prepare paths and commit messages only.
- `COMMIT`: create approved stage commits locally.
- `COMMIT_AND_CHECKPOINT_PUSH`: also push complete stage commits to the task branch.
- `DELIVERY_PUSH`: also push the accepted finalization revision to its approved target.

The value must come from explicit user authorization recorded by the parent. Missing authorization means
`NONE`; never infer permission from an autonomous-development request alone.

When the value is `NONE`, use `SNAPSHOT` candidate mode. Run
`scripts/create-candidate-manifest.mjs` over the exact candidate path allowlist, store the generated manifest
beside task state, and pass its manifest and entry-set SHA-256 to reviewer and verifier. Regenerate and compare
the manifest before and after each gate; any mismatch invalidates earlier evidence.

Use a dedicated task branch for `STANDARD` and `LONG_HIGH_RISK` work. Create traceable commits at these gates:

1. `contract`: contract and test design frozen.
2. `candidate`: implementation is `SELF_CHECKED`.
3. `repair-N`: one commit per accepted finding set.
4. `finalization`: independently accepted documentation and delivery records.

Before every commit:

- Confirm changed paths are inside the task packet.
- Exclude pre-existing dirty and secret-bearing files.
- Run the stage's required checks.
- Prepare the task-state fields that are knowable before the commit.

After a stage commit:

1. Compute its immutable commit/tree/patch identity.
2. Update task state with that identity.
3. If checkpoint persistence is authorized, create a separate metadata-only `checkpoint` commit containing
   task state and role artifacts, then push both commits to the task branch.

The checkpoint commit is orchestration metadata, not a new candidate. Reviewer and verifier continue to target
the recorded candidate commit/tree or snapshot manifest, not task-branch `HEAD`.

A checkpoint push may push the task branch after a complete stage commit when remote access is available.
It is backup only and must not be described as deployable. A delivery push is allowed only after the accepted
candidate remains unchanged and finalization completes. Never automatically push directly to the protected or
default branch, force-push, or hide a push failure.

## Repair And Stop Rules

- Persist `repair_round`, finding IDs, and a normalized failure fingerprint in task state.
- Run at most two repair rounds for the same failure fingerprint.
- A changed contract requires a new contract version and invalidates candidate/review/verdict evidence.
- A changed candidate invalidates review and verification evidence.
- Missing credentials or runtime access is `BLOCKED` or `NOT_VERIFIED`, never a simulated pass.
- Checkpoint before 40% context use and hand off before 60%.

## Required Output

Return the current state, lane, contract hash, candidate mode/identity, role artifacts, exact evidence,
commit/push status, unverified dimensions, blockers, and next state. Do not summarize an incomplete lifecycle
as delivered.
