---
name: qta-product-design
description: Design Quant Trading Assistant product features before implementation. Use when a request needs PRD, feature scope, module boundaries, DB/API/UI drafts, checklist, or product acceptance criteria for QTA backend/frontend work, especially trading ledger, position snapshot, review, risk, or AI image import features.
---

# QTA Product Design

## Workflow

1. Read project context:
   - `../../../docs/AI_DEVELOPMENT_INDEX.md`
   - `../../../docs/PRODUCT_BLUEPRINT.md`
   - `../../../docs/BUILD_CHECKLIST.md`
2. If the feature touches current architecture, read:
   - `../../../docs/CURRENT_ARCHITECTURE_AND_MODULES.md`
3. If the feature is position snapshot related, read:
   - `../../../docs/features/POSITION_SNAPSHOT_DESIGN.md`
4. Produce a design, not code, unless the user explicitly asks for implementation.

## Output Shape

For each feature, output:

1. User goal.
2. In scope / out of scope.
3. Data ownership: DB, localStorage, derived calculation, or external provider.
4. Backend module impact.
5. Frontend page impact.
6. API draft.
7. DB draft if persistence is needed.
8. Product risks and guardrails.
9. Acceptance checklist.

## QTA Product Rules

- Core business data should land in backend DB in remote/production mode.
- `localStorage` is for local development, offline fallback, or pre-migration data only.
- AI recognition is only automatic form filling; users must confirm before DB write.
- Never design auto trading, broker login, broker API key storage, or guaranteed-profit claims.
- Risk disclaimer must be visible where trading decisions, PnL, or AI parsing appears.

## Checklist Style

Use checklist items when planning work:

```markdown
- [ ] Backend migration
- [ ] Backend API
- [ ] Frontend page
- [ ] Tests
- [ ] Docs
- [ ] Product acceptance
```
