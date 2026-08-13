package com.quant.trade.marketdata.analysis.derived;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SectorRotationPersistenceCalculatorTest {

    private final SectorRotationPersistenceCalculator calculator =
            new SectorRotationPersistenceCalculator(new RankAverageRanker());

    @Test
    void calculatesFrozenGoldenPersistenceMetricsFromRawCrossSections() {
        List<SectorRotationPersistenceCalculator.DailyCrossSection> days = List.of(
                day(1, "0.01", "0.02", "0.04", "0.05", "0.03"),
                day(2, "0.01", "0.02", "0.03", "0.05", "0.04"),
                day(3, "0.01", "0.02", "0.03", "0.05", "0.04"),
                day(4, "0.01", "0.02", "0.03", "0.04", "0.05"),
                day(5, "0.01", "0.02", "0.03", "0.04", "0.05"));

        var target = calculator.calculate(days, 5, 5).stream()
                .filter(result -> result.sectorIdentityId().equals(5L)).findFirst().orElseThrow();

        assertDecimal("0.800000000000", target.meanRankPercentile());
        assertDecimal("0.187082869339", target.rankPercentileStdDev());
        assertDecimal("0.400000000000", target.topBucketOccupancyRate());
        assertEquals(2, target.consecutiveLeadingDays());
        assertEquals(0, target.consecutiveLaggingDays());
        assertDecimal("0.500000000000", target.rankPercentileChange());
    }

    private SectorRotationPersistenceCalculator.DailyCrossSection day(int day, String... values) {
        Map<Long, BigDecimal> returns = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index++) {
            returns.put((long) index + 1, new BigDecimal(values[index]));
        }
        return new SectorRotationPersistenceCalculator.DailyCrossSection(LocalDate.of(2026, 1, day), returns);
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
