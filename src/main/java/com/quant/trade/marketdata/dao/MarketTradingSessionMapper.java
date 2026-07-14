package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketTradingSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 交易时段 Mapper。 */
@Mapper
public interface MarketTradingSessionMapper {
    int insert(MarketTradingSessionDO record);
    int batchInsert(@Param("list") List<MarketTradingSessionDO> list);
    int updateById(MarketTradingSessionDO record);
    MarketTradingSessionDO selectById(@Param("id") Long id);
    List<MarketTradingSessionDO> selectByMarket(@Param("marketCode") String marketCode,
                                                @Param("enabled") Boolean enabled);
    int countByMarket(@Param("marketCode") String marketCode);
}
