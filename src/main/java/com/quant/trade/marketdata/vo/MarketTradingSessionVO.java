package com.quant.trade.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 交易时段 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketTradingSessionVO {
    private Long id;
    private String marketCode;
    private String sessionType;
    private String sessionName;
    private String startTime;
    private String endTime;
    private Boolean isAuction;
    private Integer sortOrder;
    private Boolean enabled;
}
