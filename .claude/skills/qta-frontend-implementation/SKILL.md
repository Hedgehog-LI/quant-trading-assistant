---
name: qta-frontend-implementation
description: Use to implement or repair bounded QTA React frontend work after page behavior and API acceptance criteria are frozen. Covers features, adapters, state, forms, tables, charts, accessibility, and self-tests; never accepts its own work.
when_to_use: Use when assigned frontend ACs require code or test changes. Do not use for product design, read-only review, final verification, invented endpoints, or an unfrozen requirement.
---

# QTA Frontend Implementation

## Purpose

Implement a bounded React feature in the frontend repository using its existing feature-based architecture
and real backend contracts.

## Trigger Conditions

Invoke when:

- A task contract includes frontend changes.
- A confirmed page, routing, interaction, API integration, or display defect must be repaired.
- An approved product design requires a new or changed frontend workflow.

Do not invoke only to judge whether another implementation is acceptable.

## Start Gate

1. Run `$qta-context-bootstrap` and include the frontend repository.
2. Read the feature design, API contract, and affected feature code.
3. Confirm whether the page uses remote API, localStorage/mock, or pure local calculation.
4. Record both repositories' status when the contract spans frontend and backend.

Never invent endpoints. If the backend contract is missing, record the dependency rather than silently
substituting fake data.

## Architecture Boundaries

- `pages/routes`: composition and navigation.
- `features/<feature>`: feature UI, hooks, domain-specific state, and feature API.
- `services/api`: shared transport, envelope, authentication, and error handling.
- `components`: reusable presentation components without hidden business workflows.
- `utils`: stateless, genuinely shared helpers.

Keep server state, form state, and UI state distinct. Mechanical response-to-view conversion belongs in a
dedicated adapter, not scattered across components.

## UX Requirements

- Provide loading, success, empty, validation, permission, network, and retry states.
- Use existing design tokens and icon library.
- Use tables, filters, forms, and charts suited to repeated operational use.
- Preserve responsive layout and keyboard/accessibility behavior.
- Show data source, freshness, market timezone, and provider limitations where relevant.
- Do not claim that empty data is an error or that stale data is live.

## Execution Loop

1. Implement the smallest complete user path.
2. Add focused component, hook, or adapter tests.
3. Run typecheck and lint.
4. Run targeted tests while editing, then run the full test/build gate once when the candidate is ready to
   freeze.
5. Inspect routes and API URLs against the backend contract.
6. Use browser/runtime verification only when the task contract requires it.
7. Run `node scripts/check-ai-architecture.mjs --base <baseline> --architecture-review-count 0` for changed TypeScript/TSX files and
   disposition triggered complexity warnings.
8. At 40% context, checkpoint before another workstream. At 60% or the first compaction, use
   `$qta-task-checkpoint` and end this role instance.

Self-tests are implementation evidence, not independent acceptance.

## Required Output

Report changed behavior, files, API dependencies, checks run, uncovered runtime states, and remaining
blockers. Hand off to `$qta-independent-verification` for clean-context verification.

## Stop Conditions

Stop and checkpoint when the API contract is contradictory, a required environment is unavailable, or the
task expands beyond its contract.
