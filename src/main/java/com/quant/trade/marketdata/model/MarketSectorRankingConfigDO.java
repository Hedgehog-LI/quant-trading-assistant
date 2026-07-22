package com.quant.trade.marketdata.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 全市场行业排行采集配置 DO。 */
@Data
@Builder
public class MarketSectorRankingConfigDO {
    private Long id;
    private String providerCode;
    private String marketCode;
    private Boolean enabled;
    private Integer intradayIntervalMinutes;
    private Boolean closeSnapshotEnabled;
    private Integer rankLimit;
    private String executionState;
    private LocalDateTime lastIntradayAt;
    private LocalDate lastCloseTradeDate;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime nextRetryAt;
    private Integer consecutiveFailures;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String runClaimToken;
    private LocalDateTime runClaimedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
