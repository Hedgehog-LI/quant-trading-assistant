# Progressive Disclosure Protocol

> Goal: keep AI sessions small, restartable, and auditable. Every Claude / ZCode / Codex session should load only the context needed for the current task, then write a compact handoff before stopping.

## 1. Core Rule

Do not load the whole repository or the whole `docs/` tree.

Use this sequence:

1. Read the global entry files.
2. Identify the task type and affected modules.
3. Read only the routed documents for that task.
4. Inspect only changed or directly related code.
5. Work in a narrow scope.
6. At the end, write a compact result and next-step handoff.

## 2. Session Start Checklist

Always start with:

```text
AGENTS.md
CLAUDE.md
docs/AI_DEVELOPMENT_INDEX.md
docs/AI_HANDOFF.md
```

Then answer these before reading more:

- What is the current task?
- Which repo is affected: backend, frontend, or both?
- Which feature/module is affected?
- Is this design, implementation, testing, deployment, or documentation?
- What files are already modified?

Command hints:

```bash
git status --short
git diff --name-only
```

## 3. Task Context Manifest

Before editing, produce a short manifest in the chat or task notes:

```text
Task:
Affected repo:
Affected module:
Must read:
May read if needed:
Must not read:
Planned edits:
Verification:
Handoff target:
```

Keep "must read" to the smallest possible set. If more context becomes necessary, add one document at a time and state why.

## 4. Routing Rules

Use `docs/AI_DEVELOPMENT_INDEX.md §4` as the routing table.

Examples:

- Backend API bug: read API index, corresponding API doc, affected controller/service/mapper, and related tests.
- DB bug: read current migration, mapper XML, `DATABASE_DESIGN.md` only for affected table.
- Product design: read `PRODUCT_BLUEPRINT.md`, relevant feature design, and `BUILD_CHECKLIST.md`.
- Frontend page work: read `FRONTEND_ARCHITECTURE.md`, `mock/MOCK_REMOTE_CONTRACT.md`, affected feature folder, and relevant API doc.
- Testing/acceptance: read `BUILD_CHECKLIST.md`, latest acceptance log, changed files, and only the docs that define expected behavior.

Avoid historical prompts and old handoff files unless debugging history.

## 5. Context Budget Guardrails

Use these limits unless the user explicitly asks for broader research:

- Prefer reading file sections with `sed -n` over whole large files.
- Prefer `rg` summaries before opening files.
- Do not open generated outputs, build artifacts, `target/`, `dist/`, large logs, or long JSONL session logs unless investigating those exact artifacts.
- Do not run full frontend and backend gates for a backend-only documentation change.
- For a failed verification loop, do at most one direct fix and one rerun. If still failing, stop and write the blocker.

Avoid prompts like:

- "不在乎 token"
- "跑很久直到全部通过"
- "专家团全部开启"
- "读取所有文档"

Use prompts like:

- "只读必要文档"
- "不要开启专家团"
- "失败后只做一轮直接相关修复"
- "若仍失败，输出阻塞原因"

## 6. End Of Task Handoff

At the end of every substantial task, write a compact handoff. Use `docs/templates/TASK_HANDOFF_TEMPLATE.md`.

Where to write:

- Current short-lived continuation: `docs/ai/HANDOFF_YYYY-MM-DD_<topic>.md`
- Important development history: append `docs/development/DEVELOPMENT_LOG.md`
- Verification history: append `docs/acceptance/ACCEPTANCE_LOG.md`
- Current durable state only: update `docs/AI_HANDOFF.md`

Do not append long chat transcripts. Summarize decisions, files, tests, and next action.

## 7. Completion Summary

Every final response should include:

- What changed.
- What was verified.
- What remains.
- Which handoff or docs were updated.

If the task stops midway, still write:

- Current git status.
- Modified files.
- Last successful command.
- Last failing command and error.
- Exact next command/prompt to resume.

