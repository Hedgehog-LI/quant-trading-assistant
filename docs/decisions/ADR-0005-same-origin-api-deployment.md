# ADR-0005: 正式部署使用同源 /api/v1

- 状态：Accepted
- 日期：2026-06

## 背景
公网部署时，浏览器访问前端页面，再请求后端 API。若前端写死后端地址，容易误指向访问者本机 localhost。

## 决策
正式部署默认 remote 模式，`apiBaseUrl` 留空，走相对路径 `/api/v1`，由 Nginx 反代到后端容器；不在代码中写死公网 IP。公网页面禁止保存指向 localhost/127.0.0.1/::1 的后端地址。

## 原因
- 同源避免 CORS、避免误配 localhost、不暴露服务器 IP。
- 防误配规则（`settingsApi.isLocalhostUrl`）在公网环境阻断错误配置。

## 影响
链路：浏览器 → 同源 /api/v1 → Nginx → 127.0.0.1:18081 → Spring Boot Docker → MySQL Docker。设置页提供"测试连接"按钮验证整条链路。

## 替代方案
前端直连后端公网 IP——放弃，暴露 IP 且易误配。

## 关联
`../../docs/FRONTEND_ARCHITECTURE.md`、`../mock/MOCK_REMOTE_CONTRACT.md`、`../CONVERSATION_HANDOFF.md`（Historical，含部署链路图）
