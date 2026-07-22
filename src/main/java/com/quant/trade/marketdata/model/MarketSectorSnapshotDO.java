package com.quant.trade.marketdata.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 行业聚合快照 DO。 */
@Data
@Builder
public class MarketSectorSnapshotDO {
    private Long id;
    private Long watchId;
    private LocalDateTime snapshotTime;
    private LocalDateTime snapshotBucketTime;
    private String triggerType;
    private LocalDateTime fetchedAt;
    private String rankIndicator;
    private BigDecimal changeRate;
    private BigDecimal yearToDateChangeRate;
    private String leadingName;
    private String leadingSymbol;
    private BigDecimal leadingChangeRate;
    private Integer constituentCount;
    private Integer riseCount;
    private Integer fallCount;
    private Integer flatCount;
    private BigDecimal totalNetInflow;
    private BigDecimal totalTurnoverAmount;
    private BigDecimal totalVolume;
    private String dataSource;
    private Integer expectedMemberCount;
    private Integer validMemberCount;
    private Integer delayedMemberCount;
    private Integer unmappedMemberCount;
    private String qualityStatus;
    private LocalDateTime createdAt;
}
