package com.quant.trade.marketdata.analysis.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 板块轮动持续性衍生结果。 */
@Data
@Builder
public class SectorRotationPersistenceResultDO {
    private Long calculationRunId;
    private Long sectorIdentityId;
    private LocalDate asOfDate;
    private Integer windowDays;
    private BigDecimal currentRank;
    private BigDecimal previousRank;
    private BigDecimal meanRankPercentile;
    private BigDecimal rankPercentileStdDev;
    private BigDecimal topBucketOccupancyRate;
    private Integer consecutiveLeadingDays;
    private Integer consecutiveLaggingDays;
    private BigDecimal rankPercentileChange;
    private String qualityStatus;
    private String reasonCodes;
}
