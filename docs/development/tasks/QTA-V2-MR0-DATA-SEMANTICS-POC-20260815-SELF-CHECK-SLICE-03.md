# Self-Check (slice 3): QTA-V2-MR0-DATA-SEMANTICS-POC-20260815

> SLICE-03 局部自检回执（父协调者持久化）。切片局部结论，不构成全局状态或验收。

## SLICE-03（ROLE-RUN-IMP-S3-G1，dispatch ...-IMP-S3-G1-D1）

- 状态：`SELF_CHECKED`（父级复测 12/12 全绿：Mr0PocAnalysisServiceTest 7/7 + Mr0PocQualityServiceTest 5/5 含嵌套端点隔离；SLICE-02 六用例复跑仍 6/6）
- 变更（8 文件）：Mr0PocAnalysisMapper.java（117 行/显著 52，注解式只读 6 SELECT）、Mr0PocAnalysisService.java（385/343）、Mr0PocQualityService.java（269/238，八族结构化+toMarkdown）、Mr0PocController.java（76/58，ingest 受控写/analyze/report 只读）、两个测试类（364+254，12 冻结方法）、docs/api/MARKET_RESEARCH_API.md（+110，§7 MR-0 PoC 节）、application.properties（+4，ingest-enabled 默认 false）。
- 门禁：check-ai-architecture.mjs exit 0 errors=0 warnings=2（IngestService deps 11>10 承前；总行数提示）；additions=1175（S2 484 + S3 691）。
- 父级裁决 1（预算例外，已披露）：SLICE-03 显著行 691>500、S2+S3 合计 1175>契约 1000。实施者三轮压缩 850→691；进一步削减需删除 AMD 冻结功能（analysisContentHash 字段白名单、逐块 provider/caliber 标注、质量族 details、toMarkdown），违反"不得削弱验收标准"。父级接受为已披露例外，移交 CODE_REVIEWER 与 FINAL_VERIFIER 裁量；无静默超限。
- 父级裁决 2（注解式 Mapper 偏离复述）：SLICE-03 冻结允许列表锚定后不可变且不含 mapper XML；只读查询按注解 @Select 放 poc/ 包内接口。写入/业务 SQL 仍在 SLICE-02 XML。偏离记录于接口 Javadoc。
- 实施者决策 3（as-of 读取语义）：membership/universe 快照取最新档不设 as_of ≤ end 上界——与冻结 TEST-06 M4 场景（2026-08-15 成分聚合 2026-07 必须标记时点穿越）一致；lookaheadAffected + TIME_POINT_LOOKAHEAD 族显式标记"当前成分聚合历史=显式假设"。
- 其他：读取清单 +1（countMarketCalendar，质量族 4 空日历 WARN 所需）；同事务 MyBatis 一级缓存发现（测试写路径统一走 Mr0PocMapper upsert）；analysisContentHash=Jackson 树白名单过滤+键排序+DecimalNode.asText+剔除运行元数据的紧凑 JSON sha256，跨进程稳定性由 SLICE-04 两次运行证明。
- 未验证维度：RUNTIME（SLICE-04 TEST-07）、DEPLOYMENT（NOT_REQUIRED）。
