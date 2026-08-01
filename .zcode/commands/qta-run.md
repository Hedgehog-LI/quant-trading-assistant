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

Use L0-L3 risk lanes and create `<TASK-ID>-CONTROL.json` from the Skill's `TASK_CONTROL_TEMPLATE.json`.
Validate it before every dispatch and transition. Every implementation, repair, review generation, and final
verification uses a fresh role/session and ends after one artifact. Use one long Agent wait plus at most one
follow-up (two waits total); never perform status polling. Require both functional and architecture gates on
the frozen candidate before finalization.

Continue through the state machine until `FINALIZED`, `BLOCKED`, or the user explicitly stops. A plan or one
subagent response is not completion. For reversible choices inside the frozen contract, select the recommended
option yourself instead of asking the user. Ask only for unresolved product/financial meaning, destructive or
credential-bearing authorization, or a genuine external blocker. Do not expand into another product task.
