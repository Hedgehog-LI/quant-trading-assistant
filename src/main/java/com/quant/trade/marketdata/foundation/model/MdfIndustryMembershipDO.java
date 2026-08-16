package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** PIT 行业成分 DO（mdf_industry_membership；半开区间 [from, to)，to=NULL 至今）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfIndustryMembershipDO {
    private Long id;
    private String taxonomyCode;
    private String industryCode;
    private String industryName;
    private String canonicalSymbol;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String sourceProvider;
    private LocalDateTime fetchedAt;
    private LocalDateTime createdAt;
}
