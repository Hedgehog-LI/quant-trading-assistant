package com.quant.trade.marketdata.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 全市场行业排行采集批次 DO。 */
@Data
@Builder
public class MarketSectorRankingBatchDO {
    private Long id;
    private String providerCode;
    private String marketCode;
    private LocalDate tradeDate;
    private String snapshotType;
    private LocalDateTime snapshotBucketTime;
    private LocalDateTime snapshotTime;
    private Integer itemCount;
    private Integer risingCount;
    private Integer fallingCount;
    private Integer flatCount;
    private String leaderSectorId;
    private String leaderSectorName;
    private BigDecimal leaderChangeRate;
    private String laggardSectorId;
    private String laggardSectorName;
    private BigDecimal laggardChangeRate;
    private String qualityStatus;
    private LocalDateTime createdAt;
}
