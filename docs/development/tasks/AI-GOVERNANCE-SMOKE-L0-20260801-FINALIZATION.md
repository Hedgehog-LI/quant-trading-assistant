# AI Governance L0 Smoke Finalization

- Task ID: `AI-GOVERNANCE-SMOKE-L0-20260801`
- Lane: `L0`
- Candidate identity: `a94f99f0f206caf0272a9fce0fdf5ad96d6eabb8aa7528891f6ac3220846c7b4`
- Verdict: `ACCEPTED`
- Delivery status: `FINALIZED`

## Result

- A fresh implementer changed exactly one allowed three-line Markdown target and returned `SELF_CHECKED`.
- The parent froze a one-entry SNAPSHOT manifest and exact patch artifact.
- Candidate control, changed-path coverage, static content, and architecture gates passed.
- A different fresh verifier returned AC-01/AC-02, functional, architecture, and required STATIC evidence as
  `PASS`; optional dimensions remained `NOT_REQUIRED`.
- Parent waiting used exactly one long wait plus one follow-up and no shell polling.
- No commit or push was authorized or performed.

## Trial Finding

The first control-gate invocation passed content checks but could not create `.git/qta-governance` inside the
default Codex sandbox (`EPERM`). Re-running only the control-gate command with scoped `.git` write approval
created and advanced the hash-chain normally. The validator and active documentation now return and preserve
that recovery rule instead of exposing only a raw stack trace.

The trial also exposed two self-reporting risks that were corrected after candidate acceptance without changing
the candidate: unavailable context telemetry is now recorded as `UNAVAILABLE + null`, and anchored snapshots
now include review, verification, finalization, and AC evidence so a completed verdict cannot be silently
rewritten.
