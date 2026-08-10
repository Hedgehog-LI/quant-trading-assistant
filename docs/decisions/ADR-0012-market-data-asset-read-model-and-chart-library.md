# ADR-0012: 行情资产采用只读聚合模型与 Lightweight Charts

- 状态：Accepted
- 日期：2026-08-10
- 决策者：项目维护者；产品、交易、量化、数据、后端和前端视角联合评审

## 背景

行情数据已落在日 K、分钟 K、最新价、任务和水位等表中，但现有页面只能分页查看原始行。前端若自行翻页、合并来源并计算摘要，会产生查询过大、来源混算、图表与表格口径不一致的问题；手写 K 线和缩放交互也会形成不必要的维护成本。

## 决策

1. 在现有 Spring Boot 单体内新增 `marketdata.asset` 只读查询门面。
2. 查询门面只读现有表，返回有界的 availability/series/summary/quality read model；不调用 provider、不写原始表、不新增 migration。
3. 单次 series 最多 2000 bars，SQL 使用时间范围和 `LIMIT 2001` 判断截断，禁止加载全量后在 Java 截断。
4. `dataSource`、`adjustType` 和 interval 必须显式，禁止自动混合。
5. 前端采用 `lightweight-charts` 5.2.x 绘制 Candlestick 与成交量多 pane，保留 attribution logo。
6. P1.9-A 只展示历史事实、摘要和质量；指标、策略、P1.7 板块分析继续使用各自独立的衍生层。

## 原因

- 图表、表格和摘要绑定同一响应，减少口径漂移。
- 有界查询保护数据库和浏览器，截断状态可解释。
- 原始事实与显示 read model 分层，后续可以演进而不污染采集表。
- Lightweight Charts 提供金融 K 线、成交量、时间轴、缩放、十字光标和多 pane，避免手写图表引擎。
- 继续保持单体和现有 Mapper/XML，避免为只读展示过早拆服务。

## 影响

- 后端增加三个只读 API 和聚合 VO，不增加表。
- 前端增加一个图表依赖和 `market-assets` feature。
- 部署产物需要保留 TradingView attribution。
- 超过上限的查询需要用户缩小范围或改用更粗粒度，第一期不做降采样。
- 现有日 K/分钟 K 分页 API 保持兼容。

## 替代方案

- 前端循环分页拼图：拒绝，口径、性能和取消请求复杂。
- 手写 Canvas/SVG K 线：拒绝，重复实现缩放、坐标、十字光标和多 pane。
- 直接使用现有分页 API并把 size 调很大：拒绝，无法提供稳定上限、摘要和质量契约。
- 新建行情分析数据库或微服务：拒绝，当前单体规模不需要。
- 第一版直接实现技术指标：拒绝，显示层和原始数据质量应先稳定。

## 关联

- `../features/MARKET_DATA_ASSET_CENTER_DESIGN.md`
- `../development/MARKET_DATA_ASSET_CENTER_IMPLEMENTATION_PLAN.md`
- `ADR-0001-single-module-spring-boot.md`
- `ADR-0002-mybatis-xml-not-jpa.md`
- `ADR-0008-longport-quote-only-provider.md`
