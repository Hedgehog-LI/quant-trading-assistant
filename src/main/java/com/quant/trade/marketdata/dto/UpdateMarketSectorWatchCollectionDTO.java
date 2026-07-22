package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 关注板块自动采集配置。 */
public record UpdateMarketSectorWatchCollectionDTO(
        @NotNull Boolean autoCollectEnabled,
        @NotNull @Min(5) @Max(60) Integer collectIntervalMinutes) {
}
