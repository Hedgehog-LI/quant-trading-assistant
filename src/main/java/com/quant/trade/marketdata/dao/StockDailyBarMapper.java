package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.StockDailyBarDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StockDailyBarMapper {
    int insert(StockDailyBarDO record);
    int batchInsert(@Param("list") List<StockDailyBarDO> list);
    StockDailyBarDO selectByUniqueKey(@Param("canonicalSymbol") String canonicalSymbol,
                                      @Param("tradeDate") LocalDate tradeDate,
                                      @Param("adjustType") String adjustType,
                                      @Param("dataSource") String dataSource);
    int updateByUniqueKey(StockDailyBarDO record);
    List<StockDailyBarDO> selectByFilter(@Param("canonicalSymbol") String canonicalSymbol,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate,
                                          @Param("adjustType") String adjustType,
                                          @Param("dataSource") String dataSource,
                                          @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("canonicalSymbol") String canonicalSymbol,
                       @Param("fromDate") LocalDate fromDate,
                       @Param("toDate") LocalDate toDate,
                       @Param("adjustType") String adjustType,
                       @Param("dataSource") String dataSource);
    long countByCanonicalSymbol(@Param("canonicalSymbol") String canonicalSymbol);
}
