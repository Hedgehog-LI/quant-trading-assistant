# Code Review G2: QTA-V2-MR0-DATA-SEMANTICS-POC-20260815

> Role run: `ROLE-RUN-CR-G2`（CODE_REVIEWER，fresh，dispatch ...-CR-G2-D1）
> Candidate: COMMIT generation-2 `05eece11a8bbfa92c79341efac0a3f5ef818fc74`（tree cfa2232…，patch sha 7c3eeb73…）
> Architecture gate: PASS exit 0 errors 0 warnings 2（ARCH-W-001/002 父级已结构化处置）；report sha 9572ee83…
> Verdicts: **FUNCTIONAL: PASS / ARCHITECTURE: PASS → REVIEW_CLEAR**（修复轮 7 项全部 FIXED、3 项披露偏差合理；4 项 P3 修正案路由 finalization/MR-1，不构成 generation-3）

## 修复验证矩阵（摘要）

| 项 | 结论 |
| --- | --- |
| CR-1 基准抓取 | FIXED（伪 entry 恒抓 sh000001、失败隔离、不进资金流/成分/样本；TEST-04 基准断言真实；真实运行 tradingDays=23） |
| CR-2 重算样本口径 | FIXED（总体=analysis 样本清单；真实运行 RECOMPUTED_MATCH 31=31） |
| CR-3 Top-150 派生 | FIXED（cap 降序+symbol tie-break、排除基准/null；universeSize=151；哈希=样本∪基准；分母全链一致） |
| CR-4 deviation 绝对差 | FIXED（Σmain_net_inflow−cate_na，元；M-15 一致） |
| CR-6 asOf 当日有 bar | FIXED（containsKey 前置；陈旧窗口计入 excluded 专测） |
| CR-7 caliber 标签 | FIXED（"as_of 无上界；时点穿越显式标记"） |
| CR-10 十位小数 | FIXED（setScale10；Σshare=1±1e-6） |
| 披露偏差 1/2/3 | 合理（mapper 增列纯增量；基准免个股 VWAP 自检=点位域不同，三处透明；quality 样本口径为 CR-3 明文要求并修复 gaps 负 missing） |

## 真实工件诚实性

诚实无编造：时间自洽（193s、CST）、双哈希一致、四表 inserted=0、whitelist 从源码提取、全部计数互洽（coverage 134/150、missing 370=23×150−3080、coverageGapDays 1127=49×23、membershipCoverage 101/150、DUPLICATES 17、STALENESS 3103、RECOMPUTE 31=31）。UNIT_ANOMALY FAIL(8)=本地预存 LONGPORT SH.600519 行 volume 存"手"未×100 的**真实既有脏数据检出**（PoC 指标未纳入）；COVERAGE WARN(0.673)=SINA 行业节点缺 49 只样本股成分的真实公共源缺口。POC-REPORT 八族完整+MR-1-BND-A/B/C/D 齐。

## P3 Findings（路由：finalization / MR-1）

- **CR2-1**：冻结字典 §3 未加注"指数行（SH.000001）点位域豁免个股 VWAP 自检"——finalization 补一句，防 MR-1 按字面实现误报。
- **CR2-2**：ingest 侧 Top-N 无 cap 并列 tie-break（分析侧有），精确并列时两侧样本可能不一致（真实运行一致；概率趋近零）——MR-1 前补 `thenComparing(canonicalSymbol)`。
- **CR2-3**：CONTROL.json `contract.version` 仍为 "1.0"（锚定不可变，契约实为 v1.1）——finalization 元数据修正（若锚定允许）或在交付记录中说明。
- **CR2-4**：ingest 期 VWAP 负分支无聚焦用例、`TableSummary.skipped` 不进 POC-EVIDENCE（16 只 SH.688xxx zeroBar 无法区分空返回 vs 自检丢行）——MR-1 前补负例断言/观测。

## Residual risks（verifier 需知）

- industryTurnover 对 market=0 除零无守卫（真实 A 股不可达，PoC 可接受）。
- 复跑日变化 → 新 as_of 快照 → analysisContentHash 与冻结证据不同（D5 预期；TEST-07 断言对象是同次运行内 run1==run2，非与已提交证据相等）。
- MR-1 接手前需清理本地 LONGPORT 手/股脏数据（已由报告披露）。

## Contract coverage

- AC-01/02/03 本轮未深审（修复未触碰 SLICE-01 文档，G1 无相关 finding，STATIC 归 FINAL_VERIFIER）——声明依赖。
- AC-04..08 无覆盖缺口；无越界写入；无密钥/自动交易触碰。
