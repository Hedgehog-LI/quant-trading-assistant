# ADR-0001: 单体 Spring Boot，v0.1 不拆微服务

- 状态：Accepted
- 日期：2026-06

## 背景
项目是个人交易辅助系统，数据量与并发有限，需快速迭代并保证本地可运行。

## 决策
后端采用单个 Spring Boot 模块，按业务分包（dashboard/journal/review/portfolio/...），不拆微服务。

## 原因
- 个人项目复杂度低，微服务带来运维与一致性问题大于收益。
- 单体便于本地启动、调试、Docker 部署。
- 模块边界用包结构 + 分层（controller/service/manager/dao）保证清晰。

## 影响
跨模块协作通过 Manager/Service 注入完成；后续若规模扩大可按包拆分。

## 替代方案
微服务 / 模块化单体（Maven 多模块）——v0.1 不必要。

## 关联
`AGENTS.md`、`../CURRENT_ARCHITECTURE_AND_MODULES.md`
