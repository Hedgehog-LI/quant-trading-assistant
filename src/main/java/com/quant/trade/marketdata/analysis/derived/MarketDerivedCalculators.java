package com.quant.trade.marketdata.analysis.derived;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 市场级衍生指标共享计算器（MR-0 冻结公式的唯一实现，MR-1A 市场全景与 {@code marketdata.poc}
 * 分析引擎共同委托，禁止任何一方再复制第二套算法）。公式、单位与缺失语义冻结于
 * docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md；本类只做纯函数计算，不访问数据库、
 * 不外联 provider、无状态。数值行为与 MR-0 PoC 内联实现逐位一致（BigDecimal scale 与舍入不动），
 * 保证 PoC analysisContentHash 与既有测试不变。
 *
 * <p>失效场景（各调用方必须在输出中声明）：NONE 复权下除权日收益失真（字典 D7）；行业成分按
 * 抓取日快照聚合历史属于时点穿越假设（由质量检查显式标记，不静默）。</p>
 */
public final class MarketDerivedCalculators {

    private MarketDerivedCalculators() {
    }

    /** 证券池快照行投影（流通市值降序 Top-N 样本派生的排序键，单位元；cap=null 行不入样本）。 */
    public record SymbolMarketCap(String canonicalSymbol, BigDecimal circulatingMarketCap) {
    }

    /** 行业成分归属投影（行业代码 + 成分 as_of 日期，供聚合与时点穿越标记）。 */
    public record IndustryRef(String industryCode, LocalDate asOfDate) {
    }

    /** 单日市场广度计数（字典 M-06：t 与 t-1 均有有效收盘才计入）。 */
    public record AdvanceDeclineCounts(long advancing, long declining, long flat, long validStocks) {
    }

    /** 单日行业成交聚合（覆盖域=有成分映射且有成交额的样本股；M-11/M-12 冻结口径）。 */
    public record IndustryDayAggregate(Map<String, BigDecimal> sectorTurnovers, Map<String, Long> sectorStockCounts,
                                       BigDecimal marketTurnover, boolean membershipLookahead) {
    }

    /**
     * 样本派生（CR-3 冻结）：快照行排除基准与 null 市值后，按流通市值降序（并列按代码升序）
     * 取前 sampleSize 只，输出 symbol 升序去重清单。全池快照行仅作事实保留，不进任何分母。
     */
    public static List<String> deriveSampleSymbols(List<SymbolMarketCap> snapshotRows, String benchmarkSymbol,
                                                   int sampleSize) {
        return snapshotRows.stream()
                .filter(row -> !benchmarkSymbol.equals(row.canonicalSymbol()))
                .filter(row -> row.circulatingMarketCap() != null)
                .sorted(Comparator.comparing(SymbolMarketCap::circulatingMarketCap).reversed()
                        .thenComparing(SymbolMarketCap::canonicalSymbol))
                .limit(Math.max(sampleSize, 0))
                .map(SymbolMarketCap::canonicalSymbol).distinct().sorted().toList();
    }

    /**
     * 单日广度计数（字典 M-06）：仅统计 t 与 t-1 均有非空收盘的样本股；调用方对 prevDay 为空的
     * 首个已加载交易日自行按全零处理。
     */
    public static AdvanceDeclineCounts advanceDeclineCounts(Map<String, TreeMap<LocalDate, BigDecimal>> closes,
                                                            LocalDate day, LocalDate previousDay,
                                                            List<String> sampleSymbols) {
        long advancing = 0, declining = 0, flat = 0, valid = 0;
        for (String symbol : sampleSymbols) {
            TreeMap<LocalDate, BigDecimal> series = closes.get(symbol);
            if (series == null) {
                continue;
            }
            BigDecimal close = series.get(day);
            BigDecimal previous = series.get(previousDay);
            if (close == null || previous == null) {
                continue;
            }
            valid++;
            int comparison = close.compareTo(previous);
            if (comparison > 0) {
                advancing++;
            } else if (comparison < 0) {
                declining++;
            } else {
                flat++;
            }
        }
        return new AdvanceDeclineCounts(advancing, declining, flat, valid);
    }

    /**
     * 单日行业成交聚合（字典 M-11/M-12 覆盖域冻结）：无成分映射的样本股不入任何行业、不入分母
     * （由调用方计入 coverageGap）；成分 as_of 晚于交易日即标记时点穿越。返回行业→成交额(元)、
     * 行业→覆盖证券数、覆盖域总成交额与穿越标记。
     */
    public static IndustryDayAggregate aggregateIndustryDay(Map<String, IndustryRef> membership,
                                                            Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
                                                            List<String> sampleSymbols, LocalDate day) {
        Map<String, BigDecimal> sectorTurnovers = new TreeMap<>();
        Map<String, Long> sectorStockCounts = new TreeMap<>();
        BigDecimal marketTurnover = BigDecimal.ZERO;
        boolean membershipLookahead = false;
        for (String symbol : sampleSymbols) {
            IndustryRef ref = membership.get(symbol);
            if (ref == null) {
                continue;
            }
            TreeMap<LocalDate, BigDecimal> series = amounts.get(symbol);
            BigDecimal amount = series == null ? null : series.get(day);
            if (amount == null) {
                continue;
            }
            sectorTurnovers.merge(ref.industryCode(), amount, BigDecimal::add);
            sectorStockCounts.merge(ref.industryCode(), 1L, Long::sum);
            marketTurnover = marketTurnover.add(amount);
            membershipLookahead = membershipLookahead || ref.asOfDate().isAfter(day);
        }
        return new IndustryDayAggregate(sectorTurnovers, sectorStockCounts, marketTurnover, membershipLookahead);
    }

    /** 行业成交占比（字典 M-12）：part/total，10 位小数 HALF_UP；total 由调用方保证非零。 */
    public static BigDecimal turnoverShare(BigDecimal part, BigDecimal total) {
        return part.divide(total, 10, RoundingMode.HALF_UP);
    }

    /** 简单收益率（字典 M-19/M-20 的 r）：close/previous − 1，20 位小数 HALF_UP。 */
    public static BigDecimal priceRatio(BigDecimal close, BigDecimal previous) {
        return close.divide(previous, 20, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
    }

    /**
     * 日频价格冲击代理（字典 M-20）：|close(t)/close(t−1) − 1| / amount(t)，20 位小数 HALF_UP。
     * 仅是日频代理，不替代买卖价差、盘口深度或真实交易冲击成本；调用方须保证 amount>0 且两日收盘非空。
     */
    public static BigDecimal illiquidityValue(BigDecimal close, BigDecimal previous, BigDecimal amount) {
        return priceRatio(close, previous).abs().divide(amount, 20, RoundingMode.HALF_UP);
    }

    /**
     * 线性插值分位（字典 M-21 冻结方法）：index=q×(n−1)，在相邻序位间线性插值，12 位小数 HALF_UP。
     * 输入必须已升序排序；n=1 时恒返回该值。
     */
    public static BigDecimal percentile(List<BigDecimal> sortedValues, double quantile) {
        double index = quantile * (sortedValues.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = Math.min(lower + 1, sortedValues.size() - 1);
        return sortedValues.get(lower).add(sortedValues.get(upper).subtract(sortedValues.get(lower))
                .multiply(BigDecimal.valueOf(index - lower))).setScale(12, RoundingMode.HALF_UP);
    }
}
