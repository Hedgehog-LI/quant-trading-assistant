package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 板块 DO（market_segment）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSegmentDO {
    private Long id;
    private String segmentCode;
    private String segmentName;
    private String segmentType;
    private String description;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
