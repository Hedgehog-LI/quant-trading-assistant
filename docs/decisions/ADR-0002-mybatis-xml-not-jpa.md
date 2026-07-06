# ADR-0002: MyBatis + XML SQL，不使用 JPA/Hibernate

- 状态：Accepted
- 日期：2026-06

## 背景
需要精确控制 SQL（FIFO、BigDecimal 金额、复杂聚合），且希望 AI 协作时 SQL 可读、可审查。

## 决策
数据访问使用 MyBatis Mapper + XML SQL（`src/main/resources/mapper/*.xml`），不使用 JPA/Hibernate/Repository/`@Entity`。

## 原因
- SQL 写在 XML，便于审查、优化与 AI 理解。
- 避免 Hibernate 的 N+1、懒加载、自动建表等不可控行为。
- 金额字段必须 BigDecimal，MyBatis 显式映射更可控。

## 影响
DO（数据对象）为纯 POJO，无 JPA 注解；转换用 MapStruct；新增查询写 XML。早期文档中的 "Entity/Repository/JPA" 表述已失效（见 `AI_DEVELOPMENT_INDEX.md §6` Historical）。

## 替代方案
JPA/Spring Data JPA——放弃，原因如上。

## 关联
`AGENTS.md`、`../CURRENT_ARCHITECTURE_AND_MODULES.md`、`../DATABASE_DESIGN.md`
