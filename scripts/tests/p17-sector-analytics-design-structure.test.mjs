// Static structure / cross-reference / pollution-probe check for
// P17-SECTOR-ANALYTICS-DESIGN-REPAIR-20260803 (SLICE-01, AC-R1..R4).
//
// Read-only: node:test + node:assert/strict + fs/promises. No shell, no network.
// Mirrors the style of scripts/tests/ai-governance.test.mjs.
//
// Implements the structure-test spec from the frozen TEST-DESIGN.md:
//   S-RS-01/02, S-ROT-01/02/03, S-CC-01/02, S-VOL-01/02, S-LIN-01/02,
//   S-API-01/02, S-DB-01, S-PLAN-01/02, plus the pre-existing
//   append-only / baseline-anchor / no-writeback / V19+ / subtask-structure
//   assertions (carried forward from the original test).
//
// On success prints exactly this selector token to stdout:
//   P17-SECTOR-ANALYTICS-REPAIR-STRUCT
// On any failure exits non-zero with a diagnostic naming the failing assertion
// and the offending file (and line where applicable).

import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const REPO = process.cwd();

const FILES = {
  design: "docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md",
  api: "docs/api/MARKET_DATA_API.md",
  db: "docs/DATABASE_DESIGN.md",
  plan: "docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md"
};

async function readRel(rel) {
  const text = await readFile(`${REPO}/${rel}`, "utf8");
  return text;
}

// Split a markdown document into sections by ## / ### headings. Each section
// is { heading, title, body, startLine }.
function splitSections(doc) {
  const lines = doc.split(/\r?\n/);
  const sections = [];
  let current = { heading: null, title: "", body: [], startLine: 1 };
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const match = /^(#{2,3})\s+(.*)$/.exec(line);
    if (match) {
      if (current.heading !== null || current.body.length > 0) {
        sections.push({ ...current, body: current.body.join("\n") });
      }
      current = { heading: match[1], title: match[2], body: [], startLine: index + 1 };
    } else {
      current.body.push(line);
    }
  }
  sections.push({ ...current, body: current.body.join("\n") });
  return sections;
}

// Extract the body of a top-level `## N.` section from a markdown doc (from the
// `## N.` heading up to the next `## ` heading at the same level, or EOF).
function topLevelSection(doc, sectionPrefix) {
  const lines = doc.split(/\r?\n/);
  const start = lines.findIndex((l) => new RegExp(`^${sectionPrefix}`).test(l));
  if (start === -1) return null;
  let end = lines.length;
  for (let i = start + 1; i < lines.length; i += 1) {
    if (/^##\s/.test(lines[i])) { end = i; break; }
  }
  return lines.slice(start, end).join("\n");
}

const FORMULA_NAMES = ["相对强弱", "轮动持续性", "收益贡献", "交易集中度", "量价确认", "异动提醒"];
const FIVE_ELEMENTS = ["输入", "窗口", "基准", "样本", "失效"];

// ---------------------------------------------------------------------------
// Pre-existing structural assertions (carried forward, now against repaired docs)
// ---------------------------------------------------------------------------

test("BASE-01 design doc exists and is non-empty", async () => {
  const doc = await readRel(FILES.design);
  assert.ok(doc.trim().length > 0, `${FILES.design} is empty`);
});

test("BASE-02 at least 5 sections whose heading matches a formula name", async () => {
  const doc = await readRel(FILES.design);
  const sections = splitSections(doc);
  const matched = sections.filter((section) =>
    FORMULA_NAMES.some((name) => new RegExp(name).test(section.title)));
  assert.ok(matched.length >= 5,
    `expected >=5 formula sections, found ${matched.length}: ${matched.map((m) => m.title).join(" | ")}`);
});

test("BASE-03 each of the first 5 formula sections contains all five elements", async () => {
  const doc = await readRel(FILES.design);
  const sections = splitSections(doc);
  const matched = sections.filter((section) =>
    FORMULA_NAMES.some((name) => new RegExp(name).test(section.title)));
  assert.ok(matched.length >= 5, "need >=5 formula sections first");
  for (const section of matched.slice(0, 5)) {
    for (const element of FIVE_ELEMENTS) {
      assert.ok(section.body.includes(element),
        `formula section "${section.title}" (line ${section.startLine}) missing element "${element}"`);
    }
  }
});

test("BASE-04 design doc has three-layer tokens + no-write-back declaration", async () => {
  const doc = await readRel(FILES.design);
  for (const token of ["原始事实", "衍生指标", "提醒事件"]) {
    assert.ok(doc.includes(token), `${FILES.design} missing token "${token}"`);
  }
  assert.match(doc, /禁止写回|不得写回|严禁写回|不可写回|不写回|只读.{0,12}原始事实/,
    `${FILES.design} missing no-write-back declaration`);
});

test("BASE-05 API doc has a `## 5.` section mentioning 板块分析 and 规划|未实现", async () => {
  const doc = await readRel(FILES.api);
  const sections = doc.split(/\r?\n/);
  let found = false;
  for (let index = 0; index < sections.length; index += 1) {
    if (/^## 5\./.test(sections[index])) {
      const head = sections.slice(index, index + 6).join("\n");
      if (/板块分析/.test(head) && /(规划|未实现)/.test(head)) {
        found = true;
        break;
      }
    }
  }
  assert.ok(found, `${FILES.api} missing \`## 5.\` section with 板块分析 + 规划|未实现`);
});

test("BASE-06 API baseline anchors (## 1. / ## 2. / ## 3. / ## 4.) preserved", async () => {
  const doc = await readRel(FILES.api);
  const anchors = [
    "## 1. 当前已实现接口",
    "## 2. LongPort 只读行情接口（真实外联已验收）",
    "## 3. 行情工作台、采集计划、分钟 K、水位（P1.2）",
    "## 4. 安全约束"
  ];
  for (const anchor of anchors) {
    assert.ok(doc.includes(anchor), `${FILES.api} missing baseline anchor "${anchor}"`);
  }
});

test("BASE-07 DB has a 板块分析 + 规划 + V19 block AND baseline anchors preserved", async () => {
  const doc = await readRel(FILES.db);
  assert.match(doc, /板块分析[\s\S]*?规划[\s\S]*?V19|规划[\s\S]*?板块分析[\s\S]*?V19/,
    `${FILES.db} missing 板块分析 + 规划 + V19 block`);
  const anchors = [
    "### market_sector_watch / market_sector_snapshot / market_sector_member_snapshot",
    "状态：已实现（V14，V15 扩展）",
    "### market_sector_ranking_batch / market_sector_ranking_item",
    "### security_directory_sync_state",
    "状态：已实现（V18 migration）"
  ];
  for (const anchor of anchors) {
    assert.ok(doc.includes(anchor), `${FILES.db} missing baseline anchor "${anchor}"`);
  }
});

test("BASE-08 new `## 5.` API section and new DB planned block do NOT contain 已实现", async () => {
  const apiDoc = await readRel(FILES.api);
  const apiSection = topLevelSection(apiDoc, "## 5\\.");
  assert.ok(apiSection, `${FILES.api} has no \`## 5.\` section`);
  assert.ok(!apiSection.includes("已实现"),
    `${FILES.api} \`## 5.\` section must NOT contain 已实现`);

  const dbDoc = await readRel(FILES.db);
  const plannedStart = dbDoc.indexOf("## 板块分析规划表");
  assert.notEqual(plannedStart, -1, `${FILES.db} has no planned 板块分析 block`);
  const plannedSection = dbDoc.slice(plannedStart);
  assert.ok(!plannedSection.includes("已实现"),
    `${FILES.db} planned 板块分析 block must NOT contain 已实现`);
});

// ---------------------------------------------------------------------------
// No-write-back pollution probe (carried forward)
// ---------------------------------------------------------------------------

const WRITEBACK_VERBS = [
  /UPDATE\s+(market_sector|stock_|market_sector_ranking)/i,
  /(写回|回写|覆盖)[\s\S]{0,40}?(market_sector|stock_|ranking)/,
  /(market_sector|stock_|ranking)[\s\S]{0,40}?(写回|回写|覆盖)/
];
const NEGATION = /(禁止|不得|严禁|不可|勿|不|未|prohibit|never|read-only|只读)/;

function findPollution(text, source) {
  const lines = text.split(/\r?\n/);
  const hits = [];
  lines.forEach((line, index) => {
    if (!line) return;
    const matchesVerb = WRITEBACK_VERBS.some((pattern) => pattern.test(line));
    if (matchesVerb && !NEGATION.test(line)) {
      hits.push({ source, line: index + 1, text: line.trim() });
    }
  });
  return hits;
}

test("BASE-09 no write-back-to-original-fact violations across all four artifacts", async () => {
  const all = [];
  for (const [name, rel] of Object.entries(FILES)) {
    const doc = await readRel(rel);
    all.push(...findPollution(doc, rel));
  }
  assert.equal(all.length, 0,
    `write-back violations found (must be 0):\n${all.map((h) => `  ${h.source}:${h.line} ${h.text}`).join("\n")}`);
});

test("BASE-10 each new analysis table block references V19+ and not 已实现（V1[0-8]", async () => {
  const dbDoc = await readRel(FILES.db);
  const plannedStart = dbDoc.indexOf("## 板块分析规划表");
  assert.notEqual(plannedStart, -1, `${FILES.db} has no planned block`);
  const planned = dbDoc.slice(plannedStart);
  assert.match(planned, /V19\+|V19|V2[0-9]|V3[0-9]/, "planned block must reference V19+");
  assert.doesNotMatch(planned, /已实现（V1[0-8]/, "planned block must not contain 已实现（V1[0-8]");

  const designDoc = await readRel(FILES.design);
  assert.match(designDoc, /V19\+|V19|V2[0-9]|V3[0-9]/, "design doc must reference V19+");
});

test("BASE-11 design or DB planned block contains read-only-original-fact declaration", async () => {
  const designDoc = await readRel(FILES.design);
  const dbDoc = await readRel(FILES.db);
  const combined = `${designDoc}\n${dbDoc}`;
  assert.match(combined, /衍生.{0,12}只读|只读.{0,12}(原始事实|market_sector|stock_)|读.{0,8}原始事实/,
    "missing read-only-original-fact declaration");
});

// ---------------------------------------------------------------------------
// Implementation plan structure (carried forward + tightened)
// ---------------------------------------------------------------------------

function sliceSubtaskBlocks(doc) {
  const lines = doc.split(/\r?\n/);
  const blocks = [];
  let current = null;
  for (const line of lines) {
    if (/^###\s+(子任务|ST-|TASK-)/.test(line)) {
      if (current) blocks.push(current);
      current = { heading: line, body: [] };
    } else if (current) {
      if (/^(#{2,3})\s/.test(line) && !/^###\s+(子任务|ST-|TASK-)/.test(line)) {
        blocks.push(current);
        current = null;
      } else {
        current.body.push(line);
      }
    }
  }
  if (current) blocks.push(current);
  return blocks.filter((b) => /^###\s+(子任务|ST-|TASK-)/.test(b.heading));
}

function collectWritePaths(blockBody) {
  const paths = [];
  for (const bodyLine of blockBody) {
    const pathRegex = /(^|\s|`|；|;|，|,|\()((src|docs|scripts)\/[^\s`，,；;）)]+)/g;
    let pathMatch;
    while ((pathMatch = pathRegex.exec(bodyLine)) !== null) {
      paths.push(pathMatch[2]);
    }
  }
  return paths;
}

test("BASE-12 implementation plan exists, >=4 sub-tasks, each has 写路径/依赖/AC/测试/合并顺序", async () => {
  const doc = await readRel(FILES.plan);
  assert.ok(doc.trim().length > 0, `${FILES.plan} is empty`);
  const subtaskBlocks = sliceSubtaskBlocks(doc);
  assert.ok(subtaskBlocks.length >= 4, `expected >=4 sub-task blocks, found ${subtaskBlocks.length}`);
  for (const block of subtaskBlocks) {
    const body = block.body.join("\n");
    for (const label of ["写路径", "依赖", "AC", "测试", "合并顺序"]) {
      assert.ok(body.includes(label),
        `sub-task "${block.heading}" missing field label "${label}"`);
    }
  }
});

test("BASE-13 sub-task write-paths do not pairwise prefix-overlap", async () => {
  const doc = await readRel(FILES.plan);
  const subtaskBlocks = sliceSubtaskBlocks(doc);
  const perTask = subtaskBlocks.map((block) => ({
    heading: block.heading,
    paths: collectWritePaths(block.body)
  }));
  for (let i = 0; i < perTask.length; i += 1) {
    for (let j = i + 1; j < perTask.length; j += 1) {
      for (const a of perTask[i].paths) {
        for (const b of perTask[j].paths) {
          const overlap = a === b || a.startsWith(`${b}/`) || b.startsWith(`${a}/`) ||
            a.startsWith(b) || b.startsWith(a);
          assert.equal(overlap, false,
            `write-path overlap between "${perTask[i].heading}" and "${perTask[j].heading}": "${a}" vs "${b}"`);
        }
      }
    }
  }
});

test("BASE-14 plan has a heading/section containing BOTH 并行 and 串行 (or DAG)", async () => {
  const doc = await readRel(FILES.plan);
  const sections = splitSections(doc);
  const ok = sections.some((section) =>
    /并行/.test(section.title) && /(串行|DAG)/.test(section.title));
  assert.ok(ok, "missing a heading containing both 并行 and 串行|DAG");
});

// ---------------------------------------------------------------------------
// AC-R1: Relative Strength (S-RS-01 / S-RS-02)
// ---------------------------------------------------------------------------

test("S-RS-01 relative-strength section has no Mansfield / P_sector / P_baseline / literal `...`", async () => {
  const doc = await readRel(FILES.design);
  const sections = splitSections(doc);
  const rsSections = sections.filter((s) => /相对强弱/.test(s.title));
  assert.ok(rsSections.length >= 1, "no 相对强弱 section found");
  const body = rsSections.map((s) => s.body).join("\n");
  for (const forbidden of ["Mansfield", "P_sector", "P_baseline"]) {
    assert.ok(!body.includes(forbidden),
      `relative-strength section must not contain "${forbidden}"`);
  }
  assert.ok(!/\.\.\./.test(body),
    "relative-strength section must not contain a literal `...` placeholder");
});

test("S-RS-02 relative-strength section contains closed-form relativeReturn + index recurrence", async () => {
  const doc = await readRel(FILES.design);
  const sections = splitSections(doc);
  const rsSections = sections.filter((s) => /相对强弱/.test(s.title));
  const body = rsSections.map((s) => s.body).join("\n");
  assert.ok(/relativeReturn/.test(body) || /相对收益/.test(body),
    "relative-strength section must mention relativeReturn / 相对收益");
  assert.ok(/index\(t\)\s*=\s*index\(t-1\)\s*\*\s*\(1\s*\+\s*sectorReturn\(t\)\)/.test(body) ||
    /index\(t-1\)\s*\*\s*\(1\s*\+/.test(body),
    "relative-strength section must contain the index recurrence index(t)=index(t-1)*(1+sectorReturn(t))");
});

test("S-RS-03 FULL_MARKET rank scope uses N-day relativeReturn rebuilt from full-market history, not single-day change_rate", async () => {
  const doc = await readRel(FILES.design);
  const sections = splitSections(doc);
  const rsSections = sections.filter((s) => /相对强弱/.test(s.title));
  const body = rsSections.map((s) => s.body).join("\n");
  // FULL_MARKET must read consecutive N trading days of full-market CLOSE
  // history and rebuild each sector's synthetic net value before ranking.
  assert.ok(/FULL_MARKET/.test(body), "design must define FULL_MARKET rank scope");
  assert.ok(/连续 N 个交易日/.test(body) && /market_sector_ranking_item/.test(body) &&
    /重建/.test(body) && /relativeReturn_N/.test(body),
    "FULL_MARKET must read N consecutive trading days of full-market CLOSE history and rebuild net values");
  // The design must forbid substituting a single-day change_rate for the N-day
  // relativeReturn_N in the rank set (scope mismatch).
  assert.ok(/禁止用单日/.test(body) || /单日.{0,20}relativeReturn_N|不得.{0,20}change_rate.{0,20}相对收益/.test(body),
    "design must forbid single-day change_rate as a substitute for N-day relativeReturn_N");
  // Degradation paths must be explicit.
  assert.ok(/WATCHED_ONLY/.test(body) && /INSUFFICIENT_SAMPLE/.test(body),
    "design must define WATCHED_ONLY / INSUFFICIENT_SAMPLE degradation when full-market history is insufficient");
});

test("S-RS-04 missing trading days freeze to a single rule: any CLOSE gap in the N-day window -> INSUFFICIENT_SAMPLE, no forward-fill / no skip", async () => {
  const doc = await readRel(FILES.design);
  const sections = splitSections(doc);
  const rsSections = sections.filter((s) => /相对强弱/.test(s.title));
  const body = rsSections.map((s) => s.body).join("\n");
  // The N-day window is taken over the market trading calendar (asOfDate + prior
  // N-1 trading days); any required CLOSE missing -> whole row INSUFFICIENT_SAMPLE.
  assert.ok(/asOfDate/.test(body) && /前\s*N-1\s*个交易日/.test(body),
    "design must define the window as asOfDate plus the prior N-1 trading days");
  assert.ok(/任一应有 CLOSE 缺失|任何.*CLOSE.*缺失|缺失.*不满足门槛/.test(body),
    "design must state that any missing CLOSE in the window fails the threshold");
  assert.ok(/不前向填补/.test(body) && /INSUFFICIENT_SAMPLE/.test(body),
    "design must forbid forward-fill and mark the row INSUFFICIENT_SAMPLE");
  // The contradictory skip-the-gap interpretation must not survive.
  assert.ok(!/跳过该缺口|跳过缺口后继续|不跳过缺口后继续声称连续有效/.test(body) ||
    /不跳过缺口|不采用.*最近 N 个有效交易日/.test(body),
    "design must not leave the skip-the-gap interpretation as a valid alternative");
  // Quality status must be named explicitly.
  assert.ok(/quality_status\s*=\s*INSUFFICIENT_SAMPLE/.test(body),
    "design must map the gap to quality_status=INSUFFICIENT_SAMPLE");
});

test("S-RS-05 FULL_MARKET data source = ranking_batch/ranking_item CLOSE history; WATCHED_ONLY = market_sector_snapshot; tracking = stock_daily_bar (design §6.2 + DB)", async () => {
  const design = await readRel(FILES.design);
  const db = await readRel(FILES.db);
  // FULL_MARKET source: consecutive N trading days of full-market CLOSE ranking history.
  for (const src of [design, db]) {
    assert.ok(/market_sector_ranking_batch/.test(src),
      "design/db must name market_sector_ranking_batch as the FULL_MARKET source");
    assert.ok(/market_sector_ranking_item/.test(src),
      "design/db must name market_sector_ranking_item as the FULL_MARKET source");
    assert.ok(/snapshot_type\s*=\s*'CLOSE'|snapshot_type\s*=\s*"CLOSE"/.test(src),
      "design/db must state the ranking history read uses snapshot_type='CLOSE'");
  }
  assert.ok(/连续 N 个交易日/.test(design),
    "design must state FULL_MARKET reads consecutive N trading days of full-market history");
  // WATCHED_ONLY source: market_sector_snapshot CLOSE snapshots.
  assert.match(design,
    /WATCHED_ONLY[\s\S]{0,200}?market_sector_snapshot|market_sector_snapshot[\s\S]{0,120}?WATCHED_ONLY/,
    "design must state WATCHED_ONLY reads market_sector_snapshot");
  assert.ok(/trigger_type\s*=\s*'CLOSE'|trigger_type\s*=\s*"CLOSE"/.test(design),
    "design must state the WATCHED_ONLY snapshot read uses trigger_type='CLOSE'");
  // Tracking-symbol benchmark source: stock_daily_bar.close_price.
  assert.ok(/stock_daily_bar[\s\S]{0,40}?close_price/.test(design),
    "design must state the tracking-symbol benchmark reads stock_daily_bar.close_price");
});

test("S-RS-06 tracking-symbol benchmark requires N+1 consecutive close_price (N daily returns need N+1 closes)", async () => {
  const design = await readRel(FILES.design);
  const api = await readRel(FILES.api);
  for (const src of [design, api]) {
    assert.ok(/N\+1\s*`?\s*个.{0,24}close_price|N\+1\s*`?\s*个.{0,16}收盘价/.test(src),
      "tracking-symbol benchmark must require N+1 consecutive close_price to build N daily returns");
    assert.ok(/BENCHMARK_TRACKING_SYMBOL_INSUFFICIENT/.test(src),
      "tracking-symbol insufficient closes must degrade to the same-scope equal-weight benchmark with BENCHMARK_TRACKING_SYMBOL_INSUFFICIENT");
  }
  // The degradation must stay same-scope with the rank set.
  assert.ok(/与 rank set 同范围|同范围的等权基准|回退到.{0,12}等权基准/.test(design),
    "design must state the insufficient-closes fallback is a same-scope equal-weight benchmark");
});

test("S-RS-07 WATCHED_ONLY equal-weight benchmark is same-scope as the rank set, never full-market", async () => {
  const design = await readRel(FILES.design);
  const api = await readRel(FILES.api);
  const db = await readRel(FILES.db);
  for (const src of [design, api, db]) {
    assert.ok(/SECTOR_EQUAL_WEIGHT/.test(src) || /等权基准/.test(src),
      "each artifact must mention the equal-weight benchmark (SECTOR_EQUAL_WEIGHT / 等权基准)");
    assert.ok(/同源|同范围/.test(src),
      "each artifact must tie the equal-weight benchmark to the rank-set scope (同源/同范围)");
  }
  // FULL_MARKET -> full-market equal weight.
  assert.ok(/FULL_MARKET[\s\S]{0,120}?(全市场等权|全部有效板块)/.test(design),
    "design must state FULL_MARKET uses full-market equal weight");
  // WATCHED_ONLY degradation -> watched-set equal weight, and must NOT claim full-market.
  assert.ok(/被关注集合.{0,24}等权|被 watch 板块.{0,24}等权|被关注集合（被 watch 板块）的等权/.test(design),
    "design must state WATCHED_ONLY degrades to the watched-set equal weight");
  assert.ok(/不得.{0,20}全市场等权|不.{0,8}声称.{0,12}全市场等权/.test(design),
    "design must forbid claiming full-market equal weight when full-market history is missing");
  assert.ok(/RANK_SCOPE_WATCHED_ONLY/.test(design) && /RANK_SCOPE_WATCHED_ONLY/.test(api),
    "design/API must name RANK_SCOPE_WATCHED_ONLY as the WATCHED_ONLY degradation reason");
});

// ---------------------------------------------------------------------------
// AC-R2: Rotation persistence split (S-ROT-01 / S-ROT-02 / S-ROT-03)
// ---------------------------------------------------------------------------

test("S-ROT-01 design has market-level + sector-level rotation tables", async () => {
  const doc = await readRel(FILES.design);
  assert.ok(/sector_rotation_market_stability/.test(doc),
    "design must define market-level table sector_rotation_market_stability");
  assert.ok(/sector_rotation_sector_persistence/.test(doc),
    "design must define sector-level table sector_rotation_sector_persistence");
});

test("S-ROT-02 market-level Spearman is NOT described as stored per-sector", async () => {
  const doc = await readRel(FILES.design);
  const sections = splitSections(doc);
  const marketSections = sections.filter((s) =>
    /sector_rotation_market_stability/.test(s.body) || /市场级.*Spearman|市场级.*轮动/.test(s.title));
  const body = marketSections.map((s) => `${s.title}\n${s.body}`).join("\n---\n");
  // The market-level table must explicitly state it is keyed without sector_identity
  // and must NOT be described as "每个板块/每个 sector 记录存储".
  assert.ok(/不.*sector_identity|不含\s*sector_identity|不存\s*sector_identity|不按\s*sector_identity|不归属.*板块|不重复存/.test(body),
    "market-level Spearman must be described as NOT stored per-sector / without sector_identity");
  assert.ok(!/每个板块.*存储.*Spearman|每个\s*sector.*记录.*Spearman/.test(body),
    "market-level Spearman must not be described as stored per-sector record");
});

test("S-ROT-03 design defines Spearman via Pearson of average ranks and gates 1-6Σd² to no-ties only", async () => {
  const doc = await readRel(FILES.design);
  assert.ok(/Pearson|皮尔逊|相关系数/.test(doc),
    "design must define Spearman as the Pearson correlation of the average-rank vectors (ties)");
  assert.ok(/Spearman/.test(doc), "design must mention Spearman");
  // If the no-ties simplification 1-6Σd²/(n(n²-1)) is present, it must be
  // explicitly gated to the no-ties case and never offered as the general formula.
  if (/ρ\s*=\s*1\s*[-−]\s*6/.test(doc)) {
    assert.ok(/无并列|不适用于并列|并列下不成立|仅当.{0,24}无并列/.test(doc),
      "design must gate the 1-6Σd²/(n(n²-1)) formula to the no-ties case only");
  }
});

// ---------------------------------------------------------------------------
// AC-R3: Contribution vs concentration split (S-CC-01 / S-CC-02)
// ---------------------------------------------------------------------------

test("S-CC-01 design has memberContribution AND top_k_turnover_share as distinct concepts", async () => {
  const doc = await readRel(FILES.design);
  assert.ok(/memberContribution/.test(doc) || /member_return_contribution/.test(doc),
    "design must mention memberContribution / member_return_contribution");
  assert.ok(/top_k_turnover_share/.test(doc) || /turnover_concentration/.test(doc),
    "design must mention top_k_turnover_share / turnover_concentration");
});

test("S-CC-02 net-inflow concentration handles zero denominator (INSUFFICIENT / no division)", async () => {
  const doc = await readRel(FILES.design);
  assert.ok(/INSUFFICIENT/.test(doc) && /不除零|不除以零|absSum|绝对流量/.test(doc),
    "design must state zero-denominator handling (INSUFFICIENT / 不除零 / absSum)");
});

// ---------------------------------------------------------------------------
// AC-R4: Six-state volume confirmation (S-VOL-01 / S-VOL-02)
// ---------------------------------------------------------------------------

test("S-VOL-01 design contains all six volume states", async () => {
  const doc = await readRel(FILES.design);
  for (const state of ["UP_CONFIRMED", "UP_UNCONFIRMED", "DOWN_CONFIRMED",
    "DOWN_UNCONFIRMED", "NEUTRAL", "INSUFFICIENT"]) {
    assert.ok(doc.includes(state), `design must contain volume state "${state}"`);
  }
});

test("S-VOL-02 volume section does NOT use CONFIRMED/DIVERGENCE as the only two states", async () => {
  const doc = await readRel(FILES.design);
  const sections = splitSections(doc);
  const volSections = sections.filter((s) => /量价确认/.test(s.title));
  assert.ok(volSections.length >= 1, "no 量价确认 section found");
  const body = volSections.map((s) => s.body).join("\n");
  // Must explicitly reject CONFIRMED/DIVERGENCE as the only two states.
  assert.ok(/不使用\s*CONFIRMED\s*\/\s*DIVERGENCE|不以.*CONFIRMED.*DIVERGENCE.*为唯|不是.*背离/.test(body),
    "volume section must explicitly state it does not use CONFIRMED/DIVERGENCE as the only states");
});

// ---------------------------------------------------------------------------
// AC-R4: Lineage / versioning (S-LIN-01 / S-LIN-02)
// ---------------------------------------------------------------------------

test("S-LIN-01 every planned derived table block contains formula_code/formula_version/parameter_hash", async () => {
  const doc = await readRel(FILES.design);
  // Restrict to the §6 region (data model) and identify the per-table subsections
  // whose TITLE contains a derived table name (these are the table definitions,
  // not the §4 three-layer overview that merely lists table names).
  const sixRegion = doc.slice(doc.indexOf("## 6."));
  const sections = splitSections(sixRegion);
  const tableNames = [
    "sector_relative_strength_snapshot",
    "sector_rotation_market_stability",
    "sector_rotation_sector_persistence",
    "sector_member_return_contribution",
    "sector_turnover_concentration",
    "sector_volume_confirmation_snapshot"
  ];
  const tableSections = sections.filter((s) =>
    tableNames.some((name) => s.title.includes(name)));
  assert.ok(tableSections.length >= tableNames.length,
    `expected >=${tableNames.length} derived-table definition sections in §6, found ${tableSections.length}`);
  for (const token of ["formula_code", "formula_version", "parameter_hash"]) {
    assert.ok(sixRegion.includes(token), `design §6 must contain lineage column "${token}"`);
  }
  for (const s of tableSections) {
    const hasDirect = ["formula_code", "formula_version", "parameter_hash"].every((t) => s.body.includes(t));
    const hasRef = /统一版本血缘列|见\s*§6\.1/.test(s.body);
    assert.ok(hasDirect || hasRef,
      `derived table section "${s.title}" must contain lineage columns or reference §6.1`);
  }
});

test("S-LIN-02 idempotency keys include formula_version", async () => {
  const doc = await readRel(FILES.design);
  // Every unique-key declaration in §6 must include formula_version.
  const sixRegion = doc.slice(doc.indexOf("## 6."));
  const ukLines = sixRegion.split(/\r?\n/).filter((l) => /unique\s+`?uk_/.test(l));
  assert.ok(ukLines.length >= 4, `expected >=4 unique-key lines in §6, found ${ukLines.length}`);
  for (const line of ukLines) {
    assert.ok(line.includes("formula_version"),
      `idempotency key must include formula_version: ${line.trim()}`);
  }
});

// ---------------------------------------------------------------------------
// AC-R4: API §5 semantics (S-API-01 / S-API-02)
// ---------------------------------------------------------------------------

test("S-API-01 API §5 does NOT contain MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED", async () => {
  const apiDoc = await readRel(FILES.api);
  const section = topLevelSection(apiDoc, "## 5\\.");
  assert.ok(section, `${FILES.api} has no \`## 5.\` section`);
  assert.ok(!section.includes("MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED"),
    `${FILES.api} §5 must NOT contain MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED`);
});

test("S-API-02 API §5 contains planned MARKET_SECTOR_ANALYTICS_ error codes", async () => {
  const apiDoc = await readRel(FILES.api);
  const section = topLevelSection(apiDoc, "## 5\\.");
  assert.ok(section, `${FILES.api} has no \`## 5.\` section`);
  assert.ok(/MARKET_SECTOR_ANALYTICS_[A-Z_]+/.test(section),
    `${FILES.api} §5 must contain a planned MARKET_SECTOR_ANALYTICS_* error code`);
  assert.ok(section.includes("MARKET_SECTOR_ANALYTICS_DATA_UNAVAILABLE"),
    `${FILES.api} §5 must mention MARKET_SECTOR_ANALYTICS_DATA_UNAVAILABLE`);
});

test("S-API-03 formula-version-not-found is HTTP 404, distinct from 400 VALIDATION_ERROR, with no deferral-to-ST-2 wording", async () => {
  const apiDoc = await readRel(FILES.api);
  const section = topLevelSection(apiDoc, "## 5\\.");
  assert.ok(section, `${FILES.api} has no \`## 5.\` section`);
  // Resource (formula version) missing -> HTTP 404 via the analytics-domain code.
  assert.ok(/MARKET_SECTOR_ANALYTICS_FORMULA_VERSION_NOT_FOUND[\s\S]{0,120}?HTTP 404/.test(section),
    "§5.6 must bind FORMULA_VERSION_NOT_FOUND to HTTP 404");
  // Invalid request parameters -> 400 VALIDATION_ERROR, kept distinct from 404.
  assert.ok(/VALIDATION_ERROR.{0,120}?HTTP 400/.test(section),
    "§5.6 must bind VALIDATION_ERROR to HTTP 400");
  // The HTTP semantics must be settled now, not deferred to ST-2 implementation.
  assert.ok(!/由 ST-2 落库时决定|最终枚举命名.{0,40}ST-2/.test(section),
    "§5.6 must NOT defer HTTP semantics for the formula-version error to ST-2");
});

// ---------------------------------------------------------------------------
// AC-R4: DB facts (S-DB-01)
// ---------------------------------------------------------------------------

test("S-DB-01 DATABASE_DESIGN.md header line states V1-V18 (not V1-V17)", async () => {
  const dbDoc = await readRel(FILES.db);
  const head = dbDoc.split(/\r?\n/).slice(0, 8).join("\n");
  assert.ok(/V1-V18/.test(head), `DB header must state V1-V18; got head:\n${head}`);
  assert.ok(!/V1-V17/.test(head), `DB header must NOT state V1-V17; got head:\n${head}`);
});

// ---------------------------------------------------------------------------
// AC-R4: Plan no-prefix-overlap + DAG (S-PLAN-01 / S-PLAN-02)
// ---------------------------------------------------------------------------

test("S-PLAN-01 ST-1 and ST-3 write-paths have no pairwise prefix overlap (sibling dirs)", async () => {
  const doc = await readRel(FILES.plan);
  const subtaskBlocks = sliceSubtaskBlocks(doc);
  const byHeading = (label) => subtaskBlocks.find((b) =>
    new RegExp(label).test(b.heading));
  const st1 = byHeading(/ST-1|子任务 1/);
  const st3 = byHeading(/ST-3|子任务 3/);
  assert.ok(st1, "ST-1 sub-task block not found");
  assert.ok(st3, "ST-3 sub-task block not found");
  const st1Paths = collectWritePaths(st1.body);
  const st3Paths = collectWritePaths(st3.body);
  assert.ok(st1Paths.length > 0, "ST-1 has no write paths");
  assert.ok(st3Paths.length > 0, "ST-3 has no write paths");
  for (const a of st1Paths) {
    for (const b of st3Paths) {
      const overlap = a === b || a.startsWith(`${b}/`) || b.startsWith(`${a}/`) ||
        a.startsWith(b) || b.startsWith(a);
      assert.equal(overlap, false,
        `ST-1 vs ST-3 prefix overlap: "${a}" vs "${b}"`);
    }
  }
  // Additionally assert ST-1 owns analysis/derived and analysis/model, ST-3 owns
  // analysis/alert — sibling directories, none a prefix of another.
  const st1Joined = st1Paths.join(" ");
  const st3Joined = st3Paths.join(" ");
  assert.ok(/analysis\/derived/.test(st1Joined), "ST-1 must own analysis/derived");
  assert.ok(/analysis\/alert/.test(st3Joined), "ST-3 must own analysis/alert");
});

test("S-PLAN-02 plan has a parallel/serial DAG section", async () => {
  const doc = await readRel(FILES.plan);
  const sections = splitSections(doc);
  const ok = sections.some((section) =>
    /并行/.test(section.title) && /(串行|DAG)/.test(section.title));
  assert.ok(ok, "missing a heading containing both 并行 and 串行|DAG");
});

// ---------------------------------------------------------------------------
// Aggregate: print selector token if all assertions pass.
// ---------------------------------------------------------------------------

test("all structure assertions pass -> print P17-SECTOR-ANALYTICS-REPAIR-STRUCT", async () => {
  // Re-verify a representative subset inline so the token only prints when green.
  const design = await readRel(FILES.design);
  const api = await readRel(FILES.api);
  const db = await readRel(FILES.db);
  const plan = await readRel(FILES.plan);

  // RS closed-form + forbidden tokens
  const rsBody = splitSections(design).filter((s) => /相对强弱/.test(s.title)).map((s) => s.body).join("\n");
  for (const forbidden of ["Mansfield", "P_sector", "P_baseline"]) {
    assert.ok(!rsBody.includes(forbidden));
  }
  assert.ok(/\.\.\./.test(rsBody) === false);
  assert.ok(/relativeReturn|相对收益/.test(rsBody));

  // Six states
  for (const state of ["UP_CONFIRMED", "UP_UNCONFIRMED", "DOWN_CONFIRMED",
    "DOWN_UNCONFIRMED", "NEUTRAL", "INSUFFICIENT"]) {
    assert.ok(design.includes(state));
  }

  // Rotation split + market-level not per-sector
  assert.ok(design.includes("sector_rotation_market_stability"));
  assert.ok(design.includes("sector_rotation_sector_persistence"));

  // Contribution vs concentration
  assert.ok(/memberContribution|member_return_contribution/.test(design));
  assert.ok(/top_k_turnover_share|turnover_concentration/.test(design));

  // Lineage columns + versioned idempotency
  const sixRegion = design.slice(design.indexOf("## 6."));
  for (const token of ["formula_code", "formula_version", "parameter_hash"]) {
    assert.ok(sixRegion.includes(token));
  }

  // API §5 no auth code + planned analytics codes
  const apiSection = topLevelSection(api, "## 5\\.");
  assert.ok(!apiSection.includes("MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED"));
  assert.ok(apiSection.includes("MARKET_SECTOR_ANALYTICS_DATA_UNAVAILABLE"));

  // DB header V1-V18
  const head = db.split(/\r?\n/).slice(0, 8).join("\n");
  assert.ok(/V1-V18/.test(head) && !/V1-V17/.test(head));

  // Plan no overlap + DAG
  const subtaskBlocks = sliceSubtaskBlocks(plan);
  assert.ok(subtaskBlocks.length >= 4);
  const perTask = subtaskBlocks.map((b) => ({ h: b.heading, p: collectWritePaths(b.body) }));
  for (let i = 0; i < perTask.length; i += 1) {
    for (let j = i + 1; j < perTask.length; j += 1) {
      for (const a of perTask[i].p) {
        for (const b of perTask[j].p) {
          assert.equal(a === b || a.startsWith(`${b}/`) || b.startsWith(`${a}/`) ||
            a.startsWith(b) || b.startsWith(a), false);
        }
      }
    }
  }

  console.log("P17-SECTOR-ANALYTICS-REPAIR-STRUCT");
});
