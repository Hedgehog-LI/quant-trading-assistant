package com.quant.trade.marketdata.asset.dao;

import com.quant.trade.marketdata.asset.model.MarketDataAssetCatalogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 已入库行情资产目录只读 Mapper。 */
@Mapper
public interface MarketDataAssetCatalogMapper {

    List<MarketDataAssetCatalogDO> selectByFilter(@Param("market") String market,
                                                   @Param("keyword") String keyword,
                                                   @Param("limit") int limit,
                                                   @Param("offset") int offset);

    long countByFilter(@Param("market") String market, @Param("keyword") String keyword);
}
