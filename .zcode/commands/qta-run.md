---
description: Run the bounded QTA development lifecycle with fixed roles, frozen evidence, and stage commits.
argument-hint: <task objective or task-contract path>
skills: qta-development-orchestration
disable-noninteractive: false
---

Use `$qta-development-orchestration` as the parent controller.

Task input:

$ARGUMENTS

Do not pass the full conversation to child roles. Select one lane, persist the task packet and state, enforce
ordered role gates, and stop on the Skill's repair or evidence conditions.

Use L0-L3 risk lanes and create schema-v3 `<TASK-ID>-CONTROL.json` from the Skill's
`TASK_CONTROL_TEMPLATE.json`. Freeze bounded implementation slices and the test inventory before dispatch.
Validate it before every dispatch and transition. Every implementation, repair, review generation, and final
verification uses a fresh role/session and ends after one artifact. Use one long Agent wait plus at most one
follow-up (two waits total); never perform status polling. Require both functional and architecture gates on
the frozen candidate before finalization.

The parent coordinator must never implement, review, or verify when a specialist times out or returns only a
plan. Record every failed dispatch. Two timeouts for one slice require `BLOCKED` and reslicing. The final
verifier must be execution-capable and create machine receipts with `scripts/run-ai-evidence-command.mjs`.
Architecture errors are hard failures and cannot be reinterpreted by prose.

Continue through the state machine until `DELIVERY_READY`, `BLOCKED`, or the user explicitly stops.
`FINALIZED`, a plan, or one subagent response is not completion. Goal success is forbidden until
`node scripts/check-ai-delivery-ready.mjs <TASK_CONTROL>` exits `0`; do not ask a model-only completion judge
to override that command.

`/qta-run` is unattended. Never call `AskUserQuestion` while it is active. For a reversible engineering
choice, select the documented or clearly recommended option and record the decision. If unresolved
product/financial meaning, destructive or credential-bearing authorization, or an external dependency makes
all safe paths impossible, persist an evidence-backed `BLOCKED` checkpoint and stop instead of waiting for
the user. Do not expand into another product task.

The workspace Stop Hook will request continuation up to the ZCode limit while this parent session is active.
When progress cannot continue within the frozen repair/timeout limits, persist `BLOCKED` with evidence so the
Hook releases the task; never create a fake `DELIVERY_READY` state merely to stop.
