# MR-0 现状数据盘点（冻结）

> 状态：`FROZEN FOR MR-0 PoC`（任务 QTA-V2-MR0-DATA-SEMANTICS-POC-20260815，AC-03）
>
> 盘点日期：2026-08-15（本地库只读盘点，连接运行中 qta-mysql；不改任何数据）
>
> 盘点口径（严格区分，不得混写）：
>
> - **已实现事实**：可由 Flyway migration（`V\d+__`）+ `src/(main|test)/` 代码 + 实测行数三方证据支撑的当前能力。
> - **V2 设计目标/缺口**：仅存在于 V2 冻结文档（`QTA_V2_QUANT_RESEARCH_PLATFORM_PRD.md`、`QTA_V2_INSTITUTIONAL_MARKET_RESEARCH_DESIGN.md`、ADR-0014）中的目标，或当前事实与目标的差距。设计目标在独立验收前不得写成"已完成"。
>
> 行数均为 2026-08-15 只读盘点实测值；本地库为开发样本库，不代表生产覆盖能力。

## I-01 证券主数据

### 已实现事实

- 表结构：`V5__add_market_data_tables.sql` 建 `stock_basic`（canonical_symbol 唯一、market、list_date、delisted）；`V17__add_security_directory.sql` 扩展目录列（name_cn/name_hk/name_en/short_name、拼音、exchange、currency、security_type、list_status、data_source、source_updated_at、source_hash）并建 `stock_alias`；`V18__add_security_directory_sync_state.sql` 建 `security_directory_sync_state`（per-provider 同步状态）。
- 代码：`src/main/java/com/quant/trade/marketdata/service/SecurityDirectoryService.java`、`src/main/java/com/quant/trade/marketdata/service/SecurityDirectorySyncService.java`、`src/main/java/com/quant/trade/marketdata/provider/csv/CsvSnapshotSecurityDirectoryProvider.java`、`src/main/java/com/quant/trade/marketdata/dao/StockBasicMapper.java`、`src/main/resources/mapper/StockBasicMapper.xml`。
- 测试：`src/test/java/com/quant/trade/marketdata/SecurityDirectoryIntegrationTest.java`、`src/test/java/com/quant/trade/marketdata/SecurityDirectoryMigrationTest.java`、`src/test/java/com/quant/trade/marketdata/SecurityDirectorySyncIntegrationTest.java`。
- 实测行数（2026-08-15 只读盘点）：`stock_basic` 2 行（市场分布 SH 2）。

### V2 设计目标/缺口

- 缺口（关键）：覆盖仅 2 行，距全市场证券池（SINA `hs_a` 实测含沪深北全 A，含 bj920000 类北交所标的）差距巨大；无行业分类字段；`list_status` 经 V17 回填后大部分仍为 `UNKNOWN`。
- 设计目标（V2 PRD §17 数据验收、设计 §9.1）：全市场主数据 + 停复牌/上市退市状态 + 权威来源（Tushare 候选，当前 `NOT_VERIFIED` 阻断）。MR-0 PoC 以新浪 `hs_a` 快照补样本池身份（SLICE-02 的 `mr0_universe_snapshot`，V23 计划、纯新增可废弃），不回填正式表。

## I-02 日K

### 已实现事实

- 表结构：`V5__add_market_data_tables.sql` 建 `stock_daily_bar`（OHLC、volume BIGINT、amount DECIMAL(20,6)、adjust_type、data_source；唯一键 canonical_symbol+trade_date+adjust_type+data_source）；`V6__add_fetched_at_to_daily_bar.sql` 补 `fetched_at`。
- 代码：`src/main/java/com/quant/trade/marketdata/service/StockDataService.java`（CSV 日 K 导入，`MarketDataConstants.DATA_SOURCE_CSV`）、`src/main/java/com/quant/trade/marketdata/provider/longport/LongPortQuoteClient.java`（LongPort 单标的日 K）、`src/main/resources/mapper/StockDailyBarMapper.xml`。
- 测试：`src/test/java/com/quant/trade/marketdata/StockDataServiceTest.java`。
- 实测行数（2026-08-15 只读盘点）：`stock_daily_bar` 9 行，全部为 SH.600519（来源构成：CSV×1（2026-07-01）+ LONGPORT×8（2026-07-01..07-10））。

### V2 设计目标/缺口

- 缺口（关键）：`stock_daily_bar` **无 `turnover_rate`、无 `pre_close` 列**（对比：分钟表 V10 有 turnover_rate，快照表 V7 有 pre_close_price）——换手率入库与涨跌幅直接推导在日线上不可用，MR-0 换手率依赖腾讯日 K 行内字段另行落 PoC 表或扩展列（按契约不改现有表，V23 纯新增）。
- 缺口：无全市场日频 Provider 与历史回补（当前仅 CSV 手工 + LongPort 单标的；Longbridge 当前 `NOT_RETESTED`）。
- 设计目标（设计 §9.1/§9.2）：全市场股票日 K、2021-01-01 起第一批历史。MR-0 PoC 用 `TENCENT_PUBLIC` 写入本表（`data_source='TENCENT_PUBLIC'` 14 字符，实测可容纳于 VARCHAR(16)），单位换算按字典 D6 冻结（amount 万元×10000=元、volume 手×100=股、turnover %÷100=小数）。

## I-03 分钟K

### 已实现事实

- 表结构：`V10__add_market_data_workbench.sql` 建 `stock_minute_bar`（interval_type 1M/5M/15M/30M/60M、session_type、turnover_rate DECIMAL(10,4)、adjust_type 默认 NONE、raw_hash、quality_status）。
- 代码：`src/main/java/com/quant/trade/marketdata/service/MinuteBarIngestService.java`、`src/main/java/com/quant/trade/marketdata/dao/StockMinuteBarMapper.java`、`src/main/resources/mapper/StockMinuteBarMapper.xml`；采集编排 `src/main/java/com/quant/trade/marketdata/service/MarketDataPlanExecutionService.java` 与水位表 `market_data_watermark`。
- 实测行数（2026-08-15 只读盘点）：`stock_minute_bar` 107 行。

### V2 设计目标/缺口

- 缺口：分钟历史覆盖极小（107 行、个别标的）；无全市场分钟回补通道。
- 设计目标边界：V2 首期为 A 股盘后日频研究（PRD §18 ASSUMPTION），分钟全量与盘中研究属 MR-4；历史分钟依赖 Tushare（`NOT_VERIFIED`，PRD IMPLEMENTATION_GATE 对应维度阻断）。MR-0 PoC 不读写分钟数据。

## I-04 板块目录

### 已实现事实

- 表结构：`V11__add_market_segment.sql` 建 `market_segment`/`market_segment_member`（自定义分组，无生效日期）；`V14__add_market_sector_watch.sql` 建 `market_sector_watch`（provider_code+provider_sector_id 唯一、tracking_symbol、自动采集列见 V15）；`V19__add_sector_analytics_identity_and_readiness.sql` 建 `market_sector_identity`（taxonomy_version、valid_from/valid_to 左闭右开、soft-archive）与 `market_sector_identity_lock`。
- 代码：`src/main/java/com/quant/trade/marketdata/service/MarketSegmentService.java`、`src/main/java/com/quant/trade/marketdata/service/MarketSectorWatchService.java`、`src/main/java/com/quant/trade/marketdata/service/MarketSectorCatalogService.java`、`src/main/resources/mapper/MarketSectorIdentityMapper.xml`。
- 实测行数（2026-08-15 只读盘点）：`market_segment` 0 行；`market_sector_watch` 1 行；`market_sector_identity` 5 行。

### V2 设计目标/缺口

- 缺口（关键）：**无行业分类主数据表**——申万体系缺失，现有 identity 全部为 `LONGPORT_INDUSTRY_V1` taxonomy 的板块身份；MR-0 的 `SINA_INDUSTRY` 是新浪互斥行业（当前口径、无生效日期），与申万禁混称混算（契约 D5）。
- 缺口（关键）：`market_segment` 无生效日期，不具时点语义（契约 F2）。
- 设计目标（设计 §9.1）：互斥行业体系（申万候选，经 Tushare 验证，当前 `NOT_VERIFIED`）+ 分类版本管理。MR-0 以 `mr0_industry_membership` 快照（as_of_date + fetched_at + provider_code，V23 计划）满足 PoC 时点需求。

## I-05 板块成分

### 已实现事实

- 表结构：`V11__add_market_segment.sql` 的 `market_segment_member`（自定义分组成员，无时点）；`V14__add_market_sector_watch.sql` 的 `market_sector_member_snapshot`（随板块快照落成分 + 个股行情/份额/tags/trade_status，V19 补 sector_identity_id）。
- 代码：`src/main/java/com/quant/trade/marketdata/dao/MarketSectorMemberSnapshotMapper.java`、`src/main/resources/mapper/MarketSectorMemberSnapshotMapper.xml`；成分随 `src/main/java/com/quant/trade/marketdata/service/MarketSectorWatchService.java` 采集写入。
- 实测行数（2026-08-15 只读盘点）：`market_sector_member_snapshot` 9 行（对应 3 个板块快照批次）。

### V2 设计目标/缺口

- 缺口（关键）：**无 point-in-time 行业成分表**——成分仅以快照形式存在（Longbridge 关注板块口径），用当前成分回填历史聚合会造成未来函数（V2 设计 §12 数据正确性红线；契约 D4 据此新增 `mr0_industry_membership`）。
- 设计目标（设计 §9.1）：带纳入/剔除日期的 PIT 成分（Tushare index_member_all 候选，`NOT_VERIFIED` 阻断；PRD §17 "行业成分具备纳入和剔除日期，可按历史日期还原"）。MR-0 以 as_of_date 快照近似 PoC，样本随抓取日确定（D5）。

## I-06 排行

### 已实现事实

- 表结构：`V15__add_market_sector_automatic_collection.sql` 建 `market_sector_ranking_batch`（provider/market/snapshot_type/snapshot_bucket_time 唯一、涨跌家数、领涨领跌板块、quality_status）与 `market_sector_ranking_item`（批次内名次、板块涨跌幅、领涨股）；`V20__add_sector_analytics_run_and_publication.sql` 给 item 补 `sector_identity_id` 并建计算 run/原子发布批次；`V22__strengthen_sector_analytics_publication_scope.sql` 强化发布范围唯一键并加 `momentum_window_days`（缺省 5）。
- 代码：`src/main/java/com/quant/trade/marketdata/service/MarketSectorRankingService.java`、`src/main/java/com/quant/trade/marketdata/analysis/service/SectorAnalyticsCalculationService.java`、衍生计算 `src/main/java/com/quant/trade/marketdata/analysis/derived/RelativeStrengthCalculator.java`；测试在 `src/test/java/com/quant/trade/marketdata/analysis/`。
- 实测行数（2026-08-15 只读盘点）：`market_sector_ranking_batch` 2 行；`market_sector_ranking_item` 5 行；`sector_relative_strength_snapshot` 0 行。

### V2 设计目标/缺口

- 缺口（关键）：**V19-V22 衍生引擎覆盖 RANKED_UNIVERSE 而非全市场**——LongPort 排行接口单次最大 100 条、无独立总数/分页，P1.7 设计冻结为只能产 `RANKED_UNIVERSE`，禁止标注"全市场"（`MARKET_SECTOR_ANALYTICS_DESIGN.md` §范围与 §550）；相对强弱/轮动持续性为排行样本级结论。
- 缺口：衍生资产 `sector_relative_strength_snapshot` 0 行（引擎在、数据未产）；依赖 Longbridge（当前 `NOT_RETESTED`）。
- 设计目标（设计 §5.6/§9）：全市场互斥行业池上的相对强弱-动量与轮动带。MR-0 在样本域（Top150∪基准）重算行业聚合，不宣称全市场。

## I-07 快照

### 已实现事实

- 表结构：`V7__add_longport_market_data.sql` 建 `stock_quote_snapshot`（canonical_symbol+data_source+quote_time 唯一、current/OHLC/pre_close_price、volume、amount、trade_status、raw_hash）；`V14__add_market_sector_watch.sql` 的 `market_sector_snapshot`（rank_indicator、涨跌家数、领涨股、成交额/量，V15 补 fetched_at/snapshot_bucket_time/trigger_type 与成员计数质量列）。
- 代码：`src/main/java/com/quant/trade/marketdata/service/MarketQuoteService.java`、`src/main/java/com/quant/trade/marketdata/dao/StockQuoteSnapshotMapper.java`、`src/main/resources/mapper/StockQuoteSnapshotMapper.xml`。
- 实测行数（2026-08-15 只读盘点）：`stock_quote_snapshot` 1 行；`market_sector_snapshot` 3 行。

### V2 设计目标/缺口

- 缺口：快照是时点报价/板块观测，不是日频历史序列；`pre_close_price` 只在快照表存在（日 K 表无 pre_close，见 I-02 缺口）。
- 缺口：快照链路依赖 Longbridge（当前 `NOT_RETESTED`，2026-07-19 起外部鉴权故障，见 `docs/development/LONGPORT_TOKEN_INCIDENT_2026-07-19.md`）。
- 设计目标：MR-0/MR-1 市场全景以日频历史为事实源，快照只作辅助对照；本切片不改快照链路。

## I-08 资金字段

### 已实现事实

- 表结构：`V14__add_market_sector_watch.sql` 的 `market_sector_snapshot.total_net_inflow DECIMAL(30,6)` 与 `market_sector_member_snapshot.net_inflow DECIMAL(30,6)`（Longbridge 口径，随板块快照写入）；无独立资金流表。
- 代码：`src/main/java/com/quant/trade/marketdata/provider/longport/LongPortSectorClient.java`、`src/main/java/com/quant/trade/marketdata/service/MarketSectorWatchService.java`。
- 实测行数（2026-08-15 只读盘点）：资金列随板块快照存在（`market_sector_snapshot` 3 行、`market_sector_member_snapshot` 9 行；无按日资金流序列）。

### V2 设计目标/缺口

- 缺口（关键）：**资金字段仅 Longbridge 关注板块口径**——无个股日资金流表、无历史资金流序列、无行业级按日聚合；且 Longbridge 当前 `NOT_RETESTED`，该口径当前不可再生。
- 缺口：无跨 Provider 资金口径比较；PRD §18 IMPLEMENTATION_GATE 冻结"行业资金流 Provider 必须比较覆盖率、稳定性、授权和口径后通过 ADR 决定"，Tushare 资金流 `NOT_VERIFIED` 阻断。
- 设计目标（设计 §5/§8.4、D9）：资金净流入是 Provider 事实（禁从价量猜测）。MR-0 新增 `mr0_stock_money_flow_daily` 落新浪 `ssl_qsfx_zjlrqs` 实测字段（netamount 元、r0_net、cate_ra/cate_na），只做个股→行业聚合与 cate_na 偏差一致性报告，不做跨口径等式断言。

## I-09 质量字段

### 已实现事实

- 表结构：`V10__add_market_data_workbench.sql` 的 `stock_minute_bar.quality_status/raw_hash` 与 `market_data_watermark`（provider+symbol+interval+adjust 水位）；`V14__add_market_sector_watch.sql`/`V15__add_market_sector_automatic_collection.sql` 的板块快照与排行批次 `quality_status` 及 expected/valid/delayed/unmapped_member_count；`V19__add_sector_analytics_identity_and_readiness.sql` 的 `market_calendar.source_code/verification_status`（默认 INFERRED）；`V20__add_sector_analytics_run_and_publication.sql`/`V21__add_sector_relative_strength_and_rotation.sql` 的计算 run `status/quality_status/reason_codes/source_manifest_hash` 与发布批次完整性键。
- 代码：`src/main/java/com/quant/trade/marketdata/analysis/readiness/SectorAnalyticsReadinessManager.java`、`src/main/java/com/quant/trade/marketdata/analysis/service/MarketResearchQueryService.java`；测试 `src/test/java/com/quant/trade/marketdata/analysis/SectorAnalyticsArchitectureGuardTest.java` 及 `analysis/` 子目录。
- 实测行数（2026-08-15 只读盘点）：`market_calendar` CN 0 行（空表）；`sector_relative_strength_snapshot` 0 行。

### V2 设计目标/缺口

- 缺口（关键）：**`market_calendar` CN 空表**——无权威交易日历（表结构在、数据无），当前 scheduler 依赖 INFERRED 推断；MR-0 按 D8 用 SH.000001 指数日 K 推导交易日集合（`INDEX_KLINE_DERIVED`）并在质量报告记录该陈旧度发现，不回填。
- 缺口：质量体系集中在板块分析域；日线、资金流、行业成分域缺覆盖率/缺口/重复/陈旧/时点穿越/Provider 混用/单位异常/重算一致性八类检查族（MR-0 质量报告引擎的目标范围，AC-06）。
- 设计目标（设计 §9.3、PRD §17）：发布门禁——研究页面只读同一发布批次，覆盖不足即阻断，不用上一指标替代当前指标。

## 附：关键缺口清单（MR-1 输入边界的盘点依据）

1. `stock_daily_bar` 无 `turnover_rate`/`pre_close` 列（I-02）。
2. 无行业分类主数据与 point-in-time 成分表（I-04/I-05）。
3. `market_segment` 无生效日期（I-04）。
4. `market_calendar` CN 空表（I-09）。
5. 资金字段仅 Longbridge 关注板块口径，且当前 `NOT_RETESTED`（I-08）。
6. V19-V22 衍生引擎覆盖 RANKED_UNIVERSE 而非全市场（I-06）。
<!-- frozen-selector: grep -c '^## I-' docs/features/MARKET_RESEARCH_MR0_DATA_INVENTORY.md -> >=9 -->
