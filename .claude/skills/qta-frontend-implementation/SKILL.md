---
name: qta-frontend-implementation
description: Implement or review the Quant Trading Assistant React frontend. Use for feature pages, Ant Design UI, TypeScript types, mock/localStorage and remote REST adapters, API mode semantics, frontend tests, and product copy alignment.
---

# QTA Frontend Implementation

## Workflow

1. Read backend project docs:
   - `../../../docs/AI_DEVELOPMENT_INDEX.md`
   - `../../../docs/PRODUCT_BLUEPRINT.md`
   - `../../../docs/CURRENT_ARCHITECTURE_AND_MODULES.md`
   - relevant `../../../docs/features/*.md`
2. Work in frontend repo:
   - `/Users/joker/code/quant-trading-assistant-web`
3. Inspect existing feature structure before editing.
4. Keep pages as orchestration; put business logic in feature api/hooks/components/utils.
5. Update tests and README when data-mode semantics change.

## Frontend Data Rules

- `mock` mode uses localStorage through shared/local client only.
- `remote` mode uses backend REST API through shared axios client.
- Do not put API keys in frontend code.
- Production should use remote mode with `apiBaseUrl` empty, so requests go to same-origin `/api/v1`.
- Do not claim data is saved to DB when the current mode is localStorage.
- Do not claim localStorage data auto-syncs to DB unless a migration feature exists.

## UI Rules

- Build actual app screens, not marketing pages.
- Use Ant Design components consistently.
- Use A-share colors: profit/red, loss/green.
- Keep risk disclaimers visible for PnL, risk calculator, AI import, and trading-related pages.
- AI image recognition results must be editable drafts before confirmation.

## Required Verification

Run in `/Users/joker/code/quant-trading-assistant-web`:

```bash
npm run typecheck
npm run lint
npm run test
npm run build
```

If any command fails, fix and rerun before final delivery.
