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

    /** R1：入队（PENDING/PAUSED/PARTIAL_FAILED/FAILED→QUEUED，释放 claim；POST run 只做此转换立即返回）。 */
    int markQueued(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** R1：worker 认领 QUEUED→RUNNING（条件 UPDATE，双 worker 仅一个成功）。 */
    int claimQueued(@Param("id") Long id, @Param("token") String token,
                    @Param("now") LocalDateTime now);

    /** R2 §一：心跳续租（id+status=RUNNING+token 三重校验）；返回 0=所有权已丢失，worker 必须立即停止。 */
    int heartbeat(@Param("id") Long id, @Param("token") String token, @Param("now") LocalDateTime now);

    /** R2 §一/§二：按所有权栅栏更新任务（仅持有 token 且 RUNNING 时生效；旧 token 不可覆盖新 owner 状态）。 */
    int updateByIdIfOwner(@Param("task") MdfBackfillTaskDO task, @Param("token") String token);

    /** R1：QUEUED/RUNNING 均可暂停（PAUSED 释放 claim）。 */
    int pauseIfActive(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** R1：崩溃恢复——claim 超时/丢失的 RUNNING 条件回队（幂等：仅命中时更新）。 */
    int requeueStaleRunning(@Param("id") Long id, @Param("now") LocalDateTime now,
                            @Param("staleBefore") LocalDateTime staleBefore);

    /** R1：待恢复的僵尸任务（RUNNING 且 claim 为空或超时）。 */
    List<MdfBackfillTaskDO> selectStaleRunning(@Param("staleBefore") LocalDateTime staleBefore,
                                               @Param("limit") int limit);

    /** 待认领的 QUEUED 任务（worker 轮询取一个；按 queued_at 先进先出）。 */
    MdfBackfillTaskDO selectNextQueued(@Param("now") LocalDateTime now);

    /** R1：按版本反查回补任务（质量检查解析版本 scope=任务证券范围）。 */
    MdfBackfillTaskDO selectByDatasetVersionId(@Param("datasetVersionId") Long datasetVersionId);

    /** R1：QUEUED 且无 claim、但仍有 RUNNING 分片的任务（worker 重派后残留分片的恢复对象）。 */
    List<MdfBackfillTaskDO> selectQueuedUnclaimedWithRunningChunks(@Param("limit") int limit);

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
