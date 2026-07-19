package com.quant.trade.marketdata.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Provider 行业关注 DO。 */
@Data
@Builder
public class MarketSectorWatchDO {
    private Long id;
    private String providerCode;
    private String providerSectorId;
    private String marketCode;
    private String sectorName;
    private String topName;
    private String trackingSymbol;
    private Boolean enabled;
    private LocalDateTime lastRefreshedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
