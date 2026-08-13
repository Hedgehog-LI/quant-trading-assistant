package com.quant.trade.marketdata.analysis.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 多公式结果的一致发布批次。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorAnalyticsPublicationBatchDO {
    private Long id;
    private String providerCode;
    private String marketCode;
    private LocalDate asOfDate;
    private Integer windowDays;
    private Integer momentumWindowDays;
    private String formulaVersion;
    private String parameterHash;
    private String requiredFormulaSetHash;
    private String sourceManifestGroupHash;
    private String scopeCode;
    private String status;
    private String qualityStatus;
    private LocalDateTime publishedAt;
}
