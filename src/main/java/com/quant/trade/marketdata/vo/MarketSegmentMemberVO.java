package com.quant.trade.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 板块成员 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSegmentMemberVO {
    private Long id;
    private Long segmentId;
    private String canonicalSymbol;
    private Integer sortOrder;
    private String remark;
    private String createdAt;
}
