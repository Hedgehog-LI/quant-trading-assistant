package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketSectorSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 行业聚合快照 Mapper。 */
@Mapper
public interface MarketSectorSnapshotMapper {
    int insert(MarketSectorSnapshotDO record);
    MarketSectorSnapshotDO selectById(@Param("id") Long id);
    MarketSectorSnapshotDO selectLatestByWatchId(@Param("watchId") Long watchId);
    MarketSectorSnapshotDO selectByWatchAndBucket(@Param("watchId") Long watchId,
                                                   @Param("bucketTime") java.time.LocalDateTime bucketTime);
    List<MarketSectorSnapshotDO> selectByWatchId(@Param("watchId") Long watchId,
                                                  @Param("limit") int limit, @Param("offset") int offset);
    long countByWatchId(@Param("watchId") Long watchId);
}
