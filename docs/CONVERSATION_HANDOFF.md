# Conversation Handoff

这份文档整理了本轮 Codex 对话中的关键背景、决策、当前项目状态和下一步开发方向。新的 Codex / Claude Code 对话接手时，建议先读本文件，再读 `AGENTS.md`、`docs/AI_HANDOFF.md` 和 `docs/ARCHITECTURE.md`。

## 一句话结论

当前路线是：先做一个 Java Spring Boot 后端 + 独立 React 前端的本地交易辅助系统，帮助用户记录数据、计算指标、生成辅助信号、做回测、管风险、写复盘；不自动下单，不连接券商账户。

## 用户背景

- 用户是 Java 开发者。
- 用户是金融和量化交易学习者。
- 用户希望学习股票投资、短线交易、做 T、看公司、看股票、量化投资。
- 当前痛点是买点、卖点、止损点、仓位规则不清晰。
- 用户希望下周开始一边炒股一边记录，所以需要尽快跑出可用雏形。
- 用户倾向用 AI 辅助开发前端和后端。

## 最重要的安全边界

本项目只做交易辅助，不做自动交易。

禁止事项：

- 不自动下单。
- 不连接真实券商账户。
- 不读取、保存或要求任何真实 API Key、券商密码、交易密码。
- 不输出稳赚、必涨、无风险收益等结论。
- 所有买入/卖出提示都必须表达为辅助信号，并配套风险提示和人工确认。

## 项目路径和仓库

后端项目路径：

```text
/Users/joker/code/quant-trading-assistant
```

GitHub 仓库：

```text
https://github.com/Hedgehog-LI/quant-trading-assistant.git
```

当前本地状态：

```text
main...origin/main [ahead 2]
```

含义：本地已经有 2 个文档提交还没 push 到 GitHub。用户可以在 IDEA 中执行普通 Push。

## 当前项目技术栈

后端：

- Java 17
- Spring Boot 3.5.14
- Maven Wrapper
- Spring Web
- Spring Data JPA
- Spring Validation
- Spring Boot Actuator
- Flyway
- MySQL 8.4
- H2 for tests
- Docker Compose

前端设计：

- 独立项目：`/Users/joker/code/quant-trading-assistant-web`
- Vite
- React
- TypeScript
- Ant Design
- React Router
- TanStack Query
- Zustand
- ECharts
- Axios
- dayjs

## 已完成的本地初始化

后端项目已经完成：

- Spring Boot 项目初始化。
- Java 版本修正为 17。
- MySQL 8.4 作为数据库。
- Flyway 接入。
- Docker Compose 接入 app + mysql。
- `.env.example` 添加。
- `.dockerignore` 添加。
- README 添加。
- 基础健康检查通过过：`/actuator/health`。
- 本地 initial commit 已推送到 GitHub。

额外文档已经添加到本地：

- `AGENTS.md`
- `CLAUDE.md`
- `docs/AI_HANDOFF.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPMENT_ROADMAP.md`
- `docs/DATABASE_DESIGN.md`
- `docs/STRATEGY_PLUGIN_DESIGN.md`
- `docs/BACKTEST_ENGINE_DESIGN.md`
- `docs/RISK_RULES.md`
- `docs/FRONTEND_ARCHITECTURE.md`
- `docs/prompts/CODEX_CLAUDE_PROMPTS.md`

## 本轮关键决策

### 1. 新系统以 Java 后端为主

虽然最早研究过一个 Python 量化开源项目，但用户明确纠正：这个仓库只是学习参考，新系统要自己开发，后端更倾向 Java。

最终决策：

```text
Java Spring Boot 后端为主
Python 量化库后续可作为外部服务或工具通过 REST/CLI 对接
```

### 2. 不一开始做自动交易

原因：

- 用户当前目标是学习和辅助决策。
- 自动交易会引入券商账户、密钥、安全和真实资金风险。
- 新手更需要先建立可复盘、可风控的交易流程。

最终决策：

```text
先做数据记录、指标、信号、回测、风控、复盘
不做自动下单
```

### 3. 数据库选 MySQL 8.4

用户曾询问是否应该用 MySQL 5.7，因为公司环境使用 5.7。

结论：

- 5.7 已停止维护，不适合作为新项目默认版本。
- 8.4 是 LTS，更适合作为新系统的默认版本。
- 代码和 SQL 尽量避免使用过于新的特性，降低未来兼容成本。

### 4. 先用 Java 17

用户担心后续切 Java 21 是否有兼容风险。

结论：

- Spring Boot 3.5.x + Java 17 是稳妥组合。
- 后续从 Java 17 升到 21 通常比从 8 升到 17 简单。
- 当前项目先锁定 Java 17，不追新。

### 5. 后端和前端分离

当前后端项目只放 Java 后端代码。

前端建议单独项目：

```text
/Users/joker/code/quant-trading-assistant      # 后端
/Users/joker/code/quant-trading-assistant-web  # 前端
```

前端通过 REST API 调用后端。

### 6. 前端今天要先跑雏形

因为用户希望下周开始边炒股边记录，所以前端第一版可以先使用 localStorage/mock 数据，不必等待后端所有 API 完成。

今天最小前端范围：

- Dashboard 今日工作台
- Watchlist 自选股
- TradeJournal 交易记录
- Review 盘后复盘
- Settings 配置页

验收：

- `npm run dev` 能跑。
- 能新增自选股。
- 能新增交易记录。
- 能新增复盘。
- 刷新后 localStorage 数据不丢。

## 推荐系统整体架构

```text
Browser
  -> quant-trading-assistant-web
  -> REST API
  -> quant-trading-assistant Spring Boot
  -> MySQL
```

后续可选扩展：

```text
Spring Boot backend
  -> REST/CLI
  -> Python quant engines
```

Python 引擎可以接：

- backtesting.py
- Backtrader
- vectorbt
- AKShare

但 v0.1 不要急着集成。

## 后端模块规划

推荐包结构：

```text
com.quant.trade
├── common
├── config
├── data
├── storage
├── indicator
├── factor
├── strategy
├── signal
├── risk
├── backtest
├── portfolio
├── review
├── report
├── scheduler
└── web
```

v0.1 最小闭环：

```text
自选股
-> 日 K 数据导入
-> 指标计算
-> 策略信号
-> 简单回测
-> 风险提示
-> 交易复盘
```

## 前端模块规划

推荐页面：

```text
Dashboard
Watchlist
StockDetail
DataImport
Signals
Backtest
RiskAlerts
TradeJournal
Review
Settings
```

今天优先实现：

```text
Dashboard
Watchlist
TradeJournal
Review
Settings
```

## 下周实际使用流程

盘前：

1. 打开 Dashboard。
2. 查看自选股。
3. 给关注股票写计划：支撑位、压力位、止损位、仓位计划。

盘中：

1. 不依赖系统自动下单。
2. 手动交易后，在 TradeJournal 记录理由。
3. 临时观察写到复盘草稿或个股备注。

盘后：

1. 补完整交易结果。
2. 给每笔交易打错误标签。
3. 对比系统信号和真实操作。
4. 修改下一次交易规则。

## 推荐下一步

### 立即要做

1. 用户在 IDEA 中 push 当前后端仓库文档提交。
2. 新开 Codex 项目读取本仓库。
3. 创建前端项目 `quant-trading-assistant-web`。
4. 先做 localStorage 版前端雏形。

### 后端下一步

1. 创建核心数据库 migration。
2. 实现 watchlist 后端 API。
3. 实现 trade_journal 和 review_note API。
4. 前端从 localStorage 逐步切到后端 API。

### 前端下一步

1. 创建 Vite React 项目。
2. 实现 AppLayout。
3. 实现 Dashboard。
4. 实现 Watchlist localStorage。
5. 实现 TradeJournal localStorage。
6. 实现 Review localStorage。

## 给新 Codex 对话的启动提示词

```text
你现在接手 quant-trading-assistant 项目。

请先阅读：
- AGENTS.md
- CLAUDE.md
- docs/CONVERSATION_HANDOFF.md
- docs/AI_HANDOFF.md
- docs/ARCHITECTURE.md
- docs/FRONTEND_ARCHITECTURE.md
- docs/DEVELOPMENT_ROADMAP.md

然后请总结：
1. 当前项目定位；
2. 当前后端状态；
3. 前端项目应该怎么建；
4. 今天最小可运行雏形应该做什么；
5. 哪些事情不能做。

注意：
- 不自动下单；
- 不连接券商；
- 不保存真实密钥；
- 不宣传稳赚；
- 所有交易信号都必须带风险提示。
```

## 给前端项目的创建提示词

```text
请在 /Users/joker/code 下创建新的前端项目 quant-trading-assistant-web。

请参考后端仓库：
- /Users/joker/code/quant-trading-assistant/docs/CONVERSATION_HANDOFF.md
- /Users/joker/code/quant-trading-assistant/docs/FRONTEND_ARCHITECTURE.md

技术栈：
- Vite
- React
- TypeScript
- Ant Design
- React Router
- TanStack Query
- Zustand
- ECharts
- Axios
- dayjs

今天目标：
先跑出可用雏形，支持我下周开始边炒股边记录。

第一版必须实现：
1. AppLayout：左侧菜单 + 顶部栏；
2. Dashboard：今日工作台；
3. Watchlist：自选股管理，先用 localStorage；
4. TradeJournal：交易记录，先用 localStorage；
5. Review：复盘笔记，先用 localStorage；
6. Settings：显示 mock/remote API 模式；
7. 页面明确提示：本系统只做辅助记录和复盘，不自动交易。

验收：
- npm install 成功；
- npm run dev 成功；
- 浏览器能打开；
- 新增自选股、交易记录、复盘后刷新不丢。
```

## 给后端继续开发的提示词

```text
请基于 quant-trading-assistant 后端项目继续开发 v0.1。

请先阅读：
- AGENTS.md
- docs/CONVERSATION_HANDOFF.md
- docs/DATABASE_DESIGN.md
- docs/DEVELOPMENT_ROADMAP.md

当前任务：
实现 v0.1 核心数据库表和 watchlist API。

要求：
1. 使用 Flyway 新建 migration；
2. 创建 JPA Entity / Repository / Service / Controller；
3. 支持自选股新增、查询、更新、停用；
4. 使用 Spring Validation；
5. 不连接券商，不读取真实密钥，不做自动下单；
6. 实现后运行 ./mvnw test。
```

## 需要避免的方向

近期不要做：

- 自动交易。
- 券商 API。
- 高频交易。
- 机器学习量化。
- 复杂微服务。
- 复杂动态插件加载。
- 过度漂亮但不实用的前端页面。
- 没有复盘字段的“只看信号”系统。

当前最优路线：

```text
先把交易记录和复盘跑起来
-> 再做后端数据持久化
-> 再做指标和信号
-> 再做回测
-> 再做风控和报告
```
