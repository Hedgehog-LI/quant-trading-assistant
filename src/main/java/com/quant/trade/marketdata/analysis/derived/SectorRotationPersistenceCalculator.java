package com.quant.trade.marketdata.analysis.derived;

import com.quant.trade.marketdata.analysis.constant.SectorAnalyticsConstants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 基于每日涨跌横截面平均秩的板块轮动持续性计算器。 */
@Component
public class SectorRotationPersistenceCalculator {

    private static final int SCALE = 12;
    private final RankAverageRanker ranker;

    public SectorRotationPersistenceCalculator(RankAverageRanker ranker) {
        this.ranker = ranker;
    }

    public List<Result> calculate(List<DailyCrossSection> source, int windowDays, int minimumCohortSize) {
        if (source.size() < windowDays) {
            return List.of();
        }
        List<DailyCrossSection> days = source.stream().sorted(Comparator.comparing(DailyCrossSection::tradeDate))
                .skip(Math.max(0, source.size() - windowDays)).toList();
        Set<Long> cohort = new HashSet<>(days.get(0).returns().keySet());
        days.forEach(day -> cohort.retainAll(day.returns().keySet()));
        if (cohort.size() < minimumCohortSize) {
            return List.of();
        }

        List<Map<Long, RankAverageRanker.Rank>> dailyRanks = days.stream()
                .map(day -> ranker.rank(restrict(day.returns(), cohort))).toList();
        List<Result> results = new ArrayList<>();
        for (Long sectorId : cohort) {
            List<BigDecimal> percentiles = dailyRanks.stream()
                    .map(day -> day.get(sectorId).percentile()).toList();
            BigDecimal mean = mean(percentiles);
            BigDecimal stdDev = populationStdDev(percentiles, mean);
            long topDays = percentiles.stream().filter(value ->
                    value.compareTo(SectorAnalyticsConstants.TOP_BUCKET_THRESHOLD) >= 0).count();
            BigDecimal occupancy = BigDecimal.valueOf(topDays)
                    .divide(BigDecimal.valueOf(days.size()), SCALE, RoundingMode.HALF_UP);
            results.add(new Result(sectorId,
                    dailyRanks.get(days.size() - 1).get(sectorId).averageRank(),
                    days.size() > 1 ? dailyRanks.get(days.size() - 2).get(sectorId).averageRank() : null,
                    mean, stdDev, occupancy,
                    trailingExtremeDays(days, cohort, sectorId, true),
                    trailingExtremeDays(days, cohort, sectorId, false),
                    percentiles.get(percentiles.size() - 1).subtract(percentiles.get(0))));
        }
        return results.stream().sorted(Comparator.comparing(Result::meanRankPercentile).reversed()).toList();
    }

    private Map<Long, BigDecimal> restrict(Map<Long, BigDecimal> values, Set<Long> cohort) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        cohort.forEach(id -> result.put(id, values.get(id)));
        return result;
    }

    private int trailingExtremeDays(List<DailyCrossSection> days, Set<Long> cohort,
                                    Long sectorId, boolean maximum) {
        int count = 0;
        for (int index = days.size() - 1; index >= 0; index--) {
            Map<Long, BigDecimal> returns = restrict(days.get(index).returns(), cohort);
            BigDecimal extreme = maximum ? returns.values().stream().max(BigDecimal::compareTo).orElseThrow()
                    : returns.values().stream().min(BigDecimal::compareTo).orElseThrow();
            if (returns.get(sectorId).compareTo(extreme) != 0) {
                break;
            }
            count++;
        }
        return count;
    }

    private BigDecimal mean(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal populationStdDev(List<BigDecimal> values, BigDecimal mean) {
        BigDecimal variance = values.stream().map(value -> value.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), SCALE * 2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public record DailyCrossSection(LocalDate tradeDate, Map<Long, BigDecimal> returns) {
    }

    public record Result(Long sectorIdentityId, BigDecimal currentRank, BigDecimal previousRank,
                         BigDecimal meanRankPercentile, BigDecimal rankPercentileStdDev,
                         BigDecimal topBucketOccupancyRate, int consecutiveLeadingDays,
                         int consecutiveLaggingDays, BigDecimal rankPercentileChange) {
    }
}
