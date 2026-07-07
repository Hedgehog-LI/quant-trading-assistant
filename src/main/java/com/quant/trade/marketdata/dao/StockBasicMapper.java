package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.StockBasicDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockBasicMapper {
    int insert(StockBasicDO record);
    int updateById(StockBasicDO record);
    StockBasicDO selectById(@Param("id") Long id);
    StockBasicDO selectByCanonicalSymbol(@Param("canonicalSymbol") String canonicalSymbol);
    List<StockBasicDO> selectByFilter(@Param("market") String market, @Param("keyword") String keyword,
                                      @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("market") String market, @Param("keyword") String keyword);
    int deleteByCanonicalSymbol(@Param("canonicalSymbol") String canonicalSymbol);
    List<StockBasicDO> selectByCanonicalSymbols(@Param("ids") List<String> canonicalSymbols);
}
