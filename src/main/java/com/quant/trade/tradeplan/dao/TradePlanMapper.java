package com.quant.trade.tradeplan.dao;

import com.quant.trade.tradeplan.model.TradePlanDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易计划 MyBatis Mapper。
 */
@Mapper
public interface TradePlanMapper {

    int insert(TradePlanDO record);

    int updateById(TradePlanDO record);

    TradePlanDO selectById(@Param("id") Long id);

    TradePlanDO selectBySymbolAndDate(@Param("symbol") String symbol,
                                      @Param("planDate") LocalDate planDate);

    int existsBySymbolAndDate(@Param("symbol") String symbol,
                              @Param("planDate") LocalDate planDate);

    List<TradePlanDO> selectByFilter(@Param("date") LocalDate date,
                                     @Param("symbol") String symbol);

    long countActiveByDate(@Param("planDate") LocalDate planDate);

    long countByDate(@Param("planDate") LocalDate planDate);

    /**
     * 根据主键物理删除。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
