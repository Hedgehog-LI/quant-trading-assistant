package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 板块成员 DO（market_segment_member）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSegmentMemberDO {
    private Long id;
    private Long segmentId;
    private String canonicalSymbol;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
