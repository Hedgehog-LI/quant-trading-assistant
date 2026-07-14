package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketSegmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 板块 Mapper。 */
@Mapper
public interface MarketSegmentMapper {
    int insert(MarketSegmentDO record);
    int updateById(MarketSegmentDO record);
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);
    int deleteById(@Param("id") Long id);
    MarketSegmentDO selectById(@Param("id") Long id);
    MarketSegmentDO selectByCode(@Param("segmentCode") String segmentCode);
    List<MarketSegmentDO> selectByFilter(@Param("segmentType") String segmentType,
                                         @Param("enabled") Boolean enabled,
                                         @Param("keyword") String keyword,
                                         @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("segmentType") String segmentType,
                       @Param("enabled") Boolean enabled,
                       @Param("keyword") String keyword);
}
