package com.quant.trade.marketdata.analysis.derived;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 固定 cohort、等权基准的可解释板块相对强弱计算器。 */
@Component
public class RelativeStrengthCalculator {

    private static final MathContext MATH_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);
    private static final int SCALE = 12;

    private final RankAverageRanker ranker;

    public RelativeStrengthCalculator(RankAverageRanker ranker) {
        this.ranker = ranker;
    }

    public List<Result> calculate(List<DailyReturns> source, int windowDays, int minimumCohortSize) {
        if (source.size() < windowDays) {
            return List.of();
        }
        List<DailyReturns> days = source.stream().sorted(Comparator.comparing(DailyReturns::tradeDate))
                .skip(Math.max(0, source.size() - windowDays)).toList();
        Set<Long> cohort = new HashSet<>(days.get(0).returns().keySet());
        days.forEach(day -> cohort.retainAll(day.returns().keySet()));
        if (cohort.size() < minimumCohortSize) {
            return List.of();
        }

        Map<Long, Double> sectorLogReturns = new LinkedHashMap<>();
        cohort.forEach(id -> sectorLogReturns.put(id, 0D));
        double benchmarkLogReturn = 0D;
        for (DailyReturns day : days) {
            double dailyBenchmark = cohort.stream().map(day.returns()::get)
                    .mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
            requireValidReturn(dailyBenchmark);
            benchmarkLogReturn += Math.log1p(dailyBenchmark);
            for (Long sectorId : cohort) {
                double dailyReturn = day.returns().get(sectorId).doubleValue();
                requireValidReturn(dailyReturn);
                sectorLogReturns.compute(sectorId, (key, value) -> value + Math.log1p(dailyReturn));
            }
        }

        double finalBenchmarkLogReturn = benchmarkLogReturn;
        Map<Long, BigDecimal> relativeReturns = new LinkedHashMap<>();
        sectorLogReturns.forEach((sectorId, sectorLogReturn) -> relativeReturns.put(sectorId,
                decimal(sectorLogReturn - finalBenchmarkLogReturn)));
        Map<Long, RankAverageRanker.Rank> ranks = ranker.rank(relativeReturns);
        BigDecimal benchmarkReturn = decimal(Math.expm1(benchmarkLogReturn));
        List<Result> results = new ArrayList<>();
        sectorLogReturns.forEach((sectorId, sectorLogReturn) -> results.add(new Result(
                sectorId, decimal(Math.expm1(sectorLogReturn)), benchmarkReturn,
                relativeReturns.get(sectorId), ranks.get(sectorId).percentile())));
        return results.stream().sorted(Comparator.comparing(Result::rsRankPercentile).reversed()).toList();
    }

    private void requireValidReturn(double value) {
        if (value <= -1D) {
            throw new IllegalArgumentException("板块收益必须大于 -1");
        }
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).round(MATH_CONTEXT).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public record DailyReturns(LocalDate tradeDate, Map<Long, BigDecimal> returns) {
    }

    public record Result(Long sectorIdentityId, BigDecimal sectorReturn, BigDecimal benchmarkReturn,
                         BigDecimal relativeReturn, BigDecimal rsRankPercentile) {
    }
}
