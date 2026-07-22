package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 行业聚合历史快照。 */
public record MarketSectorSnapshotVO(Long id, Long watchId, LocalDateTime snapshotTime,
                                     LocalDateTime snapshotBucketTime, String triggerType, LocalDateTime fetchedAt,
                                     String rankIndicator, BigDecimal changeRate,
                                     BigDecimal yearToDateChangeRate, String leadingName,
                                     String leadingSymbol, BigDecimal leadingChangeRate,
                                     Integer constituentCount, Integer riseCount, Integer fallCount,
                                     Integer flatCount, BigDecimal totalNetInflow,
                                     BigDecimal totalTurnoverAmount, BigDecimal totalVolume,
                                     String dataSource, Integer expectedMemberCount, Integer validMemberCount,
                                     Integer delayedMemberCount, Integer unmappedMemberCount, String qualityStatus) {
}
