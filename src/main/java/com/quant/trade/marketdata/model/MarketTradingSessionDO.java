package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 交易时段 DO（market_trading_session）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketTradingSessionDO {
    private Long id;
    private String marketCode;
    private String sessionType;
    private String sessionName;
    private String startTime;
    private String endTime;
    private Boolean isAuction;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
