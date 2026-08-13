package com.quant.trade.marketdata.analysis.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 已发布板块研究查询行。 */
@Data
public class MarketResearchRowDO {
    private Long publicationBatchId;
    private Long calculationRunId;
    private Long momentumCalculationRunId;
    private String providerCode;
    private String marketCode;
    private LocalDate asOfDate;
    private Integer windowDays;
    private Integer momentumWindowDays;
    private String formulaVersion;
    private String parameterHash;
    private String scopeCode;
    private String publicationQualityStatus;
    private LocalDateTime publishedAt;
    private LocalDateTime sourceQuoteTime;
    private Integer actualItemCount;
    private Long sectorId;
    private String sectorName;
    private String providerSectorId;
    private String taxonomyVersion;
    private BigDecimal sectorReturn;
    private BigDecimal benchmarkReturn;
    private BigDecimal relativeReturn;
    private BigDecimal rsRankPercentile;
    private BigDecimal currentRank;
    private BigDecimal previousRank;
    private BigDecimal meanRankPercentile;
    private BigDecimal rankPercentileStdDev;
    private BigDecimal topBucketOccupancyRate;
    private Integer consecutiveLeadingDays;
    private Integer consecutiveLaggingDays;
    private BigDecimal rankPercentileChange;
    private String leadingName;
    private String leadingSymbol;
    private String trackingSymbol;
}
