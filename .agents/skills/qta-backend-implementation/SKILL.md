---
name: qta-backend-implementation
description: Use to implement or repair bounded QTA Spring Boot backend work after the contract is frozen. Covers API, services, providers, MyBatis, Flyway, schedulers, and self-tests; never performs independent acceptance.
when_to_use: Use when assigned backend ACs require code or test changes. Do not use for product design, read-only review, final verification, documentation-only finalization, or an unfrozen requirement.
---

# QTA Backend Implementation

## Purpose

Implement a bounded backend task according to an approved design or task contract while preserving the
project's Spring Boot, MyBatis, Flyway, MapStruct, and Alibaba Java conventions.

## Trigger Conditions

Invoke when:

- A task contract includes backend changes.
- A confirmed backend defect needs correction.
- An API, persistence, scheduler, provider, or backend rule must be added or changed.

Do not invoke merely to inspect or independently verify somebody else's implementation.

## Start Gate

Before editing:

1. Run `$qta-context-bootstrap` if this task has no current context digest.
2. Read the task contract or write down explicit scope and acceptance criteria.
3. Inspect current code and tests in the affected module.
4. Record baseline `git status --short`; preserve unrelated changes.

If acceptance criteria are missing or contradictory, route to `$qta-task-contract`.

## Implementation Boundaries

- Controller: protocol validation and response mapping only.
- Service: use-case orchestration and transaction boundary.
- Manager/provider: external systems, reusable domain operations, and infrastructure coordination.
- Mapper/XML: database access and SQL.
- Converter: MapStruct for mechanical model conversion.
- Common utilities: only when genuinely cross-module and not already supplied by a mature library.

Use named constants and categorized error-code enums. Add field and public API comments where they carry
business meaning. Avoid builder/getter chains used only as manual conversion.

## Data and API Rules

- Schema changes require an additive Flyway migration.
- SQL belongs in Mapper XML unless the existing module clearly uses another established pattern.
- Writes must define idempotency, uniqueness, transaction, and retry behavior.
- Time and market data must define timezone, market, trading date, interval, adjustment type, and source.
- API changes must preserve the standard response envelope and validation/error conventions.
- Never add trading, account, order, or secret exposure outside an explicitly approved scope.

## Execution Loop

1. Implement the smallest coherent slice.
2. Add or update focused tests before broad tests.
3. Run compile/static checks for the affected module.
4. Run targeted tests, then broader backend tests when risk justifies them.
5. Inspect the final diff for unrelated edits and contract drift.
6. If context grows large, use `$qta-task-checkpoint` before continuing.

Implementation self-tests prove only `SELF_CHECKED`; they do not prove independent acceptance.

## Required Output

Report:

- Files and behavior changed
- Database/API compatibility impact
- Commands run and exact results
- Acceptance criteria believed satisfied
- Unverified runtime/deployment dimensions
- Remaining blockers

Then hand off to `$qta-independent-verification` in a clean context when independent acceptance is required.

## Stop Conditions

Stop and checkpoint when:

- Required credentials, permissions, or external services are unavailable.
- The task expands beyond its contract.
- Existing unrelated changes make a safe patch impossible.
- The same failure repeats without new evidence.
