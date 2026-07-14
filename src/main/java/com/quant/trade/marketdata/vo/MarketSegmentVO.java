package com.quant.trade.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 板块 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSegmentVO {
    private Long id;
    private String segmentCode;
    private String segmentName;
    private String segmentType;
    private String description;
    private Boolean enabled;
    private Integer memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
