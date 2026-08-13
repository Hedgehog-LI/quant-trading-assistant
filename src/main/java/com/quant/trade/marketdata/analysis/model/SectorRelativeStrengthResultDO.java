package com.quant.trade.marketdata.analysis.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 板块相对强弱衍生结果。 */
@Data
@Builder
public class SectorRelativeStrengthResultDO {
    private Long calculationRunId;
    private Long sectorIdentityId;
    private LocalDate asOfDate;
    private Integer windowDays;
    private BigDecimal sectorReturn;
    private BigDecimal benchmarkReturn;
    private BigDecimal relativeReturn;
    private BigDecimal rsRankPercentile;
    private String qualityStatus;
    private String reasonCodes;
}
