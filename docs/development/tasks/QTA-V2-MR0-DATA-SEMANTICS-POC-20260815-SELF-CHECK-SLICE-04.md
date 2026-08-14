# Self-Check (slice 4, generation 1): QTA-V2-MR0-DATA-SEMANTICS-POC-20260815

> SLICE-04 第 1 次派发的局部回执（父协调者持久化）。状态 BLOCKED——真实运行被 F-001 预检门禁正确阻断，无伪造工件。

## SLICE-04（ROLE-RUN-IMP-S4-G1，dispatch ...-IMP-S4-G1-D1）

- 状态：`BLOCKED`（AC-07 脚本侧 SELF_CHECKED + RUNTIME 维度 BLOCKED；AC-08 模板内置、TEST-08 随真实运行补齐）
- 变更：仅 `scripts/run-mr0-poc.sh`（166 行，可执行位已设，bash -n 通过）。POC-REPORT.md / POC-EVIDENCE.json **未创建**（仅可由真实运行生成）。
- F-001 证据链（BLOCKED 依据）：PublicMarketDataClient.getHQNodeData node=hs_a 仅含个股（指数不在证券池）→ Mr0PocIngestService L134-137 sample=Top150 个股 → L149-160 抓取循环仅遍历 sample → L191-193 基准仅入 universe 快照行，从不 fetchDailyBars → Mr0PocAnalysisService L100-103 tradingDays 由 SH.000001 TENCENT_PUBLIC 日 K 推导 → tradingDays=0 → breadth/volatility/industryTurnover/moneyFacts 全空、质量 COVERAGE 族 NO_TRADING_DAYS。
- 脚本 AMD-1 语义对照：全量落地（退出码 0/2/3/4 + IDEMPOTENCY_VIOLATION/ANALYSIS_INVALID 扩展披露；health 轮询≤3；幂等断言四表 inserted=0；hashFieldWhitelist 从代码常量 awk 提取非硬编码；REC-10 时区标注；MR-1-BND-A/B/C/D 模板内置）。
- 自检：bash -n exit 0；jq/sed/awk 管道以 fixture 合成验证通过；TEST-08 exit 1（预期，报告未生成）；git status 仅新增脚本。
- 下一步：SLICE-02 修复轮（F-001）完成后重派 SLICE-04（新实例）执行真实运行。
