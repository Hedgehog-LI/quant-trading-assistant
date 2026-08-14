# Task Contract: QTA-V2-MR0-DATA-SEMANTICS-POC-20260815 数据与语义 PoC

## Contract Identity

- Status: `FROZEN`
- Contract version: `1.1`（并入 TEST_DESIGNER 阻塞性修订 AMD-1/2/3 与建议 REC-1..10，见 `...-TEST-DESIGN.md`）
- Frozen at: `2026-08-15`
- Lane: `L2`（migration + provider + 数据语义，无资金/授权/不可逆操作）
- Product owner: 项目维护者（V2 冻结设计）
- Task branch: `codex/qta-v2-mr0-data-semantics-poc`
- Baseline commit: `fcf758c23829ea13216291e180a640e5d30ab81a`
- Pre-existing dirty paths: 无（工作树干净）
- Git automation: `COMMIT_AND_CHECKPOINT_PUSH`（用户明确授权；仅任务分支 checkpoint push，禁止 push main / force push）

## Objective

基于已冻结的 V2 产品与机构化市场研究设计（`docs/features/QTA_V2_QUANT_RESEARCH_PLATFORM_PRD.md`、
`docs/features/QTA_V2_INSTITUTIONAL_MARKET_RESEARCH_DESIGN.md`、ADR-0014），完成 MR-0 数据与语义 PoC：
证明 MR-1 市场全景所需的证券池、日频行情、行业成分、行业成交聚合、市场广度、行业成交占比和资金事实
**真实可得、口径明确、可重算、质量可控**，并冻结指标数据字典、Provider 能力矩阵和 MR-1 输入边界。
本任务不重新设计产品，不开发 V2 前端页面，不制作展示型大屏。

## Facts（父上下文已核实）

- F1 设计冻结：V2 研究顺序 = 趋势与风险 → 广度 → 流动性与交易活跃度 → 行业成交占比迁移 → Provider 资金事实 → 相对强弱与动量 → 板块下钻 → 候选。MR-0 必须冻结指标词典、验证 Tushare 能力、用一个完整交易月证明行业成交汇总与资金数据可重算。
- F2 数据表现状：`stock_basic`(V5/V17/V18)、`stock_daily_bar`(V5/V6，无换手率/前收盘列)、`stock_minute_bar`(V10)、`market_segment`(V11，无生效日期)、`market_sector_*`(V14/V15，资金字段为 Longbridge 口径)、P1.7 分析表(V19-V22)。无申万/新浪等行业分类主数据表，无 point-in-time 行业成分表，无个股日资金流表。`market_calendar` CN 行数为 0。
- F3 本地库现状（2026-08-15 只读盘点）：stock_basic 2 行；stock_daily_bar 9 行（SH.600519 CSV×1 + LONGPORT×8，2026-07-01..10）；quote_snapshot 1；minute_bar 107；sector_watch 1 / snapshot 3 / member 9；ranking_batch 2 / item 5；sector_identity 5；rs_snapshot 0。本地库无法支撑全市场月度 PoC，必须真实外联。
- F4 凭据现状：仓库与运行容器均无 Tushare token（PRD IMPLEMENTATION_GATE 对应维度只能记 NOT_VERIFIED）。运行中 qta-server 报 LONGPORT `configured=false`；Longbridge 2026-07-19 起外部鉴权故障（事件记录 `docs/development/LONGPORT_TOKEN_INCIDENT_2026-07-19.md`），本任务不轮换凭据、不打印任何密钥。
- F5 公共无凭据源真实探针（父上下文 2026-08-15 执行，全部真实 HTTP）：
  - `TENCENT_PUBLIC` `proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get`：个股/指数日 K `[日期,开,收,高,低,量(手),{},换手率(%),成交额(万元),...]`，历史完整（实测 SH.600519、SH.000001 2026-07 真实数据）。
  - `SINA_PUBLIC` `Market_Center.getHQNodeData?node=hs_a`：全 A 证券池快照（代码/名称/总市值/流通市值(万元)/换手率），分页。
  - `SINA_PUBLIC` `newSinaHy.php` + 行业 node 成分：新浪行业分类（互斥）目录与成分，当前口径、无生效日期。
  - `SINA_PUBLIC` `MoneyFlow.ssl_qsfx_zjlrqs?daima=`：个股日资金流（netamount 主力净流入(元)、ratioamount、r0_net 超大单、cate_na 行业净流入），实测 SH.600519 共 3991 条、覆盖 2010-03-01 起全部历史，含 2026-07 全月。
  - 备选 `SOHU_PUBLIC` `q.stock.sohu.com/hisHq`：日 K 含成交额(万元)/换手率，实测可用。
  - 不可用：`push2his.eastmoney.com`（本环境 Empty reply）、网易 `chddata`（404 已下线）。
- F6 运行环境：qta-mysql(healthy, 127.0.0.1:3306, dev 默认口令可用)、qta-server(旧代码, 8080)。`application-local.properties` 默认连 localhost:3306。外网可达。

## Decisions（无人值守自主决策，均选可逆且最易验证方案）

- D1 **PoC 数据源** = 公共无凭据真实源：日 K 用 `TENCENT_PUBLIC`，证券池/行业成分/资金流用 `SINA_PUBLIC`。它们是真实 Provider 探针，不是 mock；在矩阵中如实标注"非官方公共端点、稳定性与授权风险"，且**不构成 MR-1 生产选型决策**（生产选型需 MR-1 前另立 ADR）。
- D2 Tushare 全部能力记 `NOT_VERIFIED`（无凭据，不询问用户、不等待）；PRD IMPLEMENTATION_GATE 输出 = 对应维度被阻断，写入 MR-1 边界。
- D3 Longbridge：历史能力（单标的日 K/快照/板块）以 2026-07-12/18 真实验收记录为 `VERIFIED(历史)`；当前鉴权记 `NOT_RETESTED`（容器无凭据 + 2026-07-19 事件），本任务不做凭据轮换。
- D4 新增最小 V23 migration（`mr0_universe_snapshot`、`mr0_industry_membership`、`mr0_stock_money_flow_daily`）：现有表无时点行业语义（market_segment 无生效日期），复用会造成语义失真。三表均为 PoC 前缀、纯新增、可废弃，MR-1 正式表仍需 ADR。
- D5 PoC 冻结口径：市场 CN；分析窗口 2026-07-01..2026-07-31（完整交易月）；预热抓取 2026-04-01..2026-07-31（满足 MA20/MA60 与 20 日波动率预热）；基准 SH.000001（上证指数）；样本 = 新浪证券池按流通市值(nmc)降序前 150 只 ∪ 基准指数；行业口径 = 新浪行业分类（记为 `SINA_INDUSTRY`，非申万，禁止与申万混称混算）。样本随抓取日快照确定，as-of 日期入库，不声称可跨日复现同一Top150；可复现性定义为同库重算一致 + 重导入幂等。
- D6 单位冻结：`amount` 单位=元（万元×10000 入库）；`volume` 单位=股（手×100）；`turnover_rate` 入库=小数比例（%/100）；字典附 VWAP∈[low,high] 单位自检规则。
- D7 复权冻结：PoC 事实统一 `adjust_type=NONE`（不复权，腾讯无 fq 参数原始价）；字典记录 NONE 口径下除权日收益失真为失效条件之一；qfq 可得但本任务不启用、不混存。
- D8 交易日历：PoC 交易日集合由 SH.000001 指数日 K 日期推导（`INDEX_KLINE_DERIVED`）；`market_calendar` 空表记为陈旧度发现，本任务不回填。
- D9 资金事实 = `SINA_PUBLIC` 主力净流入（netamount 及 cate_na 行业口径），只做一致性报告（偏差+容忍度），不做跨口径等式断言；QTA 不从价量猜测资金净流入（字典红线）。
- D10 PoC 运行方式：本地 `--spring.profiles.active=local` 起 jar 连既有 qta-mysql（dev 默认口令，无密钥打印），`scripts/run-mr0-poc.sh` 一键编排导入→分析→报告→复跑一致性校验。Docker 不重建（MySQL 已在运行，合同不需要重建）。

## Scope

### In Scope

- 冻结 MR-0 指标数据字典、Provider 能力矩阵、现状盘点（已实现事实 vs 设计目标）三份文档。
- V23 最小 migration（3 张 mr0_ 前缀表）+ 公共源只读客户端 + 幂等导入服务 + 分析/质量/报告引擎 + REST 入口 + 可重复执行脚本。
- 用真实公共源完成 2026-07 完整交易月 PoC 并产出质量报告与 MR-1 输入边界。
- 聚焦测试（H2，录制真实响应为 fixture，不联网）覆盖幂等、单位、时点、聚合公式、Provider 混用与未来函数守卫。

### Out Of Scope / Non-goals

- 不开发 V2 前端页面/大屏；不推倒重写 P1.7/P1.9/P1.10-A 或现有采集链路。
- 不做 Tushare/Longbridge 代码接入或凭据获取（矩阵仅记录能力与状态）。
- 不做生产 Provider 选型 ADR、不回填 `market_calendar`、不改现有表结构（V23 纯新增）。
- 不新增黑盒评分、荐股结论、自动交易；不连接账户/订单。
- 不安装系统级软件、不购买服务、不删除数据。

### Prohibited

- 用 mock、合成数据或文档描述冒充真实 Provider 验收。
- 把 NOT_VERIFIED 写成 PASS；把设计冻结写成实现完成。
- 跨 Provider/跨分类体系静默混算（腾讯 K 线 + 新浪资金流可以并存，但每个指标必须单一 provider 单一口径，混用必须被质量检查标记）。
- 打印、提交、复制任何密钥值或 `.env` 内容；提交 `runtime-libs`、大数据文件。
- 子角色执行 Git；任何角色调用 AskUserQuestion。

## Acceptance Criteria（共 8 条）

- **AC-01 指标数据字典冻结**：`docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md` 覆盖任务规定的全部指标类别（基准走势、成交额、换手率、市场广度含上涨/下跌家数、行业成交额及占比、资金净流入、资金强度、相对强弱、相对动量、波动率、流动性代理、数据覆盖率、陈旧度，≥15 个指标，每个 `### M-NN` 小节含名称、金融含义、公式、单位、频率、市场、Provider、原始字段、窗口、复权、交易日历、缺失语义、失效条件 13 项属性）。字典必须为以下四项给出唯一冻结值（AMD-3）：A/D 线首日种子 `adLine(t0)=adv(t0)−dec(t0)`；20 日实现波动率=简单收益率、ddof=1、PoC 输出不年化并标注；行业占比求和容差 ε=1e-6（BigDecimal）；覆盖域定义=占比分母为有 as_of_date 成分的样本股票成交额合计、无成分股票计入 coverageGap 单独报告。空有效股票池输出原因码（如 `EMPTY_VALID_UNIVERSE`），禁止 NaN/Infinity 字符串（REC-10）。证据：STATIC 文档检查（grep `^### M-` 计数≥15 + 13 属性逐小节 awk 检查，选择器见 TEST-DESIGN）。角色：SLICE-01。
- **AC-02 Provider 能力矩阵**：`docs/features/MARKET_RESEARCH_MR0_PROVIDER_MATRIX.md` 至少覆盖 Longbridge、Tushare、TENCENT_PUBLIC、SINA_PUBLIC、SOHU_PUBLIC（备选）五源，每源记录官方能力、权限要求、调用限制、历史范围、稳定性、授权风险和 `VERIFIED / VERIFIED(历史) / NOT_RETESTED / NOT_VERIFIED` 状态，每行为表格行并绑定可核验证据（本任务真实探针：实测日期+端点+样本行摘录，`proxy.finance.qq.com`、`getHQNodeData`、`newSinaHy`、`ssl_qsfx_zjlrqs`、`hisHq` 字样必须出现在矩阵中；或历史验收/事件文档路径）。Tushare 行必须含 `NOT_VERIFIED` 与 PRD IMPLEMENTATION_GATE 阻断结论。证据：STATIC（选择器见 TEST-DESIGN TEST-02）。角色：SLICE-01。
- **AC-03 现状盘点**：`docs/features/MARKET_RESEARCH_MR0_DATA_INVENTORY.md` 逐表逐字段盘点九类，小节标题精确冻结为 `## I-01 证券主数据`、`## I-02 日K`、`## I-03 分钟K`、`## I-04 板块目录`、`## I-05 板块成分`、`## I-06 排行`、`## I-07 快照`、`## I-08 资金字段`、`## I-09 质量字段`；每节必须同时含"已实现事实"（引用 `V\d+__` migration / `src/(main|test)/` 代码路径 / 实测行数+日期）与"设计目标/缺口"标记（REC-3），并指出关键缺口（stock_daily_bar 无换手率列、无 PIT 行业成分表、market_calendar CN 空等）。证据：STATIC（TEST-03 选择器）。角色：SLICE-01。
- **AC-04 PoC 存储与幂等导入**：V23 migration 创建 3 张 mr0_ 表（universe 快照/行业成分含 as_of_date+fetched_at/个股日资金流，均含 provider_code VARCHAR(32)，DDL 沿用 V14 的 DATETIME(6)/CURRENT_TIMESTAMP(6)/DECIMAL(30,6) 惯用法）；`PublicMarketDataClient` 只读访问腾讯日 K、新浪证券池/行业/资金流（无凭据、无密钥）；`Mr0PocIngestService` 幂等写入（幂等 SQL 统一 `ON DUPLICATE KEY UPDATE`，禁用 H2 专有 `MERGE INTO ... KEY` 与 `INSERT IGNORE`，REC-5/6）：日 K 落既有 `stock_daily_bar`（data_source=`TENCENT_PUBLIC`=14 字符 ≤ VARCHAR(16)），单位换算冻结 amount=元（万元×10000）、volume=股（手×100）、turnover_rate=小数（%/100）且 VWAP∈[low,high] 自检；样本证券幂等补齐最小 `stock_basic` 身份；不覆盖既有 CSV/LONGPORT 行；行业成分与资金流二次导入零新增且逐行含 as_of_date+fetched_at+provider_code。证据：AUTOMATION（`Mr0PocIngestServiceTest` 六用例：`ingestDailyBarsTwiceWritesNoDuplicates`、`ingestRowsCarryPublicProviderLabel`、`ingestPreservesExistingProviderRows`、`ingestConvertsUnitsPerFrozenDictionary`、`ingestMembershipAndMoneyFlowAreIdempotentWithPointInTimeColumns`、`ingestBackfillsMinimalStockBasicIdentityIdempotently`；含 TD-04-M4b 单位负例与 TD-04-M6b 身份幂等边界）。角色：SLICE-02。
- **AC-05 MR-0 分析引擎**：`Mr0PocAnalysisService` 在导入数据上按字典公式计算：证券池覆盖、日频覆盖率、市场广度（上涨/下跌/平家数、advanceRatio、A/D 线，首日种子 `adLine(t0)=adv(t0)−dec(t0)`）、行业成交额聚合与占比（Σshare=1±1e-6 于覆盖域，无成分股票计入 coverageGap）、20 日实现波动率（简单收益率、ddof=1、不年化）、流动性代理(|r|/amount)、资金事实（个股主力净流入→行业聚合 与 cate_na 偏差报告）；预热不足时逐指标输出 `INSUFFICIENT_WARMUP` 且无任何部分数值（未来函数守卫，边界两侧可测：恰好最小观测=成功、少一行=阻断）；每指标输出单一 provider 单一口径标注，混源被标记而非静默合并；分析每次调用重读存储（无进程内结果缓存）；空池输出 `EMPTY_VALID_UNIVERSE` 原因码。证据：AUTOMATION（`Mr0PocAnalysisServiceTest` 七用例：`marketBreadthMatchesDictionaryFormulas`、`industryTurnoverShareSumsWithinCoverage`、`moneyFlowIndustryDeviationIsReported`、`analysisBlocksWhenWarmupInsufficient`、`volatilityAndLiquidityProxyMatchDictionaryFormulas`、`everyAnalysisMetricCarriesSingleProviderAttribution`、`analysisRereadsStorageEachCall`）。角色：SLICE-03。
- **AC-06 质量报告引擎**：`Mr0PocQualityService`（report 引擎并入，REC-7）产出确定性 JSON+Markdown 报告，覆盖八类检查族（覆盖率、缺口、重复、陈旧、时点穿越、Provider 混用、单位异常含 VWAP∈[low,high]、重算一致性），每族为结构化对象（family/status/reasonCode/affectedCount），拒绝裸字符串列表（REC-8）；`Mr0PocController`：ingest=受控写入口（仅本地 profile 可用）、analyze/report=只读库入口，analyze/report 不外联公共源（fail-if-invoked 打桩可证）。证据：AUTOMATION（`Mr0PocQualityServiceTest` 五用例：`qualityReportContainsAllEightCheckFamilies`、`unitAnomalyDetectsVwapOutsideLowHigh`、`duplicateAndProviderMixingAreFlagged`、`staleMembershipIsFlaggedAsNotPointInTime`、嵌套 `analyzeAndReportDoNotInvokePublicClient`）。角色：SLICE-03。
- **AC-07 真实交易月 PoC 执行**（AMD-1）：`scripts/run-mr0-poc.sh` 一键编排固定序列：build → 本地起服务（`--spring.profiles.active=local`，连既有 qta-mysql）→ 真实导入 2026-07 窗口（预热 2026-04-01 起）→ 分析#1 → 二次导入同一窗口 → 分析#2 → 停服务。退出码冻结：`0`=全链路成功且两次分析哈希一致；`2`=公共源不可用（仍写出证据文件 status=RUNTIME_BLOCKED，不满足本 AC）；`3`=两次分析哈希不一致；`4`=build/启动/MySQL 故障。聚合哈希输入=分析结果规范化 JSON 的字段白名单（键排序、十进制数值规范化；仅含逐交易日 breadth 计数/advanceRatio/adLine、行业成交额与占比、20 日波动率与流动性代理聚合序列、资金流行业聚合与偏差、覆盖率数字、universe 规模；排除 generatedAt/runId/durationMs/fetchedAt 等运行元数据）。`POC-EVIDENCE.json` 必含 `status`、`exitCode`、`analysisHashRun1`、`analysisHashRun2`、`hashAlgorithm`（sha256）、`hashFieldWhitelist`、二次导入 `inserted=0/updated` 幂等计数、`universeAsOfDate`、`universeSize`、`universeSymbolsSha256`（样本清单哈希，检测样本漂移）、真实行数与窗口日期。产出 `QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-REPORT.md`（真实计数、八族质量结果、覆盖/缺口结论、时区标注 REC-10）。证据：RUNTIME（脚本 receipt exit 0 + 工件入库 + jq 键集校验）。角色：SLICE-04。
- **AC-08 MR-1 输入边界**：POC-REPORT 含 `## MR-1 输入边界` 小节，四要素用固定标记（REC-4）：`MR-1-BND-A` MR-1 可直接依赖的数据与口径（样本级广度/行业占比公式引擎、公共源日 K 可行性证据）；`MR-1-BND-B` 仍被阻断的数据（全市场历史覆盖、PIT 申万成分、官方资金流——绑定 Tushare NOT_VERIFIED/Longbridge NOT_RETESTED）；`MR-1-BND-C` 禁止使用的伪指标（价量猜资金、非互斥板块汇总成 100%、跨 Provider 混算、无标签百分数）；`MR-1-BND-D` 下一任务精确输入边界（数据集、窗口、provider、门槛）。证据：STATIC（grep 标记）。角色：SLICE-04。

## Implementation Slices（冻结顺序执行）

| Slice | ACs | 内容 | 允许写入路径 | 文件上限 | 生产代码行上限 |
| --- | --- | --- | --- | ---: | ---: |
| SLICE-01 | AC-01..03 | 三份冻结文档（字典/矩阵/盘点） | `docs/features/MARKET_RESEARCH_MR0_*.md`（3 新文件） | 3 | 1（文档不计生产行，架构门禁只统计代码文件） |
| SLICE-02 | AC-04 | V23 migration + 公共源客户端 + 幂等导入 | `src/main/resources/db/migration/V23__add_mr0_poc_tables.sql`、`src/main/java/com/quant/trade/marketdata/poc/**`、`src/main/resources/mapper/Mr0PocMapper.xml`、`src/test/java/com/quant/trade/marketdata/poc/**`、`src/test/resources/mr0/mr0-public-probe-fixtures.json`（单一 fixture 文件） | 8 | 500 |
| SLICE-03 | AC-05, AC-06 | 分析/质量/报告引擎 + REST 入口 + API 文档 | `src/main/java/com/quant/trade/marketdata/poc/**`、`src/test/java/com/quant/trade/marketdata/poc/**`、`docs/api/MARKET_RESEARCH_API.md`（追加 MR-0 PoC 节） | 8 | 500 |
| SLICE-04 | AC-07, AC-08 | 可重复执行脚本 + 真实 PoC 运行 + 报告/证据/边界 | `scripts/run-mr0-poc.sh`、`docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-{REPORT.md,EVIDENCE.json}` | 4 | 200 |

约束：SLICE-02/03 的 Java 生产代码合计新增 ≤1000 行；不修改既有生产类（只新增；如需只读复用既有 mapper 通过注入调用）。PoC 子包统一 `com.quant.trade.marketdata.poc`。

## Test Inventory（冻结 v1.1；权威选择器见 `...-TEST-DESIGN.md`；全部由 FINAL_VERIFIER 经 `scripts/run-ai-evidence-command.mjs` 出具 receipt）

| Test ID | AC | Kind | Source / Selector（摘要） | Expected |
| --- | --- | --- | --- | --- |
| TEST-01 | AC-01 | STATIC | 字典 `^### M-` 计数≥15 + 每小节 13 属性 awk 检查 | exit 0 |
| TEST-02 | AC-02 | STATIC | 矩阵五 Provider 表格行含状态词 + 实测端点引用≥4 + TUSHARE 行含 NOT_VERIFIED | exit 0 |
| TEST-03 | AC-03 | STATIC | 盘点九节标题精确命中 + 每节含可核验引用 | exit 0 |
| TEST-04 | AC-04 | AUTOMATION | `./mvnw -q test -Dtest=Mr0PocIngestServiceTest`（六用例） | exit 0，6/6 |
| TEST-05 | AC-05 | AUTOMATION | `./mvnw -q test -Dtest=Mr0PocAnalysisServiceTest`（七用例） | exit 0，7/7 |
| TEST-06 | AC-06 | AUTOMATION | `./mvnw -q test -Dtest=Mr0PocQualityServiceTest`（五用例） | exit 0，5/5 |
| TEST-07 | AC-07 | RUNTIME | `bash scripts/run-mr0-poc.sh`（退出码语义 AMD-1）+ jq 键集校验 + 哈希相等 + 二次导入 inserted=0 | exit 0 |
| TEST-08 | AC-08 | STATIC | `^## MR-1 输入边界` 存在 + `MR-1-BND-A/B/C/D` 标记各≥1 | exit 0 |
| TEST-FULL | 全部 | AUTOMATION | `./mvnw -q test` + `./mvnw -q -DskipTests package` | exit 0（candidate 冻结前一次；verifier 独立重跑一次） |

测试数据纪律：单测不联网；公共源响应用录制 fixture（来自 F5 真实探针）；fixture 是测试数据不是 Provider 验收证据，Provider 验收只认 TEST-07 真实运行。

## Verification Dimensions

- STATIC：三份文档 + 报告工件的结构化检查（TEST-01/02/03/08）。
- AUTOMATION：聚焦测试 + 一次全量 `./mvnw test` + package（TEST-04/05/06/FULL）。
- RUNTIME：TEST-07 真实公共源导入与重算（实施者跑一次留证，verifier 独立重跑一次）。
- DEPLOYMENT：`NOT_REQUIRED`（本任务无部署承诺；qta-server 旧容器不动，Docker 不重建）。

## Architecture Gate

- candidate 冻结前运行 `node scripts/check-ai-architecture.mjs --base fcf758c --architecture-review-count <N> --candidate-identity <candidate> --json-output <report>`（COMMIT 模式）；warning 需逐条结构化处置，errors>0 即阻断。
- 关注点：PoC 组件不绕过 MyBatis/Flyway 边界；不新建平行数据平台（mr0_ 表为最小事实表，读取经 mapper）；Controller 不直接持 HTTP 客户端（经 service/client 分层）；无密钥常量。

## Roles & Evidence Ownership

- TEST_DESIGNER（fresh）：挑战本契约，产出测试设计修正案（blocking amendments ≤3）。
- IMPLEMENTER（每切片 fresh，共 4 次）：按冻结顺序实现，只输出切片内 SELF_CHECKED 证据，不操作 Git。
- CODE_REVIEWER（fresh，候选冻结后）：对冻结 diff 做 FUNCTIONAL/ARCHITECTURE 双轨审查。
- FINAL_VERIFIER（fresh，disposable worktree）：经 `run-ai-evidence-command.mjs` 重跑合同门禁，唯一验收结论（FUNCTIONAL=PASS 且 ARCHITECTURE=PASS 才 ACCEPTED）。

## Budget / Stop Conditions

- repair_round ≤2（同一失败指纹）；blocking amendments ≤3（L2 上限）。
- 每角色最多 2 次 Agent wait；长命令轮询 ≤3 次；不得重复运行未变化的全量测试。
- 公共源在运行窗口不可用：ingest 如实记录失败，质量报告记 `NOT_VERIFIED` 对应维度，任务按已完成部分推进，仅 RUNTIME 维度降级为 BLOCKED 记录。
- 出现真实密钥需求、付费授权、破坏性操作诉求，或冻结文档互相矛盾 → 立即 BLOCKED 并保存 checkpoint。
- 上下文：25% 记录发现；40% 不开新阶段；60% 终止角色换新上下文。

## Evidence Artifacts（本任务产出路径）

- `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-CONTRACT.md`（本文件）
- `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-CONTROL.json`
- `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-TEST-DESIGN.md`
- `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-SELF-CHECK.md`
- `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-REVIEW-G1.md`
- `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-VERIFICATION.md`
- `docs/development/tasks/QTA-V2-MR0-DATA-SEMANTICS-POC-20260815-POC-REPORT.md` + `...-POC-EVIDENCE.json`
