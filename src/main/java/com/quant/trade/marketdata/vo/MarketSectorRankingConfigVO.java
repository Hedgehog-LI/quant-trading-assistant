package com.quant.trade.marketdata.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 全市场板块排行采集配置和运行状态。 */
public record MarketSectorRankingConfigVO(
        Long id, String providerCode, String marketCode, Boolean enabled,
        Integer intradayIntervalMinutes, Boolean closeSnapshotEnabled, Integer rankLimit,
        String executionState, LocalDateTime lastIntradayAt, LocalDate lastCloseTradeDate,
        LocalDateTime lastSuccessAt, LocalDateTime nextRetryAt, Integer consecutiveFailures,
        String lastErrorCode, String lastErrorMessage, LocalDateTime updatedAt) {
}
