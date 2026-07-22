package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketSectorRankingBatchDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 全市场行业排行批次 Mapper。 */
@Mapper
public interface MarketSectorRankingBatchMapper {
    int insert(MarketSectorRankingBatchDO record);
    MarketSectorRankingBatchDO selectById(@Param("id") Long id);
    MarketSectorRankingBatchDO selectByBucket(@Param("providerCode") String providerCode,
                                              @Param("marketCode") String marketCode,
                                              @Param("snapshotType") String snapshotType,
                                              @Param("snapshotBucketTime") LocalDateTime snapshotBucketTime);
    List<MarketSectorRankingBatchDO> selectByFilter(@Param("marketCode") String marketCode,
                                                    @Param("tradeDate") LocalDate tradeDate,
                                                    @Param("snapshotType") String snapshotType,
                                                    @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("marketCode") String marketCode, @Param("tradeDate") LocalDate tradeDate,
                       @Param("snapshotType") String snapshotType);
}
