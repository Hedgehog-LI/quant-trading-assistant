# 证券目录与智能检索实施计划

> 状态：Ready for technical team。每阶段独立验收、独立同步文档；不得把未完成项提前标记为 DONE。

## 1. 目标与交付边界

技术团队要交付本地证券目录、确定性搜索 API 和共享证券选择器，并将其接入首批行情流程。正常搜索不调用外部行情，不自动创建业务数据。

设计基线：

- `docs/features/SECURITY_DIRECTORY_SEARCH_DESIGN.md`
- `docs/decisions/ADR-0009-local-first-security-directory.md`
- `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`
- `docs/DEVELOPMENT_WORKFLOW.md`

## 2. 分阶段计划

### D0：精确代码在线验证（已完成）

已按 `../features/EXACT_SECURITY_VERIFICATION_DESIGN.md` 完成三市场代码归一化、LongPort Static Info + Quote 只读验证 API，以及行情采集计划中的“查询并验证 -> 确认加入”流程。D0 未新增目录表、未下载全市场列表；后续从 D1 继续。

完成门槛：后端/前端目标测试、静态门禁、Docker curl 最小真实验证和文档收口；浏览器 E2E 若未执行必须明确记录。

### D1：后端目录与搜索基础

交付：

- 新增更高版本 Flyway migration，扩展 `stock_basic` 并新增 `stock_alias`；沿用 `stock_basic.id` 作为稳定内部身份，不改写历史 migration。
- 增加 enum、DO/DTO/VO、MapStruct converter、MyBatis Mapper + XML、manager/service/controller。
- 实现确定性排名的搜索 API 和证券详情 API；保留现有 `/stocks` CRUD 兼容性。
- 实现可重复执行的 CSV 目录导入，按 `canonical_symbol` 幂等 upsert，旧名进入 alias；目录为空、正常无结果和目录陈旧状态可区分。
- 为完全匹配、前缀、拼音、同名跨市场、退市过滤和分页/limit 写测试。

完成门槛：

- migration 在 MySQL 与 H2 测试配置可启动。
- `./mvnw test`、`./mvnw package` 通过。
- 搜索 API 文档与 `API_INDEX.md` 在接口真正实现后同步。
- 真实规模样本或可复现基准证明 P95 目标，若暂不达标必须记录数据量与结果。

### D2：前端共享选择器与首批接入

交付：

- 新增 `SecuritySelector` 共享组件和 feature API adapter，不把请求逻辑散落在页面。
- 支持 debounce、过期请求保护、键盘操作、loading/empty/error、市场/类型筛选和手工代码后备路径。
- mock 模式提供与 remote 同形的目录数据和排名行为。
- 首批接入最新价、历史日 K、采集计划 scope 和板块成员。
- 采集计划逐步用结构化 scope builder 替代裸 JSON；保留兼容展示，不直接破坏已有计划。

完成门槛：

- 组件测试覆盖连续输入竞态、选择后编辑失效、同名市场区分和失败重试。
- 页面测试证明选择值正确提交，不触发额外 quote/sync 请求。
- `npm run typecheck`、`lint`、`test`、`build` 通过。

### D3：目录同步与元数据补全

交付：

- 定义 `SecurityDirectoryProvider`，先接一种可合法使用、可审计的目录来源；CSV 快照可作为默认实现。
- 定义 `SecurityMetadataEnricher`，将 LongPort Static Info 接为已知代码后的可选补全器。
- 提供目录同步触发和状态 API；复用 `market_data_sync_task` 的 `SECURITY_MASTER_SYNC` 记录过程，必要时新增按 provider/market 维护最近成功时间的轻量状态表。
- 每日增量、每周全量对账；失败保留上一版目录。
- 全量目录先做 staging/diff/质量门禁再发布；单次缺失不得直接标记退市。
- 若实际发生 ticker/canonical symbol 变更，再增加有效期化 `security_identifier`，不要把身份变更逻辑塞进字符串别名。
- 明确数据源许可证、额度、更新频率和再分发边界，不把 provider 凭据写进 DB、日志、前端或 Git。

完成门槛：

- provider disabled/unreachable 时应用可启动、搜索可用、错误可解释。
- 同一快照重复导入结果幂等；改名、退市和恢复上市有集成测试。
- 仅用最小外部调用验证连接，不做全市场行情下载。

### D4：跨模块推广与体验收口

交付：

- 第二批接入自选股、交易计划、交易记录、风控和持仓快照。
- 统一所有证券显示格式和市场标签，移除重复的页面级代码校验。
- Docker/curl/浏览器完成 A/H/US 端到端小样本验收。
- 更新建设看板中的“证券目录与智能检索”节点和首批/第二批接入进度。
- 同步开发日志、验收日志、API、数据库、架构、Mock 契约和 AI handoff。

## 3. 推荐任务拆分

| 任务 | 负责角色 | 依赖 | 建议产出 |
| --- | --- | --- | --- |
| D1-01 migration 与索引 | 后端/DB | 无 | migration + DB 文档 + migration test |
| D1-02 目录导入与治理 | 后端 | D1-01 | importer + mapper XML + tests |
| D1-03 搜索与排名 API | 后端 | D1-01 | API + service + integration tests |
| D2-01 API adapter 与 mock | 前端 | D1-03 contract | adapter + mock + tests |
| D2-02 `SecuritySelector` | 前端 | D2-01 | component + behavior tests |
| D2-03 首批页面接入 | 前端/联调 | D2-02 | 4 workflows + page tests |
| D3-01 provider facade | 后端架构 | D1 | interface + disabled/CSV provider |
| D3-02 同步任务与状态 | 后端 | D3-01 | API + scheduling + audit |
| D3-03 LongPort metadata enricher | 后端 | D3-01 | optional adapter + minimal test |
| D4-01 第二批推广 | 前端/产品 | D2 稳定 | remaining forms |
| D4-02 E2E 与文档收口 | QA/全栈 | 全部 | acceptance + board + handoff |

## 4. 强制工程约束

- 沿用 Spring Boot 单体、MyBatis XML、MapStruct、Flyway、React feature-based 和 mock/remote 双模式。
- 禁止 JPA、禁止修改历史 migration、禁止建立平行证券主数据、禁止手写重复 get/set 转换。
- 先解析并验证 canonical symbol，再进入业务层；错误使用现有 `ErrorCodeEnum` 体系。
- SQL 排名逻辑须可解释并有测试，不把数据库返回顺序当业务规则。
- 不接真实交易/账户/订单；不打印或提交 provider 密钥。
- 每阶段结束按 `DEVELOPMENT_WORKFLOW.md` 更新文档和轻量 handoff，避免新会话加载完整历史。

## 5. 验收数据集

至少包含：

- `SH.603308` 应流股份，别名/拼音 `ylgf`。
- `HK.02498` 速腾聚创，港股代码补零行为。
- `US.AAPL` Apple Inc.，英文名与 ticker。
- 两个同名但不同市场的样本。
- 一个改名样本及其 former name。
- 一个 DELISTED 样本。

## 6. 中止与降级规则

- 外部 provider、凭据或许可证未就绪：完成本地 CSV 目录和搜索，不阻塞 D1/D2，不伪造外联验收。
- 搜索性能不达标：先输出 explain/基准和索引调整，未经数据证明不引入 Elasticsearch。
- 跨模块改造范围过大：首批四处完成后停下验收，再启动第二批。
- 发现现有未提交改动：保留并兼容，不执行 reset/checkout，不夹带无关重构。
