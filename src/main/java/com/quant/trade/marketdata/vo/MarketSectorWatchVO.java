package com.quant.trade.marketdata.vo;

import java.time.LocalDateTime;

/** 行业关注及其最新快照。 */
public record MarketSectorWatchVO(Long id, String providerCode, String providerSectorId,
                                  String marketCode, String sectorName, String topName,
                                  String trackingSymbol, Boolean enabled, Boolean autoCollectEnabled,
                                  Integer collectIntervalMinutes, LocalDateTime lastRefreshedAt,
                                  LocalDateTime lastAutoCollectedAt, String collectionState,
                                  LocalDateTime nextRetryAt, Integer consecutiveFailures,
                                  String lastErrorCode, String lastError, LocalDateTime createdAt, LocalDateTime updatedAt,
                                  MarketSectorSnapshotVO latestSnapshot) {
}
