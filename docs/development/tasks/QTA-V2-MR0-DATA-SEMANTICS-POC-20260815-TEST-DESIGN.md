# Test Design: QTA-V2-MR0-DATA-SEMANTICS-POC-20260815

> Role run: `ROLE-RUN-TD-G1`（TEST_DESIGNER，fresh instance，dispatch `QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-TD-G1-D1`）
> Parent verdict: 3/3 blocking amendments ACCEPTED（AMD-1/2/3），REC-1..REC-10 全部并入契约 v1.1。
> 本文件为冻结测试清单 v1.1 的权威来源；选择器供 FINAL_VERIFIER 经 `scripts/run-ai-evidence-command.mjs` 出具回执。

## Blocking Amendments（已全部并入契约 v1.1）

### AMD-1 — AC-07 / TEST-07：聚合哈希无法证伪，退出码语义与停止条件矛盾

- 问题：(a) "两次分析聚合哈希一致"的哈希字段范围未冻结（含运行元数据则是噪声；进程内缓存则恒等假阳）；(b) 停止条件允许 RUNTIME 降级 BLOCKED，但 TEST-07 只断言 exit 0，无法区分成功与静默降级；(c) 可复现性承诺"重导入幂等"但脚本无重导入步骤。
- 修订（已并入 AC-07）：脚本固定序列 build → 起服务 → 真实导入 → 分析#1 → **二次导入同一窗口** → 分析#2 → 停服务；退出码 `0`=全链路成功且两次哈希一致、`2`=公共源不可用（写 `status=RUNTIME_BLOCKED` 证据，不满足 AC-07）、`3`=哈希不一致、`4`=build/启动/MySQL 故障；聚合哈希输入=分析结果规范化 JSON 字段白名单（键排序、十进制规范化；仅含逐日 breadth/advanceRatio/adLine、行业成交额与占比、波动率与流动性代理序列、资金流行业聚合与偏差、覆盖率、universe 规模；排除 generatedAt/runId/durationMs/fetchedAt 等运行元数据）；POC-EVIDENCE.json 必含 `status/exitCode/analysisHashRun1/analysisHashRun2/hashAlgorithm/hashFieldWhitelist/二次导入 inserted=0 计数/universeAsOfDate/universeSize/universeSymbolsSha256` 与真实行数、窗口日期。

### AMD-2 — AC-04 / TEST-04：单位换算零测试、幂等只测三表之一、fixture 无授权写入路径

- 修订（已并入 AC-04）：`Mr0PocIngestServiceTest` 扩为六用例（新增 `ingestConvertsUnitsPerFrozenDictionary`、`ingestMembershipAndMoneyFlowAreIdempotentWithPointInTimeColumns`、`ingestBackfillsMinimalStockBasicIdentityIdempotently`）；SLICE-02/03 允许路径追加 `src/test/resources/mr0/`；录制响应合并为单一文件 `src/test/resources/mr0/mr0-public-probe-fixtures.json`（内嵌捕获日期+端点 URL，无凭据）。

### AMD-3 — AC-05 / TEST-05：波动率/流动性代理/覆盖/Provider 标注零测试，覆盖域与 ε 未冻结，INSUFFICIENT_WARMUP 不可精确判定

- 修订（已并入 AC-01 与 AC-05）：字典为 A/D 线首日种子（`adLine(t0)=adv(t0)−dec(t0)`）、20 日实现波动率（简单收益率、ddof=1、PoC 不年化并标注）、行业占比求和容差（ε=1e-6，BigDecimal）、覆盖域定义（占比分母=有 as_of_date 成分的样本股票成交额合计；无成分股票计入 coverageGap 单独报告）给出唯一冻结值；`Mr0PocAnalysisServiceTest` 扩为七用例（新增 `volatilityAndLiquidityProxyMatchDictionaryFormulas`、`everyAnalysisMetricCarriesSingleProviderAttribution`、`analysisRereadsStorageEachCall`——两次调用之间经 mapper 追加一行，第二次输出必须变化，关闭缓存恒等假阳）；warmup 边界两侧（恰好最小值=成功；少一行=INSUFFICIENT_WARMUP 且无部分数值）都断言。

## 已并入契约的建议（REC-1..10 摘要）

- REC-1: TEST-01 选择器加 13 属性逐小节检查（awk）。
- REC-2: TEST-02 每 Provider 表格行 + 状态词 + 实测端点引用（`proxy.finance.qq.com/getHQNodeData/newSinaHy/ssl_qsfx_zjlrqs/hisHq` 字样必须出现）；TUSHARE 行含 NOT_VERIFIED 与 IMPLEMENTATION_GATE 结论。
- REC-3: TEST-03 九节标题精确冻结（`## I-01 证券主数据` … `## I-09 质量字段`），每节含可核验引用（`V\d+__` / `src/(main|test)/` / 实测行数+日期）与"设计目标/缺口"标记。
- REC-4: TEST-08 四要素固定标记 `MR-1-BND-A/B/C/D`。
- REC-5: 幂等 SQL 用 `ON DUPLICATE KEY UPDATE`（禁 H2 专有 `MERGE INTO ... KEY`；先例 `SyncScopeLockMapper.xml`）；V23 DDL 沿用 V14 的 `DATETIME(6)/CURRENT_TIMESTAMP(6)/DECIMAL(30,6)` 惯用法；MySQL 真实方言幂等由 TEST-07 二次导入 inserted=0 证明。
- REC-6: V23 `provider_code` 至少 `VARCHAR(32)`（`stock_daily_bar.data_source` VARCHAR(16) 可容纳 `TENCENT_PUBLIC`=14 字符，已在契约核对）；禁用 `INSERT IGNORE`。
- REC-7: 控制器措辞改为"ingest=受控写入口（仅本地 profile）、analyze/report=只读库入口"；`Mr0PocQualityServiceTest` 嵌套 `analyzeAndReportDoNotInvokePublicClient`（MockMvc + PublicMarketDataClient fail-if-invoked 打桩）；report 引擎并入 quality service。
- REC-8: 八检查族断言结构化对象（`family/status/reasonCode/affectedCount`），拒绝裸字符串列表。
- REC-9: SLICE-01 `maxProductionLineDelta=1`（文档不计生产行）由父级确认口径：架构门禁只统计代码文件行数，`.md` 不计入。
- REC-10: `fetched_at` 用 JVM 默认时区并在 POC-REPORT 标注时区名（哈希白名单已剔除时间戳）；空 universe 输出原因码（`EMPTY_VALID_UNIVERSE`），禁止 NaN/Infinity 字符串，纳入字典缺失语义。

## 冻结测试清单 v1.1

| 测试 ID | ACs | 类型 | 来源 | 选择器（确切） | 预期 |
| --- | --- | --- | --- | --- | --- |
| TEST-01 | AC-01 | STATIC | `docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md` | `test $(grep -c '^### M-' F) -ge 15 && for L in 名称 金融含义 公式 单位 频率 市场 Provider 原始字段 窗口 复权 交易日历 缺失语义 失效条件; do N=$(awk -v lb="$L" '/^### M-/{sec++;next} sec>0 && $0 ~ ("^(- )?" lb "[:：]") {c[sec]++} END{b=0; for(i=1;i<=sec;i++) if(!c[i]) b++; print b}' F); test "$N" -eq 0 || exit 1; done` | exit 0 |
| TEST-02 | AC-02 | STATIC | `docs/features/MARKET_RESEARCH_MR0_PROVIDER_MATRIX.md` | `for P in LONGBRIDGE TUSHARE TENCENT_PUBLIC SINA_PUBLIC SOHU_PUBLIC; do R=$(grep -E "^\|[^|]*${P}[^|]*\|" F | grep -cE 'VERIFIED|NOT_VERIFIED|NOT_RETESTED'); test "$R" -ge 1 || exit 1; done && grep -E '^\|[^|]*TUSHARE[^|]*\|' F | grep -c 'NOT_VERIFIED' | grep -qv '^0$' && test $(grep -cE 'proxy\.finance\.qq\.com|getHQNodeData|newSinaHy|ssl_qsfx_zjlrqs|hisHq' F) -ge 4` | exit 0 |
| TEST-03 | AC-03 | STATIC | `docs/features/MARKET_RESEARCH_MR0_DATA_INVENTORY.md` | `for T in 'I-01 证券主数据' 'I-02 日K' 'I-03 分钟K' 'I-04 板块目录' 'I-05 板块成分' 'I-06 排行' 'I-07 快照' 'I-08 资金字段' 'I-09 质量字段'; do grep -c "^## ${T}" F | grep -qv '^0$' || exit 1; done && test $(awk '/^## I-/{sec++;next} sec>0 && /V[0-9]+__|src\/(main\|test)\/|实测行数/{c[sec]++} END{b=0;for(i=1;i<=sec;i++)if(!c[i])b++;print b}' F) -eq 0` | exit 0 |
| TEST-04 | AC-04 | AUTOMATION | `src/test/java/com/quant/trade/marketdata/poc/Mr0PocIngestServiceTest.java` | `./mvnw -q test -Dtest=Mr0PocIngestServiceTest`（M1 `ingestDailyBarsTwiceWritesNoDuplicates`、M2 `ingestRowsCarryPublicProviderLabel`、M3 `ingestPreservesExistingProviderRows`、M4 `ingestConvertsUnitsPerFrozenDictionary`、M5 `ingestMembershipAndMoneyFlowAreIdempotentWithPointInTimeColumns`、M6 `ingestBackfillsMinimalStockBasicIdentityIdempotently`） | exit 0，6/6 |
| TEST-05 | AC-05 | AUTOMATION | `src/test/java/com/quant/trade/marketdata/poc/Mr0PocAnalysisServiceTest.java` | `./mvnw -q test -Dtest=Mr0PocAnalysisServiceTest`（M1 `marketBreadthMatchesDictionaryFormulas`、M2 `industryTurnoverShareSumsWithinCoverage`、M3 `moneyFlowIndustryDeviationIsReported`、M4 `analysisBlocksWhenWarmupInsufficient`（边界两侧）、M5 `volatilityAndLiquidityProxyMatchDictionaryFormulas`、M6 `everyAnalysisMetricCarriesSingleProviderAttribution`、M7 `analysisRereadsStorageEachCall`） | exit 0，7/7 |
| TEST-06 | AC-06 | AUTOMATION | `src/test/java/com/quant/trade/marketdata/poc/Mr0PocQualityServiceTest.java` | `./mvnw -q test -Dtest=Mr0PocQualityServiceTest`（M1 `qualityReportContainsAllEightCheckFamilies`（结构化对象断言）、M2 `unitAnomalyDetectsVwapOutsideLowHigh`、M3 `duplicateAndProviderMixingAreFlagged`、M4 `staleMembershipIsFlaggedAsNotPointInTime`、M5 嵌套 `analyzeAndReportDoNotInvokePublicClient`） | exit 0，5/5 |
| TEST-07 | AC-07 | RUNTIME | `scripts/run-mr0-poc.sh` | `bash scripts/run-mr0-poc.sh`；随后 jq 校验 `POC-EVIDENCE.json` 键集（status/exitCode/analysisHashRun1/analysisHashRun2/hashAlgorithm/hashFieldWhitelist/二次导入 inserted=0/universeAsOfDate/universeSize/universeSymbolsSha256）与 `analysisHashRun1==analysisHashRun2` | exit 0；键齐；哈希相等；幂等计数=0 |
| TEST-08 | AC-08 | STATIC | `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-REPORT.md` | `test $(grep -c '^## MR-1 输入边界' F) -ge 1 && for M in MR-1-BND-A MR-1-BND-B MR-1-BND-C MR-1-BND-D; do grep -c "$M" F | grep -qv '^0$' || exit 1; done` | exit 0 |
| TEST-FULL | 全部 | AUTOMATION | `pom.xml` | `./mvnw -q test && ./mvnw -q -DskipTests package` | exit 0（候选冻结前一次；verifier 独立重跑一次） |

### 黑盒边界用例（TEST-04/05/06 内落位）

| 用例 | 前置 | 输入 | 期望 | AC |
| --- | --- | --- | --- | --- |
| TD-04-M4b 单位换算负例 | fixture 含万元/手原始值 | ingest 后读库 | amount×10000、volume×100、turnover%/100 精确相等（BigDecimal 断言） | AC-04 |
| TD-04-M6b 身份幂等 | 库中已有 CSV 导入的 SH.600519 stock_basic 行 | 二次 backfill | 不改名/不重复/不覆盖 list_date | AC-04 |
| TD-05-M4a 恰好最小观测 | 构造恰满 MA20+20 日波动率所需行数 | analyze | 计算 success、无 INSUFFICIENT_WARMUP | AC-05 |
| TD-05-M4b 少一行 | 同上减一行 | analyze | 该指标 INSUFFICIENT_WARMUP 且无部分数值输出 | AC-05 |
| TD-05-M8 空集 | 空库 analyze | analyze | 原因码（EMPTY_VALID_UNIVERSE 类），无 NaN/Infinity 字符串 | AC-05 |
| TD-06-M5b 外联禁令 | PublicMarketDataClient 打桩 fail-if-invoked | analyze/report | 200 且 client 零调用 | AC-06 |

## 环境 / Fixture 要求

- AUTOMATION：`src/test/resources/application-test.properties`（H2 `MODE=MySQL`，Flyway 跑 `classpath:db/migration`，V23 必须在其下可迁移）；单测零联网；fixture 单文件 `src/test/resources/mr0/mr0-public-probe-fixtures.json`（F5 真实响应摘录 + 捕获日期/端点，无凭据；是测试数据不是 Provider 验收证据）。
- RUNTIME：JDK17 + `--spring.profiles.active=local` jar + 既有 qta-mysql（127.0.0.1:3306）；无密钥打印；外网可达；TEST-07 实施者跑一次留证、verifier 独立重跑一次。
- 静态检查：BSD grep/awk 兼容选择器（未用 gawk 特性）。

## 结论

READY_FOR_IMPLEMENTATION（父级已将 AMD-1..3 并入契约 v1.1 并重新冻结 contract_hash）。
