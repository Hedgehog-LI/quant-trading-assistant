# ADR Index

> 架构决策记录（Architecture Decision Record）索引。重要且长期有效的决策沉淀于此；每份 ADR 含背景/决策/原因/影响/替代方案/状态/日期。新决策用 `../templates/ADR_TEMPLATE.md`。

| ID | 决策 | 状态 | 日期 |
| --- | --- | --- | --- |
| [ADR-0001](ADR-0001-single-module-spring-boot.md) | 单体 Spring Boot，v0.1 不拆微服务 | Accepted | 2026-06 |
| [ADR-0002](ADR-0002-mybatis-xml-not-jpa.md) | MyBatis + XML SQL，不使用 JPA/Hibernate | Accepted | 2026-06 |
| [ADR-0003](ADR-0003-flyway-migration.md) | Flyway 管理所有数据库变更 | Accepted | 2026-06 |
| [ADR-0004](ADR-0004-frontend-mock-remote-dual-mode.md) | 前端 mock/remote 双模式 | Accepted | 2026-06 |
| [ADR-0005](ADR-0005-same-origin-api-deployment.md) | 正式部署走同源 `/api/v1`，不写死公网 IP | Accepted | 2026-06 |
| [ADR-0006](ADR-0006-no-auto-trading-no-broker.md) | 不自动交易、不连接券商、不保存密钥 | Accepted | 2026-06 |
| [ADR-0007](ADR-0007-snapshot-reconciliation-readonly.md) | 快照对账只读，不自动修改交易流水 | Accepted | 2026-07 |
| [ADR-0008](ADR-0008-longport-quote-only-provider.md) | LongPort 只作为只读行情 Provider | Accepted | 2026-07-10 |
