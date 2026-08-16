package com.quant.trade.marketdata.analysis.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * MR-1A 市场全景只读响应契约（正式 API，区别于 /mr0-poc PoC 入口）。
 *
 * <p>数据边界（必须随响应完整暴露，前端不得省略）：dataScope 固定 SAMPLE（Top-N 样本，不是全市场）；
 * 行业分类为 SINA_INDUSTRY 快照（非 PIT 申万）；行业成分按抓取日快照聚合历史存在时点穿越假设；
 * 不提供官方口径全市场资金流（OFFICIAL_MONEY_FLOW=UNAVAILABLE，禁止由成交额/价格/相对强弱推算）。
 * 公式冻结于 docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md（M-01..M-13/M-20/M-21）。</p>
 *
 * <p>数值口径：金额（元）2 位小数；比率类 10 位小数；均线 6 位小数；流动性代理 12 位小数（分位原生）。
 * 空值（null）一律表示"该日该指标不可计算"，禁止用 0 冒充。</p>
 */
public final class MarketOverviewVO {

    private MarketOverviewVO() {
    }

    /** 市场全景响应：元数据 + 五类核心证据序列 + 质量块。 */
    public record Overview(
            /** 数据边界、样本规模、覆盖率与整体质量状态。 */
            Metadata metadata,
            /** 基准趋势与回撤（字典 M-01/M-02）。 */
            List<BenchmarkPoint> benchmarkSeries,
            /** 市场成交活跃度（字典 M-03/M-04/M-13；只表征活跃度，不是完整市场流动性）。 */
            List<ActivityPoint> activitySeries,
            /** 市场广度（字典 M-06/M-07/M-08/M-09）。 */
            List<BreadthPoint> breadthSeries,
            /** 日频流动性代理（字典 M-20/M-21；只是价格冲击代理）。 */
            LiquidityProxySeries liquidityProxySeries,
            /** 行业成交占比迁移（字典 M-11/M-12 覆盖域口径；按日期+行业的扁平行）。 */
            List<IndustryMigrationRow> industryTurnoverMigration,
            /** 覆盖缺口、Provider 归属、质量发现与假设声明。 */
            QualityBlock quality) {
    }

    /** 元数据：声明"这份数据是什么范围、来自谁、覆盖到什么程度、门禁是否通过"。 */
    public record Metadata(
            /** 市场代码（首期固定 CN）。 */
            String market,
            /** 请求窗口起点（含）。 */
            LocalDate startDate,
            /** 请求窗口终点（含）。 */
            LocalDate endDate,
            /** 数据截至交易日（窗口内最后一个有基准日 K 的交易日；无数据为 null）。 */
            LocalDate dataAsOf,
            /** 数据范围；当前必须是 SAMPLE（Top-N 样本，不代表全市场）。 */
            String dataScope,
            /** 实际使用的样本证券数量。 */
            int sampleSize,
            /** 基准指数代码（SH.000001，上证指数）。 */
            String benchmarkSymbol,
            /** 本响应涉及的全部 Provider 代码。 */
            List<String> providerCodes,
            /** 行业分类体系代码（SINA_INDUSTRY，非申万，禁混称）。 */
            String taxonomyCode,
            /** M-22 窗口样本日 K 覆盖率 = 有效收盘(样本×交易日)对数 / (窗口交易日×样本数)，6 位小数；低于 0.90 记 LOW_BAR_COVERAGE WARN。 */
            BigDecimal barCoverage,
            /** M-22 行业映射覆盖率 = 有成分映射样本证券数 / 样本总数，6 位小数；低于 0.90 记 WARN，低于 0.50 行业迁移阻断为空。 */
            BigDecimal membershipCoverage,
            /** 预热门禁输入：真实合格交易日数（含预热）——当日存在基准日 K 且当日样本日 K 覆盖率 ≥0.90 才计 1 天；空样本恒为 0。中期结论门禁 ≥120，不足记 INSUFFICIENT_WARMUP WARN。 */
            long qualifiedTradingDays,
            /** 整体质量状态：OK / DEGRADED / NO_DATA。 */
            String qualityStatus,
            /** 数据边界声明（固定四条，见服务端常量）。 */
            List<String> limitations,
            /** 不可用指标代码清单（当前为 OFFICIAL_MONEY_FLOW）。 */
            List<String> unavailableMetrics) {
    }

    /** 基准序列点：closePrice/dailyReturn/amount 为指数事实，ma20/ma60/drawdown 由窗口前预热计算。 */
    public record BenchmarkPoint(
            /** 交易日。 */
            LocalDate tradeDate,
            /** 基准收盘价（指数点）。 */
            BigDecimal closePrice,
            /** 简单收益率 close(t)/close(t−1) − 1（首日或前收缺失为 null），10 位小数。 */
            BigDecimal dailyReturn,
            /** 指数成交额（元，TENCENT_PUBLIC 事实）。 */
            BigDecimal amount,
            /** 20 日收盘均线（含 t，观测不足 20 为 null），6 位小数。 */
            BigDecimal ma20,
            /** 60 日收盘均线（含 t，观测不足 60 为 null），6 位小数。 */
            BigDecimal ma60,
            /** 回撤 = close/窗口内累计峰值 − 1（≤0；峰值自窗口首日起算），10 位小数。 */
            BigDecimal drawdown) {
    }

    /** 成交活跃度点：marketTurnover 为样本域口径（字典 M-03），不可宣称全市场成交额。 */
    public record ActivityPoint(
            /** 交易日。 */
            LocalDate tradeDate,
            /** 样本域市场成交额 Σamount（元）。 */
            BigDecimal marketTurnover,
            /** 20 日成交额中位数（含 t，观测不足 20 为 null）。 */
            BigDecimal turnoverMedian20,
            /** 60 日成交额中位数（含 t，观测不足 60 为 null）。 */
            BigDecimal turnoverMedian60,
            /** 活跃度比值 = marketTurnover / turnoverMedian20（字典 M-04；分母缺失或为 0 时 null）。 */
            BigDecimal activityRatio,
            /** 成交扩散（字典 M-13）：当日成交额高于自身前 20 个交易日（不含 t）中位数的证券占比。 */
            BigDecimal activeStockRatio,
            /** 当日有成交额的样本证券数。 */
            long validStocks) {
    }

    /** 市场广度点：adv/dec/flat 需 t 与 t−1 两根收盘（字典 M-06）；A/D 线首日种子 = adv−dec（AMD-3）。 */
    public record BreadthPoint(
            /** 交易日。 */
            LocalDate tradeDate,
            /** 上涨证券数。 */
            long advancingStocks,
            /** 下跌证券数。 */
            long decliningStocks,
            /** 平盘证券数。 */
            long flatStocks,
            /** 有效证券数（t 与 t−1 均有收盘）。 */
            long validStocks,
            /** 上涨占比 adv/valid（空有效池为 null），10 位小数。 */
            BigDecimal advanceRatio,
            /** 累计 A/D 线（首日种子 adv−dec；出现空有效池后中断为 null，不跳日外推）。 */
            Long adLine,
            /** 收盘高于自身 MA20 的证券数。 */
            long aboveMa20Stocks,
            /** aboveMa20Stocks / 有足够历史（≥20 个收盘观测）的证券数；历史不足者不入分母（字典 M-09）。 */
            BigDecimal aboveMa20Ratio) {
    }

    /** 流动性代理序列：unit/caliber + 每日横截面分位（字典 M-20/M-21）。 */
    public record LiquidityProxySeries(
            /** 单位：1/元。 */
            String unit,
            /** 口径声明：日频价格冲击代理，不冒充买卖价差、盘口深度或真实交易冲击成本。 */
            String caliber,
            /** 按交易日的横截面分位点。 */
            List<LiquidityProxyPoint> days) {
    }

    /** 流动性代理点：median/P90 为当日全部合格样本股 illiquidity=|r|/amount 的线性插值分位（12 位小数）。 */
    public record LiquidityProxyPoint(
            /** 交易日。 */
            LocalDate tradeDate,
            /** 当日横截面中位数（合格样本为空时 null）。 */
            BigDecimal medianIlliquidity,
            /** 当日横截面 P90（合格样本为空时 null）。 */
            BigDecimal p90Illiquidity,
            /** 合格样本数（两日收盘齐备且成交额>0）。 */
            long qualifiedStocks,
            /** 成交额缺失或≤0 而被排除的行数（除零守卫）。 */
            long zeroAmountRows) {
    }

    /** 行业迁移行：每日成交占比前 8 行业单独返回，其余合并为 OTHER；占比分母仅为覆盖域（有行业映射的样本股）。 */
    public record IndustryMigrationRow(
            /** 交易日。 */
            LocalDate tradeDate,
            /** 行业代码（前 8 为 SINA_INDUSTRY 代码；其余聚合为 OTHER）。 */
            String industryCode,
            /** 行业名称（OTHER=其他）。 */
            String industryName,
            /** 行业成交额（覆盖域成分合计，元）。 */
            BigDecimal turnover,
            /** 行业成交额 / 覆盖域总成交额（10 位小数）；是"交易注意力"占比，不是资金流入。 */
            BigDecimal turnoverShare,
            /** 较前一交易日占比变化 share(t)−share(t−1)（实体自身口径；无前值为 null）。 */
            BigDecimal previousDayShareChange,
            /** 近 20 个交易日（含 t）该实体占比的中位数（窗口内无观测为 null）。 */
            BigDecimal median20Share,
            /** share(t) − median20Share（median 缺失为 null）。 */
            BigDecimal median20ShareChange,
            /** 当日按成交额降序的行业名次（1..8）；OTHER 聚合无排名（null）。 */
            Integer rank,
            /** 该行业当日有成交额的样本证券数。 */
            long coveredStocks) {
    }

    /** 质量块：覆盖缺口、Provider 归属、结构化质量发现、口径假设与不可用指标。 */
    public record QualityBlock(
            /** 行业映射覆盖缺口（未映射样本股不入占比分母，单独报告）。 */
            CoverageGap coverageGap,
            /** 数据集级 Provider 归属标注。 */
            List<ProviderAttribution> providerAttribution,
            /** 结构化质量发现（code/severity/message/affectedCount）。 */
            List<QualityFinding> qualityFindings,
            /** 口径假设声明（INDEX_KLINE_DERIVED 日历、时点穿越、NONE 复权、样本快照）。 */
            List<String> assumptions,
            /** 不可用指标代码（OFFICIAL_MONEY_FLOW；禁止由价量推算填充）。 */
            List<String> unavailableMetrics) {
    }

    /** 行业映射覆盖缺口：未映射证券数量与其窗口成交额合计（不进入行业占比分母）。 */
    public record CoverageGap(
            /** 无行业映射的样本证券数。 */
            long uncoveredSampleStocks,
            /** 未映射样本证券的窗口成交额合计（元）。 */
            BigDecimal uncoveredTurnoverAmount,
            /** 未映射样本证券清单。 */
            List<String> symbols) {
    }

    /** 数据集 Provider 归属（单一来源红线：每个数据集只列事实 Provider）。 */
    public record ProviderAttribution(
            /** 数据集/指标代码。 */
            String dataset,
            /** Provider 代码清单。 */
            List<String> providers) {
    }

    /** 结构化质量发现：severity ∈ INFO/WARN；WARN 会把整体 qualityStatus 降为 DEGRADED。 */
    public record QualityFinding(
            /** 发现码（如 LOW_BAR_COVERAGE / BENCHMARK_DATA_MISSING）。 */
            String code,
            /** 严重级别 INFO/WARN。 */
            String severity,
            /** 可读说明（含关键数值）。 */
            String message,
            /** 受影响计数（如低覆盖天数、未映射证券数）。 */
            long affectedCount) {
    }
}
