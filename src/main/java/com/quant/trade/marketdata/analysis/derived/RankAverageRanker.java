package com.quant.trade.marketdata.analysis.derived;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 为横截面数值计算升序平均秩和强弱百分位，最大值百分位为 1。 */
@Component
public class RankAverageRanker {

    private static final int SCALE = 12;

    public Map<Long, Rank> rank(Map<Long, BigDecimal> values) {
        List<Map.Entry<Long, BigDecimal>> sorted = new ArrayList<>(values.entrySet());
        sorted.sort(Map.Entry.comparingByValue(Comparator.naturalOrder()));
        Map<Long, Rank> result = new LinkedHashMap<>();
        int start = 0;
        while (start < sorted.size()) {
            int end = start + 1;
            while (end < sorted.size()
                    && sorted.get(start).getValue().compareTo(sorted.get(end).getValue()) == 0) {
                end++;
            }
            BigDecimal averageRank = BigDecimal.valueOf(start + 1L + end)
                    .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            BigDecimal percentile = sorted.size() == 1 ? BigDecimal.ONE
                    : averageRank.subtract(BigDecimal.ONE)
                    .divide(BigDecimal.valueOf(sorted.size() - 1L), SCALE, RoundingMode.HALF_UP);
            for (int index = start; index < end; index++) {
                result.put(sorted.get(index).getKey(), new Rank(averageRank, percentile));
            }
            start = end;
        }
        return result;
    }

    public record Rank(BigDecimal averageRank, BigDecimal percentile) {
    }
}
