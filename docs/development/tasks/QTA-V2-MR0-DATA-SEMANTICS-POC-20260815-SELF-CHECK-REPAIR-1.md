# Self-Check (repair round 1): QTA-V2-MR0-DATA-SEMANTICS-POC-20260815

> 修复轮 1 局部回执（父协调者持久化）。修复 CR-1/2/3/4/6/7/10 + 真实 PoC 运行 SUCCESS。

## ROLE-RUN-IMP-R1-G2（dispatch ...-IMP-R1-G2-D1）

- 状态：`REPAIRED + RUN_EXECUTED`（父级复测 18/18 全绿：Ingest 6/6 + Analysis 7/7 + Quality 5/5 含嵌套）
- 修复摘要：
  - CR-1 基准 SH.000001 日 K 恒抓（伪 entry，失败仅记 failure）；基准不参与资金流/成分/样本上限；TEST-04 增基准断言。
  - CR-2 重算总体限定分析样本（排除基准），真实运行 RECOMPUTED_MATCH 证实（修复前该路径必出假 FAIL）。
  - CR-3 Top-150 流通市值样本派生（排除基准/null；cap 并列按 symbol 升序保确定性）；universeSize=151；universeSymbolsSha256=Top150∪基准。
  - CR-4 deviation 改字典 M-15 绝对差（元）；TEST-05 M3 预期 −50/−150；API 文档同步。
  - CR-6 波动率 asOf 当日有 bar 前置。
  - CR-7 universe.caliber 标签如实（as_of 无上界+时点穿越显式标记）。
  - CR-10 share/sumShare 10 位小数；API 文档示例对齐。
- 真实运行（TEST-07）：exit 0 / SUCCESS / 193s / CST；tradingDays=23、universeSize=151、双哈希一致（4a0a73c8…）、四表二次导入 inserted=0、0 失败；TEST-08 exit 0。
- 真实质量发现（PoC 价值证明）：UNIT_ANOMALY FAIL(8)——本地预存 LONGPORT SH.600519 8 行 volume 存"手"未×100（跨源单位检查真实检出既有数据问题，不进 PoC 指标）；COVERAGE WARN membershipCoverage=0.673（SINA 行业节点缺 49 只大市值样本股成分，真实公共源缺口）；STALENESS WARN market_calendar CN 空；TIME_POINT_LOOKAHEAD WARN（显式假设）；RECOMPUTE OK。
- 修复轮披露偏差（父级批准记录）：
  1. Mr0PocAnalysisMapper.java 增 circulating_market_cap 列与字段（CR-3 前置；契约 SLICE-03 glob `poc/**` 内、纯增量、无行为变化；修复 packet 逐文件清单未枚举——已开工前知会父级）。
  2. 基准指数行豁免字典 §3 个股 VWAP∈[low,high] 自检（指数点位 vs 股价不同域；ingest 注释+quality UNIT_ANOMALY 跳过 SH.000001）。
  3. quality coverage/gaps 同步改样本口径（CR-3 明文要求，含修复 gaps 负 missing 口径错误）。
- 测试侧说明：TEST-05 首跑 2 断言失败（jdbcTemplate 直插不刷新 MyBatis 一级缓存，既有已知陷阱）→ 改经 MyBatis 写入即过；生产代码未动；非同指纹重复失败。
- 变更（9 文件 + 2 运行工件）：Mr0PocIngestService/Mr0PocAnalysisService/Mr0PocQualityService/Mr0PocAnalysisMapper/三测试/API 文档 + POC-REPORT/POC-EVIDENCE。
- 全量自检：./mvnw -q test exit 0（533/0/0/1 既有 skip）。
