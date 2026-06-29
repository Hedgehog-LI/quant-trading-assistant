# Quant Trading Assistant

本项目是一个本地运行的量化交易辅助系统后端骨架，当前目标是数据积累、指标计算、策略回测、风险提示和交易复盘，不做自动实盘下单，不连接真实券商账户。

## 技术栈

- Java 17
- Spring Boot 3.5.x
- Maven
- MySQL 8.4
- Flyway
- Docker Compose

## 本地构建

```bash
./mvnw test
./mvnw package
```

## Docker 启动

```bash
cp .env.example .env
docker compose up -d --build
```

Dockerfile 使用多阶段构建，镜像会从当前源码自行执行 Maven 打包，不依赖宿主机
`target` 目录。部署前仍建议先运行 `./mvnw test`，但不能通过预先放置 JAR 的方式跳过镜像内构建。

健康检查：

```bash
curl http://localhost:8080/actuator/health
```

停止服务：

```bash
docker compose down
```

## 运行模式与端口

| 模式 | profile | 数据源 | 启动方式 |
|------|---------|--------|----------|
| 本机开发 | `local` | Docker MySQL `127.0.0.1:3306` | IDE 或 `./mvnw spring-boot:run` |
| 容器化 | `docker` | compose 内 MySQL（服务名 `mysql`） | `docker compose up -d --build` |

- 后端 HTTP 端口：`8080`（容器名 `qta-server`）。
- MySQL 容器名 `qta-mysql`，宿主端口已收紧为 `127.0.0.1:3306`，**不对公网/局域网暴露**；容器间通过 Docker 网络以服务名 `mysql` 通信。
- 测试使用 H2 内存库（MySQL 模式），`./mvnw test` 不依赖真实 MySQL，也不占用 8080。

## 排障命令

```bash
./mvnw test                                    # 单元/集成测试（H2）
./mvnw package                                 # 本机验证 fat jar 打包
docker compose build --no-cache app            # 必要时从源码强制重建后端镜像
docker compose ps                              # 查看容器
docker logs -f qta-server                      # 后端日志
docker logs --tail 50 qta-mysql                # MySQL 日志
curl http://localhost:8080/actuator/health     # 健康检查
```

## 安全边界

- 不保存券商密码、交易密码或真实交易 API Key。
- 不做自动下单。
- 策略信号只用于辅助决策，必须结合风险控制和人工确认。

## AI 开发文档入口

后续让 Codex / Claude Code 继续开发时，优先让它读取：

- [AGENTS.md](AGENTS.md)：Codex 项目级开发指南。
- [CLAUDE.md](CLAUDE.md)：Claude Code 接手上下文。
- [docs/CONVERSATION_HANDOFF.md](docs/CONVERSATION_HANDOFF.md)：本轮对话关键决策和项目交接整理。
- [docs/AI_HANDOFF.md](docs/AI_HANDOFF.md)：项目背景、当前状态和第一阶段闭环。
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)：系统模块和数据流。
- [docs/DEVELOPMENT_ROADMAP.md](docs/DEVELOPMENT_ROADMAP.md)：v0.1/v0.2/v1.0 迭代路线。
- [docs/FRONTEND_ARCHITECTURE.md](docs/FRONTEND_ARCHITECTURE.md)：独立前端项目架构和今天可跑雏形范围。
- [docs/prompts/CODEX_CLAUDE_PROMPTS.md](docs/prompts/CODEX_CLAUDE_PROMPTS.md)：可直接复制的新对话提示词。
