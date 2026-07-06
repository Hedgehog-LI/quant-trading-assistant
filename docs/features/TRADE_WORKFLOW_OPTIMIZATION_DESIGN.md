# Trade Workflow Optimization Design

> 版本：v0.1.1 设计基线  
> 状态：待开发  
> 目标：在不接入 AI 识别和外部行情源的前提下，把现有计划、交易、账本、持仓快照、复盘和工作台串成可信、可追溯的基础闭环。

## 1. 用户目标

本轮解决五个基础问题：

1. 记录交易时能明确知道它来自哪个交易计划。
2. 交易记录与复盘之间不会因为编辑或删除产生状态不一致。
3. 实际持仓快照可以和历史快照、FIFO 理论持仓进行核对。
4. 工作台能告诉用户下一件需要处理的事情，而不只是展示数量。
5. 生产环境不会再把浏览器请求错误地指向访问者自己的 `localhost`。

目标流程：

```text
自选股
-> 交易计划
-> 交易记录
-> FIFO 交易账本
-> 实际持仓快照
-> 快照对比 / 账本对账
-> 盘后复盘
-> 工作台待办
```

## 2. 本轮范围

### 2.1 做

- 交易记录选择并关联交易计划。
- 选择计划后自动带入可复用字段。
- 后端校验计划关联是否合法。
- 复盘关联交易的删除保护和状态回算。
- 两次已确认持仓快照的差异对比。
- 已确认持仓快照与指定时点 FIFO 理论持仓的数量对账。
- 今日工作台增加可执行待办和数据质量提醒。
- 生产环境 API 地址防误配、有效地址展示和连通性测试。
- mock/localStorage 与 remote/REST API 保持清晰、可测试的行为口径。

### 2.2 不做

- 不做 AI 图片识别、OCR 或截图上传。
- 不接券商，不自动同步真实账户，不自动下单。
- 不接外部行情供应商，不保存外部行情 API Key。
- 不实现分钟行情、技术指标、策略信号或回测。
- 不根据对账差异自动补写、修改或删除交易记录。
- 本轮不把 `review_note.linked_journal_ids` 迁移为关联表；先补应用层一致性，关联表作为后续数据库治理任务。
- 不修改已经发布的 V1-V4 Flyway migration。

## 3. 数据归属

| 数据 | 归属 | 说明 |
| --- | --- | --- |
| 交易计划 | DB | remote 模式下落 MySQL |
| 交易记录及 `plan_id` | DB | 交易记录保留成交时的业务快照字段 |
| 复盘及关联交易 ID | DB | 本轮继续使用现有字段，补一致性规则 |
| 持仓快照 | DB | 只使用已确认快照参与正式比较和对账 |
| 快照差异 | DERIVED | 实时计算，不新增结果表 |
| 账本对账结果 | DERIVED | 实时计算，不自动修正原始数据 |
| 工作台待办 | DERIVED | 后端聚合，前端只展示和跳转 |
| 本地演示数据 | LOCAL_STORAGE | 仅用于开发、离线演示，不自动同步 DB |

## 4. 功能一：交易计划关联交易记录

### 4.1 产品行为

- 新增或编辑交易记录时提供“关联交易计划”选择器。
- 默认候选为同一交易日期附近、状态为 `DRAFT`、`ACTIVE` 或 `DONE` 的计划；`CANCELLED` 不作为默认候选。
- 选择计划后自动带入：股票代码、股票名称、计划止损、计划止盈、计划仓位。
- 自动带入只是表单辅助，用户提交前可以修改交易记录中的快照字段。
- 一个计划允许关联多笔交易，第一笔交易不会自动把计划改为 `DONE`。
- 交易记录列表和详情展示计划 ID 或可读计划摘要，并支持跳转/定位计划。

### 4.2 后端规则

- `planId` 为空表示未关联计划，允许保存。
- `planId` 非空时计划必须存在。
- 交易记录 `symbol` 必须与计划 `symbol` 一致，比较前统一 trim 和大写。
- `CANCELLED` 计划不得新增关联；历史上已关联的数据仍可读取。
- `allowedToTrade=false` 不阻止记录真实交易，因为这类偏离本身需要进入复盘；应形成工作台提醒。
- 不强制计划日期与交易日期完全相同，允许补录和跨日执行，但前端默认优先展示同日计划。
- 创建和更新都执行相同的关联校验。

### 4.3 API 影响

沿用现有交易计划和交易记录 API，不新建平行 CRUD：

```text
GET  /api/v1/trade-plans
GET  /api/v1/trade-plans/{id}
POST /api/v1/trade-journals
PUT  /api/v1/trade-journals/{id}
```

交易记录请求继续使用 `planId`。交易记录响应可增加以下非持久化展示字段，若增加必须保持向后兼容：

```json
{
  "planId": 12,
  "planDate": "2026-07-05",
  "planStatus": "ACTIVE"
}
```

若不扩展响应，前端可以用已加载的计划列表按 `planId` 解析摘要，避免额外接口调用。

## 5. 功能二：复盘关联一致性

### 5.1 当前问题

- 复盘使用 `linked_journal_ids` 保存关联 ID。
- 新增或编辑复盘会把新关联记录标记为 `REVIEWED`，但移除关联或删除复盘后可能不会回算。
- 删除交易记录可能让已有复盘产生悬空引用。

### 5.2 本轮规则

- 删除交易记录前检查是否仍被任意复盘引用；被引用时拒绝删除并返回明确业务错误。
- 新增、编辑、删除复盘后，对受影响交易记录重新计算复盘状态。
- 仍被至少一个复盘引用时为 `REVIEWED`。
- 不再被任何复盘引用时恢复为 `PENDING`。
- 编辑复盘时必须同时处理“旧关联 ID”和“新关联 ID”的并集。
- 读取历史复盘时容忍已经存在的无效 ID，但新增和更新不得写入不存在的交易 ID。
- 本轮继续使用现有字段和 JSON 转换工具，避免为个人项目的少量数据引入高风险迁移。

### 5.3 后续治理

数据量增大或需要复杂查询时，再新增：

```text
review_journal_relation
- review_id
- journal_id
- created_at
- unique(review_id, journal_id)
```

迁移前必须设计旧 `linked_journal_ids` 的无损回填方案；不能直接丢弃历史关联。

## 6. 功能三：持仓快照对比

### 6.1 产品行为

- 用户从历史列表选择“基准快照”和“目标快照”。
- 只允许比较 `CONFIRMED` 快照。
- 基准快照时间必须早于目标快照时间。
- 展示总成本、总市值、总浮盈亏、持仓数量的变化。
- 明细按证券代码合并，展示新增、加仓、减仓、清仓、未变化。
- 盈利红色、亏损绿色；数量变化使用中性色和明确文案，避免把加仓直接表达为利好。

变化类型：

| code | 含义 |
| --- | --- |
| `NEW` | 基准没有、目标存在 |
| `INCREASED` | 目标持仓数量增加 |
| `REDUCED` | 目标持仓数量减少但仍持有 |
| `CLOSED` | 基准存在、目标没有 |
| `UNCHANGED` | 持仓数量未变化 |

### 6.2 API 草案

```http
GET /api/v1/position-snapshots/comparison?baseSnapshotId=1&targetSnapshotId=2
```

响应结构建议：

```json
{
  "baseSnapshotId": 1,
  "targetSnapshotId": 2,
  "baseSnapshotTime": "2026-07-04T15:05:00",
  "targetSnapshotTime": "2026-07-05T15:05:00",
  "totalCostDelta": 10000.00,
  "totalMarketValueDelta": 10500.00,
  "totalUnrealizedPnlDelta": 500.00,
  "positionCountDelta": 1,
  "items": [
    {
      "symbol": "600519",
      "name": "贵州茅台",
      "changeType": "INCREASED",
      "baseQuantity": 100,
      "targetQuantity": 200,
      "quantityDelta": 100,
      "baseCostPrice": 1500.00,
      "targetCostPrice": 1490.00,
      "marketValueDelta": 152000.00,
      "unrealizedPnlDelta": 2000.00
    }
  ]
}
```

### 6.3 计算规则

- 使用 `symbol` 作为本轮合并键，比较前统一 trim 和大写。
- 金额使用 `BigDecimal`，禁止使用 `double`。
- 缺失一侧的数量和金额按 0 参与 delta 计算。
- 快照原始金额以保存时后端计算结果为准，不在比较接口中重新估值。
- 返回结果按变化类型、目标市值、证券代码形成稳定顺序。

## 7. 功能四：实际持仓与 FIFO 账本对账

### 7.1 产品行为

- 用户在已确认快照上执行“与交易账本核对”。
- 对账主要比较持仓数量，不用成本价差异直接判定账本错误。
- 显示理论数量、实际数量、差异数量和状态。
- 结果仅提示核对，不允许一键自动修正交易流水。

对账状态：

| code | 含义 |
| --- | --- |
| `MATCHED` | 快照数量与账本数量一致 |
| `QUANTITY_MISMATCH` | 两边都有，但数量不同 |
| `SNAPSHOT_ONLY` | 快照有持仓，账本没有 |
| `LEDGER_ONLY` | 账本有持仓，快照没有 |

### 7.2 时间口径

- FIFO 账本只使用快照时间之前的交易记录。
- `trade_date` 早于快照日期的交易全部纳入。
- 与快照同日且 `trade_time` 为空的记录默认纳入，并在结果 warnings 中说明时间精度不足。
- 与快照同日且 `trade_time` 非空时，只纳入 `trade_time <= snapshot_time` 的记录。
- 卖出数量超过可用 FIFO 批次时沿用现有账本异常规则，不静默吞掉。

### 7.3 API 草案

```http
GET /api/v1/position-snapshots/{snapshotId}/reconciliation
```

响应结构建议：

```json
{
  "snapshotId": 2,
  "snapshotTime": "2026-07-05T15:05:00",
  "matchedCount": 3,
  "mismatchCount": 1,
  "hasMismatch": true,
  "warnings": [],
  "items": [
    {
      "symbol": "600519",
      "name": "贵州茅台",
      "status": "QUANTITY_MISMATCH",
      "snapshotQuantity": 200,
      "ledgerQuantity": 100,
      "quantityDifference": 100,
      "snapshotCostPrice": 1490.00,
      "ledgerAverageCost": 1505.00
    }
  ]
}
```

### 7.4 风险提示

- 券商成本价与 FIFO 理论成本可能因费用、分红、送转、历史缺失等原因不同。
- 第一版只以数量差异作为核心异常。
- 对账结果不构成交易建议，也不替代券商正式对账单。

## 8. 功能五：工作台待办中心

### 8.1 待办类型

| code | 默认级别 | 说明 |
| --- | --- | --- |
| `PENDING_REVIEW` | WARNING | 存在待复盘交易 |
| `UNLINKED_TRADE_PLAN` | INFO | 交易未关联计划 |
| `TRADE_AGAINST_PLAN` | WARNING | 关联计划不允许交易或标记未按计划 |
| `MISSING_STOP_LOSS` | RISK | 买入交易没有计划止损 |
| `STALE_POSITION_SNAPSHOT` | INFO | 最近已确认快照超过 3 个自然日 |
| `POSITION_RECONCILIATION_MISMATCH` | WARNING | 最新快照与账本数量不一致 |

### 8.2 展示规则

- remote 模式以 `/api/v1/dashboard/today` 后端聚合结果为准。
- mock 模式使用前端纯函数按相同口径计算。
- 待办包含标题、说明、数量、级别和目标页面路径。
- 点击待办进入对应页面并尽量带上筛选条件。
- 工作台不展示“买入/卖出建议”，只展示记录、数据质量和纪律待办。

## 9. 功能六：生产连接防呆

### 9.1 正确链路

```text
浏览器访问公网前端
-> 同源 /api/v1
-> Nginx
-> 服务器 127.0.0.1:18081
-> Spring Boot Docker
-> MySQL Docker
```

### 9.2 产品规则

- 生产构建默认 `remote`。
- 同域部署时 `apiBaseUrl` 留空，使用相对路径 `/api/v1`。
- 不在代码中写死服务器 IP。
- 当前页面不是 localhost/127.0.0.1 时，禁止保存指向 localhost/127.0.0.1/::1 的后端地址，并给出明确说明。
- 设置页展示“当前实际请求地址”。
- 增加“测试连接”按钮，通过一个只读业务接口验证 Nginx、后端和 DB 链路。
- 测试结果区分成功、超时、HTTP 错误和业务响应错误。
- 不改变服务器安全组、Nginx 或 Docker 配置。

## 10. 后端模块影响

| 模块 | 影响 |
| --- | --- |
| `journal` | 计划关联校验、删除保护、按快照时间查询交易 |
| `tradeplan` | 为关联校验提供计划查询能力 |
| `review` | 引用检查、关联变化后的状态回算 |
| `portfolio` | 快照比较、截止时点 FIFO、账本对账 |
| `dashboard` | 聚合待办和数据质量提醒 |
| `common` | 新增错误码、枚举、常量时沿用现有规范 |

Controller 只处理 HTTP；Service 负责跨模块编排和事务；Manager 负责校验、计算和 DAO 编排；DAO 只访问数据库；转换优先使用 MapStruct。

## 11. 前端模块影响

| feature | 影响 |
| --- | --- |
| `journal` | 计划选择、自动带入、关联计划展示 |
| `review` | 编辑/删除后的状态提示和错误处理 |
| `position-snapshot` | 对比选择、差异视图、账本对账视图 |
| `dashboard` | 待办中心，remote 使用后端聚合 API |
| `settings` | 有效地址、localhost 防误配、连接测试 |
| `build-status` | 更新优先级和完成进度 |

页面必须包含 loading、empty、error、success 状态；桌面和窄屏均不能出现文字遮挡或操作不可达。

## 12. 测试要求

### 12.1 后端

- 计划不存在、计划已取消、股票不一致时的关联校验。
- 未关联计划允许保存。
- 被复盘引用的交易禁止删除。
- 编辑或删除复盘后的状态回算。
- 快照比较五种变化类型和金额 delta。
- 快照状态、顺序、ID 非法等边界。
- 截止快照时间的 FIFO 交易筛选。
- 四种对账状态及空数据。
- Dashboard 每种待办的触发和不触发条件。

### 12.2 前端

- 计划选择和字段自动带入。
- 不同股票计划不可形成错误提交。
- 快照选择限制、差异表和对账状态。
- 空状态、错误状态和重试。
- 设置页 localhost 防误配和连接测试。
- mock/remote adapter 路径及响应解包。
- 工作台待办跳转。

## 13. 验收清单

- [ ] 新增交易可以选择计划并正确落库 `plan_id`。
- [ ] 后端拒绝不存在、已取消或股票不一致的计划关联。
- [ ] 一个计划可以关联多笔交易且不会自动结束。
- [ ] 被复盘引用的交易不能直接删除。
- [ ] 编辑和删除复盘后交易复盘状态正确回算。
- [ ] 两个已确认快照可以查看稳定、可解释的差异。
- [ ] 最新实际持仓可以与指定时点 FIFO 理论持仓核对。
- [ ] 对账差异不会自动修改任何正式数据。
- [ ] 工作台能展示并跳转处理基础待办。
- [ ] 公网页面不能保存 localhost 后端地址。
- [ ] 同源 `/api/v1`、mock 和 remote 的说明准确。
- [ ] 后端测试、打包和 Docker 启动检查通过。
- [ ] 前端 typecheck、lint、test、build 和页面验收通过。
- [ ] API、架构、建设清单、README 和建设看板保持同步。

