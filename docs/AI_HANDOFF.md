# AI Handoff

> 本文件只记录**当前接手所需事实**。历史开发细节见 `development/DEVELOPMENT_LOG.md`；验收记录见 `acceptance/ACCEPTANCE_LOG.md`。若与代码冲突，以 migration、测试、`BUILD_CHECKLIST.md`、`CURRENT_ARCHITECTURE_AND_MODULES.md` 为准（优先级见 `AI_DEVELOPMENT_INDEX.md §2`）。

## 项目定位

Quant Trading Assistant：个人交易辅助系统（自选股 / 计划 / 交易 / 账本 / 持仓快照 / 复盘 / 风控 / 工作台）。**不自动交易、不连券商、不存密钥、不承诺收益。**

## 仓库与技术栈

| 仓库 | 路径 | 技术栈 |
| --- | --- | --- |
| 后端 + 文档 | `/Users/joker/code/quant-trading-assistant` | Java 17、Spring Boot 3.5、MyBatis XML、MapStruct、Flyway、MySQL 8.4、H2 test、Docker Compose |
| 前端 | `/Users/joker/code/quant-trading-assistant-web` | React 19、Vite、TypeScript、Ant Design 6、mock/remote 双模式 |

## 当前状态（2026-08）

- **P1.9-D 行情采集与资产查看闭环（2026-08-13，本地运行验证通过、待服务器部署）**：采集计划创建/修改/启用/执行和直接日 K 同步会幂等补齐最小 `stock_basic` 身份；新增真实已入库资产目录 API，前端 `/market-assets` 不再展示固定真实证券，而是展示实际存在日 K/分钟 K 的资产，并区分未登记、未采集、范围为空和系统错误。后端 **519 tests** + package、前端 **399 tests** + typecheck/lint/build 通过；Docker/MySQL 8.4 health 与资产目录/404 curl 通过，服务器部署待验。入口：`docs/development/tasks/MARKET-DATA-ASSET-INGESTION-LOOP-P19D-CONTRACT.md`。

- **P1.10-A 市场发现全栈候选已实现（2026-08-13，自动化与 mock 浏览器通过、真实运行时待验）**：后端 V19-V22 已形成稳定板块身份、provider 来源时间、readiness、相对强弱、固定 5 日轮动持续性、计算 run、公式/参数/源批次身份和原子发布；新增 `/api/v1/market-research` 下 readiness、显式重算、市场雷达、排行历史和板块详情。前端新增 `/market-research` 和 `/market-research/sectors/:sectorId`，覆盖 CN/HK/US、5/10/20/50 日窗口、热力图、轮动矩阵、证据排行、质量水位、历史轨迹和可解释空态；mock 只用虚构身份并持续显示 `LOCAL_DEMO`。当前只证明 `RANKED_UNIVERSE`，真实资金流明确为 `UNAVAILABLE/null`。后端 **515 tests**，前端 **396 tests**、typecheck/lint/build、桌面与 390px mock 浏览器交互通过。**未执行** Docker/MySQL V19-V22、真实 provider CLOSE 样本、remote 页面和服务器验收；独立干净上下文验收未运行，不得升级为完整交付验收。入口：`docs/api/MARKET_RESEARCH_API.md`、`docs/development/tasks/P110-A-BE-MARKET-DISCOVERY-20260813-R2-IMPLEMENTATION.md`。

- **多切片治理死锁已修复（2026-08-13，已独立验收）**：P110-A R1 暴露出子实施者 `SELF_CHECKED` 与全局 `SELF_CHECKED` 语义混用，导致只完成 `SLICE-01/05` 就提前冻结候选并被 anchor 锁死。现已由控制校验器和 Hook 强制初始 slice 在单个 `IMPLEMENTING` 窗口按冻结顺序累积，全部完成后才允许一次全局收口；终态 dispatch 必须先写入 `roleRuns`，repair 窗口与历史 `BLOCKED` 兼容。完整治理测试 **70/70**、最终独立定向核验 **4/4 PASS**。原 R1 继续保持 `BLOCKED` 且未被篡改；本轮按用户要求由当前上下文直接恢复后端实现，没有伪造 R2 控制账本或独立验收结论。

- **ZCode Hook 运行时兼容修复（2026-08-12，已真实验证）**：ZCode desktop 3.6.5 会按安全策略忽略项目级 `.zcode/config.json` Hook，现已迁移为用户级 `~/.zcode/cli/config.json` dispatcher + 仓库版本化规则；新增 `/qta-doctor` 和 `/qta-run` runtime preflight，治理测试 **66/66**、本机安装检查通过，且重启后的真实 ZCode 新任务返回 `PASS (user-config + runtime)`，证明 `UserPromptSubmit -> PreToolUse` 事件链有效，始终无 Stop Hook。原 P110-A control 保持 `BLOCKED`，后续以 `P110-A-BE-MARKET-DISCOVERY-20260812-R1` 新任务重试，不能直接 resume 终态 control。
- **P1.10 市场研究与个股决策中心设计冻结（2026-08-12）**：纠正此前“单证券行情查看器承担主研究入口”的产品错位，冻结“市场雷达 → 板块详情 → 候选扫描 → 个股决策台 → 计划/交易/复盘”三级研究漏斗。P1.7 作为板块衍生研究引擎；P1.9 `/market-assets` 重新定位为行情数据详情/质量追溯。设计同时冻结相对强弱、成交活跃度与真实资金流的语义边界，`RANKED_UNIVERSE/WATCHED_SECTORS` 不得冒充全市场；真实成交、人工计划和策略候选点严格分离；mock 必须使用虚构证券并持续显示演示水印。当前 P1.10-A 前后端候选已实现，真实运行时验收和 B/C 阶段尚未完成。入口：`docs/features/MARKET_RESEARCH_DECISION_CENTER_DESIGN.md`、ADR-0013。

- **AI 治理规则加固（2026-08-02 已独立验收）**：固定角色 TaskPacket 必须以前两行机器契约开头；派发使用 `PENDING` + `SUCCEEDED/FAILED` 两阶段回执并绑定 `tool_use_id`；active `main/master` 使用 Git 只读白名单，只允许安全切换到 `codex/*`；旧父会话不可用时使用 `/qta-run --resume <TASK-ID> <objective-or-control-path>` 精确接管；L0/收口不能省略 bounded implementer 和 clean verifier。治理测试 **58/58**、触发 **28/28**、最终独立 verifier `ACCEPTED`。入口：`docs/development/tasks/AI-GOVERNANCE-CLOSEOUT-HARDENING-20260802-*.md`。
- **P1.4b-D3 证券目录同步基础（2026-08-02 代码与自动化验收完成，条件验收）**：按 `SECURITY_DIRECTORY_SEARCH_DESIGN.md` §6 + ADR-0009 + D3 实施计划实现 `SecurityDirectoryProvider` 接口、`DisabledSecurityDirectoryProvider` 兜底、`CsvSnapshotSecurityDirectoryProvider`（默认可审计，P2 解析器 `SecurityDirectoryCsvParser` 复用 D1 冻结口径）、五阶段 `SecurityDirectorySyncService`（解析→校验→staging/diff→质量门禁→原子发布，单事务 `txRequiresNew`）、`SecurityDirectorySyncScheduler`（默认安全关闭，每日增量/每周全量，测试 seam）、`SecurityDirectorySyncController`（sync/任务详情/status，provider disabled→400+BUSINESS_RULE_VIOLATION，不泄露凭据）、V18 `security_directory_sync_state`、`SecurityDirectoryProperties`/`SecurityDirectoryConstants`、`util/SecurityDirectoryIdentityCalculator`（内容身份 snapshotHash，幂等以内容为准）。复用 `market_data_sync_task` 的 `SECURITY_MASTER_SYNC`、`SyncScopeLockMapper` 行锁、`parent_task_id` retry 链。冻结候选 `ff393bc69279a85eddf0d54897df4f0cb67eb4fd`（gen3/repair2）；后端 **406 tests** + package、架构门禁通过（file-protocol ERROR 已修复）。独立 gen3 `qta-code-reviewer` 返回 `REVIEW_CLEAR`（CR-1 原子发布 self-invocation 陷阱、CR-2 缺失 UNIQUENESS 门禁已修复）。**条件**：三次 implementer 子代理超时后由父上下文实现/修复；`qta-final-verifier` 子代理进入 plan 模式未执行，父上下文运行了客观确定性门禁；Docker/MySQL RUNTIME/DEPLOYMENT 为 `NOT_VERIFIED`。建议 push 前补一次真正独立的 disposable-worktree 最终核验。D2 前端 selector 和 D4 跨模块推广仍未实现。入口：`docs/development/tasks/SECURITY-DIRECTORY-D3-20260802-*.md`。
- **P1.6 板块双层自动采集（2026-07-22 代码与静态验收完成）**：V15 新增 CN/HK/US 全市场排行配置、批次和明细，并扩展关注板块自动采集。频率支持仅收盘或 5/10/15/30/60 分钟；各市场独立时区/常规交易窗口，具备 DB claim、时间桶幂等、鉴权/权限阻断、临时失败退避和质量字段。前端新增“自动采集”页签、立即采集、状态、历史榜单和关注板块独立频率。后端 299 tests + package、前端 277 tests + typecheck/lint/build 全绿；部署后的真实 provider 两时间桶验收尚未执行。入口：`docs/features/MARKET_SECTOR_AUTOMATIC_COLLECTION_DESIGN.md`、`docs/ai/HANDOFF_2026-07-22_sector_automation.md`。
- **P1.8 Agent 只读助手第一期（2026-07-26 代码交付完成、待部署验收）**：按 `OPENCLAW_AGENT_ASSISTANT_DESIGN.md` + ADR-0011 实现。`com.quant.trade.agent` 模块：Spring Security `AgentSecurityConfig`（保护 `/api/v1/agent/**` + 自定义 401 `AuthenticationEntryPoint`）、**单一审计入口 `AgentAuditFilter`**（servlet 级 `OncePerRequestFilter`，经 `FilterRegistrationBean` 注册为最外层 filter，覆盖整个 FilterChain；无论请求被 token/限流 filter 短路、被 Security entry point 拒绝、还是被 Controller 处理/抛异常，都只产生一条审计记录，覆盖 200/401/403/404/429/500；**requestId 单一来源**：由该 filter 生成并写入 request 属性 `agentRequestId` + 响应头 `X-Request-ID`，下游 token/限流 filter、`AuthenticationEntryPoint`、Controller 全部复用，不再各自生成）、`AgentTokenAuthFilter`（SHA-256 + `MessageDigest.isEqual` 恒定时间比较；token/限流 filter 已禁用 servlet 自动注册，仅在 Security chain 内运行）、`AgentRateLimitFilter`（per-IP 滑动窗口 + `Retry-After`；使用 `request.getRemoteAddr()` 不用 Authorization hash 防绕过）、`AgentAuditService`（V16 `agent_api_audit_log` 持久化脱敏审计；禁止记录 Token/凭据/完整请求）、`AgentQueryService`（复用现有 Service；8 端点基于 `Duration.between` 计算 freshness；空库/null 不抛异常；collectionFailures/dataQualityAlerts/collectionOverview 的 market/since/date 参数真实过滤结果）、`AgentController`（9 GET + `@Operation`/`@SecurityRequirement(bearerAuth)` + `ResponseEntity.status()` 真实 HTTP 状态码；**500 返回 `ApiResponse.fail(INTERNAL_ERROR)` 即 `success=false`/`code=INTERNAL_ERROR`，body 含 requestId 供关联，不泄露内部异常类名/堆栈**；移除 `/agent/audit` 与 Controller 手动审计调用）、`AgentOpenApiConfig`（GroupedOpenApi `agent` 分组 + `@SecurityRequirement`）。OpenClaw 插件 `integrations/openclaw/qta-assistant/`（`defineToolPlugin` **factory 模式**；`AnyAgentTool.execute(toolCallId, params, signal)` 官方签名；返回 `jsonResult()` `AgentToolResult`；`toolContext.requesterSenderId` fail-closed（allowlist 为空即拒绝所有）；QtaClient 双超时：`connectTimeoutMs` 仅约束到响应头到达为止（之后清除，不误杀大响应体）、`totalTimeoutMs` 覆盖响应头 + `resp.json()` body 解析全链路（仅在 body 解析完成后清除）；外部 `AbortSignal` 触发立即终止且不重试；所有计时器在 `finally` 清理；URLSearchParams+encodeURIComponent 编码；仅 502/503/timeout 重试一次；参数差异测试 CN vs HK 返回不同板块排行、limit=2 vs limit=5 返回不同计数。后端 **342 tests / 0 failures / 0 errors**（含 Agent 审计 D3 once-per-request 集成测试、D4 500 语义测试）、插件 **49 tests**（含 D5 totalTimeoutMs 覆盖 body 解析、connectTimeoutMs 不误杀慢 body、外部 signal 不重试、计时器无泄漏测试）、前端 **277 tests** 全绿。**待办**：服务器部署 + Nginx deny + 真实 QQ OpenID + OpenClaw 运行时 install/enable。
- **2026-07-19 Longbridge 外部鉴权故障与本地修复（待重新部署）**：最后一次真实成功为 2026-07-18 09:51:52，首次观察失败为 2026-07-19 14:28:59。原 Legacy 凭据、重新生成的 Legacy 凭据以及 CLI 0.24.0 全新 OAuth 登录均被服务端拒绝（`401004 token invalid` / `401102 token verification failed`），而官方 MCP 仍可读取行情；当前按 Longbridge 外部鉴权故障处理并已提交 Trace ID，停止反复轮换密钥。代码已将凭据失效与 403/301604 行情权限不足分开；盘中 scheduler 只扫描 `INTRADAY_MINUTE_REFRESH + INTRADAY + enabled`，旧非法计划不再每 30 秒告警。后端 **287 tests** 通过。部署与核验见 `development/LONGPORT_TOKEN_INCIDENT_2026-07-19.md`。
- **v0.1.0** Today MVP + 交易账本 + 持仓快照：已完成。
- **v0.1.1** 基础交易闭环优化（计划关联 + 复盘一致性 + 快照对比 + FIFO 对账 + 工作台待办 + 连接防呆）及多轮质量收尾：**已完成并验收**。范围与改动见 `development/DEVELOPMENT_LOG.md`。
- **P1.0 行情基础**：`marketdata` 模块已存在，V5/V6 已实现 `stock_basic`、`stock_daily_bar`、CSV 日 K 导入和 `fetched_at`。
- **P1.1 港美股扩展（2026-07-17）**：统一证券标识已扩展为 `SH/SZ/BJ/HK/US`；港股内部固定五位（`HK.02498`），美股统一大写（`US.AAPL`，支持 `US.BRK.B`）。LongPort 双向映射、证券主数据、最新价、历史日 K、板块成员及前端录入已接入。后端 **258 tests** + package、前端 **264 tests** + typecheck/lint/build 通过；仍需在部署环境用真实只读账号分别做港股/美股最小外联验收。分钟 K 和定时采集不在本轮范围。
- **P1.2 行情工作台 + 行情采集执行引擎（2026-07-17 验收完成）**：V10-V13 migration、工作台、采集计划 CRUD/统一合法性校验、分钟 K 存储/质量/水位、LongPort 原生 1M/5M/15M/30M/60M adapter、`DAILY_BAR_BACKFILL` / `MINUTE_BAR_BACKFILL` / `INTRADAY_MINUTE_REFRESH` 执行链路、A 股交易时段 scheduler、DB claim 与重启恢复均已实现。前端改为结构化计划表单，旧非法计划明确展示纠正状态，执行 pending 防重复，mock 执行明确拒绝而不伪造成功。后端 **270 tests** + package、前端 **267 tests** + typecheck/lint/build 已通过。用户手动 Docker 重建后，curl 再次验证 health、首次成功、幂等复跑、任务明细/收敛、水位、非法配置拒绝、盘中手工执行拒绝、非交易时段跳过及受控失败持久化留痕；真实 `SH.601318 / 5M` 最小外联也已通过。浏览器验收按用户要求跳过，不作为本轮阻塞项。完整交付证据见 `development/MARKET_DATA_EXECUTION_ENGINE_DELIVERY_2026-07-17.md`。
- **P1.4a 精确证券代码验证（2026-07-17 已完成）**：采集计划支持 A/H/US 市场 + 精确代码，通过 LongPort Static Info + Quote 展示名称、统一代码、交易所、币种、每手股数、当前价和报价时间，用户确认后加入 scope；验证不落库。后端 **276 tests**、前端 **270 tests**、Docker 三市场真实 curl 通过。入口：`docs/features/EXACT_SECURITY_VERIFICATION_DESIGN.md`。
- **P1.4b-D1 证券目录与模糊检索后端（2026-07-29 已独立验收）**：V17 扩展既有 `stock_basic` 并新增 `stock_alias`；实现 UTF-8/RFC 4180 CSV 原子幂等导入、本地确定性代码/名称/别名/拼音搜索、证券详情和目录状态，保持 `/stocks` CRUD 兼容且不调用报价/K 线/LongPort 或创建采集任务。冻结候选 `f3ba47597d54abe9a3fe391e7e8c4834fa0c94ae`；后端 **377 tests**、package、50000 securities + 100000 aliases 性能基准均通过（总体 P95 `178.420375ms`）。Docker daemon 不可用，MySQL runtime/curl 为 `NOT_VERIFIED`。D2 前端 selector 和 D3 provider/同步仍未实现。入口：`docs/ai/HANDOFF_2026-07-17_security_directory_search.md`。
- **P1.5 市场板块发现与数据资产（2026-07-18 已完成）**：Longbridge 行业排行/层级/成分改为官方签名 HTTPS，不依赖 4.3.3 缺失 JNI；CN/HK/US 排行及 CN 层级/成分资金最小真实调用通过。V14 新增行业关注、聚合快照、成分快照，API 和前端已覆盖关注、手动采集、启停、删除、历史与成分查看，可保存关联 ETF/指数代码。后端 **284 tests**，前端 typecheck/lint/test 已通过；Docker/curl/浏览器以验收日志最新条目为准。入口：`docs/features/MARKET_SECTOR_CATALOG_DESIGN.md`。
- **多轮交付总览**：2026-07-12 至 2026-07-16 的功能、六轮质量收口、最终门禁和未完成边界见 `development/MULTI_ROUND_DELIVERY_2026-07-16.md`。
- **P1.1 LongPort 单股票手动同步**：后端已实现 `LongPortProperties`、`LongPortMarketDataProvider`、`LongPortQuoteClient`、`ReflectiveLongPortQuoteClient`、Docker/env 透传和单元测试；默认仍安全 disabled。Docker `runtime-libs/` 外部 jar 通道已用 fake SDK jar 实测可加载。**官方 Java SDK artifact 已在 Maven Central 找到并安装（2026-07-12）**：之前查询失败是因为用了官方源码 `java/javasrc/pom.xml` 里错误的 groupId `io.github.longport`（缺 `app` 后缀），正确坐标是 `io.github.longportapp:openapi-sdk:4.3.3`（`<release>=4.3.3`，`versionCount=68`）。`openapi-sdk-4.3.3.jar`（约 35MB）内置全平台 native（linux/osx/windows × 64/arm64），已连同 `gson-2.10.1`、`native-lib-loader-2.4.0` 放入 `runtime-libs/`（gitignored），`inspect-longport-runtime-libs.sh` 对 osx_arm64 与 linux_64 均通过。**真实外联已于 2026-07-12 验证通过**（见下条）。
- **P1.1 真实外联验收通过（2026-07-12）**：SDK 默认域名 `openapi.longport.cn` / `openapi-quote.longport.cn` 已废弃（DNS 解析失败，长桥已更名 Longbridge）。本轮新增 `LONGPORT_HTTP_URL` / `LONGPORT_QUOTE_WEBSOCKET_URL` 可选覆盖（`Config.httpUrl(...)` / `Config.quoteWebsocketUrl(...)` 反射调用），切换到 `https://openapi.longbridge.cn` + `wss://openapi-quote.longbridge.cn/v2`；docker-compose app 服务加 `dns`（默认 223.5.5.5/119.29.29.29）保证容器 native resolver 解析。`verify-longport-real-sync.sh`（SH.600519 / 2026-07-10 单日 / NONE）全绿：provider `configured=true / reachable=true`、latest quote 写入 `stock_quote_snapshot(dataSource=LONGPORT)`、daily bar 写入 `stock_daily_bar(data_source=LONGPORT)`、sync task `SUCCEEDED/inserted=1`。后端 `./mvnw test` 187 tests 通过。凭据通过 `.env.longport`（gitignored）`source` 注入，不进 Git/文档/日志/前端。
- **P1.1 最新验收（2026-07-12）**：latest quote 请求已补齐 HTTP Bean Validation + service 层校验；`./mvnw -q test` 通过 187 tests / 0 failures / 0 errors，`./mvnw -q -DskipTests package` 通过，两个 LongPort 验收脚本 `bash -n` 通过，`git diff --check` 通过。
- **P1.1 前端联调防呆（2026-07-12）**：前端行情页已补齐 canonical symbol 格式校验、latest quote 单次 500 个上限、同步日期起止校验、HF 禁用、点击拉取/同步前 provider status 预检查与状态提示。前端 `npm run typecheck` / `lint` / `test`（214 tests）/ `build` 已通过。
- **P1.1 SDK 源码构建备选路径（2026-07-12）**：新增 `scripts/build-longport-java-sdk-from-source.sh`，在 Maven artifact 继续不可用时，可从官方 `longportapp/openapi` tag 构建当前平台或 `QTA_LONGPORT_RUST_TARGET` 指定平台的 JNI + Java jar，并复制 SDK jar/runtime deps 到 `runtime-libs/`。该脚本未执行真实构建，仅通过 `bash -n`。
- **P1.1 SDK 离线检查（2026-07-12）**：新增 `scripts/inspect-longport-runtime-libs.sh`，用于在真实外联前离线检查 `runtime-libs/` 中 SDK jar、目标平台 native、`gson`、`native-lib-loader` 是否齐全；当前空 `runtime-libs` 下会明确提示需先构建/下载 SDK。
- **P1.1 官方 SDK 合约检查（2026-07-12）**：新增 `scripts/check-longport-official-java-contract.sh` 和 `docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md`，用于核对 `ReflectiveLongPortQuoteClient` 依赖的官方类、方法、getter、枚举常量。`v4.3.3` 源码缓存检查通过；当前在线 raw GitHub 检查受代理/DNS 影响不可达。
- **P1.1 真实外联预检（2026-07-12）**：新增 `scripts/check-longport-readiness.sh`，在真实外联前集中检查只读凭据是否存在、`QTA_LONGPORT_ENABLED`、`runtime-libs` SDK/native/dependency 结构、可选官方源码合约和可选 provider status。该脚本不会打印 LongPort 密钥；`scripts/verify-longport-real-sync.sh` 也已支持 `QTA_VERIFY_RUNTIME_LIB_INSPECTION=auto|true|false`。
- **P1.1 暂停归档（2026-07-12）**：当前暂停点与 ZCode 接手清单已沉淀到 `docs/ai/HANDOFF_2026-07-12_longport_zcode_resume.md`；完整可复制 ZCode 提示词在 `docs/prompts/ZCODE_LONGPORT_RESUME_PROMPT_2026-07-12.md`。后续接手优先读这两个文件，避免重放长聊天。
- **v0.1.1 验收**：后端 121 测试通过、前端 179 测试通过、Docker 冷构建 + curl 端到端 + 浏览器（4 页面控制台 `DEPRECATED_WARNINGS=0`）全绿。详见 `acceptance/ACCEPTANCE_LOG.md`。
- **2026-07-11 轻量接手（历史）**：上一轮 Claude/GLM 在修复 `SyncScopeLockMapper.xml` MySQL 兼容问题时触发 5 小时用量上限，历史说明保留在 `docs/ai/ZCODE_HANDOFF_2026-07-11.md`。当前新会话优先读本文件和 `docs/development/DEVELOPMENT_LOG.md` 最新条目，不再按 2026-07-11 历史任务接手。

## 下一阶段

P1.0 证券主数据和 CSV 日 K 基础已由 `marketdata` 模块实现（V5/V6）。P1.1 LongPort provider facade + V7-V9 migration + 9 API + 6 Tab 前端已实现；后端反射式 SDK adapter 已实现。**P1.1 单股票手动同步真实外联已于 2026-07-12 全流程验收通过**（SDK 安装 + 域名覆盖 + 凭据 + 单 symbol 落库）。

P1.2 行情采集执行引擎和 P1.6 板块双层自动采集已完成代码与自动化门禁。P1.10-A 前后端候选也已完成自动化与 mock 浏览器验证。后续 P1 主线：

1. 用 Docker/MySQL 执行 V19-V22，在真实 CLOSE 排行样本上做 readiness/calculation/radar/history/detail curl 和 remote 浏览器验收。
2. 后端继续补真实资金流、成交集中度、量价确认和异动提醒；完成后再进入 P1.10-B 候选扫描。
4. P1.8 OpenClaw 只读助手代码已完成，服务器真实 QQ 与公网阻断仍需单独验收；未来可白名单暴露市场研究摘要，不直接开放重算写端点。
5. 港股、美股长窗口依赖权威交易日历；不得用周末猜测替代。数据资产稳定后再推进策略和回测，信号必须经过风险模块。

## 接手顺序（新会话）

1. 启用 skill `qta-context-bootstrap`（规范源 `.agents/skills/`，分阶段加载上下文）。
2. `AGENTS.md` → `CLAUDE.md` → `docs/AI_DEVELOPMENT_INDEX.md` → 本文件。
3. 按任务类型路由（`AI_DEVELOPMENT_INDEX.md §4`）只读必要文档；Historical 文档（§6）不必读。

## 开发完成定义

- 后端 `./mvnw test` + `./mvnw package` 通过；前端 typecheck / lint / test / build 通过。
- 新增 DB 结构只通过更高版本 Flyway migration；MyBatis SQL 在 XML；分层清晰。
- 开发结束按 `docs/DEVELOPMENT_WORKFLOW.md §2` 执行文档同步检查。
- 未经用户明确要求，**不自动 commit / push / 部署远程**。
