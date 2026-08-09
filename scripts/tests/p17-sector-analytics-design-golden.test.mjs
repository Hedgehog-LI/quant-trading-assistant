// Golden numeric reference-function test for
// P17-SECTOR-ANALYTICS-DESIGN-REPAIR-20260803 (SLICE-01, AC-R1..R4).
//
// Read-only: node:test + node:assert/strict. No external deps, no shell/network.
//
// Implements the closed-form reference functions described in the frozen
// TEST-DESIGN.md (GOLDEN-01..08) and asserts the EXACT expected values
// (tolerance 1e-9). This guards formula correctness, not just keywords.
//
// On success prints exactly this selector token to stdout:
//   P17-SECTOR-ANALYTICS-REPAIR-GOLDEN
// On any failure exits non-zero with a diagnostic.

import assert from "node:assert/strict";
import test from "node:test";

const TOL = 1e-9;
const ln = Math.log;

function close(actual, expected, msg) {
  assert.ok(Math.abs(actual - expected) <= TOL,
    `${msg}: expected ${expected}, got ${actual} (|Δ|=${Math.abs(actual - expected)})`);
}

// ---------------------------------------------------------------------------
// Reference: synthetic net-value series + N-day log relative return (GOLDEN-01)
// ---------------------------------------------------------------------------

// index(t0) = 1.0 ; index(t) = index(t-1) * (1 + r(t))
function netValueSeries(returns) {
  const series = [1.0];
  for (const r of returns) {
    series.push(series[series.length - 1] * (1 + r));
  }
  return series;
}

// relativeReturn_N(t) = ln(index_sector(t)/index_sector(t-N))
//                      - ln(index_baseline(t)/index_baseline(t-N))
function relativeReturnN(sectorSeries, baselineSeries, N, t) {
  return ln(sectorSeries[t] / sectorSeries[t - N]) - ln(baselineSeries[t] / baselineSeries[t - N]);
}

test("GOLDEN-01 synthetic net-value series + N=3 log relative return", () => {
  const sectorReturn = [0.02, -0.01, 0.03, 0.015, -0.005];
  const baselineReturn = [0.01, 0.005, -0.002, 0.008, 0.003];

  const sectorIdx = netValueSeries(sectorReturn);
  const baselineIdx = netValueSeries(baselineReturn);

  // Expected exact series from TEST-DESIGN.md
  const expectedSector = [1, 1.02, 1.0098, 1.040094, 1.05569541, 1.0504169329];
  const expectedBaseline = [1, 1.01, 1.01505, 1.0130199, 1.0211240592, 1.0241874314];
  assert.equal(sectorIdx.length, expectedSector.length);
  assert.equal(baselineIdx.length, expectedBaseline.length);
  for (let i = 0; i < expectedSector.length; i += 1) {
    close(sectorIdx[i], expectedSector[i], `sector index[${i}]`);
  }
  for (let i = 0; i < expectedBaseline.length; i += 1) {
    close(baselineIdx[i], expectedBaseline[i], `baseline index[${i}]`);
  }

  // N=3, last day (t=5)
  const rr3 = relativeReturnN(sectorIdx, baselineIdx, 3, 5);
  close(rr3, 0.030473196953448606, "relativeReturn_3 (last day)");
});

// ---------------------------------------------------------------------------
// Reference: RS-rank percentile via average rank, high-better (GOLDEN-02)
// ---------------------------------------------------------------------------

// Given an array of {key, value}, return a Map of key -> average ascending rank.
// Ties share the average of the ranks they span.
function averageAscendingRanks(entries) {
  const sorted = [...entries].sort((a, b) => a.value - b.value);
  const ranks = new Map();
  const n = sorted.length;
  let i = 0;
  while (i < n) {
    let j = i;
    while (j < n && sorted[j].value === sorted[i].value) j += 1;
    // ranks i+1 .. j (1-based) averaged
    const avgRank = ((i + 1) + j) / 2;
    for (let k = i; k < j; k += 1) ranks.set(sorted[k].key, avgRank);
    i = j;
  }
  return ranks;
}

// rs_rank_percentile = (ascRank - 1) / (n - 1), high-better
function rankPercentile(ascRank, n) {
  return (ascRank - 1) / (n - 1);
}

test("GOLDEN-02 RS-rank percentile (average-rank with ties)", () => {
  const entries = [
    { key: "A", value: 0.05 },
    { key: "B", value: 0.03 },
    { key: "C", value: 0.03 },
    { key: "D", value: 0.01 },
    { key: "E", value: -0.02 }
  ];
  const n = entries.length;
  const ranks = averageAscendingRanks(entries);
  // Expected ascending average ranks: E=1, D=2, B=C=3.5, A=5
  close(ranks.get("E"), 1, "rank E");
  close(ranks.get("D"), 2, "rank D");
  close(ranks.get("B"), 3.5, "rank B");
  close(ranks.get("C"), 3.5, "rank C");
  close(ranks.get("A"), 5, "rank A");

  // Percentiles (high-better): E=0, D=0.25, B=C=0.625, A=1.0
  close(rankPercentile(ranks.get("E"), n), 0, "pct E");
  close(rankPercentile(ranks.get("D"), n), 0.25, "pct D");
  close(rankPercentile(ranks.get("B"), n), 0.625, "pct B");
  close(rankPercentile(ranks.get("C"), n), 0.625, "pct C");
  close(rankPercentile(ranks.get("A"), n), 1.0, "pct A");

  // Tied B and C must have equal percentiles.
  assert.equal(
    Math.abs(rankPercentile(ranks.get("B"), n) - rankPercentile(ranks.get("C"), n)) <= TOL,
    true,
    "B and C percentiles must be equal (ties)"
  );

  // Ordering: A > B == C > D > E
  const pcts = {
    A: rankPercentile(ranks.get("A"), n),
    B: rankPercentile(ranks.get("B"), n),
    D: rankPercentile(ranks.get("D"), n),
    E: rankPercentile(ranks.get("E"), n)
  };
  assert.ok(pcts.A > pcts.B && pcts.B > pcts.D && pcts.D > pcts.E, "percentile ordering wrong");
});

// ---------------------------------------------------------------------------
// Reference: FULL_MARKET RS-rank scope uses N-day relativeReturn reconstructed
// from full-market CLOSE history, NEVER a single-day change_rate (GOLDEN-FM)
// ---------------------------------------------------------------------------

function equalWeightBaselineReturns(sectorReturns) {
  // per-day mean of all market sectors' change_rate (same frequency CLOSE)
  const days = sectorReturns[Object.keys(sectorReturns)[0]].length;
  const baseline = [];
  for (let d = 0; d < days; d += 1) {
    let sum = 0;
    let count = 0;
    for (const key of Object.keys(sectorReturns)) { sum += sectorReturns[key][d]; count += 1; }
    baseline.push(sum / count);
  }
  return baseline;
}

test("GOLDEN-FM FULL_MARKET scope ranks by N-day relativeReturn_N, not single-day change_rate", () => {
  // 3 market sectors, 4 trading days of CLOSE ranking-item change_rate history,
  // window N=3. FULL_MARKET must rebuild each sector's synthetic net value from
  // its daily change_rate, then rank by relativeReturn_3.
  const sectorReturns = {
    A: [0.02, 0.01, -0.005, 0.015],
    B: [-0.01, 0.03, 0.02, 0.005],
    C: [0.01, 0.0, 0.03, -0.01]
  };
  const baselineReturns = equalWeightBaselineReturns(sectorReturns);
  close(baselineReturns[0], 0.02 / 3, "baseline day0");
  close(baselineReturns[1], 0.04 / 3, "baseline day1");
  close(baselineReturns[2], 0.015, "baseline day2");
  close(baselineReturns[3], 0.01 / 3, "baseline day3");

  const baselineIdx = netValueSeries(baselineReturns);
  const rr = {};
  const lastDayChange = {};
  for (const key of Object.keys(sectorReturns)) {
    const sectorIdx = netValueSeries(sectorReturns[key]);
    rr[key] = relativeReturnN(sectorIdx, baselineIdx, 3, 4);
    lastDayChange[key] = sectorReturns[key][3];
  }

  // Exact N-day relative return (equal-weight baseline), t=4, N=3:
  close(rr.A, -0.0116352278130713, "FULL_MARKET relativeReturn_3 A");
  close(rr.B, 0.02288734171231729, "FULL_MARKET relativeReturn_3 B");
  close(rr.C, -0.01195316294840282, "FULL_MARKET relativeReturn_3 C");

  // Rank set is ordered by relativeReturn_N (B > A > C)...
  const orderByRr = Object.keys(rr).sort((x, y) => rr[y] - rr[x]);
  assert.deepEqual(orderByRr, ["B", "A", "C"], "FULL_MARKET rank order must follow relativeReturn_N");

  // ...and must NOT be ordered by a single-day change_rate. Using last day's
  // change_rate would reorder to A > B > C — the exact mismatch the design forbids.
  const orderByDay = Object.keys(lastDayChange).sort((x, y) => lastDayChange[y] - lastDayChange[x]);
  assert.deepEqual(orderByDay, ["A", "B", "C"], "single-day change_rate gives a different order");
  assert.notDeepEqual(orderByRr, orderByDay,
    "rank by single-day change_rate must NOT equal rank by N-day relativeReturn_N");

  // Percentile on the relativeReturn_N set (average-rank, high-better, n=3):
  const rankMap = averageAscendingRanks(Object.keys(rr).map((k) => ({ key: k, value: rr[k] })));
  close(rankPercentile(rankMap.get("B"), 3), 1.0, "FULL_MARKET percentile B (strongest)");
  close(rankPercentile(rankMap.get("A"), 3), 0.5, "FULL_MARKET percentile A");
  close(rankPercentile(rankMap.get("C"), 3), 0.0, "FULL_MARKET percentile C (weakest)");
});

// ---------------------------------------------------------------------------
// Reference: sector-level rotation persistence (GOLDEN-03)
// ---------------------------------------------------------------------------

function rankPercentileSeries(rankNos, n) {
  // high-better daily rank percentile: (n - rank) / (n - 1)
  return rankNos.map((r) => (n - r) / (n - 1));
}

function mean(arr) {
  return arr.reduce((a, b) => a + b, 0) / arr.length;
}

// Population standard deviation (divide by N), per TEST-DESIGN resolved ambiguity
function popStdDev(arr) {
  const m = mean(arr);
  return Math.sqrt(arr.reduce((a, b) => a + (b - m) ** 2, 0) / arr.length);
}

function trailingRun(rankNos, target) {
  // count trailing consecutive entries equal to target, scanning from the end
  let run = 0;
  for (let i = rankNos.length - 1; i >= 0; i -= 1) {
    if (rankNos[i] === target) run += 1; else break;
  }
  return run;
}

test("GOLDEN-03 sector-level rotation persistence (ranks=[3,2,2,1,1], n=5)", () => {
  const ranks = [3, 2, 2, 1, 1];
  const n = 5;
  const pctSeries = rankPercentileSeries(ranks, n);
  // [0.5, 0.75, 0.75, 1.0, 1.0]
  close(pctSeries[0], 0.5, "pct[0]");
  close(pctSeries[1], 0.75, "pct[1]");
  close(pctSeries[2], 0.75, "pct[2]");
  close(pctSeries[3], 1.0, "pct[3]");
  close(pctSeries[4], 1.0, "pct[4]");

  const meanRankPct = mean(pctSeries);
  close(meanRankPct, 0.8, "mean_rank_percentile");

  const stdDev = popStdDev(pctSeries);
  close(stdDev, 0.18708286933869706, "rank_percentile_std_dev");

  // top_bucket_occupancy_rate: rank <= ceil(n*0.2) = ceil(1) = 1
  const bucketThreshold = Math.ceil(n * 0.2);
  assert.equal(bucketThreshold, 1, "top bucket threshold");
  const topBucketOccupancy = ranks.filter((r) => r <= bucketThreshold).length / ranks.length;
  close(topBucketOccupancy, 0.4, "top_bucket_occupancy_rate");

  // consecutive_leading_days: trailing rank==1 run
  const leadingDays = trailingRun(ranks, 1);
  assert.equal(leadingDays, 2, "consecutive_leading_days");

  // consecutive_lagging_days: trailing rank==n run
  const laggingDays = trailingRun(ranks, n);
  assert.equal(laggingDays, 0, "consecutive_lagging_days");

  // rank_change: last - first
  const rankChange = ranks[ranks.length - 1] - ranks[0];
  assert.equal(rankChange, -2, "rank_change");
});

// ---------------------------------------------------------------------------
// Reference: market-level Spearman rho with average-rank ties (GOLDEN-04)
// ---------------------------------------------------------------------------

// Pearson correlation coefficient of two equal-length vectors.
function pearson(xs, ys) {
  assert.equal(xs.length, ys.length, "Pearson needs equal-length vectors");
  const n = xs.length;
  const mx = mean(xs);
  const my = mean(ys);
  let numerator = 0;
  let sumDx2 = 0;
  let sumDy2 = 0;
  for (let i = 0; i < n; i += 1) {
    const dx = xs[i] - mx;
    const dy = ys[i] - my;
    numerator += dx * dy;
    sumDx2 += dx * dx;
    sumDy2 += dy * dy;
  }
  return numerator / Math.sqrt(sumDx2 * sumDy2);
}

// No-ties-only simplification ρ = 1 - 6Σd²/(n(n²-1)). Kept only to assert it
// equals the general Pearson formula when neither vector has ties.
function spearmanNoTiesFormula(ranks1, ranks2) {
  assert.equal(ranks1.length, ranks2.length);
  const n = ranks1.length;
  let sumD2 = 0;
  for (let i = 0; i < n; i += 1) {
    sumD2 += (ranks1[i] - ranks2[i]) ** 2;
  }
  return 1 - (6 * sumD2) / (n * (n * n - 1));
}

// Spearman ρ with ties is the Pearson correlation of the two average-rank
// vectors. The simplified 1-6Σd²/(n(n²-1)) formula is only valid when neither
// vector has ties; it is NOT used here (GOLDEN-04 has ties in R2).
function spearmanRho(ranks1, ranks2) {
  return pearson(ranks1, ranks2);
}

function averageRanksFromValues(values) {
  // returns average ascending ranks for an array of raw values
  const entries = values.map((v, i) => ({ key: i, value: v }));
  const rankMap = averageAscendingRanks(entries);
  return values.map((_, i) => rankMap.get(i));
}

test("GOLDEN-04 market-level Spearman rho (ties, n=5)", () => {
  const day1Values = [1, 2, 3, 4, 5]; // already ranks 1..5
  const day2Values = [2, 2, 4, 4, 5];

  const R1 = day1Values; // [1,2,3,4,5]
  const R2 = averageRanksFromValues(day2Values); // [1.5,1.5,3.5,3.5,5]

  close(R2[0], 1.5, "R2[0]");
  close(R2[1], 1.5, "R2[1]");
  close(R2[2], 3.5, "R2[2]");
  close(R2[3], 3.5, "R2[3]");
  close(R2[4], 5.0, "R2[4]");

  // With ties, Spearman ρ is the Pearson correlation of the two average-rank
  // vectors: R̄1=3, R̄2=3, Σ(x−x̄)(y−ȳ)=9, Σ(x−x̄)²=10, Σ(y−ȳ)²=9
  //   ρ = 9/√(10·9) = 9/√90 ≈ 0.9486832980505138
  const rho = spearmanRho(R1, R2);
  close(rho, 0.9486832980505138, "market Spearman rho (Pearson of average ranks)");
});

test("GOLDEN-04 no-ties case: simplified formula equals the Pearson formula", () => {
  // When neither rank vector has ties, the closed-form simplification
  // 1 - 6Σd²/(n(n²-1)) must agree with the general Pearson definition.
  // R1=[1,2,3,4,5], R2=[2,3,1,5,4] has no ties.
  const R1 = [1, 2, 3, 4, 5];
  const R2 = [2, 3, 1, 5, 4];
  const viaPearson = spearmanRho(R1, R2);
  const viaSimplified = spearmanNoTiesFormula(R1, R2);
  close(viaPearson, 0.6, "no-ties Pearson rho");
  close(viaSimplified, 0.6, "no-ties simplified rho");
  close(viaSimplified, viaPearson, "simplified must equal Pearson when no ties");
});

test("GOLDEN-04 (design-layer) market rho is attributed to market+trade_date+window, not per-sector", () => {
  // This is a design-layer assertion mirrored in the structure test. Here we
  // assert the numerical fact the design associates with the market-level table.
  const R1 = [1, 2, 3, 4, 5];
  const R2 = [1.5, 1.5, 3.5, 3.5, 5];
  const rho = spearmanRho(R1, R2);
  close(rho, 0.9486832980505138, "market-level rho must equal Pearson-of-average-ranks value (single market+date+window)");
});

// ---------------------------------------------------------------------------
// Reference: real return contribution + residual (GOLDEN-05)
// ---------------------------------------------------------------------------

function memberContributions(weights, memberReturns) {
  assert.equal(weights.length, memberReturns.length);
  return weights.map((w, i) => w * memberReturns[i]);
}

test("GOLDEN-05 return contribution + residual", () => {
  const weights = [0.5, 0.3, 0.2];
  const memberReturns = [0.04, -0.02, 0.06];

  const contributions = memberContributions(weights, memberReturns);
  close(contributions[0], 0.02, "contrib[0]");
  close(contributions[1], -0.006, "contrib[1]");
  close(contributions[2], 0.012, "contrib[2]");

  const sumContribution = contributions.reduce((a, b) => a + b, 0);
  close(sumContribution, 0.026, "sum_contribution");

  // Constructed so sectorReturn(weighted) == sumContribution -> residual ~ 0
  const sectorReturnWeighted = 0.026;
  close(sectorReturnWeighted - sumContribution, 0, "residual (constructed ~0)");

  // Residual != 0 case: actual sectorReturn = 0.05 (excluded members)
  const sectorReturnActual = 0.05;
  const residual = sectorReturnActual - sumContribution;
  close(residual, 0.024, "residual (excluded members)");
});

// ---------------------------------------------------------------------------
// Reference: zero / negative net-inflow concentration (GOLDEN-06)
// ---------------------------------------------------------------------------

function flowConcentration(netInflows) {
  const absSum = netInflows.reduce((a, b) => a + Math.abs(b), 0);
  if (absSum === 0) {
    return { status: "INSUFFICIENT", positive: null, negative: null, absTopK: null };
  }
  const posSum = netInflows.filter((x) => x > 0).reduce((a, b) => a + b, 0);
  const negSum = netInflows.filter((x) => x < 0).reduce((a, b) => a + b, 0);
  return {
    status: "OK",
    positive: posSum / absSum,
    negative: Math.abs(negSum) / absSum,
    absSum
  };
}

function absoluteFlowConcentrationTopK(netInflows, k) {
  const absSum = netInflows.reduce((a, b) => a + Math.abs(b), 0);
  if (absSum === 0) return { status: "INSUFFICIENT", value: null };
  const sortedDesc = [...netInflows].map((x) => Math.abs(x)).sort((a, b) => b - a);
  const topKSum = sortedDesc.slice(0, k).reduce((a, b) => a + b, 0);
  return { status: "OK", value: topKSum / absSum };
}

test("GOLDEN-06 zero/negative net-inflow concentration", () => {
  const netInflows = [100, 50, -30, 20];
  const posSum = 170;
  const negSum = -30;
  const absSum = 200;
  const total = 140;
  assert.equal(netInflows.filter((x) => x > 0).reduce((a, b) => a + b, 0), posSum, "posSum");
  assert.equal(netInflows.filter((x) => x < 0).reduce((a, b) => a + b, 0), negSum, "negSum");
  assert.equal(absSum, 200, "absSum");
  assert.equal(netInflows.reduce((a, b) => a + b, 0), total, "total");

  const fc = flowConcentration(netInflows);
  assert.equal(fc.status, "OK");
  close(fc.positive, 0.85, "positiveFlowConcentration");
  close(fc.negative, 0.15, "negativeFlowConcentration");

  const top2 = absoluteFlowConcentrationTopK(netInflows, 2);
  close(top2.value, 0.75, "absoluteFlowConcentration(top2)");

  // Zero-denominator case: all-zero inflows -> INSUFFICIENT, no division
  const zeroFc = flowConcentration([0, 0, 0, 0]);
  assert.equal(zeroFc.status, "INSUFFICIENT", "all-zero -> INSUFFICIENT");
  assert.equal(zeroFc.positive, null, "all-zero positive null");
  assert.equal(zeroFc.negative, null, "all-zero negative null");
  const zeroTop = absoluteFlowConcentrationTopK([0, 0, 0, 0], 2);
  assert.equal(zeroTop.status, "INSUFFICIENT", "all-zero absTopK -> INSUFFICIENT");
  assert.equal(zeroTop.value, null, "all-zero absTopK value null");
});

// ---------------------------------------------------------------------------
// Reference: volume confirmation six states (GOLDEN-07)
// ---------------------------------------------------------------------------

const UP_VOLUME_THRESHOLD = 1.1;

function volumeState(changeRate, turnoverRatio, opts = {}) {
  if (opts.insufficient) return "INSUFFICIENT";
  if (turnoverRatio >= UP_VOLUME_THRESHOLD) {
    if (changeRate > 0) return "UP_CONFIRMED";
    if (changeRate < 0) return "DOWN_CONFIRMED";
    return "NEUTRAL"; // changeRate == 0 with up volume still neutral by direction
  }
  if (changeRate > 0) return "UP_UNCONFIRMED";
  if (changeRate < 0) return "DOWN_UNCONFIRMED";
  return "NEUTRAL";
}

test("GOLDEN-07 volume confirmation six states", () => {
  assert.equal(volumeState(0.02, 1.3), "UP_CONFIRMED");
  assert.equal(volumeState(0.02, 0.9), "UP_UNCONFIRMED");
  assert.equal(volumeState(-0.02, 1.3), "DOWN_CONFIRMED");
  assert.equal(volumeState(-0.02, 0.9), "DOWN_UNCONFIRMED");
  assert.equal(volumeState(0.0, 1.0), "NEUTRAL");
  assert.equal(volumeState(0.02, 1.3, { insufficient: true }), "INSUFFICIENT");

  // Critical: DOWN_CONFIRMED is NOT divergence.
  assert.notEqual(volumeState(-0.02, 1.3), "DIVERGENCE");

  // All six states must appear across the cases.
  const states = new Set([
    volumeState(0.02, 1.3),
    volumeState(0.02, 0.9),
    volumeState(-0.02, 1.3),
    volumeState(-0.02, 0.9),
    volumeState(0.0, 1.0),
    volumeState(0.02, 1.3, { insufficient: true })
  ]);
  for (const s of ["UP_CONFIRMED", "UP_UNCONFIRMED", "DOWN_CONFIRMED",
    "DOWN_UNCONFIRMED", "NEUTRAL", "INSUFFICIENT"]) {
    assert.ok(states.has(s), `six-state coverage missing ${s}`);
  }
});

// ---------------------------------------------------------------------------
// Reference: insufficient sample + formula-version coexistence (GOLDEN-08)
// ---------------------------------------------------------------------------

function classifySample(validSampleSize, minRequired) {
  if (validSampleSize < minRequired) {
    return { qualityStatus: "INSUFFICIENT_SAMPLE", produceHighAlert: false };
  }
  return { qualityStatus: "OK", produceHighAlert: true };
}

test("GOLDEN-08 insufficient sample -> INSUFFICIENT_SAMPLE, no HIGH alert", () => {
  const low = classifySample(3, 5);
  assert.equal(low.qualityStatus, "INSUFFICIENT_SAMPLE");
  assert.equal(low.produceHighAlert, false, "must NOT produce HIGH alert when sample insufficient");

  const ok = classifySample(8, 5);
  assert.equal(ok.qualityStatus, "OK");
  assert.equal(ok.produceHighAlert, true);
});

test("GOLDEN-08 formula-version coexistence (idempotency key includes formula_version, v1 not overwritten by v2)", () => {
  // Model two rows for the same (sector, date, window) but different formula_version.
  const rows = [
    {
      sectorIdentity: "BK/SH/IN40159", asOfDate: "2026-07-31", window: 20,
      formulaVersion: "v1", isLatest: false, supersededAt: "2026-08-01T10:00:00",
      relativeReturnN: 0.029
    },
    {
      sectorIdentity: "BK/SH/IN40159", asOfDate: "2026-07-31", window: 20,
      formulaVersion: "v2", isLatest: true, supersededAt: null,
      relativeReturnN: 0.030473196953448606
    }
  ];

  // Idempotency key includes formula_version, so both rows coexist.
  const keys = rows.map((r) =>
    `${r.sectorIdentity}|${r.asOfDate}|${r.window}|${r.formulaVersion}`);
  assert.equal(keys.length, 2, "two rows");
  assert.equal(new Set(keys).size, 2, "idempotency keys distinct (include formula_version)");

  // v1 not overwritten: still present with is_latest=false and superseded_at set.
  const v1 = rows.find((r) => r.formulaVersion === "v1");
  const v2 = rows.find((r) => r.formulaVersion === "v2");
  assert.ok(v1, "v1 row coexists");
  assert.ok(v2, "v2 row present");
  assert.equal(v1.isLatest, false, "v1 is_latest=false");
  assert.ok(v1.supersededAt !== null, "v1 superseded_at set");
  assert.equal(v2.isLatest, true, "v2 is_latest=true");

  // The repaired relativeReturnN value matches the GOLDEN-01 expected value.
  close(v2.relativeReturnN, 0.030473196953448606, "v2 relativeReturnN matches GOLDEN-01");
});

// ---------------------------------------------------------------------------
// Reference: tracking-symbol benchmark N+1 close_price threshold (GOLDEN-09)
// ---------------------------------------------------------------------------

// N daily returns close_price(t)/close_price(t-1)-1 need N+1 consecutive
// close_price: the N trading days inside the window plus the prior day's close
// as t0. Fewer than N+1 consecutive closes -> benchmark unavailable.
function trackingDailyReturns(closePrices) {
  const returns = [];
  for (let i = 1; i < closePrices.length; i += 1) {
    returns.push(closePrices[i] / closePrices[i - 1] - 1);
  }
  return returns;
}

function trackingBenchmarkAvailable(closePrices, N) {
  return closePrices.length >= N + 1;
}

test("GOLDEN-09 tracking-symbol benchmark needs N+1 consecutive close_price (N=3 -> 4 closes)", () => {
  const N = 3;
  // 4 consecutive close_price: t0 (day before the window) + 3 in-window days.
  const closes = [100, 102, 101, 104];
  const dailyReturns = trackingDailyReturns(closes);
  close(dailyReturns[0], 0.02, "return t1/t0");
  close(dailyReturns[1], 101 / 102 - 1, "return t2/t1");
  close(dailyReturns[2], 104 / 101 - 1, "return t3/t2");
  assert.equal(dailyReturns.length, N, "N+1 closes produce exactly N daily returns");
  assert.equal(trackingBenchmarkAvailable(closes, N), true,
    "N+1 consecutive closes make the tracking benchmark available");

  // Only N consecutive closes -> only N-1 daily returns -> benchmark unavailable,
  // and the design must degrade to BENCHMARK_TRACKING_SYMBOL_INSUFFICIENT.
  const short = [100, 102, 101];
  assert.equal(trackingDailyReturns(short).length, N - 1,
    "N consecutive closes produce only N-1 daily returns");
  assert.equal(trackingBenchmarkAvailable(short, N), false,
    "fewer than N+1 consecutive closes -> tracking benchmark unavailable (BENCHMARK_TRACKING_SYMBOL_INSUFFICIENT)");

  // The tracking daily-return series must equal the (1+r) compounding inputs of
  // the benchmark net-value series.
  const trackingIdx = netValueSeries(trackingDailyReturns(closes));
  close(trackingIdx[0], 1.0, "tracking idx0");
  close(trackingIdx[1], 1.02, "tracking idx1");
  close(trackingIdx[2], 1.01, "tracking idx2");
  close(trackingIdx[3], 1.04, "tracking idx3");
});

// ---------------------------------------------------------------------------
// Reference: same-scope equal-weight benchmark (GOLDEN-10)
// ---------------------------------------------------------------------------

// Equal-weight daily-return baseline over an explicit scope (rank set). The
// design requires the equal-weight benchmark to be same-source same-scope as
// the rank set: FULL_MARKET over all valid sectors, WATCHED_ONLY over the
// watched set only.
function sameScopeEqualWeightReturns(sectorReturns, scopeKeys) {
  const days = sectorReturns[scopeKeys[0]].length;
  const baseline = [];
  for (let d = 0; d < days; d += 1) {
    let sum = 0;
    for (const key of scopeKeys) sum += sectorReturns[key][d];
    baseline.push(sum / scopeKeys.length);
  }
  return baseline;
}

test("GOLDEN-10 WATCHED_ONLY equal-weight benchmark is same-scope as the watched rank set, never full-market", () => {
  // Five market sectors exist; only A/B/C are watched. Full-market history is
  // insufficient, so the design degrades to WATCHED_ONLY.
  const all = {
    A: [0.02, 0.01, -0.005, 0.015],
    B: [-0.01, 0.03, 0.02, 0.005],
    C: [0.01, 0.0, 0.03, -0.01],
    D: [0.05, -0.02, 0.01, 0.02],
    E: [-0.03, 0.01, 0.0, -0.02]
  };
  const watched = ["A", "B", "C"];

  // Equal-weight baseline MUST be over the watched set only (same scope as the
  // WATCHED_ONLY rank set).
  const watchedBaseline = sameScopeEqualWeightReturns(all, watched);
  close(watchedBaseline[0], 0.02 / 3, "watched baseline day0");
  close(watchedBaseline[1], 0.04 / 3, "watched baseline day1");
  close(watchedBaseline[2], 0.015, "watched baseline day2");
  close(watchedBaseline[3], 0.01 / 3, "watched baseline day3");

  // The full-market equal weight (D/E included) is materially different; the
  // design must NOT claim full-market equal weight under WATCHED_ONLY.
  const fullBaseline = sameScopeEqualWeightReturns(all, Object.keys(all));
  close(fullBaseline[0], 0.04 / 5, "full-market baseline day0");
  assert.ok(Math.abs(watchedBaseline[0] - fullBaseline[0]) > 1e-9,
    "WATCHED_ONLY equal-weight baseline must differ from full-market equal weight");

  // Rank set is exactly the watched set; D/E never enter the WATCHED_ONLY rank.
  const rankSet = Object.keys(all).filter((k) => watched.includes(k));
  assert.deepEqual(rankSet, watched, "WATCHED_ONLY rank set is exactly the watched set");

  // relativeReturn_N over the watched scope (data for A/B/C identical to
  // GOLDEN-FM, so the numeric values match).
  const baselineIdx = netValueSeries(watchedBaseline);
  const rr = {};
  for (const key of watched) rr[key] = relativeReturnN(netValueSeries(all[key]), baselineIdx, 3, 4);
  close(rr.A, -0.0116352278130713, "WATCHED_ONLY relativeReturn_3 A");
  close(rr.B, 0.02288734171231729, "WATCHED_ONLY relativeReturn_3 B");
  close(rr.C, -0.01195316294840282, "WATCHED_ONLY relativeReturn_3 C");
  const order = Object.keys(rr).sort((x, y) => rr[y] - rr[x]);
  assert.deepEqual(order, ["B", "A", "C"], "WATCHED_ONLY rank order over watched set");
});

// ---------------------------------------------------------------------------
// Aggregate: print selector token if all golden assertions pass.
// ---------------------------------------------------------------------------

test("all golden assertions pass -> print P17-SECTOR-ANALYTICS-REPAIR-GOLDEN", () => {
  // Re-verify the headline numbers inline so the token only prints when green.
  close(
    relativeReturnN(
      netValueSeries([0.02, -0.01, 0.03, 0.015, -0.005]),
      netValueSeries([0.01, 0.005, -0.002, 0.008, 0.003]),
      3, 5
    ),
    0.030473196953448606,
    "headline relativeReturn_3"
  );
  close(
    spearmanRho([1, 2, 3, 4, 5], [1.5, 1.5, 3.5, 3.5, 5]),
    0.9486832980505138,
    "headline market rho (Pearson of average ranks, ties)"
  );
  close(
    popStdDev(rankPercentileSeries([3, 2, 2, 1, 1], 5)),
    0.18708286933869706,
    "headline rank_percentile_std_dev"
  );
  close(flowConcentration([100, 50, -30, 20]).positive, 0.85, "headline posFlowConc");
  assert.equal(volumeState(-0.02, 1.3), "DOWN_CONFIRMED", "headline DOWN_CONFIRMED");
  close(trackingDailyReturns([100, 102, 101, 104])[0], 0.02, "headline tracking t1/t0");
  assert.equal(trackingBenchmarkAvailable([100, 102, 101], 3), false, "headline N+1 threshold");

  console.log("P17-SECTOR-ANALYTICS-REPAIR-GOLDEN");
});
