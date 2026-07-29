# ZCode Goal Prompt: OpenClaw 远程只读助手

> 用法：在 ZCode 打开后端仓库 `/Users/joker/code/quant-trading-assistant`。先单独执行下面的 `/goal` 命令，再发送“执行提示词”全文。建议启用 Full Access 或 Auto Edit，避免非高风险文件审批打断；不要授权真实密钥、远程部署、commit 或 push。

## 第一步：Goal 命令

```text
/goal 在不依赖真实 Longbridge 密钥、服务器权限或 QQ 凭据的前提下，完整交付 QTA OpenClaw 远程只读助手第一期：实现安全的 Agent Facade、独立 OpenAPI、鉴权限流审计、8 类只读工具、官方原生 OpenClaw Tool Plugin、自动化测试、部署说明、接口与进度文档；所有本地可验证门禁通过，外部部署验收如实列为待办，禁止写操作、自动交易和伪造验收。
```

## 第二步：执行提示词

```text
你是本轮 Goal 的总负责人，兼任资深 Java 架构师、Spring Security 工程师、OpenAPI 契约工程师、OpenClaw Tool Plugin 工程师、测试负责人和技术文档负责人。请自主制定计划并持续执行，直到 Goal 的本地可验证完成条件全部满足。

【工作区】
- 后端、文档、OpenClaw 集成包：/Users/joker/code/quant-trading-assistant
- 前端：/Users/joker/code/quant-trading-assistant-web
- 后端基线参考：76423df
- 前端基线参考：4898a88

【自主执行规则】
1. 不要向我询问方案选择、是否继续、是否运行测试或如何命名。遇到多个合理方案时，选择设计文档推荐且风险最低的方案并记录决策。
2. 只有真实密钥、生产服务器权限、QQ OpenID 等无法从仓库安全获得的外部输入可以列为“部署验收待办”；不得因此停止本地代码、测试、插件和文档工作。
3. 不读取、不打印、不修改真实 .env、.env.longport、Token、QQ OpenID 或服务器私密配置。
4. 不自动 commit、push、部署远程，不执行破坏性 Git 命令，不覆盖已有用户改动。
5. 不无限循环。一个失败只做一轮直接相关修复和一次重跑；仍失败则保留准确证据，继续完成不受影响的工作，最终写 blocker。
6. 可以使用少量子代理做相互独立的安全审查、OpenAPI审查和测试审查；主代理负责代码实现和最终收敛。不要让多个代理修改同一文件，不要读取全量 docs。

【上下文加载】
先启用 qta-context-bootstrap 和 qta-openclaw-integration，输出 Task Context Manifest，然后仅按需读取：
- AGENTS.md
- CLAUDE.md
- docs/AI_DEVELOPMENT_INDEX.md
- docs/AI_HANDOFF.md
- docs/ai/PROGRESSIVE_DISCLOSURE_PROTOCOL.md
- docs/features/OPENCLAW_AGENT_ASSISTANT_DESIGN.md
- docs/decisions/ADR-0011-openclaw-agent-facade-and-tool-boundary.md
- docs/development/OPENCLAW_AGENT_ASSISTANT_IMPLEMENTATION_PLAN.md
- docs/ai/HANDOFF_2026-07-26_openclaw_agent_assistant.md
- 受影响的现有 Controller/Service/测试和配置

禁止一次性加载整个 docs、历史 prompts、Historical handoff、target、dist、node_modules、长日志和会话记录。

【第一期产品边界】
- 目标：通过 QQ 上的 OpenClaw 查询 QTA 系统健康、今日待办、持仓摘要、行情采集、失败任务、数据质量、板块排行和单证券行情摘要。
- 第一阶段只读。所有 /api/v1/agent 业务接口只能是 GET。
- 禁止写工具、删除、任意 SQL、Shell、curl、Docker、文件系统、通用 HTTP、创建/修改计划、重试任务、券商账户、订单、下单、撤单和真实持仓。
- 不把全量 Swagger 或现有全部 Controller 暴露给模型。
- 不输出买卖建议；行情与持仓结果带“不构成投资建议”。

【后端必做】
1. 新建 com.quant.trade.agent 模块，遵循 controller/service/manager/dao/model/dto/vo/convert/constant/enums 分层。
2. Controller 只调用 Agent Service；Agent Service 复用现有 Dashboard、Portfolio、MarketData 等 Service。禁止直接访问现有业务 Mapper，禁止复制 FIFO、板块或行情计算逻辑。
3. 增加 Spring Security 和兼容 Spring Boot 3.5 的 springdoc webmvc API starter；只生成 JSON，不引入 Swagger UI。
4. QTA_AGENT_ENABLED 默认 false。Agent Token 只来自环境变量或 SecretRef；启用时校验强度；使用常量时间比较；不得出现在 Git、日志、OpenAPI 示例、DB或错误响应。
5. 安全链只保护 /api/v1/agent/** 和 Agent OpenAPI 路径，现有前端普通 API 保持兼容。
6. 实现每 Client/Key 限流，默认 60 次/分钟；Provider 外联查询设置更低限额；超限返回 HTTP 429 和规范错误码。
7. 增加 requestId、统一 Agent 错误语义与脱敏审计。
8. 新增下一号 Flyway migration 和 MyBatis XML 保存 agent_api_audit_log；不得修改 V1-V15。审计成功和失败，禁止保存 Authorization、完整请求/响应、异常栈或完整持仓明细。
9. 使用 ErrorCodeEnum、constant、MapStruct 和中文 Javadoc，遵守项目现有 Alibaba Java 风格。

【只读能力必做】
对外提供并为 OpenClaw 映射以下 8 类工具；路径可按设计文件最终统一，但 operationId 必须固定、唯一、语义清晰：
- qta_system_health
- qta_today_overview
- qta_portfolio_summary
- qta_collection_overview
- qta_collection_failures
- qta_data_quality_alerts
- qta_sector_ranking_summary
- qta_security_market_summary

另提供 capabilities 查询。

每个业务结果必须包含：
- conclusion
- generatedAt
- dataAsOf
- freshnessStatus: FRESH/DELAYED/STALE/UNKNOWN
- evidence
- warnings
- data

必须区分：
- 尚未采集
- 确实无结果
- 查询失败
- Provider 不可用
- 数据过期

持仓摘要说明价格来源和时间；板块摘要说明市场、批次和数据时间；默认 limit=10、最大50，禁止无限明细。

【OpenAPI 必做】
1. 建 agent-v1 独立分组，只包含 /api/v1/agent/**。
2. Agent OpenAPI 只允许 GET，包含 Bearer security scheme、参数约束、统一响应和错误响应。
3. operationId 唯一；不得出现 Token 字段、Longbridge 凭据、内部异常栈和非 Agent 路径。
4. 保存供插件和契约测试使用的 qta-agent-openapi.json 快照。
5. 实现后新增 docs/api/AGENT_ASSISTANT_API.md 并登记 docs/api/API_INDEX.md；实现前的草案不冒充已实现事实。

【OpenClaw Tool Plugin 必做】
在 integrations/openclaw/qta-assistant/ 建符合当前官方 OpenClaw 原生插件规范的独立 TypeScript 包：
- openclaw.plugin.json
- package.json / tsconfig
- src/client、schemas、tools、formatter、policy
- skills/qta-assistant/SKILL.md
- references/capabilities.md、market-data.md、troubleshooting.md
- openapi/qta-agent-openapi.json
- tests

实现前只需核对当前官方文档：
- https://docs.openclaw.ai/plugins/tool-plugins
- https://docs.openclaw.ai/plugins/manifest
- https://docs.openclaw.ai/plugins/plugin-permission-requests

要求：
1. 使用当前官方 `defineToolPlugin` + TypeBox 静态声明固定只读工具，不动态导入全量 Swagger；使用 TypeScript ESM，`typebox` 放在 dependencies，`openclaw` peer dependency 满足官方 Tool Plugin SDK 最低版本。
2. 插件配置声明 baseUrl、Token SecretRef/env 引用、超时和 allowlist；manifest 不保存真实值。
3. QQ 私聊按不可变 OpenID allowlist，群聊默认关闭，并为 toolsBySender 提供配置示例。
4. 连接超时2秒、总超时10秒；GET 仅对超时/502/503最多重试一次；4xx不重试；连续失败短时熔断。
5. 参数校验、结果裁剪、错误翻译和脱敏完整；默认10条、最大50条。
6. Skill 仅说明何时调用哪个工具，业务事实从 API 获取；常规查询不加载完整 OpenAPI、数据库设计和开发日志。
7. 提供 mock QTA 服务或等价测试夹具，不依赖真实服务器、QQ和密钥。

如果本机 OpenClaw CLI 不存在，仍必须完成可构建、可测试、符合官方当前 manifest/contracts 的插件包；把真实 install/enable/gateway restart 写入部署手册，不以 CLI 缺失中断 Goal。

【网络与部署文档必做】
- docker-compose 生产示例将后端绑定回环或说明 Docker 私网方案；不要破坏本地开发。
- 提供 Nginx deny 示例，公网阻断 /api/v1/agent/**、/v3/api-docs/**、Swagger UI、非必要 Actuator。
- 提供 Agent Token 生成、SecretRef/env 注入、轮换和撤销说明，不含真实值。
- 提供 OpenClaw 插件 build/validate/install/enable、QQ OpenID allowlist、toolsBySender 和回滚步骤。
- 明确服务器和 QQ 真实 E2E 是部署验收，不得伪造完成。

【测试与独立验收】
后端必须覆盖：
- Agent disabled、无Token、空Token、错误Token、正确Token。
- readonly 边界、限流429、requestId。
- 审计成功/失败、字段脱敏、Token不落库。
- 空数据、旧数据、Provider不可用、limit边界。
- OpenAPI仅Agent GET、operationId唯一、Bearer存在、无敏感Schema。
- Flyway H2/MySQL兼容和现有普通API回归。

插件必须覆盖：
- manifest/schema有效。
- 工具参数校验和最大limit。
- 正常、空、401、403、429、500、超时、502/503一次重试。
- Token与敏感响应不进入格式化输出。
- mock后端下工具调用闭环。

最终执行：
后端：
- git diff --check
- ./mvnw test
- ./mvnw package

插件：
- npm test
- npm run plugin:build
- npm run plugin:validate

前端：
- npm run typecheck
- npm run lint
- npm run test
- npm run build

可以使用 Fake Provider 做 Docker 验证；不得调用真实 Longbridge。Docker不可用不阻塞纯代码门禁，但必须记录未执行原因。不要为了测试读取用户真实配置。

【文档与进度同步】
按 docs/DEVELOPMENT_WORKFLOW.md §2 完成：
- docs/api/AGENT_ASSISTANT_API.md + docs/api/API_INDEX.md
- docs/DATABASE_DESIGN.md
- docs/CURRENT_ARCHITECTURE_AND_MODULES.md
- docs/BUILD_CHECKLIST.md
- docs/PRODUCT_BLUEPRINT.md
- docs/AI_HANDOFF.md
- docs/development/DEVELOPMENT_LOG.md
- docs/acceptance/ACCEPTANCE_LOG.md
- docs/ai/HANDOFF_2026-07-26_openclaw_agent_assistant.md
- .env.example / 部署文档
- 前端 buildStatusData.ts 和 buildStatusData.test.ts

建设看板只可标“代码交付完成、待部署验收”；公网阻断、真实QQ和真实Provider未验证时不得标生产完成。顺便把 AI_HANDOFF 中 P1.6 的293 tests漂移修正为验收日志实际299 tests。

【完成判定】
只有以下全部满足才结束 Goal：
1. 后端Agent API、安全、限流、审计和独立OpenAPI实现完整。
2. 8类只读OpenClaw工具和渐进式Skill实现完整。
3. 本地可运行的全部测试、build、package、validate通过。
4. 无真实密钥、无危险写工具、无全量Swagger暴露、无业务越层。
5. API、DB、架构、产品、验收、handoff和前端建设看板与代码一致。
6. 输出最终交付报告：变更文件、功能、测试结果、未执行的外部验收、精确部署步骤和当前git状态。

任何外部条件缺失只允许形成“部署验收待办”，不能成为停止本地开发的理由。不要问我问题，自主收敛。
```
