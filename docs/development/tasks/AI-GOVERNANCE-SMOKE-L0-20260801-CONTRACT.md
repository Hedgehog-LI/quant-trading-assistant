# AI Governance L0 Smoke Contract

- Task ID: `AI-GOVERNANCE-SMOKE-L0-20260801`
- Lane: `L0`
- Git automation: `NONE`
- Candidate mode: `SNAPSHOT`

## Scope

Create only `docs/development/tasks/AI-GOVERNANCE-SMOKE-L0-20260801-TARGET.md` with the exact content required
by AC-01. Task-local control, candidate, role, evidence, and finalization artifacts are parent-owned metadata.

## Acceptance Criteria

| ID | Observable result | Required evidence |
|---|---|---|
| AC-01 | The target file contains the exact three-line smoke marker specified below. | STATIC |
| AC-02 | No non-metadata path outside the target is introduced by this smoke task. | STATIC |

Required target content:

```text
# Governance Smoke Test

Status: PASS_CANDIDATE
```

## Exclusions

- No business code, API, database, frontend, deployment, commit, or push changes.
- Do not modify any path that was dirty before this task.
- Do not weaken governance rules to make the smoke task pass.

## Roles And Stop Conditions

- Fresh implementer: create the target and self-check only.
- Code reviewer: explicitly omitted by the L0 lane.
- Fresh final verifier: independently inspect the frozen SNAPSHOT and issue the verdict.
- Stop on scope drift, candidate hash drift, failed required evidence, repeated failure, or user interruption.
