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
./mvnw package
docker compose up -d --build
```

健康检查：

```bash
curl http://localhost:8080/actuator/health
```

停止服务：

```bash
docker compose down
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
