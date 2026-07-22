package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketSectorRankingItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 全市场行业排行明细 Mapper。 */
@Mapper
public interface MarketSectorRankingItemMapper {
    int insertBatch(@Param("records") List<MarketSectorRankingItemDO> records);
    List<MarketSectorRankingItemDO> selectByBatchId(@Param("batchId") Long batchId);
}
