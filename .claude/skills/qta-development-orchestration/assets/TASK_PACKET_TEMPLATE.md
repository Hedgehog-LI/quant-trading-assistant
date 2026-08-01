# Task Packet: <TASK-ID> / <ROLE> / <ROLE-RUN-ID>

## Identity

- Lane:
- Lifecycle state:
- Role instance policy: `FRESH_ONLY`
- Role session ID:
- Role started-at timestamp:
- Runtime session receipt path:
- Repair round:
- Assigned AC IDs:
- Contract path:
- Contract hash:
- Baseline commit:
- Candidate mode: `COMMIT | SNAPSHOT`
- Candidate commit:
- Candidate tree hash:
- Patch SHA-256:
- Frozen diff artifact path:
- Frozen diff artifact SHA-256:
- Candidate manifest path:
- Candidate manifest SHA-256:
- Candidate entry-set SHA-256:
- Machine control file:
- Append-only control anchor: `.git/qta-governance/tasks/<task-hash>.jsonl`

## Scope

- Repository:
- Allowed read paths:
- Allowed write paths:
- Pre-existing dirty paths:
- Prohibited actions:
- Runtime tool enforcement: `ADVISORY` (local Hook/receipt/anchor are not a same-user security boundary)
- Compensating isolation check:

## Authority

- Product/design paths:
- API/data paths:
- Architecture paths:

## Inputs

- Previous role artifact:
- Finding IDs:
- Required fixtures/environment:

## Required Gates

| Gate | Command or inspection | Expected result | Evidence destination |
|---|---|---|---|

## Run Budget

- Maximum role turns:
- Maximum Agent waits: 2
- Maximum shell polls per command: 3
- Checkpoint at context: 40%
- Mandatory fresh-context handoff at: 60% or first compaction
- Raw-token/weekly budget when telemetry is available:

## Output Contract

- Required artifact type/path:
- Required status vocabulary:
- Stop conditions:
