package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketSectorRankingConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 全市场行业排行采集配置 Mapper。 */
@Mapper
public interface MarketSectorRankingConfigMapper {
    List<MarketSectorRankingConfigDO> selectAll();
    List<MarketSectorRankingConfigDO> selectRunnable(@Param("now") LocalDateTime now);
    MarketSectorRankingConfigDO selectByMarket(@Param("marketCode") String marketCode);
    int updateConfig(MarketSectorRankingConfigDO record);
    int tryClaim(@Param("id") Long id, @Param("token") String token,
                 @Param("claimedAt") LocalDateTime claimedAt, @Param("staleBefore") LocalDateTime staleBefore);
    int releaseClaim(@Param("id") Long id, @Param("token") String token);
    int markSuccess(@Param("id") Long id, @Param("snapshotType") String snapshotType,
                    @Param("successAt") LocalDateTime successAt, @Param("tradeDate") LocalDate tradeDate,
                    @Param("token") String token);
    int markFailure(@Param("id") Long id, @Param("state") String state,
                    @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("errorCode") String errorCode,
                    @Param("errorMessage") String errorMessage, @Param("token") String token);
}
