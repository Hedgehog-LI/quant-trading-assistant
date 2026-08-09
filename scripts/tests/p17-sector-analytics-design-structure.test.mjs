import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const files = {
  design: "docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md",
  api: "docs/api/MARKET_DATA_API.md",
  db: "docs/DATABASE_DESIGN.md",
  plan: "docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md",
  mock: "docs/mock/MOCK_REMOTE_CONTRACT.md",
  apiIndex: "docs/api/API_INDEX.md"
};

const read = path => readFile(path, "utf8");

test("BASE-01 all authoritative artifacts are planned and read-only", async () => {
  for (const path of Object.values(files)) {
    const text = await read(path);
    assert.match(text, /规划|未实现/);
  }
  const design = await read(files.design);
  assert.match(design, /禁止写回/);
  assert.match(design, /不自动交易|不生成买卖指令/);
});

test("BASE-02 pre-P17 API and DB anchors remain intact", async () => {
  const api = await read(files.api);
  for (const heading of ["## 1.", "## 2.", "## 3.", "## 4.", "## 5."]) assert.match(api, new RegExp(heading.replace(".", "\\.")));
  assert.match(await read(files.db), /当前已发布 V1-V18/);
  const p17 = api.slice(api.indexOf("## 5. 板块分析接口设计"));
  assert.doesNotMatch(p17, /状态：[^\n]*已实现/);
});

test("UNIT-01 decimal-ratio contract is frozen across design and plan", async () => {
  const design = await read(files.design);
  const plan = await read(files.plan);
  for (const text of [design, plan]) {
    assert.match(text, /decimal ratio/);
    assert.match(text, /0\.0240/);
    assert.match(text, /2\.40%/);
  }
  assert.doesNotMatch(design, /sectorReturn\(t\)\s*=\s*change_rate\(t\)\s*\/\s*100/);
  assert.doesNotMatch(design, /memberReturn[^\n]*\/\s*100/);
});

test("SCOPE-01 full-market claims require completeness and ranked-universe fallback", async () => {
  for (const path of [files.design, files.api, files.db, files.plan]) {
    const text = await read(path);
    assert.match(text, /VERIFIED_FULL_MARKET/);
    assert.match(text, /RANKED_UNIVERSE/);
    assert.match(text, /is_truncated|isTruncated|截断|上限 100|无独立总数/);
  }
  for (const path of [files.design, files.api, files.plan]) {
    assert.match(await read(path), /当前 LongPort|MVP[^\n]*RANKED_UNIVERSE|MVP 固定.*RANKED_UNIVERSE/s);
  }
});

test("IDENTITY-01 stable identity excludes watch_id", async () => {
  const design = await read(files.design);
  const plan = await read(files.plan);
  assert.match(design, /provider_code.*market_code.*provider_sector_id.*taxonomy_version/s);
  assert.match(design, /禁止使用 watch_id|watch_id.*不能/);
  assert.match(plan, /禁止 watch_id/);
  assert.match(design, /sector_identity_id.*FK/);
  assert.match(await read(files.db), /自然唯一键.*provider_code.*market_code.*provider_sector_id.*taxonomy_version/s);
  assert.match(design, /market_sector_identity_lock/);
  assert.match(design, /READ COMMITTED/);
  assert.match(design, /INSERT IGNORE.*SELECT \.\.\. FOR UPDATE/s);
  for (const path of [files.design, files.api, files.db, files.plan]) {
    assert.match(await read(path), /移除.*级联删除|不删除快照|历史快照保留/);
  }
});

test("RS-01 public ranking freezes one common benchmark", async () => {
  const design = await read(files.design);
  const api = await read(files.api);
  assert.match(design, /共同基准/);
  assert.match(design, /tracking_symbol.*不参与公共 RS 排名/);
  assert.match(api, /公共 RS.*(共同|同一).*(等权基准)/);
  assert.match(design, /固定窗口 cohort/);
  assert.match(api, /固定 cohort/);
  assert.match(api, /稳定身份的交集/);
});

test("ROT-01 rotation handles identity, changing universe and zero variance", async () => {
  const design = await read(files.design);
  assert.match(design, /稳定.*sector_identity|sector_identity_id/);
  assert.match(design, /覆盖率|pair_coverage/);
  assert.match(design, /零方差.*无定义/);
  assert.match(design, /rank_percentile_change/);
  assert.match(design, /pair_coverage=min\(intersection_count\/left_count, intersection_count\/right_count\)/);
  assert.match(design, /sector_rotation_pair_metric/);
  for (const token of ["min_pair_coverage", "avg_pair_coverage", "valid_pair_count", "weighted_intersection_count"]) {
    assert.match(design, new RegExp(token));
  }
  assert.match(design, /窗口汇总固定使用 `intersection_count` 加权均值/);
  assert.match(design, /rank_percentile >= 0\.8/);
  assert.doesNotMatch(design, /rank ?== ?1|rank ≤ ceil/);
  assert.doesNotMatch(design, /全市场 `rank_no` 向量/);
});

test("FLOW-01 MVP restores capital-flow trend and defers return contribution", async () => {
  for (const path of [files.design, files.api, files.db, files.plan]) {
    const text = await read(path);
    assert.match(text, /资金趋势|capital.flow/i);
    assert.match(text, /收益贡献.*P1\.7-C|P1\.7-C.*收益贡献/s);
  }
  assert.doesNotMatch(await read(files.plan), /产出：收益贡献/);
  for (const path of [files.design, files.api, files.db, files.mock]) {
    assert.match(await read(path), /WATCHED_SECTORS/);
  }
});

test("TIME-01 volume and anomaly baselines strictly exclude current day", async () => {
  const design = await read(files.design);
  assert.match(design, /t-5\.\.t-1/);
  assert.match(design, /t-20\.\.t-1/);
  assert.match(design, /当日不得进入自身历史均值/);
  assert.match(design, /盘中累计成交额只能与历史交易日.*同一.*时间桶/);
});

test("LINEAGE-01 calculation run, manifest, parameter hash and atomic publish exist", async () => {
  for (const path of [files.design, files.db, files.plan]) {
    const text = await read(path);
    assert.match(text, /calculation.run|calculation_run/i);
    assert.match(text, /manifest/i);
    assert.match(text, /parameter_hash|参数哈希/);
    assert.match(text, /原子发布/);
  }
  assert.match(await read(files.design), /结果以 `calculation_run_id \+ 结果业务维度` 唯一/);
  assert.match(await read(files.db), /唯一键.*formula_code.*formula_version.*parameter_hash.*source_manifest_hash/s);
  for (const path of [files.design, files.api, files.db, files.plan]) {
    assert.match(await read(path), /publication.?batch|publication_batch/i);
  }
  const design = await read(files.design);
  assert.match(design, /required_formula_set_hash=SHA256\(sorted\(formula_code \+ ':' \+ formula_version \+ ':' \+ parameter_hash\)\)/);
  assert.match(design, /source_manifest_group_hash=SHA256\(sorted\(formula_code \+ ':' \+ formula_version \+ ':' \+ parameter_hash \+ ':' \+ source_manifest_hash \+ ':' \+ calculation_run_id\)\)/);
  assert.match(design, /run 必须保存不可变 `provider_code\/market_code\/as_of_date`/);
  assert.match(design, /member[\s\S]*复合 FK 指向 batch[\s\S]*复合 FK 指向 run/);
  assert.match(design, /衍生结果行只保存 `calculation_run_id`/);
});

test("ALERT-01 sector alerts have queryable subject and dedup semantics", async () => {
  for (const path of [files.design, files.api, files.db, files.plan]) {
    const text = await read(path);
    assert.match(text, /sector_identity_id|sectorId|板块告警主体/);
    assert.match(text, /dedup_key|dedupKey|重复告警|重复调度/);
  }
  const api = await read(files.api);
  assert.match(api, /SECTOR_RANK_JUMP.*0\.30/s);
  assert.match(api, /SECTOR_VOLUME_CONFIRMATION.*2\.0.*0\.03/s);
  assert.match(api, /降级.*不得产 HIGH/);
  const design = await read(files.design);
  const alertSection = design.slice(design.indexOf("### 6.8"), design.indexOf("### 6.9"));
  assert.match(alertSection, /publication_batch_id/);
  assert.match(alertSection, /\(publication_batch_id, calculation_run_id\).*复合 FK/);
  const db = await read(files.db);
  const dbAlertSection = db.slice(
    db.indexOf("#### market_data_alert 扩展"),
    db.indexOf("#### MyBatis / Flyway 边界")
  );
  assert.match(dbAlertSection, /publication_batch_id/);
  assert.match(dbAlertSection, /\(publication_batch_id, calculation_run_id\).*复合 FK/);
});

test("PRODUCT-01 daily overview and actionable readiness are first-class", async () => {
  const design = await read(files.design);
  const api = await read(files.api);
  assert.match(design, /今日板块总览/);
  assert.match(api, /daily-overview/);
  assert.match(api, /readiness/);
  assert.match(api, /BLOCKED_AUTH/);
  assert.match(api, /BLOCKED_PERMISSION/);
  assert.match(api, /Top\/Bottom.*5|leaders\/laggards.*各 5/);
  assert.match(api, /显式日期无 CLOSE|传 `asOfDate`.*无 CLOSE.*不回退/s);
  assert.match(api, /flowScope=WATCHED_SECTORS/);
  assert.match(api, /modules/);
  assert.match(api, /DERIVED_MODULES_NOT_PUBLISHED/);
  assert.match(api, /每个模块分别返回自己的 `calculationRunId`/);
  assert.match(api, /viewMode` 默认 `THIN`/);
  assert.match(api, /ADVANCED.*publicationBatchId/);
  assert.match(api, /page>1.*回传.*market\/asOfDate.*锚点.*冲突均 400/s);
  assert.match(api, /readiness 响应固定为/);
  assert.match(api, /WATCH_SNAPSHOT_RETENTION/);
  assert.match(api, /PageData\{page,size,total,sortBy,sortDirection,anchorType,anchorId,items\}/);
  const dailyOverviewLine = api.split("\n").find(line => line.includes("/daily-overview?"));
  assert.match(dailyOverviewLine, /rankingBatchId=.*publicationBatchId=/);
  assert.match(api, /eligibility=NOT_WATCHED,value=null,qualityReasonCodes=\['NOT_IN_WATCHED_SCOPE'\]/);
});

test("PRODUCT-02 ETF comparison and anomaly evidence are testable and non-causal", async () => {
  const design = await read(files.design);
  const api = await read(files.api);
  assert.match(design, /adjust_type=NONE/);
  assert.match(design, /缺失日期.*质量状态/);
  assert.match(api, /未复权价格收益/);
  assert.match(api, /returnSpread=null/);
  assert.match(api, /summary\/evidenceCodes\/evidenceValues\/qualityReasonCodes/);
  assert.match(api, /不得声称某成分.*导致/);
});

test("CALENDAR-01 verified calendars fail closed for missing HK or US data", async () => {
  for (const path of [files.design, files.db, files.plan]) {
    const text = await read(path);
    assert.match(text, /EXCHANGE_FILE/);
    assert.match(text, /MANUAL_VERIFIED/);
    assert.match(text, /fail closed/);
  }
});

test("API-01 identity, lineage and every derived list/detail endpoint are complete", async () => {
  const api = await read(files.api);
  for (const token of ["parameterHash", "calculationRunId", "rankScope", "coverageRate", "qualityReasonCodes"]) {
    assert.match(api, new RegExp(`"${token}"`));
  }
  const detailLines = api.split("\n").filter(line => line.includes("{sectorId}"));
  assert.ok(detailLines.length >= 4);
  for (const line of detailLines) {
    assert.match(line, /formulaVersion=.*parameterHash=/);
    assert.match(line, /calculationRunId=/);
  }
  const derivedListLines = api.split("\n").filter(line =>
    line.includes("| GET | `/api/v1/market-data/sector-analytics/")
    && !line.includes("/{sectorId}") && !line.includes("/readiness") && !line.includes("/daily-overview"));
  assert.ok(derivedListLines.length >= 6);
  for (const line of derivedListLines) {
    assert.match(line, /page=.*size=.*sortBy=.*sortDirection=/);
    assert.match(line, /formulaVersion=.*parameterHash=/);
    assert.match(line, /calculationRunId=/);
  }
  assert.doesNotMatch(api, /sectorIdentity|subjectIdentity/);
  assert.ok((api.match(/"anchorType":"CALCULATION_RUN"/g) ?? []).length >= 5);
});

test("DB-01 every sector derived row uses FK identity and MVP concentration is one close", async () => {
  for (const path of [files.design, files.db]) {
    const text = await read(path);
    assert.doesNotMatch(text, /`sector_identity`（稳定|`id`、`sector_identity`|\/ `sector_identity` \/|positive_flow_concentration|negative_flow_concentration/);
    assert.match(text, /window.*MVP 固定 1/);
    assert.match(text, /SELECT \.\.\. FOR UPDATE/);
  }
  const design = await read(files.design);
  assert.match(design, /top_turnover_members_json/);
  assert.match(design, /top_absolute_flow_members_json/);
  assert.doesNotMatch(design, /top_concentrators_json/);
});

test("QUALITY-01 ranked universe and zero denominator cannot be fake green", async () => {
  const design = await read(files.design);
  const api = await read(files.api);
  assert.match(api, /RANKED_UNIVERSE \+ DEGRADED \+ RANKED_UNIVERSE_NOT_FULL_MARKET/);
  assert.match(design, /absSum=0.*INSUFFICIENT_RAW/s);
  assert.doesNotMatch(api, /零分母[^\n]*INSUFFICIENT[^_]/);
});

test("SCOPE-02 watched-only flow, concentration and volume are explicit", async () => {
  for (const path of [files.design, files.api, files.db]) {
    const text = await read(path);
    assert.match(text, /data_scope='?WATCHED_SECTORS'?|dataScope=WATCHED_SECTORS|dataScope.*WATCHED_SECTORS/s);
  }
  const api = await read(files.api);
  assert.match(api, /交易集中度与量价确认同样固定 `dataScope=WATCHED_SECTORS`/);
  assert.match(api, /watchedSectorCount/);
  assert.match(api, /validSectorCount/);
});

test("ALERT-02 alert regime, stale suppression and batch lineage are deterministic", async () => {
  for (const path of [files.design, files.api]) {
    const text = await read(path);
    assert.match(text, /rsPercentile<=0\.2.*>=0\.8.*BULLISH/s);
    assert.match(text, /Z-score.*证据.*不.*触发|Z-score.*不触发/s);
    assert.match(text, /STALE.*不产生新板块提醒|STALE.*不产新提醒/s);
    assert.match(text, /publicationBatchId|publication_batch_id/);
  }
});

test("DOCS-02 planned API index is explicit and obsolete task names are absent", async () => {
  const index = await read(files.apiIndex);
  assert.doesNotMatch(index, /sector-analytics\/\*/);
  assert.match(index, /sector-analytics\/relative-strength\/\{sectorId\}/);
  for (const path of [files.design, files.api]) assert.doesNotMatch(await read(path), /ST-[0-9]/);
});

test("API-02 planned errors distinguish validation, version absence and data quality", async () => {
  const api = await read(files.api);
  assert.match(api, /VALIDATION_ERROR.*HTTP 400/);
  assert.match(api, /MARKET_SECTOR_ANALYTICS_FORMULA_VERSION_NOT_FOUND.*HTTP 404/);
  assert.match(api, /MARKET_SECTOR_ANALYTICS_DATA_UNAVAILABLE/);
  assert.doesNotMatch(api.slice(api.indexOf("## 5. 板块分析接口设计")), /MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED/);
});

test("DOCS-01 API index and mock contract identify P17 as planned remote-only truth", async () => {
  assert.match(await read(files.apiIndex), /P1\.7[\s\S]*sector-analytics/);
  const mock = await read(files.mock);
  assert.match(mock, /LOCAL_DEMO/);
  assert.match(mock, /RANKED_UNIVERSE/);
  assert.match(mock, /WATCHED_SECTORS/);
});

test("PLAN-01 P17-A gate precedes P17-B and tests are evidence-based", async () => {
  const plan = await read(files.plan);
  assert.match(plan, /P1\.7-A 未通过时禁止进入 P1\.7-B/);
  assert.match(plan, /ST-A1 -> 可部署薄切片总览/);
  assert.match(plan, /ST-A2 -> 独立验证 P1\.7-A/);
  assert.match(plan, /原始字段到最终指标/);
  assert.match(plan, /关键词测试只能证明文档存在/);
});

test("P17 corrected structure contract", () => {
  console.log("P17-SECTOR-ANALYTICS-V11-STRUCTURE");
});
