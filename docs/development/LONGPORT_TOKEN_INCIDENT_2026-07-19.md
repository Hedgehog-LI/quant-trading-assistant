# LongPort Token Incident 2026-07-19

## 现象与结论

- 证券静态信息：`OpenApiException: token invalid`。
- 行业排行：旧版本返回 `MARKET_DATA_PROVIDER_PERMISSION_DENIED`。
- 本机使用同一份 `.env.longport` 复测结果一致，因此不是服务器独有网络或 Docker 注入问题。JWT 声明的到期时间为 2026-10-10、Token 内 App Key 与配置匹配、变量无空格/换行且 `.cn` 端点已生效，但官方接口仍返回 `HTTP 401 / code 401004 / token invalid`。
- 另用官方新版 `io.github.longbridge:openapi-sdk:4.0.5`、`com.longbridge.Config.fromApikey(...)` 和同一组凭据做独立只读 `getQuoteLevel()` 对照，仍返回 `OpenApiException: token invalid`。因此排除旧 `longport` SDK、项目签名实现及 `.cn` 域名选择导致该错误。
- 重新生成 Legacy App Key/App Secret/Access Token 后错误不变。CLI 0.24.0 执行 `auth logout` 后重新完成 OAuth Device Authorization，本地 Token 为 `valid`、CN 节点约 49ms 可达，但资源服务器仍返回 `401102 / token verification failed`。官方 MCP 对同一账户仍可读取 `600519.SH` 行情。
- 当前工作假设为 Longbridge 外部鉴权服务或模拟账户通道故障，已向官方提交 Trace ID。除非官方给出新结论，不再反复轮换密钥或修改项目签名实现。
- 行业旧错误是分类缺陷：401 被误归为权限不足；修复后 401/token invalid 与真正的 403/301604 分开。

## 故障窗口与服务端证据

- 最后一次真实成功：2026-07-18 09:51:52（GMT+8），行业快照成功写入 MySQL。
- 第一次观察到失败：2026-07-19 14:28:59（GMT+8），行业请求失败但被旧代码误分类。
- 第一次明确 `token invalid`：2026-07-19 14:29:37（GMT+8），证券静态信息失败。
- Legacy 故障发生窗口：2026-07-18 09:51:52 至 2026-07-19 14:28:59。
- OAuth Trace ID：`197b5141f20d96d3b39ae482b7101399`、`08090d5efff6a9489d253745787d8625`、`b0b68b875ff49bef31b6ce2b223bce37`。

## 服务器处理

1. 保留服务器现有 `.env.longport`，不要提交 Git、粘贴到日志或继续反复轮换凭据。中国大陆部署继续显式使用以下只读行情端点；官方主机表省略了 WebSocket 协议路径，SDK 配置使用完整 `/v2` 地址。

```dotenv
QTA_LONGPORT_ENABLED=true
LONGPORT_HTTP_URL=https://openapi.longbridge.cn
LONGPORT_QUOTE_WEBSOCKET_URL=wss://openapi-quote.longbridge.cn/v2
```

本系统不连接交易接口，因此不配置 `openapi-trade.longbridge.cn`。
2. 拉取本轮本地修复后，从后端仓库目录强制重建应用：

```bash
docker compose --env-file .env --env-file .env.longport up -d --build --force-recreate app
docker compose --env-file .env --env-file .env.longport ps
```

3. 只检查变量是否存在，不输出变量值：

```bash
docker exec qta-server sh -lc 'test -n "$LONGPORT_APP_KEY" && echo APP_KEY_PRESENT || echo APP_KEY_MISSING; test -n "$LONGPORT_APP_SECRET" && echo APP_SECRET_PRESENT || echo APP_SECRET_MISSING; test -n "$LONGPORT_ACCESS_TOKEN" && echo ACCESS_TOKEN_PRESENT || echo ACCESS_TOKEN_MISSING'
```

4. 部署后先验证错误分类和 scheduler 降噪；Longbridge 官方确认恢复后，再按顺序验收真实外联：

```bash
curl -sS http://127.0.0.1:8080/api/v1/market-data/providers/LONGPORT/status
curl -sS -H 'Content-Type: application/json' -X POST http://127.0.0.1:8080/api/v1/market-data/securities/verify -d '{"market":"CN","code":"603308"}'
curl -sS 'http://127.0.0.1:8080/api/v1/market-data/sector-catalog/industry-rankings?market=CN&indicator=leading-gainer&sortType=single&limit=3'
```

当前外部故障期间允许 provider `configured=true/reachable=false`，但错误必须明确归类为 `MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED`，不能再误报行业权限不足。恢复后的通过标准：provider `configured=true/reachable=true`；证券验证不再包含 `token invalid`；行业排行 `success=true`。若前两项成功而行业接口返回 `MARKET_DATA_PROVIDER_PERMISSION_DENIED`，才代表账号确实缺行业行情权限。

## 旧计划处理

旧计划 `planId=1` 是非法组合 `MINUTE_BAR_BACKFILL + INTRADAY`。新版本 scheduler 已不会扫描它，但页面仍会标记为需要修正。它若用于历史分钟补档，应改为 `MINUTE_BAR_BACKFILL + MANUAL` 并配置起止日期；若用于盘中采集，应改为 `INTRADAY_MINUTE_REFRESH + INTRADAY` 并配置 `30S/60S/5M` 频率。修正前也可以先停用：

```bash
curl -sS -X POST 'http://127.0.0.1:8080/api/v1/market-data/sync-plans/1/toggle?enabled=false'
```
