---
name: qta-backend-implementation
description: Implement or review Quant Trading Assistant backend modules. Use for Spring Boot, MyBatis XML, Flyway migrations, DTO/VO/DO/MapStruct conversion, manager/service/controller layering, API docs, and tests in the QTA backend repository.
---

# QTA Backend Implementation

## Workflow

1. Read:
   - `../../../docs/AI_DEVELOPMENT_INDEX.md`
   - `../../../docs/CURRENT_ARCHITECTURE_AND_MODULES.md`
   - `../../../docs/BUILD_CHECKLIST.md`
2. For feature work, read the feature design under `../../../docs/features/`.
3. Inspect existing package patterns before editing.
4. Implement narrowly within the existing module boundary.
5. Update docs and tests before reporting done.

## Layering Rules

Use the current project style:

```text
controller  -> HTTP/API only
service     -> transaction and orchestration
manager     -> domain rules, validation, calculations, DAO orchestration
dao         -> MyBatis mapper interface
model       -> DO/database object
dto         -> request object
vo          -> response object
convert     -> MapStruct converter
```

## Persistence Rules

- Add/modify tables only with Flyway migration.
- Do not edit already-released migrations unless the user explicitly asks.
- Put SQL in `src/main/resources/mapper/*.xml`.
- Use MyBatis mapper interfaces for DB reads/writes.
- Use `ErrorCodeEnum` for business errors.
- Use constants under `common.constant` or module constants.
- Use MapStruct for DTO/DO/VO conversion instead of manual get/set mapping.

## Trading Safety Rules

- Do not add broker integration.
- Do not store broker credentials, passwords, or real trading API keys.
- Do not implement auto order placement.
- Keep every trading/PnL output as assistive reference with risk disclaimer.

## Required Verification

Run at minimum:

```bash
./mvnw test
```

If packaging, deployment, Docker, or generated jar is affected, also run:

```bash
./mvnw package
```

Report failures with exact failing module/test and fix before final delivery.
