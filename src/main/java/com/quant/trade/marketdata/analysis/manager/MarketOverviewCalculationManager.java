package com.quant.trade.marketdata.analysis.manager;

import com.quant.trade.marketdata.analysis.derived.MarketDerivedCalculators;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.ActivityPoint;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.BenchmarkPoint;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.BreadthPoint;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.CoverageGap;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.IndustryMigrationRow;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.LiquidityProxyPoint;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.LiquidityProxySeries;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.QualityFinding;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * MR-1A 市场全景计算 Manager：从已加载的样本日 K / 证券池快照 / 行业成分行计算五类核心证据
 * （基准趋势与回撤、成交活跃度、市场广度、日频流动性代理、行业成交占比迁移）与质量发现。
 * 纯计算组件：不访问数据库、不外联 provider、无状态；广度计数、行业覆盖域聚合、占比、
 * 收益率、流动性代理与分位数全部委托 {@link MarketDerivedCalculators}（MR-0 冻结公式唯一实现，
 * 不复制第二套算法）。
 *
 * <p>口径要点：交易日集合由基准指数日 K 推导（INDEX_KLINE_DERIVED）；样本域成交额（M-03）
 * 与行业覆盖域成交额（M-12）是两个分母，后者只含有行业映射的样本股；MA/中位数基线读取窗口前
 * 预热数据（由 Service 预载）；所有 null 表示"不可计算"，禁止 0 冒充。</p>
 */
@Component
public class MarketOverviewCalculationManager {

    /** 行业迁移每日单独返回的前 N 行业（其余合并 OTHER）。 */
    static final int TOP_INDUSTRIES_PER_DAY = 8;
    /** MA20/MA60 窗口（字典 M-02/M-09）。 */
    static final int MA_WINDOW_SHORT = 20;
    static final int MA_WINDOW_LONG = 60;
    /** 成交额中位数基线窗口（字典 M-04：含 t 当日）。 */
    static final int TURNOVER_MEDIAN_WINDOW = 20;
    /** 成交扩散基线窗口（字典 M-13：不含 t 的前 20 个交易日）。 */
    static final int ACTIVE_BASELINE_WINDOW = 20;
    /** 占比 20 日中位数窗口（含 t；实体当日无占比则不计入观测）。 */
    static final int SHARE_MEDIAN_WINDOW = 20;
    /**
     * M-22 覆盖门禁冻结阈值：窗口样本日 K 覆盖率（barCoverage）低于 0.90 记
     * LOW_BAR_COVERAGE WARN（沿用 MR-0 PoC 质量引擎 LOW_COVERAGE 同值）。
     */
    static final BigDecimal BAR_COVERAGE_WARN_THRESHOLD = new BigDecimal("0.90");
    /**
     * M-22 覆盖门禁冻结阈值：行业映射覆盖率（membershipCoverage）低于 0.90 记
     * LOW_MEMBERSHIP_COVERAGE WARN，整体状态降为 DEGRADED。
     */
    static final BigDecimal MEMBERSHIP_COVERAGE_WARN_THRESHOLD = new BigDecimal("0.90");
    /**
     * M-22 覆盖门禁冻结阈值：行业映射覆盖率低于 0.50 视为严重不足，行业成交占比迁移
     * 阻断为空（INDUSTRY_MIGRATION_BLOCKED WARN），不得按极少数映射股票输出误导性行业图
     * （阈值沿用板块分析"低于预期成分 50% 即样本不足"既有纪律）。
     */
    static final BigDecimal MEMBERSHIP_COVERAGE_BLOCK_THRESHOLD = new BigDecimal("0.50");
    /**
     * 预热门禁冻结阈值（设计 §9.2）：市场全景中期结论至少需要 120 个合格交易日；不足记
     * INSUFFICIENT_WARMUP WARN，整体状态降为 DEGRADED。短期可计算序列保留，MA60/60 日基线等
     * 预热不足指标继续返回 null。
     *
     * <p>"合格交易日"按样本市场数据真实合格计算（不得仅以基准 K 线数量冒充）：当日存在基准日 K，
     * 且当日样本日 K 覆盖率 ≥ {@link #BAR_COVERAGE_WARN_THRESHOLD}（0.90）；空样本恒为 0。</p>
     */
    static final int MID_TERM_MIN_QUALIFIED_TRADING_DAYS = 120;
    private static final String OTHER_INDUSTRY_CODE = "OTHER";
    private static final String OTHER_INDUSTRY_NAME = "其他";
    private static final String LIQUIDITY_UNIT = "1/元";
    private static final String LIQUIDITY_CALIBER =
            "日频价格冲击代理 illiquidity=|close(t)/close(t−1)−1|/amount(t)，逐日横截面中位数与 P90（线性插值）；"
                    + "只是日频代理，不冒充买卖价差、盘口深度或真实交易冲击成本";

    /** 基准符号由 Service 常量传入（本 Manager 不持有 Provider/基准选择决策）。 */

    /**
     * 计算输入（Service 从 Mapper 装载后的纯数据视图；closes/amounts 仅含样本股，
     * 基准序列单独传入；快照行仅为最新一档）。
     */
    public record CalculationInput(
            LocalDate startDate,
            LocalDate endDate,
            String benchmarkSymbol,
            TreeMap<LocalDate, BigDecimal> benchmarkCloses,
            TreeMap<LocalDate, BigDecimal> benchmarkAmounts,
            Map<String, TreeMap<LocalDate, BigDecimal>> closes,
            Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
            List<MarketDerivedCalculators.SymbolMarketCap> latestSnapshotRows,
            Map<String, MarketDerivedCalculators.IndustryRef> membership,
            Map<String, String> industryNames,
            int sampleSizeLimit) {
    }

    /**
     * 计算输出：五类序列 + M-22 覆盖事实（barCoverage/membershipCoverage）、预热门禁输入
     * （qualifiedTradingDays）、缺口与质量发现（Service 组装元数据与质量块外壳）。
     */
    public record CalculationOutput(
            LocalDate dataAsOf,
            List<String> sampleSymbols,
            BigDecimal barCoverage,
            BigDecimal membershipCoverage,
            long qualifiedTradingDays,
            CoverageGap coverageGap,
            List<QualityFinding> findings,
            List<BenchmarkPoint> benchmarkSeries,
            List<ActivityPoint> activitySeries,
            List<BreadthPoint> breadthSeries,
            LiquidityProxySeries liquidityProxySeries,
            List<IndustryMigrationRow> industryTurnoverMigration) {
    }

    /** 执行全部计算（输入为空数据时返回空序列 + 对应发现，不抛异常）。 */
    public CalculationOutput calculate(CalculationInput input) {
        List<QualityFinding> findings = new ArrayList<>();
        List<String> sampleSymbols = MarketDerivedCalculators.deriveSampleSymbols(
                input.latestSnapshotRows(), input.benchmarkSymbol(), input.sampleSizeLimit());
        if (sampleSymbols.isEmpty()) {
            findings.add(new QualityFinding("EMPTY_SAMPLE", "WARN",
                    "最新证券池快照未派生出任何样本（无快照或全部缺失流通市值）", 0));
        }

        List<LocalDate> allBenchmarkDays = new ArrayList<>(input.benchmarkCloses().keySet());
        Map<LocalDate, LocalDate> prevDay = new HashMap<>();
        for (int i = 1; i < allBenchmarkDays.size(); i++) {
            prevDay.put(allBenchmarkDays.get(i), allBenchmarkDays.get(i - 1));
        }
        List<LocalDate> tradingDays = allBenchmarkDays.stream()
                .filter(day -> !day.isBefore(input.startDate()) && !day.isAfter(input.endDate())).toList();
        // 预热门禁输入：真实合格日（当日有基准日 K 且当日样本日 K 覆盖率 ≥0.90；空样本恒 0）
        long qualifiedTradingDays = countQualifiedTradingDays(allBenchmarkDays, sampleSymbols, input.closes());
        if (tradingDays.isEmpty()) {
            findings.add(new QualityFinding("BENCHMARK_DATA_MISSING", "WARN",
                    "窗口内没有基准指数日 K（TENCENT_PUBLIC/NONE），无法推导交易日", 0));
            return new CalculationOutput(null, sampleSymbols, null, null, qualifiedTradingDays,
                    new CoverageGap(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), List.of()),
                    findings, List.of(), List.of(), List.of(),
                    new LiquidityProxySeries(LIQUIDITY_UNIT, LIQUIDITY_CALIBER, List.of()), List.of());
        }
        LocalDate dataAsOf = tradingDays.get(tradingDays.size() - 1);

        // M-22 覆盖门禁：明确输出 membershipCoverage；低于告警阈值记 WARN（整体 DEGRADED），
        // 低于阻断阈值时行业成交占比迁移强制为空（不得按极少数映射股票输出误导性行业图）。
        CoverageGap coverageGap = coverageGap(sampleSymbols, input.membership(), input.amounts(), tradingDays);
        BigDecimal membershipCoverage = membershipCoverage(sampleSymbols, coverageGap);
        boolean migrationBlocked = membershipCoverage != null
                && membershipCoverage.compareTo(MEMBERSHIP_COVERAGE_BLOCK_THRESHOLD) < 0;
        membershipCoverageFindings(sampleSymbols, coverageGap, membershipCoverage, migrationBlocked, findings);

        BigDecimal barCoverage = barCoverage(sampleSymbols, input.closes(), tradingDays, findings);

        // 预热门禁（设计 §9.2）：中期结论至少 120 个合格交易日；不足记 WARN，短期序列保留。
        if (qualifiedTradingDays < MID_TERM_MIN_QUALIFIED_TRADING_DAYS) {
            findings.add(new QualityFinding("INSUFFICIENT_WARMUP", "WARN",
                    "中期结论门禁需要至少 " + MID_TERM_MIN_QUALIFIED_TRADING_DAYS + " 个合格交易日（当前 "
                            + qualifiedTradingDays + "）；短期序列保留，MA60/60 日基线等预热不足指标为 null",
                    qualifiedTradingDays));
        }

        return new CalculationOutput(dataAsOf, sampleSymbols, barCoverage, membershipCoverage,
                qualifiedTradingDays, coverageGap, findings,
                benchmarkSeries(tradingDays, prevDay, input.benchmarkCloses(), input.benchmarkAmounts()),
                activitySeries(allBenchmarkDays, tradingDays, prevDay, input.amounts(), sampleSymbols),
                breadthSeries(tradingDays, prevDay, input.closes(), sampleSymbols, findings),
                liquidityProxySeries(tradingDays, prevDay, input.closes(), input.amounts(), sampleSymbols),
                migrationBlocked ? List.of()
                        : industryMigration(allBenchmarkDays, tradingDays, prevDay, input, sampleSymbols));
    }

    // ==================== 基准趋势与回撤（M-01/M-02） ====================

    private List<BenchmarkPoint> benchmarkSeries(List<LocalDate> tradingDays, Map<LocalDate, LocalDate> prevDay,
                                                 TreeMap<LocalDate, BigDecimal> closes,
                                                 TreeMap<LocalDate, BigDecimal> amounts) {
        List<BenchmarkPoint> series = new ArrayList<>();
        BigDecimal runningPeak = null;
        for (LocalDate day : tradingDays) {
            BigDecimal close = closes.get(day);
            LocalDate prev = prevDay.get(day);
            BigDecimal previousClose = prev == null ? null : closes.get(prev);
            BigDecimal dailyReturn = close == null || previousClose == null ? null
                    : MarketDerivedCalculators.priceRatio(close, previousClose).setScale(10, RoundingMode.HALF_UP);
            if (close != null) {
                runningPeak = runningPeak == null ? close : close.max(runningPeak);
            }
            BigDecimal drawdown = close == null || runningPeak == null ? null
                    : close.divide(runningPeak, 20, RoundingMode.HALF_UP)
                            .subtract(BigDecimal.ONE).setScale(10, RoundingMode.HALF_UP);
            series.add(new BenchmarkPoint(day, scaleMoney(close), dailyReturn, scaleMoney(amounts.get(day)),
                    movingAverage(closes, day, MA_WINDOW_SHORT), movingAverage(closes, day, MA_WINDOW_LONG),
                    drawdown));
        }
        return series;
    }

    /** 简单均值均线（字典 M-02 观测口径）：截至 t 的最近 n 个非空收盘均值；观测不足 n 为 null。 */
    private BigDecimal movingAverage(TreeMap<LocalDate, BigDecimal> series, LocalDate day, int window) {
        List<BigDecimal> tail = observedTail(series, day, window);
        if (tail == null) {
            return null;
        }
        return tail.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(window), 6, RoundingMode.HALF_UP);
    }

    // ==================== 成交活跃度（M-03/M-04/M-13） ====================

    private List<ActivityPoint> activitySeries(List<LocalDate> allBenchmarkDays, List<LocalDate> tradingDays,
                                               Map<LocalDate, LocalDate> prevDay,
                                               Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
                                               List<String> sampleSymbols) {
        Map<LocalDate, BigDecimal> marketTurnoverByDay = new LinkedHashMap<>();
        for (LocalDate day : allBenchmarkDays) {
            BigDecimal total = BigDecimal.ZERO;
            for (String symbol : sampleSymbols) {
                TreeMap<LocalDate, BigDecimal> series = amounts.get(symbol);
                BigDecimal amount = series == null ? null : series.get(day);
                if (amount != null) {
                    total = total.add(amount);
                }
            }
            marketTurnoverByDay.put(day, total);
        }
        List<ActivityPoint> series = new ArrayList<>();
        for (LocalDate day : tradingDays) {
            BigDecimal turnover = marketTurnoverByDay.get(day);
            BigDecimal median20 = turnoverMedian(marketTurnoverByDay, allBenchmarkDays, day,
                    TURNOVER_MEDIAN_WINDOW);
            BigDecimal median60 = turnoverMedian(marketTurnoverByDay, allBenchmarkDays, day, MA_WINDOW_LONG);
            BigDecimal activityRatio = turnover == null || median20 == null || median20.signum() == 0 ? null
                    : turnover.divide(median20, 10, RoundingMode.HALF_UP);
            series.add(new ActivityPoint(day, scaleMoney(turnover), scaleMoney(median20), scaleMoney(median60),
                    activityRatio,
                    activeStockRatio(allBenchmarkDays, day, amounts, sampleSymbols),
                    countValidAmounts(day, amounts, sampleSymbols)));
        }
        return series;
    }

    /** 成交额中位数基线（字典 M-04：含 t 的近 n 个交易日；观测不足 n 为 null）。 */
    private BigDecimal turnoverMedian(Map<LocalDate, BigDecimal> turnoverByDay, List<LocalDate> allBenchmarkDays,
                                      LocalDate day, int window) {
        List<BigDecimal> values = new ArrayList<>();
        for (LocalDate trailing : trailingDays(allBenchmarkDays, day, window, true)) {
            BigDecimal value = turnoverByDay.get(trailing);
            if (value != null) {
                values.add(value);
            }
        }
        if (values.size() < window) {
            return null;
        }
        values.sort(BigDecimal::compareTo);
        return MarketDerivedCalculators.percentile(values, 0.5);
    }

    /** 成交扩散（字典 M-13）：严格大于自身前 20 个交易日（不含 t）成交额中位数的证券占比。 */
    private BigDecimal activeStockRatio(List<LocalDate> allBenchmarkDays, LocalDate day,
                                        Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
                                        List<String> sampleSymbols) {
        List<LocalDate> baselineDays = trailingDays(allBenchmarkDays, day, ACTIVE_BASELINE_WINDOW, false);
        if (baselineDays.size() < ACTIVE_BASELINE_WINDOW) {
            return null;  // 基线交易日不足 → 全体不参与（M-13）
        }
        long above = 0;
        long participating = 0;
        for (String symbol : sampleSymbols) {
            TreeMap<LocalDate, BigDecimal> series = amounts.get(symbol);
            BigDecimal amount = series == null ? null : series.get(day);
            if (amount == null) {
                continue;
            }
            List<BigDecimal> baseline = new ArrayList<>();
            for (LocalDate baselineDay : baselineDays) {
                BigDecimal value = series.get(baselineDay);
                if (value != null) {
                    baseline.add(value);
                }
            }
            if (baseline.size() < ACTIVE_BASELINE_WINDOW) {
                continue;  // 基线观测不足 20 → 该股当日不参与（M-13）
            }
            baseline.sort(BigDecimal::compareTo);
            participating++;
            if (amount.compareTo(MarketDerivedCalculators.percentile(baseline, 0.5)) > 0) {
                above++;
            }
        }
        return participating == 0 ? null
                : BigDecimal.valueOf(above).divide(BigDecimal.valueOf(participating), 10, RoundingMode.HALF_UP);
    }

    private long countValidAmounts(LocalDate day, Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
                                   List<String> sampleSymbols) {
        long valid = 0;
        for (String symbol : sampleSymbols) {
            TreeMap<LocalDate, BigDecimal> series = amounts.get(symbol);
            if (series != null && series.get(day) != null) {
                valid++;
            }
        }
        return valid;
    }

    // ==================== 市场广度（M-06/M-07/M-08/M-09） ====================

    private List<BreadthPoint> breadthSeries(List<LocalDate> tradingDays, Map<LocalDate, LocalDate> prevDay,
                                             Map<String, TreeMap<LocalDate, BigDecimal>> closes,
                                             List<String> sampleSymbols, List<QualityFinding> findings) {
        List<BreadthPoint> series = new ArrayList<>();
        Long adLine = null;
        boolean adLineBroken = false;
        long emptyValidDays = 0;
        for (LocalDate day : tradingDays) {
            LocalDate prev = prevDay.get(day);
            MarketDerivedCalculators.AdvanceDeclineCounts counts = prev == null
                    ? new MarketDerivedCalculators.AdvanceDeclineCounts(0, 0, 0, 0)
                    : MarketDerivedCalculators.advanceDeclineCounts(closes, day, prev, sampleSymbols);
            BigDecimal advanceRatio = counts.validStocks() == 0 ? null
                    : BigDecimal.valueOf(counts.advancing())
                            .divide(BigDecimal.valueOf(counts.validStocks()), 10, RoundingMode.HALF_UP);
            if (counts.validStocks() == 0) {
                adLineBroken = true;  // M-08：空有效池当日 A/D 不产出，且不得跳日外推
                emptyValidDays++;
            } else if (!adLineBroken) {
                adLine = adLine == null
                        ? counts.advancing() - counts.declining()  // 首日种子 adv(t0)−dec(t0)（AMD-3）
                        : adLine + counts.advancing() - counts.declining();
            }
            long aboveMa20 = 0;
            long enoughHistory = 0;
            for (String symbol : sampleSymbols) {
                TreeMap<LocalDate, BigDecimal> stockCloses = closes.get(symbol);
                BigDecimal close = stockCloses == null ? null : stockCloses.get(day);
                if (close == null) {
                    continue;
                }
                List<BigDecimal> tail = observedTail(stockCloses, day, MA_WINDOW_SHORT);
                if (tail == null) {
                    continue;  // 历史不足 20 个收盘观测：不入 aboveMa20Ratio 分母（M-09）
                }
                enoughHistory++;
                BigDecimal ma20 = tail.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(MA_WINDOW_SHORT), 6, RoundingMode.HALF_UP);
                if (close.compareTo(ma20) > 0) {
                    aboveMa20++;
                }
            }
            BigDecimal aboveMa20Ratio = enoughHistory == 0 ? null
                    : BigDecimal.valueOf(aboveMa20).divide(BigDecimal.valueOf(enoughHistory), 10, RoundingMode.HALF_UP);
            series.add(new BreadthPoint(day, counts.advancing(), counts.declining(), counts.flat(),
                    counts.validStocks(), advanceRatio, adLineBroken ? null : adLine, aboveMa20, aboveMa20Ratio));
        }
        if (emptyValidDays > 0) {
            findings.add(new QualityFinding("EMPTY_VALID_TRADING_DAY", "WARN",
                    "存在有效证券数为 0 的交易日，A/D 线在该日中断", emptyValidDays));
        }
        return series;
    }

    // ==================== 日频流动性代理（M-20/M-21） ====================

    private LiquidityProxySeries liquidityProxySeries(List<LocalDate> tradingDays,
                                                      Map<LocalDate, LocalDate> prevDay,
                                                      Map<String, TreeMap<LocalDate, BigDecimal>> closes,
                                                      Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
                                                      List<String> sampleSymbols) {
        List<LiquidityProxyPoint> days = new ArrayList<>();
        for (LocalDate day : tradingDays) {
            LocalDate prev = prevDay.get(day);
            List<BigDecimal> illiquidityValues = new ArrayList<>();
            long zeroAmountRows = 0;
            if (prev != null) {
                for (String symbol : sampleSymbols) {
                    TreeMap<LocalDate, BigDecimal> closeSeries = closes.get(symbol);
                    BigDecimal close = closeSeries == null ? null : closeSeries.get(day);
                    BigDecimal previous = closeSeries == null ? null : closeSeries.get(prev);
                    if (close == null || previous == null) {
                        continue;
                    }
                    TreeMap<LocalDate, BigDecimal> amountSeries = amounts.get(symbol);
                    BigDecimal amount = amountSeries == null ? null : amountSeries.get(day);
                    if (amount == null || amount.signum() <= 0) {
                        zeroAmountRows++;  // 成交额缺失或≤0：除零守卫，不参与分位
                        continue;
                    }
                    illiquidityValues.add(MarketDerivedCalculators.illiquidityValue(close, previous, amount));
                }
            }
            illiquidityValues.sort(BigDecimal::compareTo);
            days.add(new LiquidityProxyPoint(day,
                    illiquidityValues.isEmpty() ? null : MarketDerivedCalculators.percentile(illiquidityValues, 0.5),
                    illiquidityValues.isEmpty() ? null : MarketDerivedCalculators.percentile(illiquidityValues, 0.9),
                    illiquidityValues.size(), zeroAmountRows));
        }
        return new LiquidityProxySeries(LIQUIDITY_UNIT, LIQUIDITY_CALIBER, days);
    }

    // ==================== 行业成交占比迁移（M-11/M-12 覆盖域口径） ====================

    private List<IndustryMigrationRow> industryMigration(List<LocalDate> allBenchmarkDays,
                                                         List<LocalDate> tradingDays,
                                                         Map<LocalDate, LocalDate> prevDay,
                                                         CalculationInput input,
                                                         List<String> sampleSymbols) {
        // 预计算全部已加载交易日（含预热）的覆盖域聚合、行业占比与每日 OTHER 占比（供前值/中位数回看）
        Map<LocalDate, MarketDerivedCalculators.IndustryDayAggregate> aggregates = new LinkedHashMap<>();
        Map<LocalDate, Map<String, BigDecimal>> shareByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> otherShareByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> otherTurnoverByDay = new LinkedHashMap<>();
        Map<LocalDate, List<String>> topIndustriesByDay = new LinkedHashMap<>();
        for (LocalDate day : allBenchmarkDays) {
            MarketDerivedCalculators.IndustryDayAggregate aggregate = MarketDerivedCalculators
                    .aggregateIndustryDay(input.membership(), input.amounts(), sampleSymbols, day);
            if (aggregate.sectorTurnovers().isEmpty() || aggregate.marketTurnover().signum() <= 0) {
                continue;  // 该日覆盖域无成交：不产占比（M-11 缺失语义），该日无迁移行
            }
            List<String> ordered = aggregate.sectorTurnovers().entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey).toList();
            List<String> top = ordered.subList(0, Math.min(TOP_INDUSTRIES_PER_DAY, ordered.size()));
            BigDecimal topTurnover = BigDecimal.ZERO;
            for (String code : top) {
                topTurnover = topTurnover.add(aggregate.sectorTurnovers().get(code));
            }
            Map<String, BigDecimal> shares = new TreeMap<>();
            aggregate.sectorTurnovers().forEach(
                    (code, turnover) -> shares.put(code, MarketDerivedCalculators.turnoverShare(
                            turnover, aggregate.marketTurnover())));
            aggregates.put(day, aggregate);
            shareByDay.put(day, shares);
            topIndustriesByDay.put(day, top);
            otherTurnoverByDay.put(day, aggregate.marketTurnover().subtract(topTurnover));
            otherShareByDay.put(day, MarketDerivedCalculators.turnoverShare(
                    aggregate.marketTurnover().subtract(topTurnover), aggregate.marketTurnover()));
        }

        List<IndustryMigrationRow> rows = new ArrayList<>();
        for (LocalDate day : tradingDays) {
            MarketDerivedCalculators.IndustryDayAggregate aggregate = aggregates.get(day);
            if (aggregate == null) {
                continue;
            }
            LocalDate prev = prevDay.get(day);
            int rank = 1;
            for (String code : topIndustriesByDay.get(day)) {
                BigDecimal share = shareByDay.get(day).get(code);
                BigDecimal previousShare = prev == null || shareByDay.get(prev) == null ? null
                        : shareByDay.get(prev).get(code);
                BigDecimal median20Share = entityMedian20Share(allBenchmarkDays, day, trailing -> shareByDay
                        .containsKey(trailing) ? shareByDay.get(trailing).get(code) : null);
                rows.add(new IndustryMigrationRow(day, code, industryDisplayName(input, code),
                        scaleMoney(aggregate.sectorTurnovers().get(code)), share,
                        changeOrNull(share, previousShare), median20Share,
                        changeOrNull(share, median20Share), rank++,
                        aggregate.sectorStockCounts().getOrDefault(code, 0L)));
            }
            if (topIndustriesByDay.get(day).size() < aggregate.sectorTurnovers().size()) {
                long otherCovered = aggregate.sectorStockCounts().values().stream()
                        .mapToLong(Long::longValue).sum()
                        - topIndustriesByDay.get(day).stream()
                                .mapToLong(code -> aggregate.sectorStockCounts().getOrDefault(code, 0L)).sum();
                BigDecimal share = otherShareByDay.get(day);
                BigDecimal previousShare = prev == null ? null : otherShareByDay.get(prev);
                BigDecimal median20Share = entityMedian20Share(allBenchmarkDays, day, otherShareByDay::get);
                rows.add(new IndustryMigrationRow(day, OTHER_INDUSTRY_CODE, OTHER_INDUSTRY_NAME,
                        scaleMoney(otherTurnoverByDay.get(day)), share, changeOrNull(share, previousShare),
                        median20Share, changeOrNull(share, median20Share), null, otherCovered));
            }
        }
        rows.sort(Comparator.comparing(IndustryMigrationRow::tradeDate)
                .thenComparing(row -> row.rank() == null ? Integer.MAX_VALUE : row.rank()));
        return rows;
    }

    /** share(t) − previousShare；任一侧缺失为 null，结果 10 位小数。 */
    private BigDecimal changeOrNull(BigDecimal share, BigDecimal previousShare) {
        return share == null || previousShare == null ? null
                : share.subtract(previousShare).setScale(10, RoundingMode.HALF_UP);
    }

    /** 实体（行业或 OTHER）近 20 个交易日（含 t）占比观测的中位数；窗口内无观测为 null。 */
    private BigDecimal entityMedian20Share(List<LocalDate> allBenchmarkDays, LocalDate day,
                                           java.util.function.Function<LocalDate, BigDecimal> shareOf) {
        List<BigDecimal> values = new ArrayList<>();
        for (LocalDate trailing : trailingDays(allBenchmarkDays, day, SHARE_MEDIAN_WINDOW, true)) {
            BigDecimal value = shareOf.apply(trailing);
            if (value != null) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        values.sort(BigDecimal::compareTo);
        return MarketDerivedCalculators.percentile(values, 0.5);
    }

    private String industryDisplayName(CalculationInput input, String code) {
        return input.industryNames().getOrDefault(code, code);
    }

    // ==================== 覆盖率与缺口 ====================

    /** 行业映射覆盖缺口：未映射样本证券数、窗口成交额合计与清单（不进入行业占比分母）。 */
    private CoverageGap coverageGap(List<String> sampleSymbols,
                                    Map<String, MarketDerivedCalculators.IndustryRef> membership,
                                    Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
                                    List<LocalDate> tradingDays) {
        List<String> uncovered = sampleSymbols.stream()
                .filter(symbol -> membership.get(symbol) == null).sorted().toList();
        BigDecimal uncoveredTurnover = BigDecimal.ZERO;
        for (String symbol : uncovered) {
            TreeMap<LocalDate, BigDecimal> series = amounts.get(symbol);
            if (series == null) {
                continue;
            }
            for (LocalDate day : tradingDays) {
                BigDecimal amount = series.get(day);
                if (amount != null) {
                    uncoveredTurnover = uncoveredTurnover.add(amount);
                }
            }
        }
        return new CoverageGap(uncovered.size(), uncoveredTurnover.setScale(2, RoundingMode.HALF_UP), uncovered);
    }

    /** M-22：行业映射覆盖率 = 有映射样本证券数 / 样本总数；空样本返回 null。 */
    private BigDecimal membershipCoverage(List<String> sampleSymbols, CoverageGap coverageGap) {
        if (sampleSymbols.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(sampleSymbols.size() - coverageGap.uncoveredSampleStocks())
                .divide(BigDecimal.valueOf(sampleSymbols.size()), 6, RoundingMode.HALF_UP);
    }

    /** M-22 行业映射覆盖门禁发现：全缺失/低覆盖告警/轻微缺口；严重不足时追加迁移阻断发现。 */
    private void membershipCoverageFindings(List<String> sampleSymbols, CoverageGap coverageGap,
                                            BigDecimal membershipCoverage, boolean migrationBlocked,
                                            List<QualityFinding> findings) {
        if (membershipCoverage == null || coverageGap.uncoveredSampleStocks() == 0) {
            return;
        }
        long uncovered = coverageGap.uncoveredSampleStocks();
        if (uncovered == sampleSymbols.size()) {
            findings.add(new QualityFinding("INDUSTRY_MAPPING_MISSING", "WARN",
                    "样本证券全部缺少行业成分映射，行业成交占比迁移不可用（coverageGap 单独报告）", uncovered));
        } else if (membershipCoverage.compareTo(MEMBERSHIP_COVERAGE_WARN_THRESHOLD) < 0) {
            findings.add(new QualityFinding("LOW_MEMBERSHIP_COVERAGE", "WARN",
                    "行业映射覆盖率 " + membershipCoverage.toPlainString() + " 低于告警阈值 "
                            + MEMBERSHIP_COVERAGE_WARN_THRESHOLD.toPlainString()
                            + "（未映射 " + uncovered + "/" + sampleSymbols.size() + " 只）", uncovered));
        } else {
            findings.add(new QualityFinding("PARTIAL_INDUSTRY_MAPPING", "INFO",
                    "部分样本证券缺少行业成分映射，未计入行业占比分母（coverageGap 单独报告）", uncovered));
        }
        if (migrationBlocked) {
            findings.add(new QualityFinding("INDUSTRY_MIGRATION_BLOCKED", "WARN",
                    "行业映射覆盖率 " + membershipCoverage.toPlainString() + " 低于阻断阈值 "
                            + MEMBERSHIP_COVERAGE_BLOCK_THRESHOLD.toPlainString()
                            + "，行业成交占比迁移已阻断为空，不得按极少数映射股票输出行业图", uncovered));
        }
    }

    /** M-22：窗口样本日 K 覆盖率 = 有效收盘(样本×交易日)对数 / (窗口交易日×样本数)；同时产出低覆盖发现。 */
    private BigDecimal barCoverage(List<String> sampleSymbols,
                                   Map<String, TreeMap<LocalDate, BigDecimal>> closes,
                                   List<LocalDate> tradingDays, List<QualityFinding> findings) {
        if (sampleSymbols.isEmpty() || tradingDays.isEmpty()) {
            return null;
        }
        long coveredPairs = 0;
        long lowCoverageDays = 0;
        for (LocalDate day : tradingDays) {
            long dayCovered = 0;
            for (String symbol : sampleSymbols) {
                TreeMap<LocalDate, BigDecimal> series = closes.get(symbol);
                if (series != null && series.get(day) != null) {
                    dayCovered++;
                }
            }
            coveredPairs += dayCovered;
            BigDecimal dayRatio = BigDecimal.valueOf(dayCovered)
                    .divide(BigDecimal.valueOf(sampleSymbols.size()), 6, RoundingMode.HALF_UP);
            if (dayRatio.compareTo(BAR_COVERAGE_WARN_THRESHOLD) < 0) {
                lowCoverageDays++;
            }
        }
        BigDecimal ratio = BigDecimal.valueOf(coveredPairs)
                .divide(BigDecimal.valueOf((long) tradingDays.size() * sampleSymbols.size()), 6,
                        RoundingMode.HALF_UP);
        if (ratio.compareTo(BAR_COVERAGE_WARN_THRESHOLD) < 0 || lowCoverageDays > 0) {
            findings.add(new QualityFinding("LOW_BAR_COVERAGE", "WARN",
                    "窗口样本日 K 覆盖率 " + ratio.toPlainString() + "（存在单日覆盖低于 "
                            + BAR_COVERAGE_WARN_THRESHOLD.toPlainString() + " 的交易日）", lowCoverageDays));
        }
        return ratio;
    }

    // ==================== 通用工具 ====================

    /**
     * 真实合格交易日（预热门禁输入，含预热）：当日存在基准日 K 且当日样本日 K 覆盖率 ≥
     * {@link #BAR_COVERAGE_WARN_THRESHOLD}；空样本恒为 0——不得仅以基准 K 线数量冒充样本市场合格。
     */
    private long countQualifiedTradingDays(List<LocalDate> allBenchmarkDays, List<String> sampleSymbols,
                                           Map<String, TreeMap<LocalDate, BigDecimal>> closes) {
        if (sampleSymbols.isEmpty()) {
            return 0;
        }
        long qualified = 0;
        for (LocalDate day : allBenchmarkDays) {
            long covered = 0;
            for (String symbol : sampleSymbols) {
                TreeMap<LocalDate, BigDecimal> series = closes.get(symbol);
                if (series != null && series.get(day) != null) {
                    covered++;
                }
            }
            BigDecimal dayCoverage = BigDecimal.valueOf(covered)
                    .divide(BigDecimal.valueOf(sampleSymbols.size()), 6, RoundingMode.HALF_UP);
            if (dayCoverage.compareTo(BAR_COVERAGE_WARN_THRESHOLD) >= 0) {
                qualified++;
            }
        }
        return qualified;
    }

    /** 截至day 的最近 window 个非空观测；不足 window 返回 null（均线/足够历史判定共用）。 */
    private List<BigDecimal> observedTail(TreeMap<LocalDate, BigDecimal> series, LocalDate day, int window) {
        List<BigDecimal> observed = new ArrayList<>();
        for (BigDecimal value : series.headMap(day, true).values()) {
            if (value != null) {
                observed.add(value);
            }
        }
        if (observed.size() < window) {
            return null;
        }
        return observed.subList(observed.size() - window, observed.size());
    }

    /** 以 allDays 为交易日轴取 day 的近 count 个交易日（includeDay=false 时为不含 day 的前 count 日）。 */
    private List<LocalDate> trailingDays(List<LocalDate> allDays, LocalDate day, int count, boolean includeDay) {
        int index = allDays.indexOf(day);
        if (index < 0) {
            return List.of();
        }
        int to = includeDay ? index + 1 : index;
        int from = Math.max(0, to - count);
        return allDays.subList(from, to);
    }

    private static BigDecimal scaleMoney(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
