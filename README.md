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
