# P110-A-BE-MARKET-DISCOVERY-20260812-R1 · Test Designer Artifact

> Role run: `rr-p110a-r1-td-7e3a1c92b4d0` · Dispatch: `dispatch-p110a-r1-td-20260812-7e3a1c92b4d0`
> Role policy: `FRESH_ONLY` · Repair round: 0 · Compaction count: 0 · Wait calls: 0 · Enforcement: `ADVISORY`
> Session: fresh TEST_DESIGNER subagent (single, no continuation) · Lifecycle: `CONTRACT_DRAFTED` → pre-freeze challenge
> Contract challenged: `docs/development/tasks/P110-A-BE-MARKET-DISCOVERY-20260812-R1-CONTRACT.md` sha256 `c37b41f0713f2eb5e51b3a45e1f4d040e0c71947a8d1d7906f9387a3055123cd`
> Baseline: `0bc907ae50d27965113cfcf996137828f56b5eb7` · Dirty manifest: clean (control `preExistingDirtyPaths: []`)
> Verdict: **`ACCEPTED_WITH_AMENDMENTS`** · executionOutcome: **`COMPLETED`**
> READY_FOR_IMPLEMENTATION: **yes** after 1 new refinement (NEW-A1) + 5 folded A1–A5 carried forward unchanged.

## 0. Fresh-role provenance check (passed)

- Control `roleRuns: []`, `repairHistory: []`, `transitionHistory` single entry from `CONTEXT_READY` → `CONTRACT_DRAFTED` by `parent-qta-orchestrator-20260812-r1`. No reused role/session ID.
- Old task `P110-A-BE-MARKET-DISCOVERY-20260812` (v2, hash `efe1fc93…`) and its TEST-DESIGN (`d69cf4ff…`) were read for reference only; the design content (ACs/slices/testInventory/blockingAmendments) is content-identical to R1 per the task packet. This is a FRESH challenge of the R1, not a re-litigation of folded v2 amendments unless a NEW defect is found.
- No repository file was edited, no shell command run, no agent summoned. First compaction would invalidate this artifact; none occurred.

## 1. AC testability verdict (AC-01..AC-08)

Each AC is independently testable from an external user/API/database perspective. Refinements preserve the folded A1–A5 semantics and add one NEW refinement (NEW-A1) for TEST-06 fixture precision.

| acId | testable | refinement (carries folded amendments forward; NEW findings marked) |
|---|---|---|
| AC-01 | yes | (a) `expected_item_count` MUST NOT come from response rowcount (scope-forgery guard). (b) `provider_quote_time` null → `reasonCodes` contains `SOURCE_TIME_UNKNOWN`, `qualityStatus≠OK`. (c) HK/US long-window with `market_calendar.verification_status=INFERRED` → `INSUFFICIENT_RAW`, no silent acceptance. (d) No CLOSE batch → `NO_DERIVED_DATA` + non-empty `reasonCodes`, radar refuses derived conclusions. |
| AC-02 | yes | Two-layer verification: (a) **functional** = soft-archive / delete+recreate watch → derived `sectorId` unchanged AND snapshots preserved (H2-provable). (b) **concurrency** = two claims same anchor → exactly one identity row, asserted via row-count + unique constraint. H2/`MODE=MySQL` cannot reproduce InnoDB `FOR UPDATE` gap-lock ordering → true contention is RUNTIME `NOT_VERIFIED` (already declared). `watch_id` must not appear in any derived idempotent key or JOIN. |
| AC-03 | yes | Pin §6.1 canonical hash input strings as the frozen fixture: `required_formula_set_hash=SHA256(sorted(formula_code+':'+formula_version+':'+parameter_hash))`; `source_manifest_group_hash=SHA256(sorted(formula_code+':'+formula_version+':'+parameter_hash+':'+source_manifest_hash+':'+calculation_run_id))`. Assert byte-identical SHA-256 to persisted; changing one `parameter_hash` changes the group hash. Add cross-batch composite-FK rejection and atomic-publish rollback (partial fail → batch not published). |
| AC-04 | yes (CRITICAL) | Unit-e2e must deserialize a fixture matching the real LongPort wire format — `chg` and `value_data` carried in the SAME JSON item (verified at `LongPortIndustryHttpClient.java:83-84`, `decimal(item,"chg")` + `text(item,"value_data")`) — through the production parse path, and assert `sectorReturn=0.0240` (NOT `/100`) plus formatter → `"2.40%"`. Hardcoding `0.0240` in the calculator test (skipping parse) is the §5.1-forbidden shortcut and fails review. See **NEW-A1** for fixture-resource precision. Add: cohort-below-threshold → `INSUFFICIENT_SAMPLE` (no impute); ties resolved by `relative_return_n` value (not by stored `rank_no`); cohort fingerprint enters `parameter_hash`/`source_manifest_hash`. |
| AC-05 | yes (CRITICAL) | Frozen 5-sector × 5-day `change_rate` fixture, ascending average rank `[3,4,4,5,5]`, daily `n_t=5`. Must assert all 6 GOLDEN-03 outputs end-to-end from raw `change_rate` with tol ≤1e-9. Hardcoding the 6 numbers without input wiring is the forbidden shortcut. Required subtests: missing-day-breaks-continuity (no skip-and-splice); taxonomy-change → `ORIGIN_CHANGED` break. |
| AC-06 | yes | (a) Four-quadrant thresholds (`LEADING/IMPROVING/WEAKENING/LAGGING/INSUFFICIENT_DATA`) frozen in v1 `parameter_hash`, asserted. (b) Capital/volume columns when uncomputed: non-null `flowMetricNature` + null value (returning `0` is the forbidden fabrication shortcut and fails the test). (c) Cross-batch join rejected. (d) Radar/rotation/ranking use the SAME `publicationBatchId` + `asOfDate`. (e) Each sector carries ≥2 evidence items + non-empty `reasonCodes`. OPEN (non-blocking): exact numeric thresholds are a v1 product decision; implementer documents the default in `MARKET_RESEARCH_API.md`. |
| AC-07 | yes | ★tracking-symbol-isolation: add a tracking symbol whose RS would shift the cohort; assert every OTHER sector's `rs_rank_percentile` vector is byte-identical with vs. without; detail exposes the tracking symbol under `benchmark_symbol` (non-null) while `benchmark_type=RANK_SET_EQUAL_WEIGHT` is unchanged. Detail with no derived data → `NO_DERIVED_DATA`, no stale `published_at`. Ranking history distinguishes persistent-strong vs. single-day-pulse via `consecutive_leading_days`. |
| AC-08 | yes (CRITICAL) | Architecture guard asserts 3 frozen rules via `scripts/check-ai-architecture.mjs` (script confirmed to exist): (a) no file under `analysis/` issues UPDATE/INSERT/DELETE/MERGE against the 8 raw-fact tables (`stock_daily_bar`, `stock_minute_bar`, `stock_quote_snapshot`, `market_sector_snapshot`, `market_sector_member_snapshot`, `market_sector_ranking_batch`, `market_sector_ranking_item`, `market_sector_watch`); (b) no class under `analysis/` autowires a provider client; (c) `watch_id` does not appear in any derived idempotent key or JOIN. Plus calendar-INFERRED-default scheduler regression: after V19 ALTERs `market_calendar` to add `verification_status NOT NULL DEFAULT 'INFERRED'`, the existing scheduler path (`TradingSessionManager` reads only `is_trading_day`) must still work, AND only the new readiness long-window gate fails closed for HK/US. No forbidden-product tokens (candidate-scan, strategy-signal, auto-trade, return-contribution MVP). |

## 2. Compact black-box test cases per AC

★ = golden-asserted (frozen numeric / frozen input fixture, machine-observable).

- **AC-01**: ★happy(`is_truncated` echoed, scope=`RANKED_UNIVERSE`, 中文说明"排行样本，不代表全市场") · no-batch(`NO_DERIVED_DATA`, reasonCodes non-empty, radar refuses derived) · single-batch(`THIN` overview) · stale(`STALE`, no same-day mix) · source-time-null(reasonCodes=`SOURCE_TIME_UNKNOWN`, `qualityStatus≠OK`) · scope-forgery-guard(`expected_item_count≠rowcount`, never `VERIFIED_FULL_MARKET`) · hk-us-inferred-calendar(`INSUFFICIENT_RAW`).
- **AC-02**: concurrent-anchor(2 threads same key → exactly 1 identity row, assert row-count + unique-constraint, NOT lock ordering) · cross-taxonomy-interval(overlap rejected) · delete-recreate-watch(derived `sectorId` unchanged, snapshots preserved) · no-watch-id-in-derived-join.
- **AC-03**: idempotent-rerun · param-change-no-overwrite · concurrent-claim(1 winner) · cross-market-FK-reject · ★hash-recompute(byte-identical to §6.1 canonical strings) · atomic-publish-rollback(partial fail → batch not published).
- **AC-04**: ★unit-e2e(real wire-format fixture `chg="0.0240"` + `value_data="2.40%"` → `sectorReturn=0.0240` raw / `"2.40%"` display, NOT `/100`) · fixed-cohort(intersection-only, drifters excluded, fingerprint in hash) · ties-by-`relative_return_n` · missing-day → `INSUFFICIENT_SAMPLE` · no-fake-full-market.
- **AC-05**: ★golden-03-e2e(frozen 5×5 fixture → all 6 values tol ≤1e-9) · missing-day-breaks-continuity · taxonomy-break(`ORIGIN_CHANGED`) · insufficient-sample(metrics nulled).
- **AC-06**: four-quadrant-classification(thresholds in `parameter_hash`) · capital-unavailable(non-null `flowMetricNature` + null value, NOT `0`) · cross-batch-reject · consistent-batch(radar/rotation/ranking same `publicationBatchId` + `asOfDate`) · evidence-count(≥2 evidence + non-empty `reasonCodes`) · degradation(`DEGRADED`/`NO_DERIVED_DATA` shown with reason).
- **AC-07**: persistent-vs-pulse(`consecutive_leading_days` distinguishes) · detail-no-derived(`NO_DERIVED_DATA`, no stale `published_at`) · ★tracking-symbol-isolation(public RS-rank vector byte-identical with/without tracking symbol; detail exposes tracking under `benchmark_symbol`, `benchmark_type` unchanged) · component-scope(NOT `VERIFIED_SECTOR_UNIVERSE` for this MVP).
- **AC-08**: no-writeback · no-provider-reverse-call · no-watch-id-in-derived-key · no-forbidden-product · calendar-inferred-default-non-blocking(scheduler regression).

## 3. Test inventory validation (TEST-01..TEST-13)

Repo grep confirmed: no `@DisplayName` anywhere in the test tree → method-name selectors apply. Selectors below are precise enough for `scripts/run-ai-evidence-command.mjs` to observe a machine receipt. H2/`MODE=MySQL` caveats called out where they change what is assertable.

| testId | acIds | kind | valid | sourcePath | selector (refined) |
|---|---|---|---|---|---|
| TEST-01 | AC-01 | STATIC | yes | `src/test/java/com/quant/trade/marketdata/analysis/readiness/SectorAnalyticsReadinessManagerTest.java` | `class SectorAnalyticsReadinessManagerTest` + methods matching `readiness*NoBatch`, `readiness*Stale`, `readiness*SourceTimeUnknown`, `readiness*HkUs*Inferred`, `readiness*ScopeForgery` |
| TEST-02 | AC-01 | STATIC | yes | `src/test/java/com/quant/trade/marketdata/analysis/controller/SectorAnalyticsReadinessControllerTest.java` | `class SectorAnalyticsReadinessControllerTest` + methods `readiness*Happy`, `readiness*NoBatch` (the prior bare `readiness*` glob was too loose — refine to class + named methods) |
| TEST-03 | AC-02 | STATIC | yes | `src/test/java/com/quant/trade/marketdata/analysis/manager/SectorIdentityManagerTest.java` | `class SectorIdentityManagerTest` + methods `concurrent*Anchor`, `crossTaxonomy*Interval`, `deleteRecreate*Watch`, `no*WatchId*Join`. H2: assert OUTCOME (row-count + unique-constraint), NOT lock ordering |
| TEST-04 | AC-03 | STATIC | yes | `src/test/java/com/quant/trade/marketdata/analysis/run/CalculationRunManagerTest.java` | `class CalculationRunManagerTest` + methods `idempotent*Rerun`, `paramChange*NoOverwrite`, `concurrent*Claim`, `crossMarket*FkReject`, `hash*Recompute` |
| TEST-05 | AC-03 | STATIC | yes | `src/test/java/com/quant/trade/marketdata/analysis/run/PublicationBatchManagerTest.java` | `class PublicationBatchManagerTest` + methods `atomic*Rollback`, `crossBatch*Reject`. H2: assert via row-visibility, not savepoint semantics |
| TEST-06 | AC-04 | STATIC | **CRITICAL** | `src/test/java/com/quant/trade/marketdata/analysis/derived/RelativeStrengthCalculatorTest.java` | `class RelativeStrengthCalculatorTest` + methods `unit*EndToEnd*0_0240` (deserialize fixture through production parse path, `chg="0.0240"` + `value_data="2.40%"` → `sectorReturn=0.0240`, formatter → `"2.40%"`), `fixed*Cohort*Intersection`, `ties*Average*Rank`, `missingDay*InsufficientSample`. **NEW-A1** applies (see §6) |
| TEST-07 | AC-05 | STATIC | **CRITICAL** | `src/test/java/com/quant/trade/marketdata/analysis/derived/SectorRotationPersistenceCalculatorTest.java` | `class SectorRotationPersistenceCalculatorTest` + methods `golden03*EndToEnd` (all 6 values tol ≤1e-9 from raw `change_rate`), `missingDay*Breaks*Continuity`, `taxonomy*OriginChanged`, `insufficientSample*Nulled` |
| TEST-08 | AC-06 | STATIC | yes | `src/test/java/com/quant/trade/marketdata/analysis/controller/MarketRadarControllerTest.java` | `class MarketRadarControllerTest` + methods `fourQuadrant*Classification`, `capital*Unavailable`, `crossBatch*Reject`, `consistent*Batch`, `evidence*Count` |
| TEST-09 | AC-07 | STATIC | yes | `src/test/java/com/quant/trade/marketdata/analysis/controller/SectorRankingHistoryControllerTest.java` | `class SectorRankingHistoryControllerTest` + method `persistent*Vs*Pulse` |
| TEST-10 | AC-07 | STATIC | yes | `src/test/java/com/quant/trade/marketdata/analysis/controller/SectorDetailControllerTest.java` | `class SectorDetailControllerTest` + methods `no*Derived*Data`, `tracking*Symbol*Isolation` |
| TEST-11 | AC-08 | STATIC | **CRITICAL** | `src/test/java/com/quant/trade/marketdata/analysis/SectorAnalyticsArchitectureGuardTest.java` | `class SectorAnalyticsArchitectureGuardTest` + methods `no*Writeback*RawFact`, `no*Provider*Reverse*Call`, `no*WatchId*In*Derived*Key`, `calendar*Inferred*Default*NonBlocking`. Frozen pattern strings live INSIDE the test |
| TEST-12 | AC-01..08 | AUTOMATION | yes | `pom.xml` | `./mvnw -q -Dtest=SectorAnalytics* test` (contract §4 form; specific enough to be machine-observable) |
| TEST-13 | AC-08 | AUTOMATION | yes | `pom.xml` | `./mvnw -q -DskipTests=false package` (CONTROL form; ensures tests actually run during packaging, not skipped) |

Coverage: every AC has ≥1 required STATIC test plus the AC-01..08-wide AUTOMATION gate (TEST-12) and the AC-08 packaging gate (TEST-13). PASS.

## 4. Boundary findings

| finding | severity | recommendation |
|---|---|---|
| Candidate/decision/strategy/backtest/auto-trade/return-contribution excluded (§1.3, §2 non-targets) | WARNING (mitigated) | Folded A4 freezes the forbidden-token list in TEST-11; carried forward unchanged. |
| AMENDMENT_02 "capital returns 0" shortcut could satisfy a weak AC-06 | WARNING | Folded A2: capital/volume col = non-null `flowMetricNature` + null value; returning `0` fails TEST-08 `capital*Unavailable`. |
| AC-04 scope forgery (`expected=actual` → fake `coverage_rate=1`) | WARNING | Folded A1 + TEST-01 `readiness*ScopeForgery`: `expected_item_count` must NOT come from rowcount; never `VERIFIED_FULL_MARKET`. |
| AC-05 GOLDEN fake (hardcode 6 numbers, no input wiring) | BLOCKING (evidence) | Folded A5: TEST-07 `golden03*EndToEnd` constructs frozen 5×5 `change_rate` input, asserts all 6 outputs e2e with tol ≤1e-9. Hardcoding without input wiring fails review. |
| HK/US calendar (AMENDMENT_03) silent `INFERRED` acceptance | WARNING | Folded A1 + TEST-01 `readiness*HkUs*Inferred`: HK/US long-window with `INFERRED` → `INSUFFICIENT_RAW`. |
| Watch-cascade FK (AMENDMENT_01) | WARNING (ok) | TEST-03 `deleteRecreate*Watch` must NOT depend on V14 FK being dropped — assert via the derived-query path; the V14 `ON DELETE CASCADE` FK remains in place this round. |
| **NEW-B1**: TEST-06 "real fixture resource" imprecision | WARNING | NEW-A1 (see §6): the codebase has zero JSON resources under `src/test/resources/`; existing provider tests use inline `JsonNode`. Tighten TEST-06 to "deserialized through the production parse path (`LongPortIndustryHttpClient` item shape: `chg` + `value_data` in the same JSON item), final `0.0240` not hardcoded." |
| **NEW-B2**: `MarketQuoteController` pre-exists; SLICE-04 must not collide | WARNING (informational) | SLICE-04 correctly scopes the radar query service to `marketdata/analysis/service/MarketRadarQueryService.java` and the radar controller to `marketdata/analysis/controller/MarketRadarController.java`. The pre-existing `marketdata/controller/MarketQuoteController.java` is out of scope. Implementer must not modify it. Not a contract defect; flagged for the reviewer's attention. |
| H2 vs InnoDB lock semantics (AC-02 concurrency layer) | WARNING (declared) | Already declared RUNTIME `NOT_VERIFIED` in AC-02; true `FOR UPDATE` contention requires real MySQL. No new action. |

## 5. Blocking amendment review (BLOCKING_AMENDMENT_01/02/03)

All three correctly scope IN vs OUT. **No financial/unit/scope semantic is relaxed.** None withdrawn.

- **BLOCKING_AMENDMENT_01 (watch cascade FK)** — IN: V19 adds `sector_identity_id` column + backfill on `market_sector_snapshot`/`market_sector_member_snapshot`, derived layer uses `sector_identity_id` as identity. OUT: does NOT drop the existing V14 `fk_sector_snapshot_watch ON DELETE CASCADE` FK (would break existing watch-lifecycle tests and exceed the L2 slice). AC-02 identity independence is proven at the derived-query layer (`deleteRecreate*Watch`), not via FK drop. Verified against V14 line 40 — cascade FK present; amendment text is accurate. **Confirmed correct.**
- **BLOCKING_AMENDMENT_02 (capital flow / concentration / volume / alerts deferred)** — IN: `FlowMetricNatureEnum` includes `UNAVAILABLE`; radar VO capital/volume fields nullable + degradation marker. OUT: actual computation of `sector_capital_flow_trend`, `sector_turnover_concentration`, `sector_volume_confirmation_snapshot`, `SECTOR_*` alerts. No fabrication: TEST-08 `capital*Unavailable` requires non-null `flowMetricNature` + null value (returning `0` fails). **Confirmed correct; A2 strengthens testability.**
- **BLOCKING_AMENDMENT_03 (HK/US authoritative calendar)** — IN: V19 `market_calendar` adds `source_code VARCHAR(32) NULL` + `verification_status VARCHAR(24) NOT NULL DEFAULT 'INFERRED'`; readiness long-window gate (RS `window=20`) fail-closes for HK/US when `INFERRED`. OUT: real calendar data population (deployment data governance). Default `INFERRED` does NOT block the existing scheduler path (verified: `TradingSessionManager` reads only `is_trading_day`, never `verification_status`). TEST-11 `calendar*Inferred*Default*NonBlocking` is the scheduler regression; TEST-01 `readiness*HkUs*Inferred` is the readiness fail-closed assertion. **Confirmed correct.**

## 6. New amendments

Only NEW findings are listed. The prior A1–A5 are folded into the R1 contract and carried forward unchanged.

### NEW-A1 (AC-04 / TEST-06, refinement — non-blocking)

**Defect found:** The folded A5 / prior TEST-06 guidance requires TEST-06 to "read a real fixture resource, not a literal." A repository scan shows `src/test/resources/` contains ZERO JSON fixture files today, and existing provider tests (e.g. `LongPortMarketSectorProviderTest`) construct their inputs inline. The §5.1 / AC-04 prohibition is actually against **hardcoding the final `sectorReturn=0.0240` value in the calculator test while skipping the deserialization step** — not against the fixture being an inline `JsonNode` vs a resource file. As written, "must read a fixture resource" is ambiguous and could be mis-implemented (an implementer might create a JSON file just to satisfy the literal wording while still hardcoding the asserted value).

**Required refinement to TEST-06 selector:** the `unit*EndToEnd*0_0240` test MUST (a) construct a fixture whose JSON item shape matches the real LongPort wire format — both `chg` and `value_data` carried in the SAME item (verified at `src/main/java/com/quant/trade/marketdata/provider/longport/LongPortIndustryHttpClient.java:83-84`), with `chg="0.0240"` and `value_data="2.40%"`; (b) pass that fixture through the production parse path (the same `decimal(item,"chg")` extraction the industry HTTP client uses, or the calculator's documented input contract that consumes the parsed `change_rate`); (c) assert the parsed `sectorReturn == 0.0240` (NOT `/100`) and the formatter produces `"2.40%"`. Whether the fixture is an inline `JsonNode` or a `src/test/resources/*.json` resource is the implementer's choice; what is mandatory is that the `chg → sectorReturn` parse step runs and the final `0.0240` is not hardcoded in the assertion source.

**Effect:** No financial semantic changed. This removes ambiguity in the §5.1 evidence method and prevents two opposite mis-implementations (hardcoded literal, or a resource file that is never actually deserialized).

No other NEW defects found. NEW-B1 and NEW-B2 in §4 are informational, not amendments.

## 7. Golden fixtures (frozen for implementation)

Values match design §5.1 and §5.2.2 exactly (re-verified line-by-line).

### GOLDEN-03 — sector rotation persistence (§5.2.2, AC-05/TEST-07)
5-sector × 5-trading-day `change_rate` fixture, daily `n_t=5`, ascending average-rank sequence `[3,4,4,5,5]`:

| output | frozen value | tolerance |
|---|---|---|
| `mean_rank_percentile` | `0.8` | ≤1e-9 |
| `rank_percentile_std_dev` | `0.18708286933869706` | ≤1e-9 |
| `top_bucket_occupancy_rate` (`rank_percentile ≥ 0.8`) | `0.4` (= 2/5) | ≤1e-9 |
| `consecutive_leading_days` | `2` (last 2 days' `change_rate` == daily cross-section max; ties count) | exact |
| `consecutive_lagging_days` | `0` (last day `change_rate` ≠ daily cross-section min) | exact |
| `rank_percentile_change` | `0.5` (= 1.0 − 0.5) | ≤1e-9 |

The test MUST construct the raw `change_rate` input that yields the `[3,4,4,5,5]` average-rank sequence and assert all 6 outputs end-to-end. Hardcoding the 6 numbers without input wiring is the forbidden shortcut.

### RS unit contract (§5.1, AC-04/TEST-06)
Real LongPort wire format: one JSON item carries `chg="0.0240"` AND `value_data="2.40%"` simultaneously. After parsing through the production path: `sectorReturn = 0.0240` (decimal ratio, NOT `/100`); formatter output `"2.40%"`. The parse step must run; the asserted `0.0240` must not be hardcoded.

## 8. Slice coherence check (§3 of contract)

Each slice is within the lane cap: ≤3 ACs, ≤8 expected files, ≤500 production-line delta.

| slice | acIds | ACs | expected files | verdict |
|---|---|---|---|---|
| SLICE-01 | AC-01, AC-02 | 2 | 8 (V19 + identity DO/mapper+xml + manager + readiness manager+VO + controller + enum + 2 tests) | coherent |
| SLICE-02 | AC-03 | 1 | 8 (V20 + 3 DO + 3 mapper.java + 3 xml + run mgr + batch mgr + hasher + constants + 2 tests) | coherent; tight — implementer should keep the 3 mappers + 3 xml compact |
| SLICE-03 | AC-04, AC-05 | 2 | 8 (V21 + 2 DO + 2 mapper.java + 2 xml + RS calc + rotation calc + cohort resolver + ranker + 2 tests) | coherent |
| SLICE-04 | AC-06 | 1 | 8 (radar VO + read-model mgr + classifier + query service + controller + 2 enums + MockMvc test) | coherent |
| SLICE-05 | AC-07, AC-08 | 2 | 8 (2 VO + 2 mgr + query service + controller + tracking guard + 2 controller tests + arch guard test) | coherent; tight — nine items listed but two are test files under one slot; acceptable |

No oversized slice. Total role-run budget (14) covers 5 implementers + 2 test-design/review repair + 1 review + 1 verify + margin. **No blocking amendment triggered by slice size.**

## 9. Verdict

- **`ACCEPTED_WITH_AMENDMENTS`** — 1 NEW refinement (NEW-A1) for TEST-06 fixture precision; no financial/unit/scope semantic relaxed; no slice oversized; no blocking defect.
- **`executionOutcome: COMPLETED`**
- READY_FOR_IMPLEMENTATION: **yes** after NEW-A1 is folded into the AC-04 evidence description (parent persists; this role does not write the contract).
- The first compaction would invalidate this artifact; none occurred.

## 10. Role provenance

- Role run id: `rr-p110a-r1-td-7e3a1c92b4d0` (FRESH_ONLY — verified not reused)
- Session id: this conversation (single, no continuation)
- Start: 2026-08-12 (role invocation) · Finish: 2026-08-12 (artifact delivered)
- Wait count: 0 · Compaction count: 0 · Repair round: 0
- Runtime tool enforcement: `ADVISORY`
- Runtime receipt path: parent-owned (test designer is read-only; no receipt written by this role)

## Relevant paths

- Contract: `docs/development/tasks/P110-A-BE-MARKET-DISCOVERY-20260812-R1-CONTRACT.md`
- Control: `docs/development/tasks/P110-A-BE-MARKET-DISCOVERY-20260812-R1-CONTROL.json`
- Authority: `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md` (§5.1 RS unit, §5.2.2 GOLDEN-03, §6.1 hash strings), `docs/features/MARKET_RESEARCH_DECISION_CENTER_DESIGN.md`, `docs/decisions/ADR-0013-research-funnel-and-asset-inspection-boundary.md`, `docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md`
- Schema evidence for amendments: `src/main/resources/db/migration/V14__add_market_sector_watch.sql` (line 40 cascade FK), `src/main/resources/db/migration/V10__add_market_data_workbench.sql` (`market_calendar` has no verification columns)
- Parse-path evidence for NEW-A1: `src/main/java/com/quant/trade/marketdata/provider/longport/LongPortIndustryHttpClient.java` (lines 83-84 read `chg` + `value_data` from same item)
- Scheduler evidence for AC-08: `src/main/java/com/quant/trade/marketdata/manager/TradingSessionManager.java` (reads `is_trading_day` only)
- Architecture gate: `scripts/check-ai-architecture.mjs`
- Test profile (H2 MODE=MySQL): `src/test/resources/application-test.properties`
- Prior frozen artifacts (reference only): `docs/development/tasks/P110-A-BE-MARKET-DISCOVERY-20260812-CONTRACT.md`, `docs/development/tasks/P110-A-BE-MARKET-DISCOVERY-20260812-TEST-DESIGN.md`
