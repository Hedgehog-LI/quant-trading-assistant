---
name: qta-openclaw-integration
description: Use as a QTA domain overlay only for explicit OpenClaw or QQ remote-assistant work involving agent APIs, capabilities, knowledge access, authorization, audit, or remote workflows. It never replaces a lifecycle skill.
when_to_use: Pair with design, contract, implementation, verification, or finalization for explicit OpenClaw integration. Do not trigger for generic agents, subagents, expert teams, model prompts, or ordinary AI features.
---

# QTA OpenClaw Integration

## Purpose

Apply OpenClaw-specific domain and security constraints to an already classified product, implementation,
or verification task. This is an overlay skill, not an end-to-end development workflow.

## Trigger Conditions

Invoke only when at least one is present:

- The user explicitly says OpenClaw or QQ remote assistant.
- The task touches `/api/v1/agent`.
- The task touches the OpenClaw capability manifest, knowledge index, agent API key handling, audit log, or
  remote command boundary.
- The task references the project's OpenClaw design/API documents or module packages.

Do not trigger on generic words such as agent, subagent, expert team, AI assistant, or automation.

## Lifecycle Pairing

Pair this skill with exactly the relevant lifecycle skill:

- Design: `$qta-product-design`
- Contract: `$qta-task-contract`
- Backend work: `$qta-backend-implementation`
- Frontend work: `$qta-frontend-implementation`
- Review: `$qta-independent-verification`
- Delivery: `$qta-delivery-finalization`

## Progressive Loading

Read in order:

1. `docs/features/OPENCLAW_AGENT_ASSISTANT_DESIGN.md`
2. `docs/api/AGENT_ASSISTANT_API.md`
3. Affected controller/service/security/audit source
4. Deployment configuration only if deployment is in scope

Do not load unrelated market-data, portfolio, or historical prompt documents.

## Security Boundaries

- Read-only by default; no automatic trading, order, account, or credential operations.
- The public Nginx route must not accidentally bypass server-side authorization.
- API keys and secrets must never appear in Git, logs, reports, examples, or returned payloads.
- Every remote action needs an auditable actor, capability, request result, and timestamp.
- Capability discovery must expose only implemented and authorized operations.
- Error responses must distinguish authentication, authorization, validation, provider failure, and absence
  of data without leaking internals.

## Verification Dimensions

Evaluate separately:

- `STATIC`: route, authorization, schema, and secret scanning.
- `AUTOMATION`: focused unit/integration tests.
- `RUNTIME`: local request path and expected error/success envelope.
- `DEPLOYMENT`: Nginx-to-backend path, external reachability policy, and server configuration.

A dimension without evidence is `NOT_VERIFIED`, never implicitly passed.

## Required Output

Add an OpenClaw-specific section to the active lifecycle artifact with:

- Capabilities in scope
- Authorization and audit boundary
- API routes and callers
- Evidence by verification dimension
- Explicit exclusions and deployment assumptions

## Stop Conditions

Stop on any possible secret leak, authorization bypass, unsupported write capability, or mismatch between
the advertised capability manifest and implemented routes.
