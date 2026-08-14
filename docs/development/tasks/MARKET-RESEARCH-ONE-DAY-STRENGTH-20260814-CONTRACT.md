# Market Research One-Day Strength Contract

> Task ID: `MARKET-RESEARCH-ONE-DAY-STRENGTH-20260814`  
> Status: FROZEN  
> Scope: backend + frontend + focused product/API documentation

## 1. Objective

Allow the market radar to produce a useful view from one valid market-sector `CLOSE` ranking batch while
preserving the semantic boundary between one-day cross-sectional strength and multi-day rotation.

## 2. Frozen Behavior

- Add `window=1` to market-research query endpoints and the frontend window selector.
- One-day strength reads the latest valid `CLOSE` raw fact. It does not require or create a multi-day
  analytics publication.
- One-day metrics are limited to sector return, equal-weight benchmark return, relative return,
  cross-sectional strength percentile, source rank, market breadth, and leading-security evidence.
- One-day mode must report rotation as unavailable. It must not expose improving/weakening, persistence,
  rank change, or a five-day momentum conclusion.
- `POST /market-research/calculations` remains a multi-day operation and continues to accept only
  `5/10/20/50`.
- Multi-day behavior and existing persisted publications remain unchanged.
- One-day ranking history and sector detail read available daily `CLOSE` facts so navigation does not
  produce a 404 solely because the selected window is one day.

## 3. Acceptance Criteria

1. With one valid `CLOSE` batch containing at least five identified sectors, `GET /radar?window=1` returns
   HTTP 200 without a publication row and identifies the raw source batch.
2. Percentiles are tie-aware; the strongest sector has percentile `1`, and relative return uses the same
   equal-weight/log-relative formula as the existing strength calculator.
3. Every one-day sector has `rotationState=INSUFFICIENT_DATA`, `rotationAvailable=false`, and explicit
   `ONE_DAY_STRENGTH_ONLY` / `ROTATION_REQUIRES_5_DAYS` reason codes.
4. No valid `CLOSE` batch returns the existing explicit no-data error; raw `MANUAL`/`INTRADAY` batches are
   never substituted.
5. The frontend offers `1 日强度`, defaults a query without a window to one day, hides multi-day generation
   and rotation controls in one-day mode, and provides a one-click one-day fallback from a multi-day empty state.
6. One-day sector detail shows daily strength history from available `CLOSE` facts; multi-day detail is unchanged.
7. Backend targeted/full tests and frontend typecheck/lint/test/build pass, or any environmental blocker is
   recorded without claiming acceptance.

## 4. Exclusions

- Historical provider backfill of sector rankings.
- Intraday one-day radar.
- Capital-flow inference, trading signals, recommendations, or automatic trading.
- Database migration or mutation of raw market-sector facts.

