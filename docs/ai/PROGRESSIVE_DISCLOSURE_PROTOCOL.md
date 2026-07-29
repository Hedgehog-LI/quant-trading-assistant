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
6. Checkpoint before the context budget is exhausted.
7. Verify in a clean context, then finalize durable documents.

## 2. Session Start Checklist

Always start with:

```text
AGENTS.md
CLAUDE.md
docs/AI_DEVELOPMENT_INDEX.md
docs/AI_HANDOFF.md
git status --short
```

Then answer these before reading more:

- What is the current task?
- Which repo is affected: backend, frontend, or both?
- Which feature/module is affected?
- Is this design, implementation, testing, deployment, or documentation?
- What files are already modified?

Read this protocol itself at startup only for long-running, resumed, or context-risk tasks. For a small fresh
task, the five-item list above is the complete Level 1 baseline.

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
- For one failure fingerprint, do at most two numbered repair rounds. If the second round still fails without
  new evidence, stop and write the blocker.

Default lifecycle thresholds:

- At 25% context use, freeze discoveries into the task contract or state file.
- At 40%, checkpoint before opening a new workstream.
- At 60%, stop implementation and continue from a clean context.
- Never deliberately fill a 100% context window and rely on emergency compaction.

Avoid prompts like:

- "不在乎 token"
- "跑很久直到全部通过"
- "专家团全部开启"
- "读取所有文档"

Use prompts like:

- "只读必要文档"
- "不要开启专家团"
- "同一失败最多做两轮编号修复"
- "若仍失败，输出阻塞原因"

## 6. Skill And Role Routing

Use one lifecycle stage at a time:

1. `qta-context-bootstrap`: read-only context router.
2. `qta-development-orchestration`: parent-only controller for multi-role delivery.
3. `qta-product-design`: product behavior and scope.
4. `qta-task-contract`: frozen AC and evidence plan.
5. `qta-backend-implementation` / `qta-frontend-implementation`: implementation and self-check.
6. `qta-task-checkpoint`: resumable task state.
7. `qta-independent-verification`: clean-context verdict, no editing.
8. `qta-delivery-finalization`: verified documentation and delivery state.

`qta-openclaw-integration` is a domain overlay and triggers only for explicit OpenClaw/QQ Agent API work.

Fixed ZCode roles live in `.zcode/agents/`. The implementer cannot accept its own work; the reviewer cannot
edit it; the final verifier can run gates but cannot repair code. No role may create nested subagents.

See `docs/ai/SKILL_AND_AGENT_GOVERNANCE.md` for the durable boundary and maintenance rules.

For a multi-role task, invoke `/qta-run <objective-or-contract>` or explicitly use
`qta-development-orchestration`. The parent passes TaskPackets rather than the full conversation.

## 7. Checkpoint And Finalization

For in-progress, interrupted, blocked, or self-checked work, use `qta-task-checkpoint` and write only
task-local state under `docs/development/tasks/` plus an optional short-lived continuation handoff.

Before independent acceptance:

- Do not append `docs/development/DEVELOPMENT_LOG.md`.
- Do not append `docs/acceptance/ACCEPTANCE_LOG.md`.
- Do not update `docs/AI_HANDOFF.md` or `docs/BUILD_CHECKLIST.md`.
- Do not mark a capability verified, complete, or deployed.

After `ACCEPTED` or delivery-permitted `CONDITIONALLY_ACCEPTED`, use `qta-delivery-finalization` to update
only the project-level documents whose authoritative facts changed. Do not append long chat transcripts.

## 8. Completion Summary

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

Use `qta-task-checkpoint` for interrupted work. A checkpoint is not an acceptance record.

Stage commits and pushes follow `SKILL_AND_AGENT_GOVERNANCE.md §6`. Child roles never operate Git.
