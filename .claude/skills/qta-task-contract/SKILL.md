---
name: qta-task-contract
description: Use before non-trivial QTA implementation to freeze scope, testable acceptance criteria, evidence, exclusions, lane, roles, and stop conditions. It creates the implementation contract but never implements the task.
when_to_use: Use for multi-file, cross-repository, database, scheduler, provider, security, financial, deployment, autonomous, or repeatedly failing work. Do not use for an explicit low-risk trivial fix with contract-lite evidence.
---

# QTA Task Contract

## Purpose

Freeze the meaning of a task before implementation so completion is decided by observable evidence rather
than by the implementer's interpretation.

## Trigger Conditions

Invoke when any of the following is true:

- Work changes more than one module or repository.
- The task is expected to run longer than one focused session.
- Database, scheduler, external provider, security, deployment, or financial logic is involved.
- The user asks for autonomous, overnight, team, goal-mode, or end-to-end development.
- Previous attempts repeatedly changed code without converging.
- Acceptance criteria are missing, vague, or expressed only as “make it work.”

A tiny, explicit, low-risk fix may proceed without a separate contract if its expected behavior and test are
written down before editing.

## Inputs

- Context digest from `$qta-context-bootstrap`
- Approved product/technical design or explicit user request
- Current repository baseline and known dirty files
- Existing API/data contracts that must remain compatible

## Contract Construction

1. Assign a stable task ID and concise objective.
2. Select risk lane `L0`, `L1`, `L2`, or `L3` using `$qta-development-orchestration`.
3. Record baseline commit/branch and pre-existing dirty paths.
4. Separate `FACT`, `DECISION`, `ASSUMPTION`, and `OPEN_QUESTION`.
5. Define in-scope repositories, modules, allowed write paths, and interfaces.
6. Define explicit non-goals and prohibited behavior.
7. Write acceptance criteria as externally observable outcomes and stay within the lane cap: L0=3, L1=5,
   L2=8, L3=10.
8. Attach an evidence method and owner role to every criterion.
9. Define required verification dimensions:
   - `STATIC`
   - `AUTOMATION`
   - `RUNTIME`
   - `DEPLOYMENT`
10. Define architecture/NFR gates, stop conditions, context budget, wait/poll limits, and repair-round limit.
11. Define which clean-context role gives the final verdict.
12. Freeze `contract_version` and `contract_hash` after test-design amendments are accepted.

## Acceptance Criterion Format

Each criterion must contain:

- `AC-ID`
- Observable behavior
- Preconditions and input
- Expected result
- Required evidence
- Verification dimension
- Responsible role
- Status

Each AC describes one result and uses at most two mandatory evidence types. Avoid criteria such as “code is
high quality” or “feature is complete.” Replace them with inspectable behavior, response contracts, persisted
records, or user-visible states. Put detailed test cases in the test matrix, not in the product contract.

Test-design blockers are limited to ambiguities that prevent falsifiable behavior, safety, data integrity, or
financial correctness. Lane amendment caps are L0=0, L1=1, L2=3, and L3=5. Nonblocking implementation ideas
remain recommendations; exceeding a cap requires task splitting or a parent-owned exception.

## Required Artifact

Create a task contract using `assets/TASK_CONTRACT_TEMPLATE.md`. Store the active contract in a focused path
under `docs/development/tasks/` unless the project development index specifies another active location.

The contract is the scope authority for the implementation round. Product documents remain the business
authority; the task contract must link to them rather than copy them wholesale.

When this Skill is used by a read-only role, do not attempt to write the contract. Return a complete amendment
artifact to the parent coordinator, which owns persistence and contract freezing.

## Role Separation

- Product/design defines intended behavior.
- Test designer challenges and completes the acceptance criteria before implementation.
- Implementer changes code and supplies self-check evidence.
- Code reviewer inspects the diff without editing it.
- Final verifier executes required gates and decides the verdict.

`L1`, `L2`, and `L3` use all four roles. `L0` may omit test design and code review only when the contract-lite
record explains why; it still requires a clean final verifier.

## Stop Conditions

Do not hand off to implementation when:

- Financial meaning is unresolved.
- The expected API/data behavior is contradictory.
- Acceptance evidence cannot be produced.
- Scope is too broad to checkpoint safely.
