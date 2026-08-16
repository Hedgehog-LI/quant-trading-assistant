package com.quant.trade.marketdata.foundation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建数据集定义请求。 */
@Data
public class CreateDatasetDTO {

    @NotBlank(message = "datasetCode 不能为空")
    @Size(max = 64)
    private String datasetCode;

    @NotBlank(message = "datasetName 不能为空")
    @Size(max = 128)
    private String datasetName;

    @NotBlank(message = "marketCode 不能为空")
    private String marketCode;

    @NotBlank(message = "barType 不能为空")
    private String barType;

    @NotBlank(message = "frequency 不能为空")
    private String frequency;

    @NotBlank(message = "providerCode 不能为空")
    private String providerCode;

    @NotBlank(message = "adjustType 不能为空")
    private String adjustType;

    @Size(max = 512)
    private String description;
}
