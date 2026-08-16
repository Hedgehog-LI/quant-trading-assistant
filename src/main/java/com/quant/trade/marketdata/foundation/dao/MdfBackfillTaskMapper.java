package com.quant.trade.marketdata.foundation.dao;

import com.quant.trade.marketdata.foundation.model.MdfBackfillTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 历史回补任务 Mapper（claim token 防并发，先例 MarketDataSyncPlanMapper.tryClaimRun）。 */
@Mapper
public interface MdfBackfillTaskMapper {

    int insert(MdfBackfillTaskDO task);

    MdfBackfillTaskDO selectById(@Param("id") Long id);

    List<MdfBackfillTaskDO> selectList(@Param("status") String status,
                                       @Param("offset") int offset, @Param("limit") int limit);

    long countAll(@Param("status") String status);

    /**
     * 认领执行：仅 PENDING/PAUSED/PARTIAL_FAILED/FAILED 可进入 RUNNING；已有有效 claim（未超时）拒绝。
     * 返回 1=认领成功，0=状态不允许或被他人持有。
     */
    int tryClaim(@Param("id") Long id, @Param("token") String token,
                 @Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);

    /** 释放 claim（token 不匹配不释放）。 */
    int releaseClaim(@Param("id") Long id, @Param("token") String token);

    /** RUNNING→PAUSED 并释放 claim（暂停入口；执行循环每分片前检查状态即停止）。 */
    int pauseIfRunning(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 全字段按 id 更新（计数/状态/错误摘要）。 */
    int updateById(MdfBackfillTaskDO task);

    /** 同 scope 活跃（PENDING/RUNNING/PAUSED）任务数，创建时防重。 */
    long countActiveByScope(@Param("datasetCode") String datasetCode, @Param("providerCode") String providerCode,
                            @Param("adjustType") String adjustType, @Param("startDate") java.time.LocalDate startDate,
                            @Param("endDate") java.time.LocalDate endDate, @Param("symbolsHash") String symbolsHash);
}
