package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 行业分类体系 DO（mdf_industry_taxonomy；SINA_INDUSTRY 非申万，禁混称）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfIndustryTaxonomyDO {
    private Long id;
    private String taxonomyCode;
    private String taxonomyName;
    private String providerCode;
    private Integer isMutuallyExclusive;
    private String note;
    private LocalDateTime createdAt;
}
