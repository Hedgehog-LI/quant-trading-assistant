package com.quant.trade.marketdata.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 行业成分证券快照 DO。 */
@Data
@Builder
public class MarketSectorMemberSnapshotDO {
    private Long id;
    private Long snapshotId;
    private String canonicalSymbol;
    private String securityName;
    private BigDecimal currentPrice;
    private BigDecimal previousClose;
    private BigDecimal changeRate;
    private BigDecimal netInflow;
    private BigDecimal turnoverAmount;
    private BigDecimal volume;
    private BigDecimal totalShares;
    private BigDecimal circulatingShares;
    private String tags;
    private Integer tradeStatus;
    private Boolean delayed;
    private LocalDateTime createdAt;
}
