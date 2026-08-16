package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfSymbolBarStatDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 质量检查只读聚合 Mapper：对 stock_daily_bar / market_calendar / mdf_industry_membership 做聚合计数。
 * 只读、不写任何表；symbols 为空时不加 symbol 过滤（全源口径）。
 */
@Mapper
public interface MdfQualitySourceMapper {

    /** 版本事实范围：按 symbol 聚合行数/首末日（dataSource+adjustType+日期窗口过滤）。 */
    List<MdfSymbolBarStatDO> selectSymbolStats(@Param("dataSource") String dataSource,
                                               @Param("adjustType") String adjustType,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("symbols") List<String> symbols);

    /** 行数：OHLC 违法（high&lt;low / high&lt;open / high&lt;close / low&gt;open / low&gt;close / 价格&lt;=0）。 */
    long countOhlcViolations(@Param("dataSource") String dataSource, @Param("adjustType") String adjustType,
                             @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                             @Param("symbols") List<String> symbols);

    /** 行数：单位异常（amount/volume 落在 [low,high] 外 或 volume&lt;=0 或 amount&lt;0；volume=0 跳过 VWAP 判定）。 */
    long countUnitAnomalies(@Param("dataSource") String dataSource, @Param("adjustType") String adjustType,
                            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                            @Param("symbols") List<String> symbols);

    /** 行数：非交易日（周末；calendarDays>0 时日历非空，同时要求命中 is_trading_day=TRUE）。 */
    long countNonTradingDayRows(@Param("marketCode") String marketCode,
                                @Param("dataSource") String dataSource, @Param("adjustType") String adjustType,
                                @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                @Param("symbols") List<String> symbols,
                                @Param("calendarDays") long calendarDays);

    /** 行数：同 symbol+同日出现多于一行（跨 data_source/adjust_type 共存=重复/混用证据）。 */
    long countDuplicateSymbolDateRows(@Param("dataSource") String dataSource,
                                      @Param("adjustType") String adjustType,
                                      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                      @Param("symbols") List<String> symbols);

    /** 行数：与版本声明口径不符（adjust_type 不等于声明值，或 data_source 出现声明源之外的来源）。 */
    long countProviderAdjustMixingRows(@Param("expectedDataSource") String expectedDataSource,
                                       @Param("expectedAdjustType") String expectedAdjustType,
                                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                       @Param("symbols") List<String> symbols);

    /** 日历交易日数（CN；无日历行返回 0）。 */
    long countCalendarDays(@Param("marketCode") String marketCode,
                           @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** R1 §四：日历交易日列表（覆盖期望计算）。 */
    List<LocalDate> selectCalendarDates(@Param("marketCode") String marketCode,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    /** R1 §四：范围证券的 stock_basic 上市日（LISTED/UNKNOWN；list_date 缺失由调用方显式假设）。 */
    List<com.quant.trade.marketdata.foundation.model.MdfSymbolExpectationDO> selectListedSymbols(
            @Param("marketCode") String marketCode,
            @Param("symbols") List<String> symbols);

    /** 事实行最大 fetched_at（陈旧度；无行返回 NULL）。 */
    LocalDateTime selectMaxFetchedAt(@Param("dataSource") String dataSource, @Param("adjustType") String adjustType,
                                     @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                     @Param("symbols") List<String> symbols);
}
