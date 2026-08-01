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

Select exactly one risk lane before creating a role. Lane reflects blast radius, not expected duration:

| Lane | Use when | Maximum ACs | Required roles and gates |
|---|---|---:|---|
| `L0` | Documentation or a mechanical low-risk edit | 3 | implementer + clean verifier; static gate |
| `L1` | Bounded single-module behavior without migration | 5 | all four roles; focused and full tests |
| `L2` | Migration, transaction, compatibility, concurrency, provider, scheduler, or performance | 8 | all four roles; package and independent verifier |
| `L3` | Funds, authorization, cross-repository contract, irreversible data/runtime, or deployment-critical change | 10 | all four roles; required runtime and deployment evidence |

Record any omitted role and why. A task cannot be downgraded after a failed gate merely to obtain a pass.
Read `references/GOVERNANCE_V2_POLICY.md` only when selecting a lane, dispatching a repair, enforcing a budget,
or deciding an architecture gate. Do not copy that reference into every TaskPacket.

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
- `L0` may omit test design/review through an explicit omission record and transition directly from
  `CANDIDATE_FROZEN` to clean `VERIFIED`.

Never run code review and final verification in parallel. Never finalize before verification. `VERIFIED`
requires both `FUNCTIONAL=PASS` and `ARCHITECTURE=PASS` for the same candidate identity.

The parent continues until one terminal state: `FINALIZED`, `BLOCKED`, or an explicit user stop. Producing a
plan, one role artifact, or one repair is not task completion. Do not start a second product task merely to
keep an overnight run busy.

## Autonomous Decision Policy

Inside the frozen contract, choose the recommended reversible implementation option without asking the user.
Ask only when product/financial meaning is unresolved, a destructive or credential-bearing action requires
authorization, or external state makes every safe path impossible. Record the decision and evidence in the
control file; do not create repeated confirmation turns.

## Task Packet

Give every role only:

- `task_id`, `lane`, `role_run_id`, and assigned AC IDs.
- Contract path and `contract_hash`.
- Authority paths, not copied document contents.
- Baseline commit, branch, pre-existing dirty-path manifest, and allowed write paths.
- Candidate identity: immutable commit/tree/patch hashes, or snapshot manifest path/hashes when Git writes
  are not authorized.
- Frozen baseline-to-candidate diff artifact path/hash so read-only reviewers never need Bash or inherited
  parent output.
- Previous finding IDs and `repair_round` when repairing.
- Required output artifact and stop conditions.
- Runtime enforcement level: currently `ADVISORY`, plus the compensating hash/worktree check and optional
  Hook-observed session receipt.

Do not pass the complete parent conversation or unrelated repository history.
Create or update the machine control file from `assets/TASK_CONTROL_TEMPLATE.json`. Run
`node scripts/check-ai-task-control.mjs <control-file>` before each role dispatch and lifecycle transition.
This gate validates the JSON schema, actual contract/candidate/artifact hashes, transition and repair history,
role generations, ZCode runtime session receipts, hash-chained control anchors, SNAPSHOT changed-path coverage,
AC evidence, quality verdicts, and finalization identity. A prose status cannot override it.

## Role Dispatch

1. Parent drafts the contract with `$qta-task-contract`.
2. A fresh `qta-test-designer` instance challenges the draft and returns an artifact payload.
3. Parent persists accepted amendments and freezes `contract_hash`.
4. A fresh `qta-implementer` instance changes only assigned paths and returns `SELF_CHECKED` evidence.
5. Parent freezes candidate identity:
   - `COMMIT`: create the candidate commit and record commit/tree/patch hashes.
   - `SNAPSHOT`: create a deterministic allowlisted candidate manifest and record manifest/entry-set hashes.
   - In both modes, persist an exact frozen diff artifact and its SHA-256 for read-only review.
6. A fresh `qta-code-reviewer` instance reviews exactly that frozen candidate on functional and architecture
   tracks.
7. Parent sends one consolidated finding set to a new implementer instance for each repair round; any new
   candidate invalidates prior review.
8. Every candidate generation receives a new reviewer instance. Never reuse the prior reviewer conversation.
9. After `REVIEW_CLEAR`, a fresh `qta-final-verifier` verifies the same candidate in a disposable worktree.
10. Parent persists the verdict. Only permitted acceptance routes to `$qta-delivery-finalization`.

Read-only roles return structured artifact payloads. The parent writes those payloads to task-local files.
A fixed role is an immutable template, not a persistent Agent. Every role run has a unique `role_run_id` and
session identifier, receives no inherited child history, and is destroyed after returning its artifact. A
role run that compacts, invokes a prohibited tool, or reuses a prior session is `POLICY_VIOLATION`; discard its
artifact and rerun once in a fresh instance.

The workspace Hook may create a session first-seen receipt under `.git/qta-governance/sessions/`; record its
path and the role start/finish timestamps. The control gate rejects a reused, wrong-project, or out-of-window
receipt. Every successful control validation appends a hash-chained anchor under
`.git/qta-governance/tasks/`; direct role access to that store is prohibited. These same-user local controls
are tamper-evident workflow guards, not platform-authenticated security evidence, so current runs must remain
`ADVISORY` plus compensating isolation.
Append a role-run row only after that role reaches a terminal status; anchored role rows are immutable events,
not mutable RUNNING records.

Codex sandbox profiles may expose `.git` as read-only. In that case, request scoped permission only for
`node scripts/check-ai-task-control.mjs`; never disable the anchor. The validator returns this recovery
instruction on `EPERM/EACCES` instead of a raw stack trace.

Do not claim `ENFORCED` until the client exposes a platform-authenticated role/session attestation that the
validator can verify independently. Arbitrary Bash running as the same OS user can invoke or rewrite local
scripts; local Hook/receipt/anchor evidence cannot prove resistance to a malicious same-user agent.

## Git And Push Policy

The parent is the sole Git owner. Child roles must not stage, commit, rebase, merge, or push.

Before any Git write, read the frozen contract's `git_automation` value:

- `NONE`: prepare paths and commit messages only.
- `COMMIT`: create approved stage commits locally.
- `COMMIT_AND_CHECKPOINT_PUSH`: also push complete stage commits to the task branch.
- `DELIVERY_PUSH`: also push the accepted finalization revision to its approved target.

The value must come from explicit user authorization recorded by the parent. Missing authorization means
`NONE`; never infer permission from an autonomous-development request alone.

Preflight the selected Git mode before implementation. If the runtime cannot obtain the required scoped
approval, freeze `NONE + SNAPSHOT` at contract time. Do not discover a missing commit/push permission during
finalization.

When the value is `NONE`, use `SNAPSHOT` candidate mode. Run
`scripts/create-candidate-manifest.mjs` over the exact candidate path allowlist, store the generated manifest
beside task state, and pass its manifest and entry-set SHA-256 to reviewer and verifier. Regenerate and compare
the manifest before and after each gate; any mismatch invalidates earlier evidence.

For every candidate mode, the parent persists a baseline-to-candidate diff artifact under the task directory.
In `COMMIT` mode its SHA-256 must equal `patchSha256`; in `SNAPSHOT` mode the control gate validates the diff
artifact hash and every current manifest entry. Reviewer input must reference this artifact instead of asking
the read-only role to execute Git.
The SNAPSHOT manifest must cover every repository path changed from `baselineCommit`, except the frozen
pre-existing dirty-path list and explicitly identified task-control/evidence metadata. An omitted changed path
invalidates the candidate.

Use a dedicated task branch for `L1`, `L2`, and `L3` work. Create traceable commits at these gates:

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
- Production-code repairs receive finding-specific tests and an affected-diff review. Pure test/evidence
  repairs receive incremental review unless identity or behavior changed.
- Run focused tests during implementation and repair. Run the full suite/package once before freezing the
  final candidate and once independently in verification; do not repeat an unchanged full gate.
- At 25% context, persist discoveries. At 40%, do not open a new stage; checkpoint. At 60%, terminate the
  role and continue in a fresh context. The first compaction forces handoff; a second is prohibited.
- Record `contextMeasurement=UNAVAILABLE` and `contextPercent=null` when the runtime exposes no reliable
  telemetry. Do not invent a percentage; enforce turn, wait, poll, repair, and compaction limits instead.
- Use one long wait for a role and at most one follow-up wait. A long-running shell command may be polled at
  most three times with increasing waits. Status-only wakeups and repeated unchanged commands are prohibited.
- When usage telemetry is available, stop at the lane budget or 5% of the weekly allowance. Otherwise enforce
  the role-turn, wait, poll, repair, and context limits recorded in the control file.

## Architecture Gate

Before `REVIEW_CLEAR`, run `node scripts/check-ai-architecture.mjs --base <baseline>
--architecture-review-count <count>` in COMMIT mode, or add `--manifest <candidate-manifest>` in SNAPSHOT mode,
and require an explicit responsibility map for triggered files.
Architecture warnings require reviewer disposition; architecture
errors block the candidate. A contract that explicitly requires a layer cannot omit it without a time-bounded
ADR exception.

## Required Output

Return the current state, lane, contract hash, candidate mode/identity, role artifacts, exact evidence,
commit/push status, unverified dimensions, blockers, and next state. Do not summarize an incomplete lifecycle
as delivered.
