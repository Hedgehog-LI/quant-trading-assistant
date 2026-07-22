package com.quant.trade.marketdata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 全市场板块排行自动采集配置。 */
public record UpdateMarketSectorRankingConfigDTO(
        @NotNull Boolean enabled,
        @NotNull @Min(0) @Max(60) Integer intradayIntervalMinutes,
        @NotNull Boolean closeSnapshotEnabled,
        @NotNull @Min(1) @Max(100) Integer rankLimit) {
}
