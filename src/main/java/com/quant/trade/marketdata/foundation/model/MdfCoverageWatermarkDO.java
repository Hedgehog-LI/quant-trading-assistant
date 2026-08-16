package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 数据覆盖水位 DO（mdf_coverage_watermark；coverage_ratio=covered/expected）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfCoverageWatermarkDO {
    private Long id;
    private Long datasetVersionId;
    private String canonicalSymbol;
    private LocalDate firstDate;
    private LocalDate lastDate;
    private Long rowCount;
    private Long expectedDays;
    private Long coveredDays;
    private BigDecimal coverageRatio;
    private LocalDateTime calculatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
