package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketSectorWatchDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Provider 行业关注 Mapper。 */
@Mapper
public interface MarketSectorWatchMapper {
    int insert(MarketSectorWatchDO record);
    MarketSectorWatchDO selectById(@Param("id") Long id);
    MarketSectorWatchDO selectByProviderSector(@Param("providerCode") String providerCode,
                                                @Param("providerSectorId") String providerSectorId);
    List<MarketSectorWatchDO> selectAll(@Param("marketCode") String marketCode);
    int updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);
    int updateCollectionConfig(@Param("id") Long id, @Param("autoCollectEnabled") boolean autoCollectEnabled,
                               @Param("collectIntervalMinutes") int collectIntervalMinutes);
    List<MarketSectorWatchDO> selectAutoRunnable(@Param("now") LocalDateTime now);
    int tryClaim(@Param("id") Long id, @Param("token") String token,
                 @Param("claimedAt") LocalDateTime claimedAt, @Param("staleBefore") LocalDateTime staleBefore);
    int releaseClaim(@Param("id") Long id, @Param("token") String token);
    int markCollectionSuccess(@Param("id") Long id, @Param("refreshedAt") LocalDateTime refreshedAt,
                              @Param("autoCollected") boolean autoCollected, @Param("token") String token);
    int markCollectionFailure(@Param("id") Long id, @Param("state") String state,
                              @Param("nextRetryAt") LocalDateTime nextRetryAt,
                              @Param("errorCode") String errorCode, @Param("lastError") String lastError,
                              @Param("token") String token);
    int updateRefreshResult(@Param("id") Long id, @Param("refreshedAt") LocalDateTime refreshedAt,
                            @Param("lastError") String lastError);
    int deleteById(@Param("id") Long id);
}
