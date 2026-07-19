package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

/** 采集计划 Mapper。 */
@Mapper
public interface MarketDataSyncPlanMapper {
    int insert(MarketDataSyncPlanDO record);
    int updateById(MarketDataSyncPlanDO record);
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled,
                      @Param("updatedAt") java.time.LocalDateTime updatedAt);
    int updateLastRun(@Param("id") Long id, @Param("lastTaskId") Long lastTaskId,
                      @Param("lastRunAt") java.time.LocalDateTime lastRunAt);
    MarketDataSyncPlanDO selectById(@Param("id") Long id);
    List<MarketDataSyncPlanDO> selectByFilter(@Param("taskType") String taskType,
                                              @Param("provider") String provider,
                                              @Param("enabled") Boolean enabled,
                                              @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("taskType") String taskType,
                       @Param("provider") String provider,
                       @Param("enabled") Boolean enabled);
    List<MarketDataSyncPlanDO> selectAutoTriggerPlans(@Param("taskType") String taskType,
                                                      @Param("triggerType") String triggerType,
                                                      @Param("enabled") Boolean enabled);
    int tryClaimRun(@Param("id") Long id, @Param("token") String token,
                    @Param("claimedAt") LocalDateTime claimedAt,
                    @Param("staleBefore") LocalDateTime staleBefore);
    int setRunningTask(@Param("id") Long id, @Param("token") String token, @Param("taskId") Long taskId);
    int releaseRunClaim(@Param("id") Long id, @Param("token") String token);
    List<MarketDataSyncPlanDO> selectClaimedPlans();
}
