# MR-0 Provider 能力矩阵（冻结）

> 状态：`FROZEN FOR MR-0 PoC`（任务 QTA-V2-MR0-DATA-SEMANTICS-POC-20260815，AC-02）
>
> 冻结日期：2026-08-15
>
> 探针来源：父上下文 2026-08-15 真实 HTTP 探针（公共无凭据源）+ 仓库内历史验收与事件记录。
> 本矩阵是 PoC 数据可得性证据，**不构成 MR-1 生产选型决策**（契约 D1：生产选型需 MR-1 前另立 ADR）。
>
> 状态词定义：
>
> - `VERIFIED`：本任务窗口内真实外联探针成功且样本可复核。
> - `VERIFIED(历史)`：以既有真实验收记录为据的能力结论，当前窗口未重测。
> - `NOT_RETESTED`：历史上验证过、当前因凭据/事件无法重测。
> - `NOT_VERIFIED`：本任务窗口内无法验证（无凭据/无运行环境），对应能力不得被引用为承诺。

## 1. 能力矩阵（主表）

| Provider | 官方能力 | 权限要求 | 调用限制 | 历史范围 | 稳定性 | 授权风险 | 状态 | 证据 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LONGBRIDGE | 单标的日 K、实时/延迟行情快照、关注板块行情与行业排行（openapi quote 只读通道，含板块 watch/排行接口） | LongPort App Key/Secret/Access Token（只读行情权限；项目仅配置 quote 端点，不配置交易端点） | 官方限频（SDK 层遵守）；行业排行接口单次最大返回 100 条、无独立总数与分页 | SDK 按标的拉取（单标的日 K 可回补） | 历史窗口内稳定（2026-07-12 全链路验收全绿） | 只读行情授权；凭据不入库不入仓（`.env.longport` 独立管理） | `VERIFIED(历史)`：单标的日 K/快照/关注板块 2026-07-12 真实外联验收 `scripts/verify-longport-real-sync.sh` SH.600519 全绿；2026-07-18 09:51:52 最后真实成功 | `scripts/verify-longport-real-sync.sh`（仓库）；`docs/development/LONGPORT_TOKEN_INCIDENT_2026-07-19.md` §故障窗口（最后成功 2026-07-18 09:51:52 GMT+8） |
| LONGBRIDGE（当前） | 同上（能力未变） | 同上；当前运行容器 `configured=false`（未注入凭据） | 同上 | 同上 | 当前不可用：外部鉴权故障（官方资源服务器 401/token invalid），非本仓库代码缺陷 | 不轮换凭据、不打印密钥（事件处置决定） | `NOT_RETESTED`：2026-08-15 运行容器探针 `configured=false`、`reachable=false`；2026-07-19 起外部鉴权故障未恢复，本任务不重测 | `docs/development/LONGPORT_TOKEN_INCIDENT_2026-07-19.md`（完整事件记录：独立 SDK 对照、OAuth 重授权后仍 401、已提交官方 Trace ID） |
| TUSHARE | 官方文档宣称：全市场股票日 K、daily_basic（换手率/成交额）、指数日 K、交易日历、申万行业分类与 point-in-time 成分（index_member_all）、行业/概念资金流（moneyflow_ths/dc） | 需 Tushare token；资金流/历史分钟等接口有积分门槛（积分不足即无权限） | 按账户积分限频 | 官方文档宣称多年历史（未实测） | 未实测 | 商用/再分发授权条款未审（仅记录，不做承诺） | `NOT_VERIFIED`：仓库与运行容器均无 token（2026-08-15 检查），全部能力未验证；PRD `IMPLEMENTATION_GATE` 对应维度（行业/概念资金流、历史分钟）被阻断，未通过前不得承诺对应覆盖范围 | 契约 Fact F4；`docs/features/QTA_V2_QUANT_RESEARCH_PLATFORM_PRD.md` §18 L565-567（IMPLEMENTATION_GATE 原文）；tushare.pro 官方文档 doc_id=343/371/335（仅文档，未实测） |
| TENCENT_PUBLIC | 个股/指数日 K（无凭据公共端点，含成交额与换手率字段） | 无凭据 | 公共端点，无书面配额；礼貌性串行+退避 | 实测历史完整（SH.600519 与 SH.000001 可取多年日 K；本 PoC 用 2026-04-01..2026-07-31） | 2026-08-15 探针成功 | 非官方公共接口（`proxy.finance.qq.com`），无 SLA、字段结构可变、授权边界未声明——仅作 PoC 事实源 | `VERIFIED`：2026-08-15 真实探针 `proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get`，SH.600519 与 SH.000001 2026-07 真实数据（样本行见 §3.1） | 本文件 §3.1 内嵌样本行摘录（实测日期 2026-08-15 + 端点 + 响应行） |
| SINA_PUBLIC | 全 A 证券池快照（`getHQNodeData` node=hs_a，代码/名称/市值/换手）、新浪互斥行业目录与成分（`newSinaHy` + 行业 node）、个股日资金流（`ssl_qsfx_zjlrqs`，主力净流入/超大单/行业参考字段） | 无凭据 | 公共端点，分页（证券池）、单标的资金流整段返回 | 证券池为当前快照（无历史）；资金流实测覆盖 2010-03-01 起全历史 | 2026-08-15 探针成功 | 同上：非官方公共接口，无 SLA、口径为新浪自定义（行业体系非申万）、授权边界未声明 | `VERIFIED`：2026-08-15 真实探针，三个端点均成功（样本摘录见 §3.2；行业目录无生效日期，仅当前口径） | 本文件 §3.2 内嵌样本行摘录（实测日期 2026-08-15 + 端点 + 响应行/计数） |
| SOHU_PUBLIC（备选） | 个股日 K（无凭据公共端点，含成交额(万元)/换手率） | 无凭据 | 公共端点，无书面配额 | 实测可取历史日 K（备选源） | 2026-08-15 探针成功 | 非官方公共接口（`q.stock.sohu.com/hisHq`），无 SLA；作为 TENCENT_PUBLIC 的备选与交叉校对源 | `VERIFIED`：2026-08-15 真实探针 `q.stock.sohu.com/hisHq`，SH.600519 2026-07-01 行可解析（样本行见 §3.3） | 本文件 §3.3 内嵌样本行摘录（实测日期 2026-08-15 + 端点 + 响应行） |

## 2. 不可用源记录（失败探针也入档）

| Provider | 端点 | 探针日期 | 现象 | 状态 |
| --- | --- | --- | --- | --- |
| EASTMONEY_PUBLIC | `push2his.eastmoney.com`（历史 K 线接口） | 2026-08-15 | 本环境 Empty reply（连接拒绝），不可用 | `NOT_VERIFIED`（本环境不可达，未获得任何数据样本） |
| NETEASE | `quotes.money.163.com/service/chddata`（CSV 历史行情） | 2026-08-15 | HTTP 404，服务已下线 | `NOT_VERIFIED`（已下线，排除出 PoC 候选） |

## 3. 真实探针样本摘录（内嵌复核）

> 以下均为父上下文 2026-08-15 执行的真实 HTTP 探针摘录（无凭据公共源）。它们是 PoC 数据可得性证据；
> 单测 fixture（SLICE-02 录制）是测试数据，不构成 Provider 验收证据。

### 3.1 TENCENT_PUBLIC 日 K（`proxy.finance.qq.com`）

- 端点：`https://proxy.finance.qq.com/ifzq.gtimg/appstock/app/newfqkline/get?param=sh600519,day,,,320,qfq`（探针取无 fq 原始价变体；PoC 统一 `adjust_type=NONE`）
- 实测日期：2026-08-15；标的：SH.600519（贵州茅台）、SH.000001（上证指数）
- 响应行结构：`[日期, 开, 收, 高, 低, 量(手), {}, 换手率(%), 成交额(万元), ...]`
- SH.600519 2026-07-01 实测行（原样摘录）：

```text
["2026-07-01","1180.10","1193.01","1196.80","1166.33","42474.00",{},"0.34","503383.82","0.00","0.00"]
```

- 字段读法：开 1180.10、收 1193.01、高 1196.80、低 1166.33、量 42474 手、换手 0.34%、成交额 503383.82 万元（入库换算：amount ×10000=元、volume ×100=股、turnover ÷100=小数，D6）。
- 同端点实测 SH.000001 指数日 K 完整覆盖 2026-07（D8 交易日集合来源）。

### 3.2 SINA_PUBLIC（证券池 / 行业 / 资金流）

- 证券池端点：`https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData?node=hs_a`（分页）
  - 实测日期：2026-08-15；返回代码/名称/总市值/流通市值(万元，`nmc`)/换手率（`turnoverratio`）等字段。
  - 样本事实（原样摘录）：池内含 bj920000 安徽凤凰，行内含 `nmc`、`turnoverratio` 字段（北交所标的也在 `hs_a` 池中）。
- 行业目录端点：`https://vip.stock.finance.sina.com.cn/q/view/newSinaHy.php`（`newSinaHy`）+ 行业 node 成分接口
  - 实测日期：2026-08-15；返回互斥行业分类目录与成分；当前口径、无生效日期（非 point-in-time，PoC 以 as_of_date 快照入库弥补）。
- 资金流端点：`https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/MoneyFlow.ssl_qsfx_zjlrqs?daima=sh600519`
  - 实测日期：2026-08-15；实测 SH.600519 共 3991 条，覆盖 2010-03-01..2026-08-14（含 2026-07 全月）。
  - 字段：`opendate`（日期）、`netamount`（主力净流入，元）、`ratioamount`（主力净占比）、`r0_net`（超大单净额）、`cate_ra`/`cate_na`（新浪行业口径参考）。
  - 该字段是资金净流入的 Provider 事实（D9 红线：QTA 不从价量猜测资金净流入）。

### 3.3 SOHU_PUBLIC 日 K（`hisHq`，备选）

- 端点：`https://q.stock.sohu.com/hisHq?code=cn_600519&start=20260701&end=20260731&stat=1&order=D&period=d`
- 实测日期：2026-08-15；标的：SH.600519
- SH.600519 2026-07-01 实测行关键值（原样摘录，只列探针实际核对的字段）：

```text
成交量 40970（手）；成交额 491375.06（万元）；换手率 0.33%
```

- 字段读法：量 40970（手）、成交额 491375.06（万元）、换手 0.33%（与腾讯同日行数量级一致：成交额 491375.06 万元 vs 503383.82 万元，两源口径存在差异，PoC 单一指标只用单一来源，不做跨源等式断言）。

## 4. 与 MR-1 生产选型的关系（D1 声明）

- 本矩阵的 `VERIFIED` 仅证明"公共无凭据源在 2026-08-15 可真实取得 PoC 所需数据"，**不构成 MR-1 生产选型决策**。
- MR-1 生产 Provider 选型（覆盖率、稳定性、授权、口径四维比较）须在 MR-1 前另立 ADR 决定；实现者不得把腾讯/新浪/搜狐或任何其他口径静默混用（PRD §18 IMPLEMENTATION_GATE L566）。
- Tushare `NOT_VERIFIED` 与 Longbridge `NOT_RETESTED` 的维度在 MR-1 输入边界中按阻断处理（见任务 AC-08 交付物）。
- 禁止事项继承契约：不打印任何密钥或 `.env` 内容；不凭文档描述冒充真实探针结果。
<!-- frozen-selector: grep status markers and TUSHARE/LONGBRIDGE/TENCENT_PUBLIC/SINA_PUBLIC/SOHU_PUBLIC rows -> >=5 each -->
