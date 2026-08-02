# Code Review: P17-SECTOR-ANALYTICS-DESIGN-20260802

- Task ID: `P17-SECTOR-ANALYTICS-DESIGN-20260802`
- Lane: `L0`
- Role: CODE_REVIEWER / CR-RUN-1 / DISPATCH-CR-1
- Reviewed candidate identity: `c8341df03ca656732cc85c42dcd779f066b835a5 / 7b4b016529915cfcc0031346b5e6855b276b776f`（generation 1, repairRound 0）
- Contract hash: `d55ef734e49108259f58bfd90e45d3bdbde5163a457f817f0b5f8872bb57a182`
- Baseline→candidate diff: `P17-SECTOR-ANALYTICS-DESIGN-20260802-BASELINE-CANDIDATE.patch`（SHA-256 `80140284a27225f1b0a737e3fbd75ca7a32679cdf4e59dc87efb291abd329610`）

本文件为独立 qta-code-reviewer 在干净只读上下文返回的 artifact，由父上下文持久化。

## Verdicts

- functionalVerdict: **PASS**
- architectureVerdict: **FAIL**（procedural — 必需的机器架构门禁未运行、无报告 hash 绑定候选；实质性设计评估为 PASS）
- overallVerdict: **CHANGES_REQUESTED**

## Findings

| ID | Severity | Track | AC | 位置 | 问题 | 建议 |
|---|---|---|---|---|---|---|
| CR-1 | MAJOR | ARCHITECTURE | 契约 §Architecture And Quality Gates | `P17-SECTOR-ANALYTICS-DESIGN-20260802-CONTROL.json` `architectureGate` 块（status=NOT_RUN, reportSha256=""） | 候选已 CANDIDATE_FROZEN 但必需的机器架构门禁 `scripts/check-ai-architecture.mjs` 未在冻结候选上运行，无报告 hash 绑定。按规则 #8，缺失报告 hash 即 ARCHITECTURE FAIL，文字无法豁免。 | 在治理轮中对该候选运行 `node scripts/check-ai-architecture.mjs`，绑定 reportPath/reportSha256/candidateIdentity/exitCode/errorCount/warningCount，并对每个 warning 按 report ID disposition。 |
| CR-2 | NIT | FUNCTIONAL | AC-01 | `MARKET_SECTOR_ANALYTICS_DESIGN.md:101` | Mansfield RS 公式以省略号写出，标准化步骤只在散文描述，未给闭式。其余四公式均具体。 | 可选：替换省略号为显式标准化表达式或一行 Mansfield 典范引用。非阻塞。 |

## 实质性评估

### FUNCTIONAL（PASS）
- 五大白盒公式齐全且自洽：相对强弱（Mansfield + RS-rank）、轮动持续性（Spearman 位次相关 + 连续领涨/跌天数）、龙头贡献（成交额加权为主+净流入加权为辅，份额和≈1）、成交量确认（方向一致性 + 量价同向/背离）、异动提醒（阈值 + Z-score）。
- 无买卖指令、无自动交易、无黑盒/ML 评分、无券商/密钥。
- 跨市场时区（Asia/Shanghai、Asia/Hong_Kong、America/New_York）、陈旧（quote_time 阈值/STALE）、样本不足（INSUFFICIENT_SAMPLE，CN≥8/HK·US≥5 或 ≥expected_member_count 50%）处理均显式。
- 累计值跨日重置（成交额/净流入差分或仅 CLOSE 对齐）已述。
- 禁止写回 `market_sector_*`/`market_sector_ranking_*`/`stock_*` 仅以否定形式出现（禁止/不/不得/严禁/只读），无非否定写回。

### ARCHITECTURE（实质 PASS / 形式 FAIL）
- 分层完整性：衍生层只读原始事实层；提醒复用 `market_data_alert`（无第二套告警表）；分析不直连 provider；scheduler 只做衍生重算 + 提醒评估。
- 单一事实源：V1-V18 事实表与 API §1-§4 未改（append-only，零删除行确认）；新内容纯追加。
- MyBatis/Flyway 边界：新表 V19+，SQL 在 mapper XML，约定（id bigint auto_increment、decimal(20,6)、created_at/updated_at、幂等键 (sector_identity, as_of_date/trade_date, window)）遵循。
- 静态校验脚本诚实：A01-*/A02-*/A03-* 忠实实现冻结 TEST-DESIGN 选择器与锚点（真实内容检查，非放水；否定排除正确）。
- 责任图：原始事实（不可变）←只读← 衍生指标（V19+，幂等重算）←读← 提醒事件（复用 V7 market_data_alert, SECTOR_*）。写箭头永不指回原始事实。
- 形式 FAIL：按规则 #8，必需的机器架构报告未绑定候选（reportSha256=""，status=NOT_RUN）。文字无法豁免。见 CR-1。

## Residual risks

- CR-1（架构门禁未运行）—— 最终核验前必须关闭。
- 预存在（非本候选引入，非 finding）：`docs/DATABASE_DESIGN.md:5` 头部仍写 `当前已发布 V1-V17`（V18 已实现）；候选 DB diff 纯追加在 544+ 行，未触及第 5 行；TEST-DESIGN 注记 #6 明确排除修正该字符串为回归。仅供知晓。
- CR-2（RS 公式省略号）—— 可选完整性改进，非阻塞。
- 设计明确为可开发设计；所有实现风险（幂等重算正确性、Z-score 阈值调优、Spearman 窗口边界、累计重置差分）正确推迟到 ST-1..ST-4，不在本 L0 设计任务范围。

## Artifacts reviewed

- 契约 / TEST-DESIGN / IMPLEMENTER / CONTROL / CANDIDATE manifest / BASELINE-CANDIDATE.patch
- `docs/features/MARKET_SECTOR_ANALYTICS_DESIGN.md`
- `docs/api/MARKET_DATA_API.md`（§1-§5）
- `docs/DATABASE_DESIGN.md`（基线 + 板块分析规划表追加）
- `docs/development/P17_SECTOR_ANALYTICS_IMPLEMENTATION_PLAN.md`
- `scripts/tests/p17-sector-analytics-design-structure.test.mjs`

（独立角色 CR-RUN-1 已终止；未编辑任何文件、未运行 git/maven/npm/docker、未召唤子代理、未压缩。REVIEW_CLEAR 未返回：零 BLOCKING finding，但 CR-1 MAJOR 架构门禁缺口未决。）
