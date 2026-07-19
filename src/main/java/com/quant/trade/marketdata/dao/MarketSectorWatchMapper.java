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
    int updateRefreshResult(@Param("id") Long id, @Param("refreshedAt") LocalDateTime refreshedAt,
                            @Param("lastError") String lastError);
    int deleteById(@Param("id") Long id);
}
