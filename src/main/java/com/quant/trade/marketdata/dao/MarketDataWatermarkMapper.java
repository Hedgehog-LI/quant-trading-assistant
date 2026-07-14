package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketDataWatermarkDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 数据水位 Mapper。 */
@Mapper
public interface MarketDataWatermarkMapper {
    int insert(MarketDataWatermarkDO record);
    int updateByUniqueKey(MarketDataWatermarkDO record);
    MarketDataWatermarkDO selectByUniqueKey(@Param("canonicalSymbol") String canonicalSymbol,
                                            @Param("dataSource") String dataSource,
                                            @Param("intervalType") String intervalType,
                                            @Param("adjustType") String adjustType);
    /** upsert 语义：存在则更新，不存在则插入。由 service 层先 select 再决定。 */
    List<MarketDataWatermarkDO> selectByFilter(@Param("canonicalSymbol") String canonicalSymbol,
                                               @Param("dataSource") String dataSource,
                                               @Param("intervalType") String intervalType,
                                               @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("canonicalSymbol") String canonicalSymbol,
                       @Param("dataSource") String dataSource,
                       @Param("intervalType") String intervalType);
}
