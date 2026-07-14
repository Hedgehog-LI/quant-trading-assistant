package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketSegmentMemberDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 板块成员 Mapper。 */
@Mapper
public interface MarketSegmentMemberMapper {
    int insert(MarketSegmentMemberDO record);
    int batchInsert(@Param("list") List<MarketSegmentMemberDO> list);
    int deleteById(@Param("id") Long id);
    int deleteBySegmentAndSymbol(@Param("segmentId") Long segmentId, @Param("canonicalSymbol") String canonicalSymbol);
    List<MarketSegmentMemberDO> selectBySegmentId(@Param("segmentId") Long segmentId);
    int countBySegmentId(@Param("segmentId") Long segmentId);
}
