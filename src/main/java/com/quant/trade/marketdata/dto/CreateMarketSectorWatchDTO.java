package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 从 provider 行业目录创建关注。 */
@Data
public class CreateMarketSectorWatchDTO {
    @NotBlank(message = "market 不能为空")
    @Pattern(regexp = "^(CN|HK|US)$", message = "market 必须为 CN/HK/US")
    private String market;

    @NotBlank(message = "providerSectorId 不能为空")
    private String providerSectorId;

    /** 可选关联 ETF/指数统一证券代码，用现有行情采集计划持续沉淀 K 线。 */
    private String trackingSymbol;
}
