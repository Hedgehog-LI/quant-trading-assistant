# Quant Trading Assistant - Codex Project Guide

本文件是给 Codex 读取的项目级开发指南。新对话接手本仓库时，优先阅读本文件，然后阅读 `docs/CONVERSATION_HANDOFF.md` 和 `docs/AI_HANDOFF.md`。

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

1. 后端优先，前端可以后续由 AI 生成。
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

1. 建核心数据库表和 migration。
2. 建基础领域模型：股票、日 K、指标、信号、回测任务、复盘。
3. 建 watchlist API，自选股先跑通增删查改。
4. 建 daily bar 导入接口，先支持 CSV 手工导入。
5. 建指标计算服务，先实现 MA、MACD、RSI、BOLL。
6. 建第一个策略：均线趋势 + 成交量过滤。
7. 建简化回测引擎。
8. 建风险提示和复盘模块。

## 常用命令

```bash
./mvnw test
./mvnw package
docker compose up -d --build
curl http://localhost:8080/actuator/health
docker compose down
```

## AI 协作要求

- 修改代码前先查看现有结构。
- 不要引入复杂平台化设计，v0.1 保持简单可运行。
- 不要添加真实交易、券商接口或密钥读取功能。
- 新增表必须通过 Flyway migration。
- 新增 DB 访问优先沿用 MyBatis Mapper + XML SQL。
- 新增 REST API 时补充请求/响应示例到文档。
- 涉及策略、信号、风控时必须写明假设和失效场景。
