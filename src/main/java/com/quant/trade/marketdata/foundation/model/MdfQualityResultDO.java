package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 数据质量检查结果 DO（mdf_quality_result；FAIL 或空数据阻断发布）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfQualityResultDO {
    private Long id;
    private Long datasetVersionId;
    private String checkCode;
    private String status;
    private Long affectedCount;
    private String detailJson;
    private LocalDateTime checkedAt;
    private LocalDateTime createdAt;
}
