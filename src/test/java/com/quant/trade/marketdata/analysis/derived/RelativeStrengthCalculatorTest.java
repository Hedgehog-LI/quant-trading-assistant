package com.quant.trade.marketdata.analysis.derived;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelativeStrengthCalculatorTest {

    private final RelativeStrengthCalculator calculator =
            new RelativeStrengthCalculator(new RankAverageRanker());

    @Test
    void keepsDecimalRatioUnitAndRanksStrongestAtOne() {
        List<RelativeStrengthCalculator.DailyReturns> days = List.of(
                day(1, "0.0240", "0.0100", "-0.0100", "0.0000", "0.0050"),
                day(2, "0.0100", "0.0100", "0.0000", "0.0000", "0.0050"),
                day(3, "0.0100", "0.0100", "0.0000", "0.0000", "0.0050"),
                day(4, "0.0100", "0.0100", "0.0000", "0.0000", "0.0050"),
                day(5, "0.0100", "0.0100", "0.0000", "0.0000", "0.0050"));

        var results = calculator.calculate(days, 5, 5);

        assertEquals(5, results.size());
        assertEquals(0, new BigDecimal("0.0240").compareTo(days.get(0).returns().get(1L)));
        assertEquals(0, BigDecimal.ONE.compareTo(results.get(0).rsRankPercentile()));
        assertTrue(results.get(0).sectorReturn().compareTo(new BigDecimal("0.06")) > 0,
                "0.0240 必须按 2.40% 参与复利，不能再次除以 100");
    }

    @Test
    void freezesIntersectionCohortAcrossWholeWindow() {
        Map<Long, BigDecimal> first = returns("0.01", "0.02", "0.03", "0.04", "0.05");
        first.put(99L, new BigDecimal("0.50"));
        var results = calculator.calculate(List.of(
                new RelativeStrengthCalculator.DailyReturns(LocalDate.of(2026, 1, 1), first),
                day(2, "0.01", "0.02", "0.03", "0.04", "0.05"),
                day(3, "0.01", "0.02", "0.03", "0.04", "0.05"),
                day(4, "0.01", "0.02", "0.03", "0.04", "0.05"),
                day(5, "0.01", "0.02", "0.03", "0.04", "0.05")), 5, 5);

        assertEquals(5, results.size());
        assertTrue(results.stream().noneMatch(result -> result.sectorIdentityId().equals(99L)));
    }

    @Test
    void tiesReceiveSameAverageRankPercentile() {
        var results = calculator.calculate(List.of(
                day(1, "0.01", "0.01", "0.00", "-0.01", "-0.02"),
                day(2, "0.01", "0.01", "0.00", "-0.01", "-0.02"),
                day(3, "0.01", "0.01", "0.00", "-0.01", "-0.02"),
                day(4, "0.01", "0.01", "0.00", "-0.01", "-0.02"),
                day(5, "0.01", "0.01", "0.00", "-0.01", "-0.02")), 5, 5);

        assertEquals(0, results.stream().filter(row -> row.sectorIdentityId().equals(1L)).findFirst()
                .orElseThrow().rsRankPercentile().compareTo(results.stream()
                        .filter(row -> row.sectorIdentityId().equals(2L)).findFirst().orElseThrow()
                        .rsRankPercentile()));
    }

    @Test
    void missingDayOrInsufficientCohortProducesNoResult() {
        assertTrue(calculator.calculate(List.of(
                day(1, "0.01", "0.02", "0.03", "0.04", "0.05"),
                day(2, "0.01", "0.02", "0.03", "0.04", "0.05")), 5, 5).isEmpty());
        assertTrue(calculator.calculate(List.of(
                day(1, "0.01", "0.02", "0.03", "0.04"),
                day(2, "0.01", "0.02", "0.03", "0.04"),
                day(3, "0.01", "0.02", "0.03", "0.04"),
                day(4, "0.01", "0.02", "0.03", "0.04"),
                day(5, "0.01", "0.02", "0.03", "0.04")), 5, 5).isEmpty());
    }

    private RelativeStrengthCalculator.DailyReturns day(int day, String... values) {
        return new RelativeStrengthCalculator.DailyReturns(LocalDate.of(2026, 1, day), returns(values));
    }

    private Map<Long, BigDecimal> returns(String... values) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index++) {
            result.put((long) index + 1, new BigDecimal(values[index]));
        }
        return result;
    }
}
