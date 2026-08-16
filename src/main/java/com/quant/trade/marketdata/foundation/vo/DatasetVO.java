package com.quant.trade.marketdata.foundation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 数据集定义 VO（含当前发布版本指针）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetVO {
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
}
