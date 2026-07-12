# Handoff 2026-07-12 LongPort ZCode Resume

> 本文件是给后续 ZCode / Claude / Codex 接手用的轻量交接。先读本文件，不要回放长聊天，不要读取全量 `docs/`。

## 1. 当前任务状态

- 目标：完成 LongPort 单股票手动同步最小闭环的代码、脚本、文档准备，并把真实外联前的缺口收敛到可验证清单。
- 后端仓库：`/Users/joker/code/quant-trading-assistant`
- 前端仓库：`/Users/joker/code/quant-trading-assistant-web`
- 当前分支：`main`
- 起点 commit：`ae0413c`
- 当前状态：暂停开发，等待 ZCode 接手或用户提供官方 SDK jar/native libs + LongPort 只读凭据后做真实外联。
- 是否已 commit/push：否。用户后续决定提交。

## 2. 进度判断

本轮 LongPort 任务已经推进到“真实外联前准备完成”阶段。

- 后端 adapter / provider / 配置 / Docker runtime-libs 通道：已完成。
- API 入参校验、失败降级、provider status、同步任务/提醒留痕：已完成。
- 前端 `/market-data` 防呆与联调提示：已完成。
- SDK 安装、离线检查、真实验收、官方源码合约检查脚本：已完成。
- 文档、开发日志、验收日志、AI 交接：已同步。
- 真实 LongPort 外联：未完成，原因不是业务代码缺失，而是缺官方 Java SDK jar/native libs 与用户只读凭据。

粗略进度：代码与流程准备约 85% 完成；剩余 15% 是 SDK 产物安装、真实凭据配置、单 symbol 小调用验收和建设看板状态更新。

## 3. 本轮已完成任务清单

| 序号 | 任务 | 状态 |
| --- | --- | --- |
| 1 | 后端 `LongPortProperties` 配置绑定 | DONE |
| 2 | 后端 `LongPortMarketDataProvider` | DONE |
| 3 | 后端 `LongPortQuoteClient` / `ReflectiveLongPortQuoteClient` | DONE |
| 4 | 默认 disabled 安全兜底，enabled 后切 LongPort provider | DONE |
| 5 | Docker `runtime-libs` 外部 jar classpath 通道 | DONE |
| 6 | fake SDK runtime-libs 验证脚本 | DONE |
| 7 | 真实 LongPort 单 symbol 验收脚本 | DONE |
| 8 | SDK 源码构建备选脚本 | DONE |
| 9 | SDK runtime-libs 离线检查脚本 | DONE |
| 10 | 官方 Java SDK 合约检查脚本与文档 | DONE |
| 11 | 真实外联前 readiness 预检脚本 | DONE |
| 12 | latest quote HTTP + service 入参校验 | DONE |
| 13 | 前端行情页 symbol / 日期 / HF / provider status 防呆 | DONE |
| 14 | 文档、验收日志、AI handoff 同步 | DONE |
| 15 | 真实官方 SDK + 凭据外联 | TODO |
| 16 | 建设看板 LongPort 节点状态更新 | TODO，真实外联后再做 |

## 4. 当前剩余任务

1. 获取官方 LongPort Java SDK jar/native libs。
   - 优先路径：等待或确认 Maven Central artifact 可用。
   - 备选路径：执行 `scripts/build-longport-java-sdk-from-source.sh` 从官方源码构建。
2. 将官方 SDK jar、目标平台 native、`gson`、`native-lib-loader` 放入后端项目 `runtime-libs/`。
3. 配置 `.env` 或 shell env：
   - `QTA_LONGPORT_ENABLED=true`
   - `LONGPORT_APP_KEY`
   - `LONGPORT_APP_SECRET`
   - `LONGPORT_ACCESS_TOKEN`
4. 运行真实外联前预检：
   - `scripts/check-longport-readiness.sh`
5. 运行真实最小验收：
   - `scripts/verify-longport-real-sync.sh`
6. 真实验收通过后，更新建设看板 / `BUILD_CHECKLIST.md` / `AI_HANDOFF.md` 的 LongPort 状态。

## 5. 低成本验证记录

本次暂停前已执行：

```bash
bash -n scripts/build-longport-java-sdk-from-source.sh scripts/check-longport-official-java-contract.sh scripts/check-longport-readiness.sh scripts/inspect-longport-runtime-libs.sh scripts/verify-longport-real-sync.sh scripts/verify-longport-runtime-libs.sh
git diff --check
git -C /Users/joker/code/quant-trading-assistant-web diff --check
```

结果：全部通过。

当前环境执行：

```bash
./scripts/check-longport-readiness.sh
```

结果：按预期失败，原因是本机当前缺少 LongPort 三项凭据，`runtime-libs/` 仍为空，后端服务未运行或 provider 未 ready。这是环境准备不足，不是代码语法错误。

此前同一轮已记录通过的较重验证：

- 后端 `./mvnw -q test`：187 tests / 0 failures / 0 errors。
- 后端 `./mvnw -q -DskipTests package`：通过。
- 前端 `npm run typecheck` / `npm run lint` / `npm run test`（214 tests）/ `npm run build`：通过。
- fake SDK runtime-libs 容器链路：通过。

## 6. 当前 Git 状态摘要

后端有大量 LongPort 相关未提交改动，包括：

- 配置与 Docker：`.env.example`、`.gitignore`、`Dockerfile`、`docker-compose.yml`、`application.properties`
- 后端代码：`marketdata/config`、`marketdata/provider`、`marketdata/provider/longport`、`MarketQuoteService`、`MarketQuoteController`、DTO、常量、测试
- 脚本：`scripts/`
- 文档：`docs/AI_HANDOFF.md`、`docs/development/*LongPort*`、`docs/api/MARKET_DATA_API.md`、`docs/acceptance/ACCEPTANCE_LOG.md`、`docs/ai/*`
- 运行时目录：`runtime-libs/.gitkeep`

前端只有一个文件未提交：

```text
/Users/joker/code/quant-trading-assistant-web/src/pages/market-data.tsx
```

## 7. 接手时不要做什么

- 不要读取全量 `docs/`。
- 不要读取历史 Claude JSONL / 长聊天日志。
- 不要接 LongPort 交易、订单、账户、真实持仓能力。
- 不要把真实 LongPort 密钥写入 Git、文档、日志、前端代码。
- 不要伪造真实行情验收。
- 不要在 SDK/凭据缺失时把 LongPort 建设看板标为 DONE。
- 不要无限循环跑 Docker / Maven / npm；失败后一轮直接相关修复，仍失败就写阻塞原因。

## 8. ZCode Resume Prompt

完整可复制提示词已单独沉淀到：

```text
docs/prompts/ZCODE_LONGPORT_RESUME_PROMPT_2026-07-12.md
```

如果只需要轻量版，可使用下面这段：

```text
只接手 LongPort 单股票手动同步这条线，不要开启专家团，不要读取全量 docs，不要做自动交易/订单/账户能力。

先读取：
1. AGENTS.md
2. docs/AI_DEVELOPMENT_INDEX.md
3. docs/AI_HANDOFF.md
4. docs/ai/PROGRESSIVE_DISCLOSURE_PROTOCOL.md
5. docs/ai/HANDOFF_2026-07-12_longport_zcode_resume.md
6. docs/ai/HANDOFF_2026-07-11_longport_single_symbol_engine.md
7. docs/development/LONGPORT_SDK_RUNTIME_INSTALLATION.md
8. docs/development/LONGPORT_OFFICIAL_JAVA_CONTRACT.md
9. docs/api/MARKET_DATA_API.md

当前目标：
1. 先检查当前未提交改动范围，确认都是 LongPort / market-data / docs / scripts / 前端行情页相关。
2. 如果还没有官方 SDK jar/native libs 和 LongPort 只读凭据，只做静态验收：
   - bash -n scripts/build-longport-java-sdk-from-source.sh scripts/check-longport-official-java-contract.sh scripts/check-longport-readiness.sh scripts/inspect-longport-runtime-libs.sh scripts/verify-longport-real-sync.sh scripts/verify-longport-runtime-libs.sh
   - git diff --check
   - git -C /Users/joker/code/quant-trading-assistant-web diff --check
3. 如果官方 SDK jar/native libs 和只读凭据已准备好，先运行：
   - scripts/check-longport-readiness.sh
   再运行：
   - scripts/verify-longport-real-sync.sh
4. 真实调用只允许一个 A 股 symbol 和小日期范围，不做全量扫描。
5. 通过后更新建设看板和相关文档；未通过则记录明确阻塞原因。

限制：
- 不要打印或提交 LongPort 密钥。
- 不要修改历史 migration。
- 不要 commit/push，除非用户另行要求。
- 不要无限循环验证。
```

## 9. Pause Confirmation 2026-07-12

- 用户已明确暂停本轮开发，后续希望 ZCode 接手开发，当前 Codex 主要负责验收。
- 本文件和 `docs/prompts/ZCODE_LONGPORT_RESUME_PROMPT_2026-07-12.md` 是后续新对话的首要入口。
- 当前不继续扩展产品功能，不继续做真实外联；真实外联必须等待官方 SDK jar/native libs 与只读凭据准备完成。
