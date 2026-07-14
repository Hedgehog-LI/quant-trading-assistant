package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 添加板块成员 DTO。 */
@Data
public class SegmentMemberDTO {

    @NotBlank(message = "canonicalSymbol 不能为空")
    private String canonicalSymbol;

    private Integer sortOrder;
    private String remark;
}
