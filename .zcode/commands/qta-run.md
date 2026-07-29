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
