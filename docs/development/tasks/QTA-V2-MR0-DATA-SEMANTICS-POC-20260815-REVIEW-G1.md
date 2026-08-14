# Code Review G1: QTA-V2-MR0-DATA-SEMANTICS-POC-20260815

> Role run: `ROLE-RUN-CR-G1`（CODE_REVIEWER，fresh，dispatch ...-CR-G1-D1）
> Candidate: COMMIT generation-1 `1d136c18195883e8551aeb9e8b6cf2c72d92fd64`（tree b121d5f7…，patch sha 4ed87816…）
> Verdicts: **FUNCTIONAL: FAIL / ARCHITECTURE: FAIL（缺机器门禁绑定；代码级架构健全）** → 未达 REVIEW_CLEAR

## Findings（父级处置见文末）

- **CR-1 BLOCKER（pre-registered F-001 确认）** AC-07/08 RUNTIME：ingest 不抓基准 SH.000001 日 K（Mr0PocIngestService L149 循环仅样本、L192 基准仅入快照），分析 tradingDays 依赖基准 bar → tradingDays=0 → ANALYSIS_INVALID；TEST-07 未执行、POC 工件不存在。修复：样本循环后追加基准伪 entry 抓日 K（fixture 已含 sh000001 行）；TEST-04 增断言基准 TENCENT_PUBLIC 行存在。
- **CR-2 MAJOR** AC-05/06：RECOMPUTE_CONSISTENCY 重算遍历全部有 bar 符号（含基准+陈旧非样本），与 breadth 样本口径不一致 → 修复后真实运行将出现假 FAIL/RECOMPUTE_MISMATCH。修复：重算总体限定为分析样本（排除基准）。
- **CR-3 MAJOR** AC-05/07/AMD-1：分析把最新快照全池（~5400）当样本——universe.sampleSymbols≈5400、coverageGap≈5250、COVERAGE≈0.03 假 WARN、universeSymbolsSha256 哈希全池检测不到 Top150 漂移、脚本 MEM_ROWS 可为负。修复：按 circulatingMarketCap 降序取 Top150（排除基准）派生样本；universeSize=topN+1；哈希=topN∪基准。
- **CR-4 MAJOR** AC-01/05：deviation 实现为相对比 (sum−cateNa)/|cateNa|，冻结字典 M-15 是绝对差（元）。修复：绝对差实现+TEST-05 M3 预期改+API 文档示例改。
- **CR-5 BLOCKER（流程）**：候选未绑定机器架构门禁报告（control architectureGate NOT_RUN）。修复：修复后候选重跑 check-ai-architecture.mjs --candidate-identity <gen-2> 并绑定 reportPath/Sha256/处置。
- **CR-6 MINOR**：波动率 headMap(asOf,true) 接受 asOf 无 bar 的陈旧窗口。修复：要求 asOf 当日有 bar 才合格。
- **CR-7 MINOR**：universe.caliber 标签与 as_of 无上界读取行为矛盾。修复：标签改"分析时点可见最新档快照（as_of 无上界；时点穿越由 TIME_POINT_LOOKAHEAD 族显式标记）"。
- **CR-8 MINOR**：application.properties 追加超出 SLICE-03 冻结允许列表。
- **CR-9 MINOR**：stock_basic 回填复用既有 INSERT IGNORE（P1.9-D 习语）未在 AC-04 交付披露。
- **CR-10 MINOR**：API 文档示例与实现输出不一致（share 2 位小数太粗、universeSize、相对偏差）。修复：share/sumShare 输出 10 位小数+文档示例对齐。
- **CR-11 MINOR**："仅本地 profile"措辞 vs 属性门禁实现（默认关、脚本显式开）。
- **CR-12 MINOR**：治理元数据（提交统计 24 vs patch 26 条目；control contract.version=1.0 vs 契约 1.1）。

## 披露偏差复核（审查者）

1. SLICE-02 预算口径（原始 724/门禁 484）——接受。
2. SLICE-03 预算例外（691>500、合计 1175>1000）——有保留接受，须对 FINAL_VERIFIER 保持披露。
3. 注解式只读 Mapper——接受（poc 包隔离、仅 SELECT、Javadoc 记录）。
4. 脚本 exit-3 扩展语义——接受（强于 AMD-1 下限、头注释披露；universeSize≥100 因 CR-3 空转）。

## 父级处置（repair round 1）

- 修复：CR-1、CR-2、CR-3、CR-4、CR-6、CR-7、CR-10（代码/测试/文档，由修复实施者执行）+ 真实 PoC 运行（TEST-07/08）。
- CR-5：父级在 generation-2 冻结时运行并绑定架构门禁（若本会话被治理链拦截则由持回执角色执行并记录）。
- CR-8：父级批准为已披露偏差（属性默认值有运维价值；@Value 兜底已存在）。记录于 control。
- CR-9：父级批准复用披露（SLICE-02 TaskPacket 已明确允许复用 StockBasicRegistrationManager；既有习语非 PoC 新增 SQL）。记录于 control。
- CR-11：父级批准属性门禁为等效控制（默认关闭+脚本显式开启=同等防护）。记录于 control。
- CR-12：父级修正 control contract.version=1.1；文件计数以 `git show --stat` 复核（24 为 git commit 统计，patch 26 条目含 2 个非内容条目，核对后记录）。
