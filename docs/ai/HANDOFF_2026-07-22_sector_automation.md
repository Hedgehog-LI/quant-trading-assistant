# P1.6 板块自动采集交接

## 已完成

- 产品方案：全市场行业排行快照 + 关注板块成分明细双层采集。
- V15：`market_sector_ranking_config/batch/item`，并扩展 watch/snapshot 自动采集、claim、质量和错误状态。
- 频率：全市场 `0/5/10/15/30/60`，`0` 为仅收盘；关注板块 `5/10/15/30/60`。
- CN/HK/US 各自市场时区和有效交易窗口；CN 含 09:15-09:25 集合竞价，收盘后不再周期采集。
- API：排行配置、立即采集、历史批次、榜单明细、关注板块自动采集配置。
- 前端：板块页自动采集配置、状态、历史榜单和关注板块独立频率。
- 页面：本地 mock 桌面/390px 浏览器检查通过，console error 为 0；真实 remote 交互待部署验收。

## 接手入口

1. `docs/AI_HANDOFF.md`
2. `docs/features/MARKET_SECTOR_AUTOMATIC_COLLECTION_DESIGN.md`
3. `docs/decisions/ADR-0010-sector-ranking-dual-layer-collection.md`
4. `docs/api/MARKET_DATA_API.md`
5. `docs/acceptance/ACCEPTANCE_LOG.md` 最新条目

## 部署后最小验收

1. 检查 Flyway 已到 V15，三个市场配置均存在且默认 disabled。
2. 先对有权限市场点击“立即采集”，确认 batch/item 落库且页面历史可展开。
3. 启用 15 分钟盘中采集和收盘快照，跨两个时间桶观察每桶只新增一个 batch。
4. 对一个关注板块启用 15 分钟采集，确认聚合与成分一起落库，质量和延迟字段可见。
5. 模拟或观察权限错误，确认状态进入 `BLOCKED_PERMISSION`，不会每 30 秒重复打 provider。
6. CN 5 分钟档核对 `09:15/09:20/09:25`、`09:30` 四个边界；09:26-09:29、午休和 15:01 必须零请求，15:05 仅一份 `CLOSE`。

## 明确边界

- 当前交易日判断精确到周末和常规时段；交易所节假日数据治理仍可后续加强。
- 本轮没有真实 provider 或 Docker/MySQL 验收记录；仅完成本地 mock 浏览器布局检查，不得描述为线上通过。
- 下一阶段 P1.7 才计算相对强弱、轮动持续性、龙头贡献和异动提醒。
