package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.model.SecuritySearchCandidateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockBasicMapper {
    int insert(StockBasicDO record);
    int insertDirectory(StockBasicDO record);
    int updateById(StockBasicDO record);
    int updateDirectoryById(StockBasicDO record);
    StockBasicDO selectById(@Param("id") Long id);
    StockBasicDO selectByCanonicalSymbol(@Param("canonicalSymbol") String canonicalSymbol);
    List<StockBasicDO> selectByFilter(@Param("market") String market, @Param("keyword") String keyword,
                                      @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("market") String market, @Param("keyword") String keyword);
    int deleteByCanonicalSymbol(@Param("canonicalSymbol") String canonicalSymbol);
    List<StockBasicDO> selectByCanonicalSymbols(@Param("ids") List<String> canonicalSymbols);
    List<SecuritySearchCandidateDO> searchCandidates(
            @Param("query") String normalizedQuery,
            @Param("likeQuery") String escapedLikeQuery,
            @Param("queryUpper") String queryUpper,
            @Param("canonicalQuery") String canonicalQuery,
            @Param("hkSymbol") String hkSymbol,
            @Param("markets") List<String> markets,
            @Param("types") List<String> types,
            @Param("includeDelisted") boolean includeDelisted);
    long countAll();
    java.time.LocalDateTime selectMaxSourceUpdatedAt();
}
