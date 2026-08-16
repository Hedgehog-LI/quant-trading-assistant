package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfIndustryMembershipDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** PIT 行业成分 Mapper（半开区间；重叠/无效区间由质量检查双防线）。 */
@Mapper
public interface MdfIndustryMembershipMapper {

    int upsertBatch(@Param("list") List<MdfIndustryMembershipDO> rows);

    /** 同 taxonomy 同 symbol 区间重叠对数（m1.effective_from < COALESCE(m2.to,∞) AND m2.from < COALESCE(m1.to,∞)）。 */
    long countOverlapPairs(@Param("taxonomyCode") String taxonomyCode);

    /** effective_to 非空且 <= effective_from 的无效区间行数。 */
    long countInvalidPeriods(@Param("taxonomyCode") String taxonomyCode);

    long countByTaxonomy(@Param("taxonomyCode") String taxonomyCode);

    long countDistinctSymbols(@Param("taxonomyCode") String taxonomyCode);
}
