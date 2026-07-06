# Skill Usage

> ⚠️ Historical（历史参考，非当前执行入口）。当前事实以 AI_HANDOFF.md + development/DEVELOPMENT_LOG.md + CURRENT_ARCHITECTURE_AND_MODULES.md 为准；新会话入口见 AI_DEVELOPMENT_INDEX.md。

> 本文件说明项目级 skills 的使用方式。若 AI 工具不能自动发现 `.claude/skills`，请让它直接读取对应 `SKILL.md`。

## 1. Skills 目录

```text
.claude/skills/
├── qta-product-design/
├── qta-backend-implementation/
├── qta-frontend-implementation/
└── qta-quality-acceptance/
```

## 2. 建议触发方式

### 产品设计

```text
请使用 .claude/skills/qta-product-design/SKILL.md，
基于 docs/PRODUCT_BLUEPRINT.md 和 docs/BUILD_CHECKLIST.md，
把这个需求整理成 PRD、范围、DB/API/UI 草案和验收清单。
```

### 后端开发

```text
请使用 .claude/skills/qta-backend-implementation/SKILL.md，
先阅读 docs/CURRENT_ARCHITECTURE_AND_MODULES.md 和相关 feature 设计文档，
再按项目分层实现后端代码、Flyway migration、MyBatis XML、测试和 API 文档。
```

### 前端开发

```text
请使用 .claude/skills/qta-frontend-implementation/SKILL.md，
先阅读 docs/PRODUCT_BLUEPRINT.md、docs/FRONTEND_ARCHITECTURE.md 和 feature 设计文档，
在 /Users/joker/code/quant-trading-assistant-web 中实现页面、API adapter 和测试。
```

### 验收修复

```text
请使用 .claude/skills/qta-quality-acceptance/SKILL.md，
对前后端进行测试、构建、文档、产品语义和部署口径验收；
发现问题请修复后重新跑完整检查。
```

## 3. 协作原则

- 先读文档再改代码。
- 每个 skill 只负责单一阶段。
- 产品设计输出不能直接当实现完成。
- 实现完成必须经过质量验收 skill。
- 不要把 `localStorage` 误当正式数据源。
