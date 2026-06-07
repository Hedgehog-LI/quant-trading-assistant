# Claude Code Execution Guide

本文告诉用户如何让 Claude Code 按本仓库的设计手册执行开发。

## 1. 推荐执行顺序

如果目标是周一先有页面能用，推荐先执行前端 MVP：

```text
先前端 localStorage 可用
再后端 Today MVP API
最后前端从 mock/localStorage 切 remote API
```

原因：

- 周一最需要的是能记录自选股、计划、交易和复盘。
- 前端 localStorage 不依赖后端业务 API。
- 后端 API 可以随后按设计手册补齐。

## 2. 开发前准备

在任何 Claude Code 自动改代码之前，先确认当前仓库状态：

```bash
cd /Users/joker/code/quant-trading-assistant
git status
```

建议先把当前文档提交，或者至少确认 `git status` 中没有你不想让 Claude 改的文件。

## 3. 启动 Claude Code

安全模式，适合第一次执行：

```bash
cd /Users/joker/code/quant-trading-assistant
claude
```

如果你明确希望 Claude Code 自主批准本地文件修改和命令执行，可以在干净工作区内使用：

```bash
cd /Users/joker/code/quant-trading-assistant
claude --dangerously-skip-permissions
```

注意：

- 只建议在本地个人项目中使用。
- 使用前确认仓库里没有真实密钥、券商密码、交易密码。
- 使用前确认当前改动已经提交或可丢弃。
- 不要让 Claude 连接券商或读取真实账户信息。

## 4. 让 Claude 开发后端的提示词

把下面整段复制给 Claude Code：

```text
你现在接手 quant-trading-assistant 后端项目。

请先阅读以下文件：
- AGENTS.md
- CLAUDE.md
- docs/ARCHITECTURE.md
- docs/DATABASE_DESIGN.md
- docs/RISK_RULES.md
- docs/claude/BACKEND_TODAY_MVP_IMPLEMENTATION_MANUAL.md

当前任务：
按 BACKEND_TODAY_MVP_IMPLEMENTATION_MANUAL.md 实现 Today MVP 后端。

必须实现：
1. Dashboard 今日工作台聚合 API；
2. Watchlist 自选股 API；
3. Trade Plan 盘前计划 API；
4. Risk Calculator 风控计算器 API；
5. Trade Journal 交易记录 API；
6. Review 盘后复盘 API；
7. Flyway migration；
8. Entity / Repository / Service / Controller / DTO；
9. 基础测试；
10. API 示例文档 docs/API_TODAY_MVP.md。

要求：
- 不自动下单；
- 不连接券商；
- 不读取或保存真实密钥；
- 不输出稳赚、必涨、无风险结论；
- Controller 不返回 Entity；
- 金额和价格使用 BigDecimal；
- 使用 Spring Validation；
- 修改前先列出计划修改的文件；
- 实现后运行 ./mvnw test；
- 最后汇报修改文件和测试结果。
```

## 5. 让 Claude 创建前端项目的提示词

建议从 `/Users/joker/code` 启动 Claude Code，或者在后端仓库启动后明确告诉它前端目标路径。

```bash
cd /Users/joker/code
claude
```

如果你要让它自动审批：

```bash
cd /Users/joker/code
claude --dangerously-skip-permissions
```

复制下面提示词：

```text
请创建并开发 quant-trading-assistant-web 前端项目。

目标路径：
/Users/joker/code/quant-trading-assistant-web

请先阅读后端仓库里的以下文件：
- /Users/joker/code/quant-trading-assistant/AGENTS.md
- /Users/joker/code/quant-trading-assistant/CLAUDE.md
- /Users/joker/code/quant-trading-assistant/docs/FRONTEND_ARCHITECTURE.md
- /Users/joker/code/quant-trading-assistant/docs/claude/FRONTEND_MVP_ARCHITECTURE_AND_CLAUDE_MANUAL.md
- /Users/joker/code/quant-trading-assistant/docs/claude/BACKEND_TODAY_MVP_IMPLEMENTATION_MANUAL.md

当前任务：
按 FRONTEND_MVP_ARCHITECTURE_AND_CLAUDE_MANUAL.md 创建 Vite React TypeScript 前端 MVP。

必须实现：
1. AppLayout：顶部栏 + 左侧菜单；
2. Dashboard 今日工作台；
3. Watchlist 自选股；
4. TradePlan 盘前计划；
5. RiskCalculator 风控计算器；
6. TradeJournal 交易记录；
7. Review 盘后复盘；
8. Settings 设置页；
9. localStorage 数据持久化；
10. mock/remote API 模式结构；
11. 数据导出和导入；
12. 页面明确提示系统只做辅助记录和复盘，不自动交易。

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

要求：
- 不做官网；
- 不做 hero；
- 使用后台工作台风格；
- 表格、表单、Drawer、Modal 优先；
- 前端代码必须可读、可维护，按 FRONTEND_MVP_ARCHITECTURE_AND_CLAUDE_MANUAL.md 的“前端编码规范”执行；
- TypeScript 不要使用 any，领域类型统一放 src/types/domain.ts；
- React 组件使用函数组件和 hooks，组件文件使用 PascalCase；
- 页面只做编排，复杂表单、表格、风控计算、localStorage 读写必须拆到 forms/tables/utils/api/local；
- 不要把所有逻辑堆在 App.tsx 或单个页面文件里；
- 枚举/选项不要散落魔法字符串，统一用类型和 OPTIONS 常量维护；
- localStorage 只能通过统一 localStorageClient 访问；
- 关键风控公式加简短注释，普通代码不要堆无意义注释；
- 不连接券商；
- 不保存真实密钥；
- 不自动下单；
- 修改前先列出计划创建和修改的文件；
- 实现后运行 npm run build；
- 如果能启动 dev server，请运行 npm run dev 并告诉我 URL。
```

## 6. 让 Claude 做代码审查的提示词

```text
请按资深 Java 后端工程师 + 量化交易辅助系统架构师视角审查当前实现。

请重点检查：
1. 是否违反“不自动交易、不连接券商、不保存真实密钥”；
2. 后端分层是否清晰；
3. Controller 是否暴露 Entity；
4. 金额和价格是否使用 BigDecimal；
5. 风控计算是否存在明显错误；
6. 交易计划是否强制止损和仓位纪律；
7. 前端是否能用 localStorage 跑通周一记录流程；
8. 是否有必要测试；
9. 是否过度设计。

请先列问题，按严重程度排序，再给修改建议。
```

## 7. 一次只让 Claude 做一个大任务

建议不要一次让 Claude 同时实现前端和后端。更稳的顺序：

1. 前端 MVP。
2. 后端 Today MVP API。
3. 前端 remote API 接入。
4. 后端指标、信号、回测。

## 8. 每轮完成后的检查命令

后端：

```bash
cd /Users/joker/code/quant-trading-assistant
./mvnw test
git status
```

前端：

```bash
cd /Users/joker/code/quant-trading-assistant-web
npm run build
npm run dev
git status
```

## 9. 需要立即停止 Claude 的情况

如果 Claude 准备做以下事情，立即停止：

- 连接券商。
- 要求输入交易账号、密码、API Key。
- 实现自动下单、自动撤单。
- 把回测结果写成确定性交易建议。
- 大规模删除项目文件。
- 修改与你当前任务无关的大量代码。
