---
name: qta-product-design
description: Use to define or materially change QTA product behavior, financial meaning, workflow, scope, priority, or acceptance outcomes before implementation. It designs decisions but never implements code or gives a delivery verdict.
when_to_use: Use for ambiguous requirements, new capabilities, changed user workflows, financial semantics, or design-to-implementation gap analysis. Do not use for a bounded confirmed defect or formal final verification.
---

# QTA Product Design

## Purpose

Translate an investment workflow into an implementable, testable product specification without writing
production code or declaring delivery complete.

## Trigger Conditions

Invoke when the user asks to:

- Design a new feature or materially change an existing workflow.
- Decide what should be built next.
- Clarify trading, market-data, portfolio, review, or risk semantics.
- Define page behavior, business rules, data ownership, or acceptance criteria.
- Compare the current implementation against a product design.

Do not invoke for a narrow bug whose expected behavior is already explicit.

## Inputs

Load through `$qta-context-bootstrap`, then read only:

- The relevant active feature design.
- Current API/data model for the affected module.
- Current roadmap or capability-matrix entry.
- Existing task contract, if one exists.

## Design Process

1. State the user problem and intended decision or workflow.
2. Identify actors, entry points, happy path, alternate paths, and failure states.
3. Define business terms and financial assumptions precisely.
4. Separate MVP, later enhancement, and explicit non-goals.
5. Define backend, frontend, data, scheduling, security, and observability impacts.
6. Convert behavior into externally observable acceptance criteria.
7. Record open risks as assumptions or decisions, never as hidden implementation guesses.

## Required Product Boundaries

- The system is decision support, not automatic trading.
- Market-data permissions and provider limitations must be visible to users.
- Financial calculations must state data period, market calendar, adjustment type, and failure conditions.
- Empty data, stale data, delayed data, and provider errors are different product states.
- A feature is not complete merely because a page or endpoint exists.

## Required Output

Produce or update a focused design containing:

- Problem statement and goals
- Users and scenarios
- Scope and non-goals
- Workflow and state transitions
- Domain model and key fields
- API/page requirements
- Error and empty states
- Acceptance criteria
- Delivery slices and dependencies
- Capability-matrix/roadmap change proposal

Mark statements as `FACT`, `DECISION`, `ASSUMPTION`, or `OPEN_QUESTION` when ambiguity matters.

## Relationship to Other Skills

- Hand the approved design to `$qta-task-contract`.
- Do not call implementation complete.
- Do not perform independent verification.
- Do not update acceptance status without evidence.

## Stop Conditions

Stop for user input only when a choice changes financial meaning, destructive data behavior, security exposure,
or delivery scope materially. Otherwise choose the conservative option and document it.
