# Current Architecture And Modules

> 本文件记录当前实现事实，避免 AI 后续按旧文档误判。若代码变化，本文件也要同步更新。

## 1. 当前技术栈

后端：

- Java 17
- Spring Boot 3.5.x
- Spring Web
- Spring Validation
- Spring Boot Actuator
- MyBatis + XML Mapper
- MapStruct
- Flyway
- MySQL 8.4
- H2 test profile
- Docker Compose

前端：

- React + TypeScript + Vite
- Ant Design
- feature-based 目录结构
- `mock/localStorage` 与 `remote/REST API` 双模式

## 2. 后端分层约定

当前后端按业务模块分包，每个模块尽量保持以下分层：

```text
controller  # REST API 入参、响应、HTTP 语义
service     # 应用服务、事务边界、跨 manager 编排
manager     # 领域规则、校验、计算、DAO 调用封装
dao         # MyBatis Mapper 接口
model       # DO / 数据库对象
dto         # 请求 DTO
vo          # 响应 VO
convert     # MapStruct 转换器
```

规则：

- Controller 不写业务计算。
- Service 管事务和流程编排。
- Manager 写业务规则、校验、计算和 DAO 编排。
- DAO 只负责数据库访问。
- SQL 写在 `src/main/resources/mapper/*.xml`。
- 表结构变更必须走 `src/main/resources/db/migration/V*.sql`。

## 3. 当前已实现模块

| 模块 | 后端包 | 主要 API | 数据表 |
| --- | --- | --- | --- |
| Dashboard | `dashboard` | `/api/v1/dashboard/today` | 聚合查询 |
| Watchlist | `watchlist` | `/api/v1/watchlist` | `watchlist` |
| Trade Plan | `tradeplan` | `/api/v1/trade-plans` | `trade_plan` |
| Risk Calculator | `risk` | `/api/v1/risk/calculations/position-size` | 纯计算 |
| Trade Journal | `journal` | `/api/v1/trade-journals` | `trade_journal` |
| Review | `review` | `/api/v1/reviews` | `review_note` |
| Portfolio Ledger | `portfolio` | `/api/v1/portfolio/*` | `trade_journal`, `portfolio_price_snapshot` |

## 4. 当前数据库迁移

| Migration | 内容 |
| --- | --- |
| `V1__init_schema.sql` | schema marker |
| `V2__create_today_mvp_tables.sql` | watchlist、trade_plan、trade_journal、review_note |
| `V3__add_portfolio_ledger.sql` | 交易费用字段、portfolio_price_snapshot |

下一阶段持仓快照应新增 `V4__add_position_snapshot.sql`，不要修改已发布的 V1/V2/V3。

## 5. 交易账本口径

交易账本基于 `trade_journal` 的买卖流水计算：

- 买入形成 FIFO 批次。
- 卖出按 FIFO 配对。
- 买入成本包含按比例分摊费用。
- 卖出收入扣减卖出费用。
- 当前持仓浮盈依赖 `portfolio_price_snapshot` 手工当前价。

风险提示：

- 本模块不连接实时行情。
- 当前价为用户手工维护。
- 计算结果只用于复盘，不构成投资建议。

## 6. 持仓快照定位

持仓快照不是交易流水，也不是交易账本计算结果。

它表示某个时点券商账户实际显示的持仓盘点：

```text
某日某时刻 -> 用户实际持有哪些股票 -> 数量、成本价、当前价、市值、浮盈亏
```

持仓快照可以用来：

- 和交易账本计算结果做核对。
- 留存每日收盘持仓。
- 后续承接图片识别或 CSV 导入。

## 7. 后端命令

```bash
./mvnw test
./mvnw package
docker compose up -d --build
curl http://localhost:8080/actuator/health
```

## 8. 前端命令

在 `/Users/joker/code/quant-trading-assistant-web` 执行：

```bash
npm run typecheck
npm run lint
npm run test
npm run build
```

## 9. 不允许的实现方向

- 不添加自动下单。
- 不接真实券商账户。
- 不保存券商密码、交易密码或真实交易 API Key。
- 不把 AI 识别结果未经用户确认直接入正式表。
- 不为了跑通临时注释测试或关闭校验。
