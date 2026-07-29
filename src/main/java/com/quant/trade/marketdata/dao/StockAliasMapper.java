package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.StockAliasDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockAliasMapper {
    int insert(StockAliasDO record);
    StockAliasDO selectByIdentity(@Param("stockBasicId") Long stockBasicId,
                                  @Param("normalizedAlias") String normalizedAlias,
                                  @Param("aliasType") String aliasType);
    List<StockAliasDO> selectByStockBasicId(@Param("stockBasicId") Long stockBasicId);
    List<StockAliasDO> selectByStockBasicIds(@Param("ids") List<Long> stockBasicIds);
    long countAll();
}
