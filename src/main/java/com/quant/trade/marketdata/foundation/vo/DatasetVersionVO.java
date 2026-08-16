package com.quant.trade.marketdata.foundation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 数据集版本 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetVersionVO {
    private Long id;
    private Long datasetId;
    private String datasetCode;
    private String versionCode;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String sourceProvider;
    private String sourceNote;
    private Long rowCount;
    private LocalDateTime qualifiedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private Boolean isCurrentReleased;
}
