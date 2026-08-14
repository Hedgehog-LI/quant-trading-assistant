# QTA-V2-MR0-DATA-SEMANTICS-POC-20260815 MR-0 PoC 报告

> 本文件由 scripts/run-mr0-poc.sh 于真实运行成功后生成（SLICE-04，AC-07/AC-08）；全部数值来自
> POC-EVIDENCE.json 与 /analyze、/report 真实响应，禁止手写伪造或未运行生成。

## 运行概要
- 命令：bash scripts/run-mr0-poc.sh；退出码：0（status=SUCCESS）；时长：193s；时区：CST（REC-10：fetched_at=JVM 默认时区）
- 窗口（D5 冻结）：analysisStart=2026-07-01、analysisEnd=2026-07-31、warmupStart=2026-04-01、sampleSize=150
- analysisContentHash：run1=run2=4a0a73c8048e8a0104f38f72bf6c9ccbad36b2a5074c2e575439e36b7a31564d（sha256、字段白名单与二次导入计数见 POC-EVIDENCE.json）
- 退出码语义：0=成功且哈希一致；2=公共源不可用；3=哈希不一致/内容健全性失败/幂等违反（exit-3 扩展）；4=build/启动/存储故障

## 数据规模
- universe：as_of 2026-08-15、universeSize=151（样本 150，基准 SH.000001 恒入快照不算样本）
- tradingDays：23（INDEX_KLINE_DERIVED，由基准 SH.000001 日 K 推导，D8）
- 真实行数：bar(分析窗 tencentBars 合计)=3080、membership(覆盖样本)=101、moneyflow(分析窗写入)=3432
- 二次导入 inserted：universe=0、membership=0、dailyBar=0、moneyFlow=0（全 0=幂等，AMD-1）
- ingest 失败明细：无（两次导入 0 失败）

## 八族质量结果（来自 /report format=json，族顺序固定）
### COVERAGE
- status: WARN
- reasonCode: LOW_DAY_COVERAGE
- affectedCount: 0
- 2026-07-01 tencentBars=134 sampleSymbols=150 coverage=0.893333
- 2026-07-02 tencentBars=134 sampleSymbols=150 coverage=0.893333
- 2026-07-03 tencentBars=134 sampleSymbols=150 coverage=0.893333
- 2026-07-06 tencentBars=134 sampleSymbols=150 coverage=0.893333
- 2026-07-07 tencentBars=134 sampleSymbols=150 coverage=0.893333
- 2026-07-08 tencentBars=133 sampleSymbols=150 coverage=0.886667
- 2026-07-09 tencentBars=134 sampleSymbols=150 coverage=0.893333
- 2026-07-10 tencentBars=134 sampleSymbols=150 coverage=0.893333
- …共 24 条 detail，以上为前 8 条

### GAPS
- status: WARN
- reasonCode: MISSING_TRADING_DATA
- affectedCount: 370
- missingStockDays=370
- zeroBarSymbols=[SH.688008, SH.688012, SH.688041, SH.688072, SH.688082, SH.688111, SH.688120, SH.688256, SH.688347, SH.688361, SH.688498, SH.688506, SH.688521, SH.688525, SH.688825, SH.688981]
- coverageGapDays=1127

### DUPLICATES
- status: OK
- reasonCode: CROSS_SOURCE_COEXISTENCE
- affectedCount: 17
- SH.600519|2026-07-01 dataSources=[CSV, LONGPORT, TENCENT_PUBLIC]
- SH.600519|2026-07-02 dataSources=[LONGPORT, TENCENT_PUBLIC]
- SH.600519|2026-07-03 dataSources=[LONGPORT, TENCENT_PUBLIC]
- SH.600519|2026-07-06 dataSources=[LONGPORT, TENCENT_PUBLIC]
- SH.600519|2026-07-07 dataSources=[LONGPORT, TENCENT_PUBLIC]
- SH.600519|2026-07-08 dataSources=[LONGPORT, TENCENT_PUBLIC]
- SH.600519|2026-07-09 dataSources=[LONGPORT, TENCENT_PUBLIC]
- SH.600519|2026-07-10 dataSources=[LONGPORT, TENCENT_PUBLIC]

### STALENESS
- status: WARN
- reasonCode: MARKET_CALENDAR_CN_EMPTY
- affectedCount: 3103
- fetchedLagHours max=1082 median=722
- universeAsOfLagDays=-15
- marketCalendarCnRows=0（空表，INDEX_KLINE_DERIVED 兜底）

### TIME_POINT_LOOKAHEAD
- status: WARN
- reasonCode: CURRENT_MEMBERSHIP_FOR_HISTORY
- affectedCount: 2967
- 当前成分聚合历史=时点穿越风险，PoC 显式假设
- lookaheadAffectedIndustryDays=644
- SH.600000 asOf=2026-08-15 affectedDays=23
- SH.600016 asOf=2026-08-15 affectedDays=23
- SH.600018 asOf=2026-08-15 affectedDays=23
- SH.600019 asOf=2026-08-15 affectedDays=23
- SH.600028 asOf=2026-08-15 affectedDays=23
- SH.600030 asOf=2026-08-15 affectedDays=23
- …共 103 条 detail，以上为前 8 条

### PROVIDER_MIXING
- status: OK
- reasonCode: NONE
- affectedCount: 0


### UNIT_ANOMALY
- status: FAIL
- reasonCode: UNIT_MISMATCH_VWAP
- affectedCount: 8
- SH.600519|2026-07-01 vwap=118515.756369 outside [1166.330000,1196.800000]
- SH.600519|2026-07-02 vwap=120353.075133 outside [1190.510000,1215.520000]
- SH.600519|2026-07-03 vwap=119623.737685 outside [1185.000000,1210.140000]
- SH.600519|2026-07-06 vwap=119935.334830 outside [1180.000000,1215.000000]
- SH.600519|2026-07-07 vwap=119311.814142 outside [1188.110000,1202.000000]
- SH.600519|2026-07-08 vwap=119178.053150 outside [1177.000000,1200.980000]
- SH.600519|2026-07-09 vwap=118348.690345 outside [1178.000000,1191.990000]
- SH.600519|2026-07-10 vwap=119191.458870 outside [1170.280000,1204.980000]

### RECOMPUTE_CONSISTENCY
- status: OK
- reasonCode: RECOMPUTED_MATCH
- affectedCount: 0
- analysisContentHash present: 4a0a73c8048e8a0104f38f72bf6c9ccbad36b2a5074c2e575439e36b7a31564d
- medianDay=2026-07-16 breadthAdvancing=31 recomputedAdvancing=31
- 跨进程重算一致性由 TEST-07 两次运行与 analysisRereadsStorageEachCall 证明
## 覆盖与缺口结论
- 成员覆盖：membershipCoverage=0.673333 coverageGapSymbols=49
- 占比覆盖域缺口：coverageGap=49 只样本股无行业成分（不入占比分母，计入 coverageGap 单独报告）
- 日历陈旧度：marketCalendarCnRows=0（空表，INDEX_KLINE_DERIVED 兜底）
- TIME_POINT_LOOKAHEAD：当前成分聚合历史=显式时点假设（PIT 行业成分被阻断，见 MR-1-BND-B）

## MR-1 输入边界
- **MR-1-BND-A（MR-1 可直接依赖的数据与口径）**：指标字典公式引擎（公式/单位/缺失语义冻结）；样本级市场广度、行业成交占比、20 日波动率、流动性代理计算链（本运行 tradingDays=23、universeSize=151、样本=150，两次分析哈希一致=4a0a73c8048e8a0104f38f72bf6c9ccbad36b2a5074c2e575439e36b7a31564d）；公共源真实可得性证据（TENCENT_PUBLIC 日 K、SINA_PUBLIC 证券池/行业成分/资金流，实测见 Provider 矩阵）；幂等导入（二次导入 inserted=0，MySQL 方言 ODKU）。
- **MR-1-BND-B（仍被阻断的数据）**：全市场逐股历史覆盖的成本与稳定性（本 PoC 仅流通市值 Top-150 样本 + 单一交易月 + 2026-04-01 起预热）；PIT 申万/官方行业成分（现用 SINA_INDUSTRY 当前成分聚合历史=显式时点假设，见 TIME_POINT_LOOKAHEAD 族）；官方口径资金流（Tushare NOT_VERIFIED 无凭据、Longbridge NOT_RETESTED）。
- **MR-1-BND-C（禁止使用的伪指标）**：价量猜资金（字典红线）；非互斥板块汇总成 100%；跨 Provider 混算（flowIntensity 类混源必须显式标注、不得静默合并）；无 Provider/口径标签的百分数；SINA_INDUSTRY 冒充申万。
- **MR-1-BND-D（下一任务精确输入边界）**：数据集=全 A 证券池 + 日 K（2021-01-01 起）+ PIT 行业成分 + 官方资金流；窗口/Provider/门槛=由 MR-1 契约冻结，凭据就绪前对应维度保持阻断。
