# ADR-0003: Flyway 管理数据库变更

- 状态：Accepted
- 日期：2026-06

## 背景
需要可追溯、版本化的数据库 schema 变更，避免手动改表导致环境不一致。

## 决策
所有表结构变更通过 Flyway migration（`src/main/resources/db/migration/V*.sql`）管理，版本递增，已发布 migration 不修改。

## 原因
- 版本化、可重放、CI/测试一致（H2 + MySQL 双环境）。
- 已发布 V1-V4 不可改动，避免污染既有环境。

## 影响
新表/字段必须新增 `VX__*.sql`；测试 profile（H2）用同一套 migration 建表。当前 V1-V4：init/today_mvp/portfolio_ledger/position_snapshot。

## 替代方案
Liquibase / JPA 自动建表——后者不可控（见 ADR-0002）。

## 关联
`../DATABASE_DESIGN.md`、`../CURRENT_ARCHITECTURE_AND_MODULES.md`
