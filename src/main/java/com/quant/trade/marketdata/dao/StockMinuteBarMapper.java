package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.StockBarAvailabilityDO;
import com.quant.trade.marketdata.model.StockMinuteBarDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 分钟 K 线 Mapper。 */
@Mapper
public interface StockMinuteBarMapper {
    int insert(StockMinuteBarDO record);
    int batchInsert(@Param("list") List<StockMinuteBarDO> list);
    StockMinuteBarDO selectByUniqueKey(@Param("canonicalSymbol") String canonicalSymbol,
                                       @Param("barStartTime") LocalDateTime barStartTime,
                                       @Param("intervalType") String intervalType,
                                       @Param("adjustType") String adjustType,
                                       @Param("dataSource") String dataSource);
    int updateByUniqueKey(StockMinuteBarDO record);
    List<StockMinuteBarDO> selectByFilter(@Param("canonicalSymbol") String canonicalSymbol,
                                          @Param("intervalType") String intervalType,
                                          @Param("adjustType") String adjustType,
                                          @Param("dataSource") String dataSource,
                                          @Param("fromTime") LocalDateTime fromTime,
                                          @Param("toTime") LocalDateTime toTime,
                                          @Param("tradeDate") String tradeDate,
                                          @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("canonicalSymbol") String canonicalSymbol,
                       @Param("intervalType") String intervalType,
                       @Param("adjustType") String adjustType,
                       @Param("dataSource") String dataSource,
                       @Param("fromTime") LocalDateTime fromTime,
                       @Param("toTime") LocalDateTime toTime,
                       @Param("tradeDate") String tradeDate);
    long countByCanonicalSymbol(@Param("canonicalSymbol") String canonicalSymbol);

    /** availability：按 interval_type + data_source + adjust_type 聚合该证券存在的分钟 K 组合（只读）。 */
    List<StockBarAvailabilityDO> selectMinuteAvailability(@Param("canonicalSymbol") String canonicalSymbol);
}
