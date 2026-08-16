package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 数据集定义 DO（mdf_dataset）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfDatasetDO {
    private Long id;
    private String datasetCode;
    private String datasetName;
    private String marketCode;
    private String barType;
    private String frequency;
    private String providerCode;
    private String adjustType;
    private String unitCaliber;
    private String description;
    private Long currentVersionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
