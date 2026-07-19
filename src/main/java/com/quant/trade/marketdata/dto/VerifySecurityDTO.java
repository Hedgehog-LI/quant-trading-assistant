package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;

/** 精确证券代码验证请求。 */
public record VerifySecurityDTO(
        @NotBlank(message = "市场不能为空") String market,
        @NotBlank(message = "证券代码不能为空") String code) {
}
