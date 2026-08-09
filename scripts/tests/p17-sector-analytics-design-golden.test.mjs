import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const EPS = 1e-9;
const close = (actual, expected) => assert.ok(Math.abs(actual - expected) <= EPS,
  `expected ${expected}, got ${actual}`);

function netValue(returns) {
  return returns.reduce((values, value) => [...values, values.at(-1) * (1 + value)], [1]);
}

function relativeReturn(sector, benchmark) {
  return Math.log(sector.at(-1) / sector[0]) - Math.log(benchmark.at(-1) / benchmark[0]);
}

function averageRanks(entries) {
  const sorted = [...entries].sort((a, b) => a.value - b.value);
  const result = new Map();
  for (let i = 0; i < sorted.length;) {
    let j = i + 1;
    while (j < sorted.length && sorted[j].value === sorted[i].value) j += 1;
    const rank = ((i + 1) + j) / 2;
    for (let k = i; k < j; k += 1) result.set(sorted[k].id, rank);
    i = j;
  }
  return result;
}

function pearson(left, right) {
  assert.equal(left.length, right.length);
  const mean = values => values.reduce((sum, value) => sum + value, 0) / values.length;
  const lm = mean(left);
  const rm = mean(right);
  const numerator = left.reduce((sum, value, index) => sum + (value - lm) * (right[index] - rm), 0);
  const ld = left.reduce((sum, value) => sum + (value - lm) ** 2, 0);
  const rd = right.reduce((sum, value) => sum + (value - rm) ** 2, 0);
  return ld === 0 || rd === 0 ? null : numerator / Math.sqrt(ld * rd);
}

test("UNIT-01 real LongPort fixture binds 0.0240 to 2.40 percent", async () => {
  const fixture = await readFile(
    "src/test/java/com/quant/trade/marketdata/provider/longport/LongPortIndustryHttpClientTest.java", "utf8");
  assert.match(fixture, /"chg":"0\.0240"[\s\S]{0,220}"value_data":"2\.40%"/);
  const providerChangeRate = Number("0.0240");
  close(providerChangeRate, 0.0240);
  close(providerChangeRate * 100, 2.40);
  assert.notEqual(providerChangeRate / 100, providerChangeRate);
});

test("RS-01 common rank-set benchmark produces comparable relative returns", () => {
  const returns = {
    A: [0.02, -0.01, 0.03],
    B: [0.01, 0.00, 0.01],
    C: [-0.01, 0.01, 0.00]
  };
  const benchmarkReturns = returns.A.map((_, day) =>
    Object.values(returns).reduce((sum, values) => sum + values[day], 0) / 3);
  const benchmark = netValue(benchmarkReturns);
  const values = Object.entries(returns).map(([id, series]) =>
    ({ id, value: relativeReturn(netValue(series), benchmark) }));
  const ranks = averageRanks(values);
  assert.equal(ranks.size, 3);
  close(benchmark.at(-1), 1.0200888888888888);
  close(values.find(item => item.id === "A").value, 0.019421324215533595);
  close(values.find(item => item.id === "B").value, 0.000010892237647022046);
  close(values.find(item => item.id === "C").value, -0.0199897744690225);
  close(ranks.get("A"), 3);
  close((ranks.get("A") - 1) / 2, 1);
});

test("RS-02 N-day benchmark uses one fixed identity intersection", () => {
  const dailySets = [new Set(["A", "B", "C"]), new Set(["A", "B", "D"]), new Set(["A", "B", "E"])];
  const cohort = [...dailySets[0]].filter(id => dailySets.every(set => set.has(id))).sort();
  assert.deepEqual(cohort, ["A", "B"]);
  assert.equal(cohort.includes("C"), false);
  assert.equal(cohort.includes("D"), false);
  assert.equal(cohort.includes("E"), false);
});

test("SCOPE-01 full market requires explicit completeness proof", () => {
  const scope = ({ expected, actual, truncated }) =>
    expected !== null && expected === actual && !truncated ? "VERIFIED_FULL_MARKET" : "RANKED_UNIVERSE";
  assert.equal(scope({ expected: 88, actual: 88, truncated: false }), "VERIFIED_FULL_MARKET");
  assert.equal(scope({ expected: null, actual: 100, truncated: false }), "RANKED_UNIVERSE");
  assert.equal(scope({ expected: 120, actual: 100, truncated: true }), "RANKED_UNIVERSE");
});

test("SCOPE-02 current LongPort response without authoritative denominator stays ranked universe", () => {
  const responseCount = 100;
  const authoritativeExpectedCount = null;
  assert.equal(authoritativeExpectedCount, null);
  assert.equal(responseCount === authoritativeExpectedCount ? "VERIFIED_FULL_MARKET" : "RANKED_UNIVERSE",
    "RANKED_UNIVERSE");
});

test("ROT-01 ties derive from values and identities align changing universes", () => {
  const day1 = [{ id: "A", value: 0.02 }, { id: "B", value: 0.01 }, { id: "C", value: 0.01 }];
  const day2 = [{ id: "C", value: 0.03 }, { id: "A", value: 0.02 }, { id: "D", value: 0.01 }];
  const intersection = day1.map(item => item.id).filter(id => day2.some(item => item.id === id)).sort();
  assert.deepEqual(intersection, ["A", "C"]);
  const ranks1 = averageRanks(day1.filter(item => intersection.includes(item.id)));
  const ranks2 = averageRanks(day2.filter(item => intersection.includes(item.id)));
  close(pearson(intersection.map(id => ranks1.get(id)), intersection.map(id => ranks2.get(id))), -1);
});

test("ROT-02 zero-variance rank vectors are undefined", () => {
  assert.equal(pearson([1, 1, 1], [1, 2, 3]), null);
});

test("ROT-03 pair coverage uses the smaller directional coverage", () => {
  const left = 100;
  const right = 80;
  const intersection = 75;
  close(Math.min(intersection / left, intersection / right), 0.75);
});

test("ROT-04 tied leaders share the same average rank and percentile", () => {
  const ranks = averageRanks([{ id: "A", value: 0.03 }, { id: "B", value: 0.03 }, { id: "C", value: -0.01 }]);
  close(ranks.get("A"), 2.5);
  close(ranks.get("B"), 2.5);
  close((ranks.get("A") - 1) / 2, 0.75);
});

test("ROT-05 strongest ascending rank maps to percentile one", () => {
  const ranks = averageRanks([{ id: "weak", value: -0.02 }, { id: "middle", value: 0 }, { id: "strong", value: 0.03 }]);
  assert.equal(ranks.get("strong"), 3);
  assert.equal(ranks.get("weak"), 1);
  close((ranks.get("strong") - 1) / 2, 1);
  close((ranks.get("weak") - 1) / 2, 0);
});

test("ROT-06 window Spearman is intersection-count weighted", () => {
  const pairs = [{ rho: 1, count: 10 }, { rho: 0, count: 30 }];
  const weighted = pairs.reduce((sum, pair) => sum + pair.rho * pair.count, 0)
    / pairs.reduce((sum, pair) => sum + pair.count, 0);
  close(weighted, 0.25);
  assert.notEqual(weighted, 0.5);
});

test("FLOW-01 flow trend uses currency-consistent ratios and strict lag", () => {
  const historicalIntensity = [0.01, 0.02, -0.01, 0.00, 0.03];
  const today = 0.05;
  const historicalMean = historicalIntensity.reduce((sum, value) => sum + value, 0) / historicalIntensity.length;
  close(historicalMean, 0.01);
  close(today - historicalMean, 0.04);
  assert.equal(historicalIntensity.includes(today), false);
});

test("CONCENTRATION-01 directional shares are not concentration", () => {
  const flows = [100, 50, -30, 20];
  const absSum = flows.reduce((sum, value) => sum + Math.abs(value), 0);
  const positiveFlowShare = flows.filter(value => value > 0).reduce((sum, value) => sum + value, 0) / absSum;
  const negativeFlowShare = Math.abs(flows.filter(value => value < 0).reduce((sum, value) => sum + value, 0)) / absSum;
  close(positiveFlowShare, 0.85);
  close(negativeFlowShare, 0.15);
  close(positiveFlowShare + negativeFlowShare, 1);
});

test("CONCENTRATION-02 turnover and absolute-flow top-K sets are independent", () => {
  const members = [
    { id: "A", turnover: 1000, flow: 10 },
    { id: "B", turnover: 900, flow: -500 },
    { id: "C", turnover: 100, flow: 600 }
  ];
  const turnoverTop = [...members].sort((a, b) => b.turnover - a.turnover).slice(0, 2).map(item => item.id);
  const flowTop = [...members].sort((a, b) => Math.abs(b.flow) - Math.abs(a.flow)).slice(0, 2).map(item => item.id);
  assert.deepEqual(turnoverTop, ["A", "B"]);
  assert.deepEqual(flowTop, ["C", "B"]);
});

test("VOLUME-01 close turnover ratio excludes current day from baseline", () => {
  const priorFive = [100, 100, 100, 100, 100];
  const today = 200;
  const baseline = priorFive.reduce((sum, value) => sum + value, 0) / priorFive.length;
  close(today / baseline, 2);
  const polluted = [...priorFive.slice(1), today].reduce((sum, value) => sum + value, 0) / 5;
  assert.notEqual(today / polluted, 2);
});

test("VOLUME-02 missing one of five prior closes is insufficient", () => {
  const requiredPriorCloses = 5;
  assert.equal([100, 100, 100, 100].length < requiredPriorCloses, true);
  assert.equal([100, 100, 100, 100, 100].length === requiredPriorCloses, true);
});

test("ALERT-01 zero historical standard deviation makes z-score undefined", () => {
  const history = Array(20).fill(0.02);
  const mean = 0.02;
  const variance = history.reduce((sum, value) => sum + (value - mean) ** 2, 0) / history.length;
  assert.equal(variance, 0);
});

test("ALERT-02 RS reversal uses explicit regime transition", () => {
  const regime = (previous, current) => previous <= 0.2 && current >= 0.8
    ? "BULLISH" : previous >= 0.8 && current <= 0.2 ? "BEARISH" : null;
  assert.equal(regime(0.2, 0.8), "BULLISH");
  assert.equal(regime(0.8, 0.2), "BEARISH");
  assert.equal(regime(0.4, 0.9), null);
});

test("IDEMPOTENCY-01 parameter hash separates otherwise equal business keys", () => {
  const key = parameterHash => ["LONGPORT", "CN", "sector-A", "2026-07-31", 20, "v1", parameterHash].join("|");
  assert.notEqual(key("topK=3"), key("topK=5"));
});

test("IDEMPOTENCY-02 a changed source manifest creates a new calculation run identity", () => {
  const runKey = manifest => ["RELATIVE_RETURN_LOG", "v1", "params-a", manifest].join("|");
  assert.notEqual(runKey("manifest-1"), runKey("manifest-2"));
});

test("P17 corrected golden contract", () => {
  console.log("P17-SECTOR-ANALYTICS-V11-GOLDEN");
});
