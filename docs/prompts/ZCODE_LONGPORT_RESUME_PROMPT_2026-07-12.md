# ZCode LongPort Resume Prompt 2026-07-12

> 用途：用户后续新开 ZCode 对话时，直接复制本文件主体内容发给 ZCode，让它在不了解本项目历史的情况下接手 LongPort 单股票手动同步开发。  
> 注意：本提示词要求渐进式读取上下文，不允许加载全量聊天记录或全量 `docs/`。

> **历史状态（2026-07-12 归档）**：本提示词记录的是"真实外联前准备完成"阶段。此后该阶段已全部落地：官方 Java SDK `io.github.longportapp:openapi-sdk:4.3.3` 已装入 `runtime-libs/`（vendor jar 被 gitignore），真实单 symbol 外联已验收通过（latest quote + daily bar 落 `dataSource=LONGPORT`）。部署必须配 `LONGPORT_HTTP_URL=https://openapi.longbridge.cn` + `LONGPORT_QUOTE_WEBSOCKET_URL=wss://openapi-quote.longbridge.cn/v2`（SDK 默认域名已废弃）。后续新会话请直接读 `docs/AI_HANDOFF.md` 与 `docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md` 获取当前事实，本提示词仅作历史归档。下面 ```text 块为原始提示词，不再代表当前未完成状态。

```text
你是接手 Quant Trading Assistant 项目的资深全栈研发工程师，当前只接手 LongPort / 长桥只读行情接入这条任务线。

一、项目背景

项目名：Quant Trading Assistant
定位：个人交易辅助系统，不是自动交易系统。
核心功能：自选股、交易计划、交易记录、交易账本、持仓快照、复盘、风控、工作台、行情数据。
重要边界：
1. 不自动下单。
2. 不连接真实券商交易账户。
3. 不保存券商密码、交易密码或真实交易 API Key 到 Git / 文档 / 日志 / 前端代码。
4. LongPort 只允许作为 quote-only 只读行情源，不接交易、订单、账户、持仓能力。
5. 不承诺收益，不输出无风险建议。

二、仓库与技术栈

后端 + 文档仓库：
/Users/joker/code/quant-trading-assistant

前端仓库：
/Users/joker/code/quant-trading-assistant-web

后端技术栈：
- Java 17
- Spring Boot 3.5
- Maven Wrapper
- MyBatis + XML Mapper
- MapStruct
- Flyway
- MySQL 8.4
- H2 for tests
- Docker Compose

前端技术栈：
- React 19
- Vite
- TypeScript
- Ant Design 6
- mock/remote 双模式

当前分支：main
当前改动未 commit / 未 push，除非用户明确要求，否则不要提交或推送。

三、本轮任务状态

当前 LongPort 单股票手动同步任务已经推进到“真实外联前准备完成”阶段。

已完成：
1. 后端 LongPortProperties 配置绑定。
2. 后端 LongPortMarketDataProvider。
3. 后端 LongPortQuoteClient / ReflectiveLongPortQuoteClient。
4. 默认 disabled 安全兜底；QTA_LONGPORT_ENABLED=true 后切换 LongPort provider。
5. Docker runtime-libs 外部 jar classpath 通道。
6. fake SDK runtime-libs 验证脚本。
7. 真实 LongPort 单 symbol 验收脚本。
8. SDK 源码构建备选脚本。
9. SDK runtime-libs 离线检查脚本。
10. 官方 Java SDK 合约检查脚本与文档。
11. 真实外联前 readiness 预检脚本。
12. latest quote HTTP Bean Validation + service 层入参校验。
13. 前端 /market-data 页 symbol / 日期 / HF / provider status 防呆。
14. 文档、验收日志、AI handoff 已同步。

未完成：
1. 真实官方 LongPort Java SDK jar/native libs 尚未安装到 runtime-libs。
2. LongPort 只读凭据尚未配置。
3. 尚未执行真实 LongPort 外联小调用。
4. 建设看板 LongPort 节点尚未根据真实外联结果更新。

当前进度判断：
代码与流程准备约 85% 完成；剩余约 15% 是 SDK 产物安装、真实凭据配置、单 symbol 小调用验收、建设看板状态更新。

四、必须先读的文件

不要读取全量 docs，不要读取历史聊天日志，不要读取 Claude JSONL。

请按顺序读取：
1. /Users/joker/code/quant-trading-assistant/AGENTS.md
2. /Users/joker/code/quant-trading-assistant/docs/AI_DEVELOPMENT_INDEX.md
3. /Users/joker/code/quant-trading-assistant/docs/AI_HANDOFF.md
4. /Users/joker/code/quant-trading-assistant/docs/ai/PROGRESSIVE_DISCLOSURE_PROTOCOL.md
5. /Users/joker/code/quant-trading-assistant/docs/ai/HANDOFF_2026-07-12_longport_zcode_resume.md
6. /Users/joker/code/quant-trading-assistant/docs/ai/HANDOFF_2026-07-11_longport_single_symbol_engine.md
7. /Users/joker/code/quant-trading-assistant/docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md
8. /Users/joker/code/quant-trading-assistant/docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md
9. /Users/joker/code/quant-trading-assistant/docs/api/MARKET_DATA_API.md

如果要改前端，再读取：
/Users/joker/code/quant-trading-assistant-web/src/pages/market-data.tsx

五、当前需要你做的事情

先不要急着开发新功能。你的第一轮任务是“接手验收 + 判断下一步”：

1. 检查当前未提交改动范围：
   - 后端应该主要集中在 marketdata / LongPort / scripts / docs。
   - 前端应该只有 /Users/joker/code/quant-trading-assistant-web/src/pages/market-data.tsx。
2. 确认没有误接入 LongPort 交易、订单、账户、持仓能力。
3. 确认没有真实密钥进入 Git、文档、日志、前端代码。
4. 如果当前还没有官方 SDK jar/native libs 和真实只读凭据，只做静态验收，不要伪造真实行情验收。
5. 如果用户已经准备好官方 SDK jar/native libs 和只读凭据，再做真实外联验收。

六、低成本静态验收命令

在后端仓库执行：

cd /Users/joker/code/quant-trading-assistant

bash -n scripts/build-longport-java-sdk-from-source.sh scripts/check-longport-official-java-contract.sh scripts/check-longport-readiness.sh scripts/inspect-longport-runtime-libs.sh scripts/verify-longport-real-sync.sh scripts/verify-longport-runtime-libs.sh

git diff --check

git -C /Users/joker/code/quant-trading-assistant-web diff --check

如果只是接手确认，先不要跑 Docker，不要跑全量 Maven/npm，除非你判断必须。

七、真实外联前流程

只有在用户明确说明已经准备好官方 SDK jar/native libs 和 LongPort 只读凭据后，才执行真实外联流程。

1. 将官方 SDK jar、目标平台 native、gson、native-lib-loader 放入：
   /Users/joker/code/quant-trading-assistant/runtime-libs/

2. 配置 .env 或 shell env：
   QTA_LONGPORT_ENABLED=true
   LONGPORT_APP_KEY=***
   LONGPORT_APP_SECRET=***
   LONGPORT_ACCESS_TOKEN=***

3. 先跑预检：
   cd /Users/joker/code/quant-trading-assistant
   scripts/check-longport-readiness.sh

4. 预检通过后跑真实最小验收：
   scripts/verify-longport-real-sync.sh

真实调用限制：
1. 只允许一个 A 股 symbol。
2. 只允许小日期范围。
3. 不做全量扫描。
4. 不打印密钥。
5. 不把密钥写进文档、日志、前端代码。

八、如果要继续开发，优先级

优先级 1：确认 SDK 安装路径是否可用。
- 如果 Maven Central artifact 可用，优先用 Maven / runtime-libs 的稳定路径。
- 如果 Maven Central 仍不可用，可评估执行 scripts/build-longport-java-sdk-from-source.sh。

优先级 2：真实外联小调用。
- provider status 必须 configured=true / reachable=true。
- latest quote 必须能返回 LONGPORT 数据源。
- persist=true 时必须能在 quote-snapshots 查询到。
- daily bar sync 必须能写入 stock_daily_bar(data_source=LONGPORT)。

优先级 3：真实验收通过后更新建设状态。
- 更新 BUILD_CHECKLIST.md。
- 更新 docs/AI_HANDOFF.md。
- 更新 docs/development/DEVELOPMENT_LOG.md。
- 更新 docs/acceptance/ACCEPTANCE_LOG.md。
- 如前端建设看板有对应节点，再更新看板状态。

九、禁止事项

1. 不要读取全量 docs。
2. 不要读取长聊天记录或 Claude JSONL。
3. 不要接 LongPort 交易、订单、账户、真实持仓能力。
4. 不要把真实 LongPort 密钥写入 Git、文档、日志、前端代码。
5. 不要伪造真实行情验收。
6. SDK/凭据缺失时，不要把 LongPort 建设看板标为 DONE。
7. 不要无限循环跑 Docker / Maven / npm。
8. 失败后一轮直接相关修复，仍失败就写清楚阻塞原因。
9. 不要修改历史 migration。
10. 不要 commit/push，除非用户明确要求。

十、你最终要输出

接手验收后，请输出：
1. 当前任务是否可以继续。
2. 已完成项。
3. 未完成项。
4. 当前是否缺 SDK / 凭据 / 运行环境。
5. 你执行过哪些命令，结果如何。
6. 下一步建议。
7. 如果你修改了代码或文档，列出文件清单。

如果无法继续，请不要硬跑，请输出明确阻塞原因和用户需要提供什么。
```

