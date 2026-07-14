package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 更新采集计划 DTO。 */
@Data
public class UpdateSyncPlanDTO {

    @NotBlank(message = "计划名称不能为空")
    private String planName;

    @NotBlank(message = "scope_json 不能为空")
    private String scopeJson;

    @Pattern(regexp = "^$|^(1M|5M|15M|30M|60M|1D)$", message = "intervalType 必须为 1M/5M/15M/30M/60M/1D")
    private String intervalType;

    @Pattern(regexp = "^(NONE|QF|HF)$", message = "adjustType 必须为 NONE/QF/HF")
    private String adjustType;

    @Pattern(regexp = "^(MANUAL|SCHEDULED|INTRADAY)$", message = "triggerType 必须为 MANUAL/SCHEDULED/INTRADAY")
    private String triggerType;

    private String cronExpr;
    private Boolean includeAuction;
    private String collectFrequency;
    private String description;
}
