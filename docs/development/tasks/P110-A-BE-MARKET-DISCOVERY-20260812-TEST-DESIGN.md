# P110-A Test Designer Artifact

> 持久化自 TEST_DESIGNER role run rr-p110a-td-317fe05d1154 / dispatch-p110a-test-designer-1786531425-b86c43a7
> Verdict: `ACCEPTED_WITH_AMENDMENTS` · executionOutcome: `COMPLETED`
> Contract hash challenged: f363c93fa87087aed9dc7799fa940ba0e5d65a5e8f5ff230404a436f492d2c07
> READY_FOR_IMPLEMENTATION: yes (after 5 amendments folded in; none blocks freezing)

## 1. AC testability review

| acId | testable | refinement |
|---|---|---|
| AC-01 | yes (refine) | (a) expected_item_count MUST NOT come from response rowcount; (b) provider_quote_time null→reasonCodes=SOURCE_TIME_UNKNOWN, qualityStatus≠OK; (c) HK/US long-window w/ INFERRED calendar→INSUFFICIENT_RAW |
| AC-02 | yes | Split: (a) functional = soft-archive/delete+recreate watch, derived sectorId unchanged, snapshots preserved (H2-provable); (b) concurrency = two claims same anchor→exactly one identity row (assert row count + unique constraint, NOT lock ordering — H2/MODE=MySQL cannot reproduce FOR UPDATE ordering) |
| AC-03 | yes | Pin §6.1 canonical hash input strings as frozen fixture; assert byte-identical SHA-256 to persisted; changing one parameter_hash changes group hash; add cross-batch FK rejection |
| AC-04 | yes (strong) | GOLDEN test must deserialize real provider fixture (chg="0.0240" + value_data="2.40%"), assert sectorReturn=0.0240 (NOT /100), formatter→2.40%. Add cohort-below-threshold (INSUFFICIENT_SAMPLE, no impute) + ties-by-relative_return_n |
| AC-05 | yes (CRITICAL refine) | Pin frozen 5-day×N-sector change_rate fixture → assert all 6 GOLDEN-03 values tol ≤1e-9, end-to-end from raw change_rate. Missing-day-breaks-continuity + taxonomy-break(ORIGIN_CHANGED) REQUIRED subtests |
| AC-06 | yes (refine) | (a) quadrant thresholds frozen in v1 parameter_hash, asserted; (b) capital col returns non-null flowMetricNature + null value (returning 0 is forbidden shortcut, fails test); (c) cross-batch join rejected |
| AC-07 | yes | tracking-symbol-isolation test: add tracking symbol whose RS would shift cohort, assert every other sector's rs_rank_percentile byte-identical w/ and w/o; detail exposes tracking under benchmark_symbol (non-null) while benchmark_type=RANK_SET_EQUAL_WEIGHT unchanged |
| AC-08 | yes (refine) | Require guard assert 3 concrete things w/ frozen patterns via check-ai-architecture.mjs: (a) no analysis/ UPDATE/INSERT/DELETE/MERGE vs 8 raw-fact tables; (b) no analysis/ autowires provider client; (c) watch_id not in derived idempotent key/JOIN. Plus calendar-INFERRED-default scheduler regression |

## 2. Black-box test cases (compact)

★ = golden-asserted.

- **AC-01**: ★happy(is_truncated=true echoed, scope=RANKED_UNIVERSE) · no-batch(NO_DERIVED_DATA, reasonCodes non-empty, radar refuses derived) · single-batch(THIN) · stale(STALE, no same-day mix) · source-time-null(reasonCodes=SOURCE_TIME_UNKNOWN, ≠OK) · scope-forgery-guard(expected_item_count≠rowcount, ≠VERIFIED_FULL_MARKET) · hk-us-inferred-calendar(INSUFFICIENT_RAW)
- **AC-02**: concurrent-anchor(2 threads same key→1 row) · cross-taxonomy-interval(overlap rejected) · delete-recreate-watch(derived sectorId unchanged, snapshots preserved) · no-watch-id-in-join
- **AC-03**: idempotent-rerun · param-change-no-overwrite · concurrent-claim(1 winner) · cross-market-FK-reject · ★hash-recompute(byte-identical to §6.1) · atomic-publish-rollback(partial fail→batch not published)
- **AC-04**: ★unit-e2e(real fixture chg="0.0240"→0.0240 raw/2.40% display, NOT /100) · fixed-cohort(intersection only, drifters excluded, fingerprint in hash) · ties-by-relative_return_n · missing-day→INSUFFICIENT_SAMPLE · no-fake-full-market
- **AC-05**: ★golden-03-e2e(frozen 5×5 fixture→6 values tol 1e-9) · missing-day-breaks-continuity · taxonomy-break(ORIGIN_CHANGED) · insufficient-sample(metrics nulled)
- **AC-06**: four-quadrant-classification(thresholds in parameter_hash) · capital-unavailable(flowMetricNature non-null+null value, NOT 0) · cross-batch-reject · consistent-batch(radar/rotation/ranking same publicationBatchId+asOfDate) · evidence-count(≥2 evidence+non-empty reasonCodes) · degradation
- **AC-07**: persistent-vs-pulse(consecutive_leading_days distinguishes) · detail-no-derived(NO_DERIVED_DATA, no stale published_at) · ★tracking-symbol-isolation(public RS-rank vector identical w/wo tracking) · component-scope(NOT VERIFIED_SECTOR_UNIVERSE)
- **AC-08**: no-writeback · no-provider-reverse · no-watch-id-in-key · no-forbidden-product · calendar-inferred-default-non-blocking(scheduler regression)

## 3. Test inventory validation

Repo grep confirmed: NO `@DisplayName` anywhere → selectors use method-name patterns.

| testId | valid | refinement / H2 note |
|---|---|---|
| TEST-01 | yes | add method substrings: readiness*NoBatch, readiness*Stale, readiness*SourceTimeUnknown |
| TEST-02 | yes | bare `readiness*` glob TOO LOOSE→refine to class + methods readiness*NoBatch, readiness*Happy |
| TEST-03 | yes | add concurrent*Anchor, crossTaxonomy*Interval, deleteRecreate*Watch. H2: assert outcome(row count+unique), NOT lock ordering. True contention=RUNTIME NOT_VERIFIED |
| TEST-04 | yes | add idempotent*Rerun, paramChange*NoOverwrite, concurrent*Claim, crossMarket*FkReject, hash*Recompute |
| TEST-05 | yes | add atomic*Rollback, crossBatch*Reject. H2: assert via row-visibility not savepoint |
| TEST-06 | CRITICAL | MUST require methods: unit*EndToEnd*0_0240 (reads real fixture resource, NOT literal — §5.1 forbids), fixed*Cohort*Intersection, ties*Average*Rank |
| TEST-07 | CRITICAL | MUST require methods: golden03*EndToEnd (all 6 values tol 1e-9), missingDay*Breaks*Continuity, taxonomy*OriginChanged |
| TEST-08 | yes | add fourQuadrant*Classification, capital*Unavailable, crossBatch*Reject, consistent*Batch |
| TEST-09 | yes | add persistent*Vs*Pulse |
| TEST-10 | yes | add no*Derived*Data, tracking*Symbol*Isolation |
| TEST-11 | CRITICAL | MUST require: no*Writeback*RawFact, no*Provider*Reverse*Call, no*WatchId*In*Derived*Key, calendar*Inferred*Default*NonBlocking. Patterns frozen IN test |
| TEST-12 | yes | RECONCILED: `./mvnw -q -Dtest=SectorAnalytics* test` (contract §4 form — specific) |
| TEST-13 | yes | RECONCILED: `./mvnw -q -DskipTests=false package` (CONTROL form — ensures tests run) |

Coverage: every AC has ≥1 required test. PASS.

## 4. Boundary findings

| finding | sev | recommendation |
|---|---|---|
| Candidate/decision/strategy/backtest/auto-trade/return-contribution excluded | WARNING(mitigated) | A4 freezes forbidden-token list in TEST-11 |
| AMENDMENT_02 "return 0 for capital" shortcut could satisfy AC-06 | WARNING | A2: capital col = non-null flowMetricNature + null value; returning 0 fails |
| AC-04 scope forgery (set expected=actual) | WARNING | TC-01f + A1: expected_item_count must NOT come from rowcount |
| AC-05 GOLDEN fake (hardcode 6 numbers) | BLOCKING(evidence) | A5: TC-05a constructs frozen input, asserts e2e |
| HK/US calendar (AMENDMENT_03) silent INFERRED use | WARNING | A1: HK/US long-window w/ INFERRED→INSUFFICIENT_RAW |
| Watch-cascade FK (AMENDMENT_01) | WARNING(ok) | TC-02c must NOT depend on V14 FK dropped; use derived-query path |

## 5. Blocking amendment review

All 3 CORRECTLY scope IN vs OUT, no financial semantic relaxed. None withdrawn. A2 strengthens AMENDMENT_02 testability.

## 6. Amendments (folded before freeze; no semantic change)

- **A1 (AC-01)**: (a) expected_item_count MUST NOT come from response rowcount; (b) provider_quote_time null→reasonCodes=SOURCE_TIME_UNKNOWN, qualityStatus≠OK; (c) HK/US long-window w/ INFERRED calendar→INSUFFICIENT_RAW.
- **A2 (AC-06)**: capital/volume degradation = non-null flowMetricNature + null value (0 is forbidden shortcut, fails test).
- **A3 (AC-06)**: quadrant thresholds frozen in v1 parameter_hash, asserted TC-06a. OPEN QUESTION (not blocking): exact numeric thresholds are product decision; design §4.4 accepts as v1 param. Implementer freezes a documented default in MARKET_RESEARCH_API.md.
- **A4 (AC-08/TEST-11)**: guard asserts 3 things w/ frozen patterns — (a) no analysis/ UPDATE/INSERT/DELETE/MERGE vs 8 raw-fact tables; (b) no analysis/ autowires provider client; (c) watch_id not in derived key/JOIN. Plus calendar-INFERRED scheduler regression.
- **A5 (AC-05/TEST-07, CRITICAL)**: TC-05a constructs frozen 5×5 change_rate fixture, asserts all 6 GOLDEN-03 outputs tol 1e-9 e2e. Hardcoding 6 numbers w/o input wiring = forbidden shortcut, fails review. Mirror: TC-04a/TEST-06 reads real LongPort fixture resource (§5.1).
- **Selector reconcile**: TEST-12 = `./mvnw -q -Dtest=SectorAnalytics* test`. TEST-13 = `./mvnw -q -DskipTests=false package`.

## 7. Golden fixtures (frozen for implementation)

### GOLDEN-03 (sector rotation persistence, §5.2.2)
5-sector × 5-day fixture, daily `n_t=5`, ascending average rank `[3,4,4,5,5]`:
- mean_rank_percentile = 0.8
- rank_percentile_std_dev = 0.18708286933869706
- top_bucket_occupancy_rate (≥0.8) = 0.4
- consecutive_leading_days = 2 (last 2 days change_rate == daily cross-section max)
- consecutive_lagging_days = 0
- rank_percentile_change = 0.5
- tolerance ≤ 1e-9

### RS unit contract (§5.1)
Real LongPort fixture: `chg="0.0240"` + `value_data="2.40%"` → sectorReturn=0.0240 (NOT /100), formatter→"2.40%". Test MUST read fixture resource, not literal.

## Relevant paths
- Contract: docs/development/tasks/P110-A-BE-MARKET-DISCOVERY-20260812-CONTRACT.md
- Control: docs/development/tasks/P110-A-BE-MARKET-DISCOVERY-20260812-CONTROL.json
- Test profile: src/test/resources/application-test.properties (H2 MODE=MySQL)
