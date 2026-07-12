# Handoff 2026-07-11 LongPort Single Symbol Engine

> Read this file first for the current Codex implementation run. Do not replay the long chat and do not load all docs.

## Task

- Goal: implement the LongPort single-symbol manual sync engine.
- Repo: backend/docs `/Users/joker/code/quant-trading-assistant`, frontend `/Users/joker/code/quant-trading-assistant-web`.
- Branch: `main`.
- User requirement: Codex performs the implementation, verification, and documentation. Do not commit or push; user will commit/push manually.

## Must Read

- `AGENTS.md`
- `docs/AI_DEVELOPMENT_INDEX.md`
- `docs/AI_HANDOFF.md`
- `docs/features/LONGPORT_SINGLE_SYMBOL_SYNC_ENGINE_DESIGN.md`
- `docs/api/MARKET_DATA_API.md`

## Do Not Load

- Full `docs/` tree.
- Historical prompts or Claude JSONL logs.
- Frontend unrelated features.
- Long Docker logs unless a Docker check fails.

## Current Facts

- Existing market-data facade works without real LongPort:
  - `GET /api/v1/market-data/providers/LONGPORT/status` returns 200 and `configured=false`.
  - `POST /quotes/latest` and `POST /sync-tasks/daily-bars` return 400 `BUSINESS_RULE_VIOLATION` when provider is disabled.
  - No 500 after the MySQL upsert fix.
- Backend runtime adapter has now been implemented:
  - `LongPortProperties`
  - `LongPortMarketDataProvider`
  - `LongPortQuoteClient`
  - `ReflectiveLongPortQuoteClient`
  - `.env.example` and `docker-compose.yml` LongPort env passthrough.
- Operational scripts now exist:
  - `scripts/check-longport-readiness.sh`: preflight for credentials, enabled flag, runtime SDK libs, optional official contract check, optional provider status.
  - `scripts/inspect-longport-runtime-libs.sh`: offline SDK/native/dependency structure check.
  - `scripts/verify-longport-real-sync.sh`: one-symbol real quote + daily bar verification after official SDK and read-only credentials are available.
- Official Java SDK research found a distribution blocker:
  - README/Javadoc define Java SDK APIs and Maven coordinate `io.github.longport:openapi-sdk`.
  - Maven Central currently returns no artifact / metadata 404.
  - GitHub `v4.3.3` release currently has Node native assets, not Java jar.
  - The project therefore uses a reflection adapter that compiles without the SDK and calls it only when SDK jar/native libs are available at runtime.
- Frontend `/market-data` builds/tests pass, but local browser showed 502 when `.env.local` pointed Vite proxy to `localhost:18081` while Docker backend was on `localhost:8080`.
- Existing uncommitted work includes progressive disclosure docs and `SyncScopeLockMapper` MySQL compatibility changes.

## Implementation Scope

Backend:

- Keep `DisabledMarketDataProvider` as safe default when LongPort is disabled.
- When LongPort is enabled but SDK/credentials are absent, return `configured=false` with a clear reason.
- Do not add trade/order/account/position APIs.
- Do not put secrets in Git, DB, frontend, or logs.

Frontend:

- Done: `/market-data` user-facing handling for SDK missing / credentials missing.
- Done: `HF` is disabled in LongPort historical sync because current Java SDK only exposes `NoAdjust` and `ForwardAdjust`.
- Local dev proxy confusion is already documented in frontend README.

Docs:

- API docs, build checklist, acceptance log, and this handoff were updated after backend implementation.

## Verification Plan

Low-cost first:

- Backend `./mvnw -q -Dtest=LongPortEnabledWithoutSdkContextTest,LongPortMarketDataProviderTest,ReflectiveLongPortQuoteClientTest,MarketQuoteServiceTest test`
- Backend `./mvnw -q -DskipTests package`
- Frontend `npm run typecheck`, `npm run lint`, `npm run test`, `npm run build`
- Curl disabled-provider path: status 200, quote/sync 400 not 500.

Only if needed:

- Docker rebuild with logs redirected to `/tmp/qta_docker_build.log`.
- Before real LongPort connectivity, run `scripts/check-longport-readiness.sh`.
- Real LongPort connectivity with one symbol and small date range only, after SDK jar/native libs are available in runtime classpath and credentials are present.

## Stop Conditions

- Do not fake quote/history data. Reflection adapter is allowed because it calls the official SDK when present and fails explicitly when absent.
- If real credentials are absent, validate disabled-provider behavior and unit tests only; do not invent secrets.
- If one direct fix and rerun still fails, write a blocker summary instead of looping.
