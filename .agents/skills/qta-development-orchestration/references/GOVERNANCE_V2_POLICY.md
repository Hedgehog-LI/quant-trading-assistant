# QTA AI Governance V2 Policy

Load this reference only for lane selection, role dispatch, repair planning, budget enforcement, or an
architecture-gate decision.

## Risk Lanes

| Lane | Typical scope | AC cap | Blocking amendments | Required gates |
|---|---|---:|---:|---|
| L0 | Documentation or mechanical edit | 3 | 0 | bounded implementer, diff/static, clean verifier |
| L1 | Bounded single-module behavior | 5 | 1 | static, focused test, full test, review |
| L2 | Migration, transaction, compatibility, concurrency, provider, scheduler, performance | 8 | 3 | L1 plus package and final verifier |
| L3 | Funds, authorization, cross-repository contract, irreversible runtime/data, deployment-critical | 10 | 5 | L2 plus required runtime/deployment |

One AC describes one externally observable result and has at most two mandatory evidence types. An
implementation preference is not an AC. Exceeding the AC or blocking-amendment cap requires task splitting or
a parent-owned written exception before implementation.

## Disposable Role Instances

- Template identity is stable; role instances are disposable.
- Initial implementation, every repair, every candidate review, and final verification use different
  `role_run_id` and session identifiers.
- A child receives only its TaskPacket, authority paths, candidate identity, and applicable finding IDs.
- Child history is never forwarded to another role.
- A role that compacts, uses a prohibited tool, mutates a read-only snapshot, or creates a child Agent is a
  `POLICY_VIOLATION`. Its artifact is invalid even when the candidate hash is unchanged.

Every role-run record contains `roleRunId`, dispatch ID, runtime `sessionId`, role, candidate generation,
executor type, exact Agent definition, capability, execution outcome, `FRESH` context, enforcement level,
wait/poll/compaction counters, terminal status, artifact path, artifact SHA-256, and whether the parent accepted
the artifact. Only completed `SUBAGENT` artifacts may be accepted. The control file also
keeps ordered transition history, blocking amendments, repair/failure history, AC evidence bound to candidate
identity, and finalization artifact identity.
Role-run rows are appended only after terminal return and are immutable after anchoring; pending dispatch state
must not be represented by rewriting an anchored row.

Every repair-history row binds its failure fingerprint and generation edge to the accepted finding role run
(`CODE_REVIEWER` or `FINAL_VERIFIER`) and the fresh implementer role run that produced the next generation.
Every frozen candidate also has a baseline-to-candidate diff artifact path/hash; read-only reviewers consume
that artifact and must not execute Git to reconstruct it.

Current runs record `ADVISORY` even when ZCode applies a fixed template allowlist, because the client does not
expose a platform-authenticated role/session attestation to the project validator. Compensate with a read-only
snapshot/disposable worktree plus before/after tree and candidate hashes.

The workspace `PreToolUse` hook creates a first-seen session receipt under `.git/qta-governance/sessions/`;
the control gate binds the declared session and role time window to that observed receipt. Successful
control-file validations append a hash-chained snapshot under `.git/qta-governance/tasks/`, detecting ordinary
transition, repair, amendment, role, and counter rollback. Direct model access to this audit store is blocked
by the Hook. These are tamper-evident workflow controls, not a security boundary against malicious arbitrary
code running as the same OS user. `ENFORCED` remains unavailable until native signed attestation exists.

Every dispatched attempt is recorded after termination, including timeout, plan-only, failure, cancellation,
and policy violation. The parent cannot substitute for a specialist role. Initial implementation is split
into one fresh implementer per coherent slice; one slice has at most three ACs, eight expected files, and 500
production-line additions. Two timeouts on one slice require `BLOCKED` and reslicing.

L0 may omit only test design and code review. It never omits the bounded implementer or clean final verifier.
Closeout normally resumes the original task and role history; legacy implementation slices without valid
implementer evidence require one evidence-only implementer before candidate freeze.

Every Agent/Task dispatch starts with the exact two-line TaskPacket prefix from the template. A rejected
prefix is corrected and retried once; the Hook is never invoked manually and synthetic receipts are invalid.
Dispatch audit is two-phase: immutable `PENDING` receipt on `PreToolUse`, then immutable `SUCCEEDED` or
`FAILED` outcome. Delivery rejects pending-only dispatches.

Implementer and final-verifier profiles use `bypassPermissions` only to remove interactive Bash approvals.
Tool allowlists, prohibited actions, allowed paths, Hook denials, candidate identity, and evidence rules still
apply unchanged. An active `/qta-run` never calls `AskUserQuestion`: reversible choices use the recommended
option, while a genuine product, destructive, credential, or external-input blocker is persisted as
`BLOCKED` instead of waiting for a human response.

An unavailable parent session is replaced only through
`/qta-run --resume <TASK-ID> <objective-or-control-path>`. The Hook transfers a matching same-project,
non-terminal active lock and preserves the original task/control identity; implicit task stealing is blocked.

## Context And Control Budget

- 25% context: persist discoveries and decisions.
- 40% context: checkpoint; do not start a new lifecycle stage.
- 60% context: terminate and hand off to a fresh context.
- First automatic compaction: immediate checkpoint and handoff. Second compaction in one role run is invalid.
- Agent wait: one long wait plus at most one follow-up per role run.
- Shell polling: initial 30-second yield, then no more than three increasing waits (recommended 120/300
  seconds). Polling must not create status-only model turns.
- Same command + same candidate/evidence hash: run once unless the underlying input changed.
- Same failure fingerprint: at most two repair rounds across all contexts.

Percent thresholds apply only to `RUNTIME` or explicitly measured `MANUAL` telemetry. With no reliable
telemetry, persist `UNAVAILABLE + null`; never fabricate a low percentage. Deterministic role-turn,
wait/poll/repair, and first-compaction limits remain mandatory.

When reliable token telemetry exists, suggested raw-token ceilings are L0 2M, L1 6M, L2 12M, and L3 20M;
pause earlier when one task consumes 5% of the weekly allowance. Without telemetry, the deterministic limits
above are the hard proxy.

## Test And Review Economy

- Implementer: focused tests while editing; one full suite/package before final candidate freeze.
- Repair: finding-specific tests; full suite only when behavior changed and the repaired candidate is ready to
  freeze.
- Reviewer generation 1: complete contract, regression, scope, and architecture scan.
- Later reviewer generations: repair diff and affected regression surface; full reread only for behavior,
  contract, migration, or candidate-scope changes.
- Final verifier: one independent focused gate and one required full/package gate on the frozen candidate.
- Benchmark runs only when the contract has a performance AC or the repair touched a measured path.
- Test design freezes stable test IDs, AC mapping, source paths, and exact selectors. Final verification creates
  machine receipts with `scripts/run-ai-evidence-command.mjs`; implementer summaries are not receipts.

## Architecture Gate

Review both `FUNCTIONAL` and `ARCHITECTURE`. Both must pass.

Review trigger, requiring a responsibility map and six-dimension score:

- class over 400 significant lines;
- more than 20 methods;
- method over 60 lines;
- more than 10 direct dependencies;
- candidate adds more than 800 production lines.

Hard block:

- class over 600 significant lines together with more than 30 methods or more than three responsibilities;
- method over 100 lines;
- circular dependency, SQL outside the persistence boundary, business orchestration in a controller, or a
  service implementing an external/file protocol parser;
- candidate adds over 1500 production lines without independent architecture review;
- candidate adds over 3000 production lines without a second clean-context architecture review;
- an explicitly contracted layer is omitted.

A reviewer cannot waive a machine architecture error. If the detector itself is wrong, repair and validate
the detector in a separate governed task, then regenerate the candidate-bound report.

Score responsibility cohesion, layering, readability, transaction/error semantics, testability, and change
impact from 0-2. Passing requires at least 9/12 and no zero. A nonblocking debt record needs impact, owner,
target version, and a deadline no later than 30 days or the next feature in that module.

## Verdicts

- `ACCEPTED`: all mandatory ACs, dimensions, candidate identity, functional gate, and architecture gate pass.
- `CONDITIONALLY_ACCEPTED`: only a contract-declared optional runtime dimension is missing and delivery is
  explicitly permitted; never use it to hide a required L3 dimension.
- `REJECTED`: an AC, mandatory gate, candidate identity, or architecture gate fails.
- `BLOCKED`: required evidence or environment cannot be obtained without an external change.

`FINALIZED` is not a Goal terminal state. Only `DELIVERY_READY`, confirmed by
`scripts/check-ai-delivery-ready.mjs` on tracked evidence and an approved-clean worktree, may end Goal mode.
