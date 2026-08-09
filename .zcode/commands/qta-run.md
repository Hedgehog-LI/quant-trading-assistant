---
description: Run the bounded QTA development lifecycle with fixed roles, frozen evidence, and stage commits.
argument-hint: <task objective or task-contract path>
skills: qta-development-orchestration
disable-noninteractive: false
---

Use `$qta-development-orchestration` as the parent controller.

Task input:

$ARGUMENTS

To replace an unavailable parent session, invoke
`/qta-run --resume <TASK-ID> <objective-or-control-path>`. Resume only the exact active Task ID; do not start a
new task, delete the active lock, or fabricate dispatch receipts.

Do not pass the full conversation to child roles. Select one lane, persist the task packet and state, enforce
ordered role gates, and stop on the Skill's repair or evidence conditions.

Use L0-L3 risk lanes and create schema-v3 `<TASK-ID>-CONTROL.json` from the Skill's
`TASK_CONTROL_TEMPLATE.json`. Freeze bounded implementation slices and the test inventory before dispatch.
Validate it before every dispatch and transition. Every implementation, repair, review generation, and final
verification uses a fresh role/session and ends after one artifact. Use one long Agent wait plus at most one
follow-up (two waits total); never perform status polling. Require both functional and architecture gates on
the frozen candidate before finalization.

Every fixed-role Agent/Task prompt starts with the exact canonical prefix below, copied from the TaskPacket
template. Do not paraphrase it or place the dispatch ID elsewhere:

```text
# Task Packet: <TASK-ID> / <ROLE> / <ROLE-RUN-ID>
- Dispatch ID: <DISPATCH-ID>
```

If the Hook rejects this prefix, fix that same packet and retry once. Never run the Hook manually and never
submit a synthetic packet to manufacture a receipt. The Hook records dispatch as `PENDING` before the Agent
call and writes a separate `SUCCEEDED` or `FAILED` terminal outcome after it returns.

The parent coordinator must never implement, review, or verify when a specialist times out or returns only a
plan. Record every failed dispatch. Two timeouts for one slice require `BLOCKED` and reslicing. The final
verifier must be execution-capable and create machine receipts with `scripts/run-ai-evidence-command.mjs`.
Architecture errors are hard failures and cannot be reinterpreted by prose.

Advance through the state machine toward `DELIVERY_READY`, `BLOCKED`, or the user explicitly stopping.
`FINALIZED`, a plan, or one subagent response is not completion. Goal success is forbidden until
`node scripts/check-ai-delivery-ready.mjs <TASK_CONTROL>` exits `0`; do not ask a model-only completion judge
to override that command.

This command does not install a Stop Hook and does not force another model turn. ZCode Goal mode, when used,
is the only continuation controller. If the current turn must end before a terminal state, persist
`CHECKPOINTED` plus exactly one next action. A failed gate is evidence, not permission to loop.
Hook policy failures are terminal for the attempted tool call: a failed child dispatch is recorded as failed,
while a child failure without an accepted PreToolUse receipt is ignored as a non-dispatch. Never retry the same
rejected dispatch more than once.

`L0` may omit test designer and code reviewer only; it must still dispatch a bounded implementer and a clean
final verifier. Closeout work resumes the original task instead of inventing a second lifecycle. A legacy
implementation slice without valid implementer evidence requires an evidence-only implementer run before
candidate freeze.

Create or switch to the frozen task branch before the first file write. While a governed task is active, do
not modify, stage, commit, merge, cherry-pick, revert, or tag on `main`/`master`.

`/qta-run` is unattended. Neither the parent nor a child role may call `AskUserQuestion` while it is active. For a reversible engineering
choice, select the documented or clearly recommended option and record the decision. If unresolved
product/financial meaning, destructive or credential-bearing authorization, or an external dependency makes
all safe paths impossible, persist an evidence-backed `BLOCKED` checkpoint and stop instead of waiting for
the user. Do not expand into another product task.

When progress cannot continue within the frozen repair, role-run, context, or timeout limits, persist
`BLOCKED` with evidence. Never create a fake `DELIVERY_READY` state merely to stop, and never create another
task ID to reset a spent budget.
