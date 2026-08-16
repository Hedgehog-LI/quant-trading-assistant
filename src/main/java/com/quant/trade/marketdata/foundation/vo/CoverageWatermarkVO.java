package com.quant.trade.marketdata.foundation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 覆盖水位行 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageWatermarkVO {
    private Long datasetVersionId;
    private String canonicalSymbol;
    private LocalDate firstDate;
    private LocalDate lastDate;
    private Long rowCount;
    private Long expectedDays;
    private Long coveredDays;
    private BigDecimal coverageRatio;
    private LocalDateTime calculatedAt;
}
