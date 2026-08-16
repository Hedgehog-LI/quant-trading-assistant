package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfIndustryTaxonomyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 行业分类体系 Mapper。 */
@Mapper
public interface MdfIndustryTaxonomyMapper {

    int upsert(MdfIndustryTaxonomyDO taxonomy);

    MdfIndustryTaxonomyDO selectByCode(@Param("taxonomyCode") String taxonomyCode);

    List<MdfIndustryTaxonomyDO> selectAll();
}
