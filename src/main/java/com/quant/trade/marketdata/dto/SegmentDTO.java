package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 创建/更新板块 DTO。 */
@Data
public class SegmentDTO {

    @NotBlank(message = "板块名称不能为空")
    private String segmentName;

    @Pattern(regexp = "^(CUSTOM|INDUSTRY|CONCEPT|INDEX)$", message = "segmentType 必须为 CUSTOM/INDUSTRY/CONCEPT/INDEX")
    private String segmentType;

    private String description;
    private Boolean enabled;
}
