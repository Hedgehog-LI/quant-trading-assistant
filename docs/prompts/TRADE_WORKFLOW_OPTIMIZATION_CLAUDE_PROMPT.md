# Claude Code Prompt - v0.1.1 Trade Workflow Optimization

> ⚠️ Historical（历史参考，非当前执行入口）。当前事实以 AI_HANDOFF.md + development/DEVELOPMENT_LOG.md + CURRENT_ARCHITECTURE_AND_MODULES.md 为准；新会话入口见 AI_DEVELOPMENT_INDEX.md。

将下面代码块中的全部内容发送给已经开启 Agent Teams 和 `--dangerously-skip-permissions` 的 Claude Code。

```text
你现在是 Quant Trading Assistant v0.1.1 的研发负责人和 Agent Team Lead。请直接组织团队完成“基础交易闭环优化”的设计核对、前后端开发、联调、自测、产品验收和文档同步，不要只输出计划或分析后停止。

后端仓库：
/Users/joker/code/quant-trading-assistant

前端仓库：
/Users/joker/code/quant-trading-assistant-web

一、工作方式

1. 创建 Agent Team，并维护共享任务清单和依赖关系。
2. 建议角色：
   - Team Lead / Architect：读取全局文档、拆任务、确定接口契约、协调依赖、最后总验收。
   - Backend Engineer：独占后端 Java、resources、后端测试和相关 API 文档的修改权。
   - Frontend Engineer：独占前端 src、前端测试和前端 README 的修改权。
   - QA / Product Reviewer：只做独立审查、运行验收、整理问题，不与研发成员同时修改同一文件。
3. Team Lead 负责共享项目文档和最终修正，避免多个 teammate 同时编辑同一文件。
4. teammate 必须读取项目 CLAUDE.md、相关文档和现有代码；teammate 不会继承主会话历史，分派任务时要带完整路径、范围、接口契约和验收条件。
5. 先做简洁执行计划和接口契约核对，然后立即开发。普通技术判断自行决定，不要反复向用户确认。
6. 不要因一个检查失败就停止。定位、修复、重跑，直到所有可执行质量门禁通过。
7. 不要覆盖或回滚用户已有改动。先检查两个仓库的 git status，识别本任务开始前的改动并保留。
8. 本轮不要自动 commit、push、部署远程服务器或修改云安全组/Nginx。完成后保持代码可提交状态并汇报。

二、必读文件

所有成员先按职责阅读：

- /Users/joker/code/quant-trading-assistant/AGENTS.md
- /Users/joker/code/quant-trading-assistant/CLAUDE.md
- /Users/joker/code/quant-trading-assistant/docs/AI_DEVELOPMENT_INDEX.md
- /Users/joker/code/quant-trading-assistant/docs/PRODUCT_BLUEPRINT.md
- /Users/joker/code/quant-trading-assistant/docs/BUILD_CHECKLIST.md
- /Users/joker/code/quant-trading-assistant/docs/CURRENT_ARCHITECTURE_AND_MODULES.md
- /Users/joker/code/quant-trading-assistant/docs/features/TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md
- /Users/joker/code/quant-trading-assistant/docs/api/PORTFOLIO_API.md
- /Users/joker/code/quant-trading-assistant/docs/api/POSITION_SNAPSHOT_API.md
- /Users/joker/code/quant-trading-assistant/docs/API_TODAY_MVP.md
- /Users/joker/code/quant-trading-assistant/docs/FRONTEND_ARCHITECTURE.md

按角色继续读取项目 skills：

- 产品/架构：.claude/skills/qta-product-design/SKILL.md
- 后端：.claude/skills/qta-backend-implementation/SKILL.md
- 前端：.claude/skills/qta-frontend-implementation/SKILL.md
- 验收：.claude/skills/qta-quality-acceptance/SKILL.md

如果文档与当前代码冲突，以 migration、测试、BUILD_CHECKLIST 和 CURRENT_ARCHITECTURE_AND_MODULES 的当前事实为准；发现设计无法安全实现时，由 Team Lead 在不扩大产品范围的前提下作最小调整，并在最终报告说明。

三、必须完成的产品范围

严格实现 TRADE_WORKFLOW_OPTIMIZATION_DESIGN.md，包含以下六组能力：

1. 交易计划关联交易记录
   - 交易记录新增/编辑表单提供计划选择器。
   - 优先展示同日计划，排除 CANCELLED 默认候选。
   - 选择计划自动带入 symbol、name、planStopLoss、planTakeProfit、positionRatio。
   - 后端创建和更新时校验 planId 存在、计划未取消、证券代码一致。
   - planId 为空仍允许保存。
   - allowedToTrade=false 的真实交易允许记录，但进入待办提醒。
   - 一个计划允许多笔交易，不自动把计划改成 DONE。
   - 列表或详情展示可读的计划关联信息。

2. 复盘关联一致性
   - 被任意复盘引用的交易记录禁止删除，返回明确 ErrorCodeEnum 业务错误。
   - 新增、编辑、删除复盘后，对受影响 journal ID 重新计算 reviewStatus。
   - 仍被任意复盘引用为 REVIEWED；无任何引用恢复 PENDING。
   - 编辑时覆盖旧关联和新关联的并集。
   - 新增/更新不得关联不存在的 journal。
   - 本轮沿用 linked_journal_ids，不新增高风险迁移；读取时容忍历史无效引用。

3. 两次持仓快照差异比较
   - 新增 GET /api/v1/position-snapshots/comparison?baseSnapshotId=&targetSnapshotId=。
   - 只比较 CONFIRMED，基准时间必须早于目标时间。
   - 支持 NEW、INCREASED、REDUCED、CLOSED、UNCHANGED。
   - 返回总成本、总市值、总浮盈亏、持仓数 delta 和明细 delta。
   - 金额全部 BigDecimal，结果排序稳定，不新增比较结果表。
   - 前端在持仓快照页面提供两个快照选择、比较入口、汇总和差异表。

4. 实际快照与截止时点 FIFO 账本对账
   - 新增 GET /api/v1/position-snapshots/{snapshotId}/reconciliation。
   - 只允许已确认快照。
   - FIFO 只纳入快照时间之前的交易；同日 tradeTime 为空默认纳入并返回 warning。
   - 支持 MATCHED、QUANTITY_MISMATCH、SNAPSHOT_ONLY、LEDGER_ONLY。
   - 以数量作为核心一致性判断，成本差异只展示不直接判错。
   - 对账只读，严禁自动补写或修改交易记录。
   - 前端展示汇总、差异明细、风险说明和空状态。

5. 工作台待办中心
   - remote 模式以 /api/v1/dashboard/today 后端聚合为准，不再由前端分别请求四份数据后形成另一套口径。
   - mock 模式保留纯函数计算并与后端字段保持一致。
   - 至少支持 PENDING_REVIEW、UNLINKED_TRADE_PLAN、TRADE_AGAINST_PLAN、MISSING_STOP_LOSS、STALE_POSITION_SNAPSHOT、POSITION_RECONCILIATION_MISMATCH。
   - 待办包含 code、level、title、description、count、targetPath。
   - 点击后跳转对应页面；不生成买卖建议。
   - 快照过期第一版按 3 个自然日，并在 UI 明确口径。

6. 生产连接防呆
   - 生产默认 remote，同域 apiBaseUrl 留空，走相对路径 /api/v1。
   - 不写死公网 IP。
   - 当当前页面不是 localhost/127.0.0.1 时，禁止保存 localhost、127.0.0.1、::1 后端地址。
   - 设置页展示当前有效 API 请求地址。
   - 增加只读“测试连接”按钮，使用现有只读业务接口验证前端 -> Nginx/Vite proxy -> 后端 -> DB。
   - 区分成功、超时、HTTP 错误和业务错误。
   - 不修改远程 Nginx、Docker 和安全组。

四、明确不做

- 不做 AI、OCR、图片上传或 GLM 接入。
- 不实现 stock_basic、外部行情 provider、日 K、指标、策略或回测。
- 不连接券商，不读取或保存真实券商账号、密码、交易 API Key。
- 不自动下单，不根据对账结果自动修改正式数据。
- 不修改已经发布的 V1-V4 migration。
- 不为了通过测试而删除校验、跳过测试或关闭类型检查。
- 不引入微服务、消息队列或不必要的平台化抽象。

五、后端工程要求

- 遵循现有 controller/service/manager/dao/model/dto/vo/convert 分层。
- Controller 不写业务逻辑，不返回 DO。
- Service 负责事务和跨模块编排；Manager 负责规则、计算、DAO 编排。
- 数据访问沿用 MyBatis Mapper + XML SQL。
- 转换优先使用 MapStruct，不写大段手工 get/set 转换。
- 常量放 common.constant 或模块常量，业务错误使用 ErrorCodeEnum。
- 枚举使用带 code/description 的项目规范，不新增裸 HIGH/MEDIUM/LOW 风格枚举。
- 金额、价格、比例使用 BigDecimal，禁止 double/float。
- 新增公开类、DTO/VO 字段和复杂规则添加有价值的中文注释/Javadoc。
- 对比和对账优先实现为纯计算 Manager，便于单测。
- 任何 DB 结构变化必须新增更高版本 Flyway migration；本设计预计无需新表，不要为了方便新增结果表。

六、前端工程要求

- 沿用 feature-based 架构、Ant Design、现有 shared client 和 mock/remote adapter。
- 页面只负责编排，API、计算、hooks、表格和表单按现有 feature 分离。
- 不使用 any，不堆魔法字符串；状态码有明确 union type/常量映射。
- 核心业务数据在 remote 模式必须走 REST API；localStorage 仅作为 mock/离线演示。
- 不把 API Key 放入前端。
- 所有新增页面状态覆盖 loading、empty、error、success 和 retry。
- 对比、对账和盈亏文案明确“不构成投资建议”。
- A 股盈亏颜色保持盈利红、亏损绿。
- 使用现有图标库；按钮、表格、Drawer/Modal 与当前安静的工作台风格一致。
- 桌面和窄屏不能出现文字遮挡、横向布局失控或操作不可达。
- 修复本轮触达代码中的 Ant Design 弃用属性警告，但不要做全项目无关重构。

七、测试要求

后端至少覆盖：
- planId 为空、计划不存在、已取消、symbol 不一致、合法关联。
- 复盘新增/编辑/删除后的状态回算。
- 被复盘引用的 journal 删除保护。
- 快照比较五种 changeType、delta、排序、非法状态和非法顺序。
- 截止快照时间的交易过滤。
- 四种 reconciliation 状态、空持仓、tradeTime 为空 warning。
- 六种 Dashboard 待办的触发和不触发。

前端至少覆盖：
- 计划候选、选择和自动带入。
- comparison/reconciliation remote adapter 和 mock 计算。
- 对比选择限制和差异视图关键状态。
- 工作台 remote 后端聚合与 mock 口径。
- localhost 防误配、有效 URL 和连接测试状态。
- 页面关键 empty/error/retry 状态。

八、执行与质量门禁

开发过程中持续运行定向测试，全部实现后必须真实执行：

后端：
cd /Users/joker/code/quant-trading-assistant
./mvnw test
./mvnw package

前端：
cd /Users/joker/code/quant-trading-assistant-web
npm run typecheck
npm run lint
npm run test
npm run build

不得只口头声称通过，必须保留真实命令结果。发现失败必须修复并重跑。

九、本地联调

1. 检查端口和现有进程，不要杀死与项目无关的进程。
2. 使用本地 Docker Compose 冷构建后端和 MySQL：
   docker compose up -d --build
3. 等待健康后验证：
   - /actuator/health
   - 受影响的 trade-plans、trade-journals、reviews、position-snapshots、dashboard API
4. 用 curl 构造一套独立的验收数据，完整覆盖：
   - 创建计划
   - 创建关联计划的交易
   - 创建复盘并验证状态
   - 编辑/删除复盘并验证状态回算
   - 创建两个已确认快照
   - 调用 comparison
   - 调用 reconciliation
   - 调用 dashboard/today
5. 联调数据要使用明显的测试标识；能安全清理时清理，不能清理时在报告中列出。
6. 启动前端时用本次进程环境变量指向本地后端，不覆盖用户已有 .env.local；remote 模式验证真实 REST API。
7. 使用可用的浏览器/Playwright 能力验证关键页面。至少检查桌面和窄屏、控制台错误、网络请求、空状态和主要交互。
8. 如果默认端口被占用，选择空闲端口或临时 compose override，不要修改正式部署端口，也不要停止无关服务。
9. 完成后关闭本轮启动的临时 dev server；Docker 是否保留运行需在报告中说明。

十、文档同步

实现完成后由 Team Lead 更新：

- docs/BUILD_CHECKLIST.md：只勾选真实完成并验收的项目。
- docs/CURRENT_ARCHITECTURE_AND_MODULES.md：记录新增 API、计算口径和实现事实。
- docs/API_TODAY_MVP.md
- docs/api/PORTFOLIO_API.md
- docs/api/POSITION_SNAPSHOT_API.md
- docs/PRODUCT_BLUEPRINT.md：仅在实现与设计发生实际变化时更新。
- docs/AI_HANDOFF.md 和 docs/CONVERSATION_HANDOFF.md：更新当前进度。
- 前端 README.md：同源 API、生产 remote、localhost 限制和新增页面能力。
- 前端 src/features/build-status/api/buildStatusData.ts：更新真实成熟度、当前优先级和证据。

不要把未来行情功能写成已完成。不要修改 MARKET_DATA_FOUNDATION_DESIGN.md 的规划状态，除非只是修正文档错误。

十一、最终产品验收

QA / Product Reviewer 必须独立按以下路径验收：

1. 无计划也能记录真实交易。
2. 选择合法计划能自动填充并成功落库。
3. 错误计划关联被后端拒绝且前端提示可理解。
4. 复盘关联、移除关联、删除复盘后的 journal 状态正确。
5. 被复盘引用的交易不能误删。
6. 两次快照能正确区分新增、加仓、减仓、清仓和未变化。
7. 快照与 FIFO 账本数量差异可见且不会自动改数据。
8. 工作台能看到并跳转处理待办。
9. 公网页面语义下 localhost 配置会被阻止，同源空地址可正常请求。
10. 所有旧核心页面和 API 无明显回归。

十二、最终报告格式

全部工作结束后只提交一份由 Team Lead 汇总的最终报告，包含：

1. 完成内容和未完成内容。
2. 两个仓库的实际变更文件。
3. DB migration 情况。
4. 新增/变更 API 契约。
5. 后端 test/package 真实结果。
6. 前端 typecheck/lint/test/build 真实结果。
7. Docker、curl 和浏览器联调证据。
8. QA/Product Reviewer 的发现及修复结果。
9. 遗留风险和明确不在本轮范围的事项。
10. 两个仓库最终 git status，以及是否已达到可提交状态。

不要在仍有失败测试、未修复阻断问题、未完成联调或文档未同步时宣布完成。
```

