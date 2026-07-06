# P1 行情数据基础第一阶段 — 实施计划

> 版本：v0.2.0 · 日期：2026-07-06 · 状态：实施中

## 1. 数据模型

### stock_basic
| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT | |
| canonical_symbol | VARCHAR(32) | UNIQUE NOT NULL | SH.600519 / SZ.000001 / BJ.430047 |
| symbol | VARCHAR(16) | NOT NULL | 原始代码 600519 |
| name | VARCHAR(128) | | 贵州茅台 |
| market | VARCHAR(8) | NOT NULL | SH / SZ / BJ |
| list_date | DATE | | 上市日期 |
| delisted | BOOLEAN | NOT NULL DEFAULT FALSE | |
| created_at / updated_at | DATETIME | | |

### stock_daily_bar
| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT | |
| canonical_symbol | VARCHAR(32) | NOT NULL | FK 逻辑引用 stock_basic |
| trade_date | DATE | NOT NULL | |
| adjust_type | VARCHAR(8) | NOT NULL | NONE / QF / HF |
| data_source | VARCHAR(16) | NOT NULL | CSV / MANUAL |
| open / high / low / close | DECIMAL(20,6) | NOT NULL | |
| volume | BIGINT | NOT NULL DEFAULT 0 | |
| amount | DECIMAL(20,6) | NOT NULL DEFAULT 0 | |
| created_at / updated_at | DATETIME | | |
| UNIQUE | (canonical_symbol, trade_date, adjust_type, data_source) | 幂等键 |

## 2. API（/api/v1/market-data）
- `GET /stocks` 证券列表（分页/筛选 market/name）
- `POST /stocks` 新增
- `GET /stocks/{canonicalSymbol}` 详情
- `PUT /stocks/{canonicalSymbol}` 编辑
- `DELETE /stocks/{canonicalSymbol}` 删除
- `GET /daily-bars` 日 K 查询（canonical_symbol + dateRange + adjust_type）
- `POST /daily-bars/import` CSV 导入（multipart）
- `GET /daily-bars/template` CSV 模板下载

## 3. CSV 契约
- 库：Apache Commons CSV（后端）、PapaParse（前端）
- 编码 UTF-8；首行表头；字段：canonical_symbol,trade_date,open,high,low,close,volume,amount,adjust_type
- 限制：≤5MB / ≤10000 行
- 两阶段：全量解析校验 → 原子提交；任意错误整批不写库
- 幂等：相同 (canonical_symbol, trade_date, adjust_type, data_source=CSV) 跳过；不同数据更新
- 返回：inserted / updated / skipped / failed + errors[row, message]

## 4. 校验规则
- canonical_symbol 格式 SH./SZ./BJ. + 数字
- trade_date 合法日期
- open/high/low/close > 0
- high >= max(open, close, low); low <= min(open, close, high)
- volume >= 0; amount >= 0
- canonical_symbol 在 stock_basic 存在

## 5. 前端
- 菜单"行情数据" + 路由 /market-data
- Tab：证券主数据 / 日 K 数据
- 证券：表格 + 筛选 + 新增/编辑 Drawer + 删除
- 日 K：筛选查询 + CSV 拖拽上传 + 模板下载 + 导入结果
- mock/remote 双模式；CSV 校验口径与后端一致

## 6. 测试矩阵
- 后端：CRUD / canonical 规范化 / 唯一约束 / CSV 表头 / 非法行 / OHLC 校验 / 未知股票 / 重复导入 / 数据更新 / 事务回滚
- 前端：mock/remote adapter / 页面关键交互 / CSV 解析

## 7. 验收标准
- V5 migration H2 + MySQL 双通过
- 后端 ./mvnw test 全绿
- 前端 typecheck/lint/test/build 全绿
- Docker 冷构建 + curl 联调
- 浏览器桌面 + 390px
