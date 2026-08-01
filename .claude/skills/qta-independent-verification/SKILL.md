---
name: qta-independent-verification
description: Use in a clean non-implementing context to decide whether a frozen QTA candidate satisfies its frozen contract after code review. It verifies evidence and issues the only acceptance verdict without repairing code.
when_to_use: Use for final acceptance of a completed candidate with contract hash, candidate hash, frozen patch, review result, and required gates. Do not use for self-tests, ordinary code review, document review, design audit, or an unfrozen diff.
---

# QTA Independent Verification

## Purpose

Determine whether an implementation satisfies its frozen contract using independent evidence. The verifier
must not repair the code it judges.

## Trigger Conditions

Invoke when:

- The user asks to验收, review, verify, check completion, or decide whether deployment is safe.
- An implementer hands off work marked `SELF_CHECKED`.
- A release, deployment, or finalization decision requires evidence.
- Prior rounds reported success but production behavior contradicted it.

Do not invoke for ordinary tests run by the same implementation context.

## Independence Gate

Before verification, confirm:

- This context did not author the implementation under review.
- The task contract and baseline are available.
- The diff can be isolated from unrelated dirty changes.
- `contract_hash`, candidate identity, and `REVIEW_CLEAR` refer to the same generation. Candidate identity is
  either commit/tree/patch hashes or snapshot manifest/entry-set hashes.
- The verifier `role_run_id` and session identifier are new and differ from all implementer and reviewer runs.
- The task control file passes `scripts/check-ai-task-control.mjs`.

If this context implemented the change, report only `SELF_CHECK`; start a clean top-level task or dedicated
verifier role for independent acceptance.

## Inputs

1. Task contract
2. Product/API/data authority linked by the contract
3. Baseline and final diff
4. Implementer checkpoint and self-test evidence
5. Runtime/deployment environment only when those dimensions are required

Do not accept the implementer's prose as proof without inspecting the underlying evidence.

## Verification Process

1. Check scope: required changes present, prohibited changes absent.
2. Review the diff on two independent tracks: `FUNCTIONAL` and `ARCHITECTURE`.
3. Derive or inspect tests independently from implementation details.
4. Execute the cheapest decisive gates first.
5. Verify every AC separately.
6. Record each verification dimension independently.
7. Compare tracked candidate state before and after executable gates.
8. Run the architecture gate and inspect any triggered responsibility map or ADR exception.
9. Classify findings by severity and cite file/line or command evidence.
10. Produce a verdict without editing production files.

## Evidence Rules

- `STATIC`: diff, schema, route, types, configuration, and secret scans.
- `AUTOMATION`: targeted or full test commands with exit status.
- `RUNTIME`: health and representative success/failure calls.
- `DEPLOYMENT`: deployed route, proxy, environment, and persistence behavior.

Missing evidence is `NOT_VERIFIED`, not `PASS`.
An environment blocker is `BLOCKED`, not a code pass or failure.
Build outputs may be created only in the disposable verifier worktree. Any tracked candidate or snapshot
manifest mismatch invalidates the run and produces `REJECTED`.

## Verdicts

- `ACCEPTED`: every required AC and verification dimension passed.
- `CONDITIONALLY_ACCEPTED`: only explicitly optional dimensions remain, with documented risk.
- `REJECTED`: one or more required criteria failed.
- `BLOCKED`: verification cannot continue because required evidence or environment is unavailable.

`ACCEPTED` requires both quality tracks to pass. `CONDITIONALLY_ACCEPTED` may omit only a dimension that the
frozen contract explicitly marked optional and delivery-permitted; it cannot hide a required L3 runtime or
deployment gate.

## Required Artifact

Use `assets/INDEPENDENT_VERIFICATION_TEMPLATE.md`. Store the report with the task contract or under the
active acceptance document path.

When the verifier role has no write tool, return the completed template as an artifact payload. The parent
persists it without changing the verdict or evidence.

## No-Fix Rule

Do not edit production code, tests written by the implementer, task criteria, or product documents.
Return findings to an implementer in a new implementation round. Reverify from a fresh diff afterward.
Never reuse this verifier session for a repaired candidate.
