# Task Contract: AI-GOVERNANCE-CORE-20260729

## Objective

Complete the core QTA AI governance layer so ZCode can route work through bounded skills and fixed roles,
preserve candidate identity across clean contexts, and create traceable stage commits without treating
self-checks as final acceptance.

## Authority

- Product/design: `docs/ai/SKILL_AND_AGENT_GOVERNANCE.md`
- Workflow: `docs/DEVELOPMENT_WORKFLOW.md`
- Runtime baseline: ZCode 3.3.4 / CLI 0.15.2
- Baseline commit: `76423df8d49385915ed94949d3ec9015351deb44`
- Pre-existing dirty paths: all paths reported by the task-start `git status --short`
- Git automation: `NONE`; this task must not commit or push the dirty worktree

## Scope

### In Scope

- Project Skill triggering metadata and lifecycle boundaries.
- Parent-only lifecycle orchestration.
- Four fixed ZCode role packets, outputs, and independence rules.
- Contract, task-state, verification, and finalization evidence templates.
- Stage commit and task-branch push policy.
- Static trigger and governance checks required by these changes.

### Out Of Scope

- Business implementation under `src/`.
- ZCode hooks, CI integration, and cross-platform atomic sync hardening.
- Real provider, Docker, frontend, or deployment verification.
- Committing or pushing the current dirty worktree.

### Prohibited

- Staging or reverting unrelated user changes.
- Letting an implementer issue the final acceptance verdict.
- Treating heuristic trigger tests as proof of real model invocation.
- Pushing an unverified candidate as a deployable revision.

## Acceptance Criteria

| AC-ID | Observable behavior | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|
| AC-01 | Governance work is split into three phases and maps the original ten capabilities | Document inspection | STATIC | verifier | NOT_STARTED |
| AC-02 | A parent-only orchestrator defines lanes, ordered gates, repair limits, and finalization | Skill and command inspection | STATIC | verifier | NOT_STARTED |
| AC-03 | Every project Skill has front-loaded ZCode trigger metadata and one lifecycle stage is selected | Runtime discovery plus exact trigger tests | STATIC | verifier | NOT_STARTED |
| AC-04 | All four fixed roles use bounded task packets and return structured artifacts without recursive delegation | Agent template inspection | STATIC | verifier | NOT_STARTED |
| AC-05 | Contract/state/review artifacts bind evidence to contract and candidate hashes | Template inspection | STATIC | verifier | NOT_STARTED |
| AC-06 | Git policy creates traceable stage commits on a task branch and reserves deployable status for accepted revisions | Governance inspection | STATIC | verifier | NOT_STARTED |
| AC-07 | Project trigger and governance checks pass without modifying business code | Command exit status | AUTOMATION | verifier | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | Diff and governance inspection | AC-01 through AC-06 satisfied |
| AUTOMATION | Yes | Trigger evaluator, governance validator, ZCode skill discovery | All exit successfully with no diagnostics |
| RUNTIME | No | Deferred to enforcement phase | NOT_REQUIRED by this contract |
| DEPLOYMENT | No | Governance-only task | NOT_REQUIRED by this contract |

## Lane And Roles

- Lane: `STANDARD`
- Test designer: clean-context review of criteria and false-green scenarios.
- Implementer: current parent context, governance paths only.
- Code reviewer: clean-context read-only diff review.
- Final verifier: clean-context static/automation verification.

## Checkpoint Policy

- Checkpoint after canonical Skill/Agent edits and before compatibility synchronization.
- Stop after two repair rounds with the same failure fingerprint.
- Do not commit or push until independent verification accepts this governance slice.
