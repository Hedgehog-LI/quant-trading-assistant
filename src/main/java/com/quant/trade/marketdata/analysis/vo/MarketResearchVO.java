package com.quant.trade.marketdata.analysis.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 市场研究只读 API 模型。 */
public final class MarketResearchVO {

    private MarketResearchVO() {
    }

    public record Calculation(Long publicationBatchId, LocalDate asOfDate, int strengthWindowDays,
                              int momentumWindowDays, String status, int sectorCount, boolean reused) {
    }

    public record Radar(Long publicationBatchId, Long sourceBatchId, Long strengthCalculationRunId,
                        Long momentumCalculationRunId, String analysisMode, boolean rotationAvailable, String market,
                        LocalDate asOfDate, int strengthWindowDays, int momentumWindowDays,
                        String scope, String scopeDescription,
                        String strengthFormulaCode, String momentumFormulaCode,
                        String formulaVersion, String parameterHash, String qualityStatus,
                        List<String> reasonCodes,
                        LocalDateTime sourceQuoteTime, LocalDateTime publishedAt,
                        int actualItemCount, int expectedItemCount, BigDecimal coverageRate,
                        String flowMetricNature, BigDecimal capitalFlow,
                        List<Sector> sectors) {
    }

    public record Sector(Long sectorId, String sectorName, String providerSectorId,
                         BigDecimal sectorReturn, BigDecimal benchmarkReturn,
                         BigDecimal relativeReturn, BigDecimal rsRankPercentile,
                         BigDecimal currentRank, BigDecimal previousRank,
                         BigDecimal meanRankPercentile, BigDecimal rankPercentileStdDev,
                         BigDecimal topBucketOccupancyRate, Integer consecutiveLeadingDays,
                         Integer consecutiveLaggingDays, BigDecimal rankPercentileChange,
                         String rotationState, String leadingName, String leadingSymbol,
                         List<String> evidence, List<String> reasonCodes) {
    }

    public record RankingHistory(String market, int windowDays, String scope,
                                 List<SectorHistory> sectors) {
    }

    public record SectorHistory(Long sectorId, String sectorName, List<HistoryPoint> points) {
    }

    public record HistoryPoint(LocalDate asOfDate, Long publicationBatchId, Long sourceBatchId,
                               BigDecimal rsRankPercentile, BigDecimal currentRank,
                               BigDecimal meanRankPercentile, String qualityStatus) {
    }

    public record SectorDetail(Long sectorId, String sectorName, String providerSectorId,
                               String taxonomyVersion, String market, int windowDays,
                               String analysisMode, boolean rotationAvailable,
                               String scope, String scopeDescription, String leadingName,
                               String leadingSymbol, String trackingSymbol, List<HistoryPoint> history,
                               LocalDateTime sourceQuoteTime, int actualItemCount,
                               int expectedItemCount, BigDecimal coverageRate,
                               String qualityStatus, List<String> reasonCodes) {
    }
}
