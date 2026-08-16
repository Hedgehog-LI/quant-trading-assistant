package com.quant.trade.marketdata.foundation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 质量检查结果 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityResultVO {
    private Long datasetVersionId;
    private String checkCode;
    private String status;
    private Long affectedCount;
    private String detailJson;
    private LocalDateTime checkedAt;
}
