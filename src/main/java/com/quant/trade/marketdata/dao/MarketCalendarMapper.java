package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketCalendarDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 交易日历 Mapper。 */
@Mapper
public interface MarketCalendarMapper {
    int insert(MarketCalendarDO record);
    int batchInsert(@Param("list") List<MarketCalendarDO> list);
    MarketCalendarDO selectByMarketAndDate(@Param("marketCode") String marketCode,
                                           @Param("tradeDate") LocalDate tradeDate);
    List<MarketCalendarDO> selectByRange(@Param("marketCode") String marketCode,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate,
                                         @Param("isTradingDay") Boolean isTradingDay);
    /** 查询指定日期及之后的最近交易日（含当天）。 */
    MarketCalendarDO selectLatestTradingDayOnOrBefore(@Param("marketCode") String marketCode,
                                                      @Param("tradeDate") LocalDate tradeDate);
    int countByMarket(@Param("marketCode") String marketCode);
}
