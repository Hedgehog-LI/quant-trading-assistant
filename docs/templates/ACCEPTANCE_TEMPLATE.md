# Acceptance 条目模板

> 复制以下结构追加到 `../acceptance/ACCEPTANCE_LOG.md`。**只有实际执行过的验证才能写通过。**

---

## vX.Y 验收（日期）

- **后端** `./mvnw test`：<Tests run / Failures / Errors>
- **后端** `./mvnw package`：<BUILD SUCCESS / jar 大小>
- **前端** `typecheck / lint / test / build`：<数字与结果>
- **Docker** `docker compose up -d --build`：<容器与健康检查>
- **curl 端到端**：<逐条关键断言>
- **浏览器**（Playwright/手动）：<页面清单 + 控制台 deprecated/error 数>
- **结论**：<通过 / 部分通过 / 失败>
