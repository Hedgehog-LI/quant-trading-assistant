// Static structure / cross-reference / pollution-probe check for
// P17-SECTOR-ANALYTICS-DESIGN-20260802 (SLICE-01, AC-01/AC-02/AC-03).
//
// Read-only: node:test + node:assert/strict + fs/promises. No shell, no network.
// Mirrors the style of scripts/tests/ai-governance.test.mjs.
//
// On success prints exactly these selector tokens to stdout (one per AC):
//   P17-SECTOR-ANALYTICS-AC01
//   P17-SECTOR-ANALYTICS-AC02
//   P17-SECTOR-ANALYTICS-AC03
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

const FORMULA_NAMES = ["相对强弱", "轮动持续性", "龙头贡献", "成交量确认|量价确认", "异动提醒"];
const FIVE_ELEMENTS = ["输入", "窗口", "基准", "样本", "失效"];

// ---------------------------------------------------------------------------
// TEST-01 (AC-01): main design doc + API §5 + DB planned block structure
// ---------------------------------------------------------------------------

test("A01-01 design doc exists and is non-empty", async () => {
  const doc = await readRel(FILES.design);
  assert.ok(doc.trim().length > 0, `${FILES.design} is empty`);
});

test("A01-02 at least 5 sections whose heading matches one of the five formula names", async () => {
  const doc = await readRel(FILES.design);
  const sections = splitSections(doc);
  const matched = sections.filter((section) =>
    FORMULA_NAMES.some((name) => new RegExp(name).test(section.title)));
  assert.ok(matched.length >= 5,
    `expected >=5 formula sections, found ${matched.length}: ${matched.map((m) => m.title).join(" | ")}`);
});

test("A01-03 each formula section body contains all five elements (输入/窗口/基准/样本/失效)", async () => {
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

test("A01-04 design doc has three-layer tokens + no-write-back declaration", async () => {
  const doc = await readRel(FILES.design);
  for (const token of ["原始事实", "衍生指标", "提醒事件"]) {
    assert.ok(doc.includes(token), `${FILES.design} missing token "${token}"`);
  }
  assert.match(doc, /禁止写回|不得写回|严禁写回|不可写回|不写回|只读.{0,12}原始事实/,
    `${FILES.design} missing no-write-back declaration`);
});

test("A01-05 API doc has a `## 5.` section mentioning 板块分析 and 规划|未实现", async () => {
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

test("A01-06 API baseline anchors (## 1. / ## 2. / ## 3. / ## 4.) preserved", async () => {
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

test("A01-07 DB has a new 板块分析 + 规划 + V19 block AND baseline anchors preserved", async () => {
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

test("A01-08 new `## 5.` API section and new DB planned block do NOT contain 已实现", async () => {
  const apiDoc = await readRel(FILES.api);
  const apiLines = apiDoc.split(/\r?\n/);
  let apiSectionStart = -1;
  for (let index = 0; index < apiLines.length; index += 1) {
    if (/^## 5\./.test(apiLines[index])) { apiSectionStart = index; break; }
  }
  assert.notEqual(apiSectionStart, -1, `${FILES.api} has no \`## 5.\` section`);
  const apiSection = apiLines.slice(apiSectionStart).join("\n");
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
// TEST-02 (AC-02): original facts are not polluted by analysis results
// ---------------------------------------------------------------------------

// Candidate write-back verb patterns. A line is a violation only if it matches
// a write-back verb + a raw-fact table pattern AND does NOT contain a negation
// marker (compliant declarations are allowed).
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

test("A02-01 no write-back-to-original-fact violations across all four artifacts", async () => {
  const all = [];
  for (const [name, rel] of Object.entries(FILES)) {
    const doc = await readRel(rel);
    all.push(...findPollution(doc, rel));
  }
  assert.equal(all.length, 0,
    `write-back violations found (must be 0):\n${all.map((h) => `  ${h.source}:${h.line} ${h.text}`).join("\n")}`);
});

test("A02-02 each new analysis table block references V19+ and not 已实现（V1[0-8]", async () => {
  const dbDoc = await readRel(FILES.db);
  const plannedStart = dbDoc.indexOf("## 板块分析规划表");
  assert.notEqual(plannedStart, -1, `${FILES.db} has no planned block`);
  const planned = dbDoc.slice(plannedStart);
  assert.match(planned, /V19\+|V19|V2[0-9]|V3[0-9]/, "planned block must reference V19+");
  assert.doesNotMatch(planned, /已实现（V1[0-8]/, "planned block must not contain 已实现（V1[0-8]");

  const designDoc = await readRel(FILES.design);
  assert.match(designDoc, /V19\+|V19|V2[0-9]|V3[0-9]/, "design doc must reference V19+");
});

test("A02-03 design or DB planned block contains read-only-original-fact declaration", async () => {
  const designDoc = await readRel(FILES.design);
  const dbDoc = await readRel(FILES.db);
  const combined = `${designDoc}\n${dbDoc}`;
  assert.match(combined, /衍生.{0,12}只读|只读.{0,12}(原始事实|market_sector|stock_)|读.{0,8}原始事实/,
    "missing read-only-original-fact declaration");
});

// ---------------------------------------------------------------------------
// TEST-03 (AC-03): implementation plan structure
// ---------------------------------------------------------------------------

test("A03-01 implementation plan exists and is non-empty", async () => {
  const doc = await readRel(FILES.plan);
  assert.ok(doc.trim().length > 0, `${FILES.plan} is empty`);
});

test("A03-02 plan has >=4 sub-task blocks headed `### 子任务 N` / `### ST-N` / `### TASK-N`", async () => {
  const doc = await readRel(FILES.plan);
  const matches = doc.split(/\r?\n/).filter((line) => /^###\s+(子任务|ST-|TASK-)/.test(line));
  assert.ok(matches.length >= 4, `expected >=4 sub-task blocks, found ${matches.length}`);
});

test("A03-03 each sub-task block contains 写路径/依赖/AC/测试/合并顺序", async () => {
  const doc = await readRel(FILES.plan);
  const lines = doc.split(/\r?\n/);
  // Slice into blocks starting at each sub-task heading until the next heading.
  const blocks = [];
  let current = null;
  for (const line of lines) {
    if (/^###\s+(子任务|ST-|TASK-)/.test(line)) {
      if (current) blocks.push(current);
      current = { heading: line, body: [] };
    } else if (current) {
      // stop a block at the next ## or ### heading that is NOT another sub-task
      if (/^(#{2,3})\s/.test(line) && !/^###\s+(子任务|ST-|TASK-)/.test(line)) {
        blocks.push(current);
        current = null;
      } else {
        current.body.push(line);
      }
    }
  }
  if (current) blocks.push(current);
  const subtaskBlocks = blocks.filter((b) => /^###\s+(子任务|ST-|TASK-)/.test(b.heading));
  assert.ok(subtaskBlocks.length >= 4, `expected >=4 sub-task blocks, found ${subtaskBlocks.length}`);
  for (const block of subtaskBlocks) {
    const body = block.body.join("\n");
    for (const label of ["写路径", "依赖", "AC", "测试", "合并顺序"]) {
      assert.ok(body.includes(label),
        `sub-task "${block.heading}" missing field label "${label}"`);
    }
  }
});

test("A03-04 sub-task write-paths do not pairwise prefix-overlap", async () => {
  const doc = await readRel(FILES.plan);
  const lines = doc.split(/\r?\n/);
  // Re-derive sub-task blocks (same logic as A03-03).
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
  const subtaskBlocks = blocks.filter((b) => /^###\s+(子任务|ST-|TASK-)/.test(b.heading));

  // Collect, per sub-task, the path tokens that look like src/.../docs/.../scripts/...
  const perTask = subtaskBlocks.map((block) => {
    const paths = [];
    for (const bodyLine of block.body) {
      // Only consider the write-path field area: match leading repo-relative paths.
      const pathRegex = /(^|\s|`|；|;|，|,|\()((src|docs|scripts)\/[^\s`，,；;）)]+)/g;
      let pathMatch;
      while ((pathMatch = pathRegex.exec(bodyLine)) !== null) {
        paths.push(pathMatch[2]);
      }
    }
    return { heading: block.heading, paths };
  });

  // Verify pairwise no-prefix-overlap across sub-tasks.
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

test("A03-05 plan has a heading/section containing BOTH 并行 and 串行 (or DAG)", async () => {
  const doc = await readRel(FILES.plan);
  const sections = splitSections(doc);
  const ok = sections.some((section) =>
    /并行/.test(section.title) && /(串行|DAG)/.test(section.title));
  assert.ok(ok, "missing a heading containing both 并行 and 串行|DAG");
});

// ---------------------------------------------------------------------------
// Aggregate: print selector tokens if all assertions for an AC pass.
// ---------------------------------------------------------------------------

test("AC-01 all assertions pass -> print P17-SECTOR-ANALYTICS-AC01", async () => {
  // Re-run AC-01 checks inline (idempotent) so the token only prints when green.
  const design = await readRel(FILES.design);
  const api = await readRel(FILES.api);
  const db = await readRel(FILES.db);

  assert.ok(design.trim().length > 0);
  const sections = splitSections(design);
  const matched = sections.filter((s) => FORMULA_NAMES.some((n) => new RegExp(n).test(s.title)));
  assert.ok(matched.length >= 5);
  for (const section of matched.slice(0, 5)) {
    for (const element of FIVE_ELEMENTS) {
      assert.ok(section.body.includes(element), `section "${section.title}" missing ${element}`);
    }
  }
  for (const token of ["原始事实", "衍生指标", "提醒事件"]) {
    assert.ok(design.includes(token), `missing token ${token}`);
  }
  assert.match(design, /禁止写回|不得写回|严禁写回|不可写回|不写回|只读.{0,12}原始事实/);

  const apiLines = api.split(/\r?\n/);
  let apiFive = false;
  for (let i = 0; i < apiLines.length; i += 1) {
    if (/^## 5\./.test(apiLines[i])) {
      const head = apiLines.slice(i, i + 6).join("\n");
      if (/板块分析/.test(head) && /(规划|未实现)/.test(head)) { apiFive = true; break; }
    }
  }
  assert.ok(apiFive);
  for (const anchor of ["## 1. 当前已实现接口",
    "## 2. LongPort 只读行情接口（真实外联已验收）",
    "## 3. 行情工作台、采集计划、分钟 K、水位（P1.2）",
    "## 4. 安全约束"]) {
    assert.ok(api.includes(anchor), `missing api anchor ${anchor}`);
  }
  assert.match(db, /板块分析[\s\S]*?规划[\s\S]*?V19|规划[\s\S]*?板块分析[\s\S]*?V19/);
  for (const anchor of [
    "### market_sector_watch / market_sector_snapshot / market_sector_member_snapshot",
    "状态：已实现（V14，V15 扩展）",
    "### market_sector_ranking_batch / market_sector_ranking_item",
    "### security_directory_sync_state",
    "状态：已实现（V18 migration）"]) {
    assert.ok(db.includes(anchor), `missing db anchor ${anchor}`);
  }

  // A01-08: planned regions must not contain 已实现
  const apiSectionStart = apiLines.findIndex((l) => /^## 5\./.test(l));
  assert.ok(!apiLines.slice(apiSectionStart).join("\n").includes("已实现"));
  const dbPlanned = db.slice(db.indexOf("## 板块分析规划表"));
  assert.ok(!dbPlanned.includes("已实现"));

  console.log("P17-SECTOR-ANALYTICS-AC01");
});

test("AC-02 all assertions pass -> print P17-SECTOR-ANALYTICS-AC02", async () => {
  let violations = 0;
  for (const rel of Object.values(FILES)) {
    const doc = await readRel(rel);
    violations += findPollution(doc, rel).length;
  }
  assert.equal(violations, 0, `write-back violations: ${violations}`);

  const db = await readRel(FILES.db);
  const dbPlanned = db.slice(db.indexOf("## 板块分析规划表"));
  assert.match(dbPlanned, /V19\+|V19|V2[0-9]|V3[0-9]/);
  assert.doesNotMatch(dbPlanned, /已实现（V1[0-8]/);
  const design = await readRel(FILES.design);
  assert.match(design, /V19\+|V19|V2[0-9]|V3[0-9]/);

  assert.match(`${design}\n${db}`,
    /衍生.{0,12}只读|只读.{0,12}(原始事实|market_sector|stock_)|读.{0,8}原始事实/);

  console.log("P17-SECTOR-ANALYTICS-AC02");
});

test("AC-03 all assertions pass -> print P17-SECTOR-ANALYTICS-AC03", async () => {
  const plan = await readRel(FILES.plan);
  assert.ok(plan.trim().length > 0);
  const lines = plan.split(/\r?\n/);
  const subHeadings = lines.filter((l) => /^###\s+(子任务|ST-|TASK-)/.test(l));
  assert.ok(subHeadings.length >= 4, `sub-tasks: ${subHeadings.length}`);

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
  const subtaskBlocks = blocks.filter((b) => /^###\s+(子任务|ST-|TASK-)/.test(b.heading));
  for (const block of subtaskBlocks) {
    const body = block.body.join("\n");
    for (const label of ["写路径", "依赖", "AC", "测试", "合并顺序"]) {
      assert.ok(body.includes(label), `"${block.heading}" missing ${label}`);
    }
  }

  // A03-04 re-check no pairwise prefix overlap
  const perTask = subtaskBlocks.map((block) => {
    const paths = [];
    for (const bodyLine of block.body) {
      const pathRegex = /(^|\s|`|；|;|，|,|\()((src|docs|scripts)\/[^\s`，,；;）)]+)/g;
      let pathMatch;
      while ((pathMatch = pathRegex.exec(bodyLine)) !== null) {
        paths.push(pathMatch[2]);
      }
    }
    return { heading: block.heading, paths };
  });
  for (let i = 0; i < perTask.length; i += 1) {
    for (let j = i + 1; j < perTask.length; j += 1) {
      for (const a of perTask[i].paths) {
        for (const b of perTask[j].paths) {
          const overlap = a === b || a.startsWith(`${b}/`) || b.startsWith(`${a}/`) ||
            a.startsWith(b) || b.startsWith(a);
          assert.equal(overlap, false, `overlap "${a}" vs "${b}"`);
        }
      }
    }
  }

  // A03-05 parallel/serial heading
  const sections = splitSections(plan);
  assert.ok(sections.some((s) => /并行/.test(s.title) && /(串行|DAG)/.test(s.title)),
    "missing 并行 + 串行|DAG heading");

  console.log("P17-SECTOR-ANALYTICS-AC03");
});
