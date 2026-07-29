---
name: qta-delivery-finalization
description: Use only after independent acceptance to synchronize verified QTA handoff, development, API, architecture, acceptance, capability, release, and deployment records. It never fixes code or upgrades unverified status.
when_to_use: Use when an unchanged candidate has ACCEPTED or delivery-permitted CONDITIONALLY_ACCEPTED evidence. Do not use after self-check, rejection, blocking, missing evidence, or any post-verdict code change.
---

# QTA Delivery Finalization

## Purpose

Convert an accepted task into durable project knowledge and a deployable handoff without changing the
verified implementation.

## Trigger Conditions

Invoke only when:

- `$qta-independent-verification` produced `ACCEPTED`, or
- `CONDITIONALLY_ACCEPTED` explicitly permits delivery and lists the residual risk.

Do not invoke merely because implementation or self-tests finished.

## Inputs

- Task contract
- Independent verification report
- Final candidate identity:
  - `COMMIT`: commit/tree/patch hashes, or
  - `SNAPSHOT`: manifest and entry-set SHA-256
- Relevant active product, API, architecture, deployment, and capability documents

## Finalization Process

1. Confirm the accepted candidate identity is unchanged since the verdict:
   - `COMMIT`: contract, commit, tree, and patch hashes.
   - `SNAPSHOT`: contract, manifest, and entry-set hashes.
2. Extract current facts; do not copy conversation history.
3. Update only documents whose authoritative facts changed.
4. Record acceptance evidence and residual limitations.
5. Update capability-matrix status from evidence, not optimism.
6. Write deployment/restart/migration steps when deployment is in scope.
7. Refresh `docs/AI_HANDOFF.md` with the smallest useful current-state summary.
8. Run documentation and governance consistency checks.
9. Ask the parent Git owner to create the finalization commit; this Skill does not stage unrelated files.

## Document Ownership Matrix

- Product behavior: relevant feature design.
- Interface contract: API document.
- Database/module boundary: architecture or data-design document.
- Chronological work record: development log.
- Verification result: acceptance log/report.
- Current project state: `docs/AI_HANDOFF.md`.
- User-facing construction status: capability matrix/roadmap source.
- AI routing: development index and project agent guide.

Do not duplicate the same full narrative across every document.

## Status Rules

- `PLANNED`: designed, not implemented.
- `IN_PROGRESS`: implementation started.
- `IMPLEMENTED`: code present, not independently accepted.
- `VERIFIED`: accepted with required evidence.
- `DEPLOYED`: verified revision actually deployed and smoke-tested.
- `BLOCKED`: external or internal blocker recorded.

Never mark `DEPLOYED` from local tests alone.

## Required Output

Report:

- Accepted task/revision
- Documents updated and why
- Capability-matrix status changes
- Deployment or migration steps
- Residual risks and deferred work
- Git paths ready for commit
- Accepted candidate mode/identity and finalization commit proposal

## No-Code Rule

Do not fix implementation defects during finalization. If the verified diff changes, invalidate the verdict
and return to implementation followed by independent verification.
