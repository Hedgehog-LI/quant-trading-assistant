# Task Contract: <TASK-ID> <Title>

## Contract Identity

- Status: `DRAFT | TEST_DESIGN_READY | FROZEN | SUPERSEDED`
- Contract version:
- Frozen at:
- Frozen by parent run:
- Lane: `L0 | L1 | L2 | L3`

After freezing the file, the parent computes its SHA-256 and records it in task state and TaskPackets. The
contract does not contain its own hash.

## Objective

## Authority

- Product/design:
- API/data contract:
- Baseline commit:
- Baseline branch:
- Pre-existing dirty paths:
- Allowed write paths:

## Facts And Decisions

| Type | Item |
|---|---|
| FACT | |
| DECISION | |
| ASSUMPTION | |
| OPEN_QUESTION | |

## Scope

### In Scope

### Out Of Scope

### Prohibited

## Acceptance Criteria

| AC-ID | Observable behavior | Preconditions/input | Expected result | Evidence | Dimension | Owner | Status |
|---|---|---|---|---|---|---|---|
| AC-01 | | | | | STATIC/AUTOMATION/RUNTIME/DEPLOYMENT | | NOT_STARTED |

## Verification Plan

| Dimension | Required | Command or inspection | Pass rule |
|---|---|---|---|
| STATIC | Yes | | |
| AUTOMATION | | | |
| RUNTIME | | | |
| DEPLOYMENT | | | |

## Implementation Slices

Each initial slice owns at most three ACs, eight expected files, and 500 production-line additions. One fresh
implementer receives one slice. Cross-slice assembly is owned by the parent without editing implementation.

| Slice ID | Coherent boundary | AC IDs | Allowed write paths | Max files | Max production-line delta |
|---|---|---|---|---:|---:|
| SLICE-01 | | | | 5 | 500 |

## Frozen Test Inventory

Every mandatory evidence item has a stable test ID and an exact selector observable in a machine receipt.

| Test ID | AC IDs | Kind | Required | Source path | Exact selector | Receipt path |
|---|---|---|---|---|---|---|
| TEST-01 | AC-01 | STATIC/AUTOMATION/RUNTIME/DEPLOYMENT | YES | | | |

## Architecture And Quality Gates

- Required architecture review: `YES | NO`
- Triggered thresholds:
- Required layers/boundaries:
- Responsibility-map evidence:
- ADR exception and expiry:

## Role Assignments

- Test designer:
- Implementer:
- Code reviewer:
- Final verifier:
- Omitted roles and justification:

## Candidate And Git Policy

- Git automation: `NONE | COMMIT | COMMIT_AND_CHECKPOINT_PUSH | DELIVERY_PUSH`
- User authorization evidence:
- Task branch:
- Contract commit:
- Candidate mode: `COMMIT | SNAPSHOT`
- Candidate commit:
- Candidate tree hash:
- Patch SHA-256:
- Candidate manifest path/hash:
- Checkpoint push allowed: `YES | NO`
- Delivery push target:
- Protected/default branch direct push: `NO`

## Checkpoint Policy

- Context budget:
- Persist discoveries at: 25%
- Stop opening stages at: 40%
- Mandatory fresh-context handoff at: 60%
- Maximum waits per role run: 2
- Maximum shell polls per command: 3
- Automatic compaction policy: first compaction forces handoff; second is prohibited
- Maximum repair rounds for one failure fingerprint: 2
- Lane AC cap:
- Blocking amendment cap:
- Blocking amendment history:
- Stop conditions:
