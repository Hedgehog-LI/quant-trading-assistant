---
name: qta-quality-acceptance
description: Verify Quant Trading Assistant work before delivery. Use for final acceptance, regression checks, test/build execution, API/doc consistency review, product copy review, deployment sanity checks, and deciding whether Claude/Codex changes are ready to commit.
---

# QTA Quality Acceptance

## Workflow

1. Read:
   - `../../../docs/AI_DEVELOPMENT_INDEX.md`
   - `../../../docs/BUILD_CHECKLIST.md`
   - `../../../docs/CURRENT_ARCHITECTURE_AND_MODULES.md`
2. Inspect git status in affected repos.
3. Review changed files before running checks.
4. Run the relevant quality gates.
5. Report findings first, then tests, then next actions.

## Backend Gate

In `/Users/joker/code/quant-trading-assistant`:

```bash
./mvnw test
```

Also run `./mvnw package` if packaging, Docker, deployment, generated jar, or runtime startup may be affected.

Check:

- Flyway migrations are additive and ordered.
- MyBatis XML matches mapper interfaces.
- API docs mention new/changed endpoints.
- Risk disclaimers remain intact.
- No broker integration or secret storage was added.

## Frontend Gate

In `/Users/joker/code/quant-trading-assistant-web`:

```bash
npm run typecheck
npm run lint
npm run test
npm run build
```

Check:

- `mock` and `remote` semantics are accurate.
- Production remote uses same-origin `/api/v1` when `apiBaseUrl` is empty.
- UI text does not mislead users about DB persistence.
- Trading/PnL colors follow A-share convention.
- No API key appears in frontend code.

## Product Acceptance Gate

For each user-facing feature, verify:

- The primary user workflow can be completed.
- Empty, loading, error, and success states are present.
- Persisted data lands in the intended place.
- Destructive operations are confirmed.
- AI output, if any, requires user confirmation before DB write.

## Report Format

Use this shape:

```markdown
Findings
- P0/P1/P2 issue with file reference, or "No blocking findings."

Verification
- Backend: ...
- Frontend: ...

Current State
- Changed files:
- Untracked files:
- Commit readiness:

Next Actions
- ...
```
