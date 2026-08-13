# Quant Trading Assistant - Codex Project Guide


## 新会话上下文加载（必读）

启用项目 skill `qta-context-bootstrap`（分阶段加载，避免一次读全部 docs）。信息真实性优先级与任务路由见 `docs/AI_DEVELOPMENT_INDEX.md` §2/§4；开发结束文档同步见 `docs/DEVELOPMENT_WORKFLOW.md`。

本文件是给 Codex 读取的项目级开发指南。新对话接手时启用 `qta-context-bootstrap` 分阶段加载；Skill 规范源在 `.agents/skills/`，`.claude/skills/` 是 Claude 兼容镜像。入口顺序见 `docs/AI_DEVELOPMENT_INDEX.md`；当前事实见 `docs/AI_HANDOFF.md`。Historical 文档（清单见 `AI_DEVELOPMENT_INDEX §6`）不在主流程，无需读取。

## 项目定位

`quant-trading-assistant` 是一个本地优先的量化交易辅助系统，不是自动交易系统。

系统目标：

- 积累股票行情、指标、信号、复盘和持仓快照数据。
- 支持技术指标计算、规则策略、历史回测、风险提示和交易复盘。
- 帮助用户把短线交易和做 T 过程规则化、数据化、可回测、可复盘。

明确不做：

- 不自动下单。
- 不连接真实券商账户。
- 不保存券商密码、交易密码或真实交易 API Key。
- 不宣传稳赚，不给出无风险收益承诺。

## 当前技术栈

- Java 17
- Spring Boot 3.5.14
- Maven Wrapper
- Spring Web
- MyBatis + XML Mapper
- MapStruct
- Spring Validation
- Spring Boot Actuator
- Flyway
- MySQL 8.4
- H2 for tests
- Docker Compose

## 开发原则

1. 前后端均已存在：后端 Spring Boot 单体 + 前端 React feature-based，按任务类型在对应仓库实现。
2. 先做单体应用，模块边界清晰；成熟后再拆服务。
3. 先做数据积累和可解释规则，不碰自动实盘。
4. 所有策略信号必须经过风险模块。
5. 回测逻辑必须避免未来函数、忽略手续费、忽略滑点、忽略 T+1 等常见错误。
6. 数据库结构使用 Flyway migration 管理。
7. 当前数据库访问使用 MyBatis，SQL 写在 `src/main/resources/mapper/*.xml`。
8. 本地配置使用 `.env`，仓库只提交 `.env.example`。
9. 新增功能必须能通过 `./mvnw test`，至少保证应用上下文可启动。

## 推荐包结构

```text
com.quant.trade
├── common          # 通用异常、响应、时间、分页、枚举
├── config          # Spring 配置、属性绑定
├── data            # 数据采集、数据源适配、行情导入
├── storage         # 数据库存储边界；当前以 MyBatis mapper/xml 为主
├── indicator       # MA/MACD/RSI/BOLL/成交量指标
├── factor          # 因子计算，如趋势、波动率、量价关系
├── strategy        # 策略定义、策略配置、策略插件
├── signal          # 买入/卖出/观望信号生成和持久化
├── risk            # 仓位、止损、回撤、风险预警
├── backtest        # 回测引擎、撮合、手续费、滑点、绩效
├── portfolio       # 持仓、资金、仓位快照
├── review          # 交易日志、复盘记录
├── report          # 报告输出
├── scheduler       # 定时任务
└── web             # REST API controller
```

## 下一阶段优先级

v0.1.0 Today MVP + 交易账本 + 持仓快照、v0.1.1 基础交易闭环优化**均已完成并验收**。下一阶段（P1）按 `docs/features/MARKET_DATA_FOUNDATION_DESIGN.md`：

1. 建 `stock_basic` 与统一证券标识。
2. CSV 日 K 幂等导入。
3. 封装行情 provider，接入一个数据源。
4. 明确手工估值、外部价格快照与日 K 的边界。

后续（P2+）：指标（MA/MACD/RSI/BOLL）、策略信号、简化回测。AI 图片识别暂缓。

## 常用命令

```bash
./mvnw test
./mvnw package
docker compose up -d --build
curl http://localhost:8080/actuator/health
docker compose down
```

## AI 协作要求

- 非简单任务先用 `qta-task-contract` 冻结范围、验收标准和证据；长任务按 `qta-task-checkpoint` 主动存档，禁止等到上下文耗尽才压缩。
- 标准、跨仓或长任务由父上下文启用 `qta-development-orchestration`，也可用 `/qta-run` 显式启动。父协调者负责 L0-L3 风险 lane、TaskPacket、机器控制文件、角色顺序、候选哈希和 Git；子角色不得操作 Git。
- ZCode 项目级 Hook 会被当前客户端安全策略忽略。首次使用或治理脚本更新后运行 `node scripts/install-zcode-governance-user-hooks.mjs --install`，重启 ZCode 并用 `/qta-doctor` 验证；`/qta-run` 的首个门禁必须是 runtime doctor。禁止恢复 Stop Hook。
- 固定 Agent 是模板，不是持续会话。初始实现、每轮 repair、每代 review 和最终 verifier 必须使用新的 role/session；子角色结束即销毁。
- 每次状态迁移运行任务控制校验，候选冻结前运行架构门禁；最终验收必须同时通过 FUNCTIONAL 与 ARCHITECTURE。
- 实现者只可把任务标记为 `SELF_CHECKED`。独立验收必须由未参与实现的干净上下文使用 `qta-independent-verification` 完成，验收者不得同时修代码。
- 只有独立验收允许交付后，才能使用 `qta-delivery-finalization` 更新建设看板、交接、接口和验收记录。
- 固定 ZCode 角色在 `.zcode/agents/`：测试设计者、实施者、代码审查者、最终核验者。各角色禁止递归创建子代理。
- Git 使用任务分支和阶段提交：contract、candidate、repair-N、finalization。checkpoint push 仅作备份；只有独立验收允许交付后才能 delivery push。
- 修改代码前先查看现有结构。
- 不要引入复杂平台化设计，v0.1 保持简单可运行。
- 不要添加真实交易、券商接口或密钥读取功能。
- 新增表必须通过 Flyway migration。
- 新增 DB 访问优先沿用 MyBatis Mapper + XML SQL。
- 新增 REST API 时补充请求/响应示例到文档。
- 涉及策略、信号、风控时必须写明假设和失效场景。
- Skill/Agent 的职责、触发和维护规则以 `docs/ai/SKILL_AND_AGENT_GOVERNANCE.md` 为准。
