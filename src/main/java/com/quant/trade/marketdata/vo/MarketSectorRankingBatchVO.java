package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 全市场板块排行历史批次。 */
public record MarketSectorRankingBatchVO(
        Long id, String providerCode, String marketCode, LocalDate tradeDate,
        String snapshotType, LocalDateTime snapshotBucketTime, LocalDateTime snapshotTime,
        Integer itemCount, Integer risingCount, Integer fallingCount, Integer flatCount,
        String leaderSectorId, String leaderSectorName, BigDecimal leaderChangeRate,
        String laggardSectorId, String laggardSectorName, BigDecimal laggardChangeRate,
        String qualityStatus) {
}
