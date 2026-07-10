package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketDataAlertDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MarketDataAlertMapper {
    int insert(MarketDataAlertDO record);
    MarketDataAlertDO selectById(@Param("id") Long id);
    List<MarketDataAlertDO> selectByFilter(@Param("resolved") Boolean resolved, @Param("severity") String severity,
                                           @Param("canonicalSymbol") String symbol,
                                           @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("resolved") Boolean resolved, @Param("severity") String severity,
                       @Param("canonicalSymbol") String symbol);
    int updateResolved(@Param("id") Long id, @Param("resolved") Boolean resolved);
}
