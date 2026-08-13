package com.quant.trade.marketdata.analysis.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 板块衍生公式计算运行及来源血缘。 */
@Data
@Builder
public class SectorAnalyticsCalculationRunDO {
    private Long id;
    private String providerCode;
    private String marketCode;
    private LocalDate asOfDate;
    private String formulaCode;
    private String formulaVersion;
    private Integer windowDays;
    private String parameterHash;
    private String sourceManifestHash;
    private String sourceManifest;
    private String status;
    private String qualityStatus;
    private String reasonCodes;
    private Integer sampleSize;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
