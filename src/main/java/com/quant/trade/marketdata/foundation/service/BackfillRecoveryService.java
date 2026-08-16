package com.quant.trade.marketdata.foundation.service;

import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillChunkMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskMapper;
import com.quant.trade.marketdata.foundation.model.MdfBackfillTaskDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回补崩溃恢复（Repair R1 §三）。
 *
 * claim 超时/丢失的 RUNNING 任务 → 条件回队 QUEUED；其 RUNNING chunk → PENDING（attempts 保留、
 * 错误置 RECOVERED 标记可追溯）。全部条件更新：重复恢复幂等（第二次无命中）；正常执行中的任务
 * 因 claim 未超时不受影响。由启动runner与定时调度调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillRecoveryService {

    /** 恢复扫描批量。 */
    private static final int RECOVERY_BATCH = 50;

    private final MdfBackfillTaskMapper taskMapper;
    private final MdfBackfillChunkMapper chunkMapper;
    private final TransactionTemplate txRequiresNew;
    private final Clock marketDataClock;

    /** 恢复指定时限前 claim 的僵尸任务 + QUEUED 无 claim 任务的残留 RUNNING 分片；返回恢复条数（幂等）。 */
    public int recoverStaleTasks(int staleMinutes) {
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        List<MdfBackfillTaskDO> stale = taskMapper.selectStaleRunning(now.minusMinutes(staleMinutes), RECOVERY_BATCH);
        int recovered = 0;
        for (MdfBackfillTaskDO task : stale) {
            // R2 §二：任务回队与 RUNNING chunk→PENDING 同一事务（避免半恢复状态被新 worker 抢占）
            Integer requeued = txRequiresNew.execute(status -> {
                int rows = taskMapper.requeueStaleRunning(task.getId(), now, now.minusMinutes(staleMinutes));
                if (rows == 1) {
                    chunkMapper.resetRunningToPending(task.getId(), FoundationConstants.RECOVERY_STALE_CODE,
                            "claim 超时/丢失恢复：RUNNING→PENDING（attempts 保留）");
                }
                return rows;
            });
            if (requeued != null && requeued == 1) {
                recovered++;
                log.warn("回补任务崩溃恢复: taskId={}, 上次claimAt={}", task.getId(), task.getClaimedAt());
            }
        }
        // worker 重派产生的 QUEUED 任务可能残留 RUNNING 分片（前一 worker 崩溃）：复位后可继续
        for (MdfBackfillTaskDO task
                : taskMapper.selectQueuedUnclaimedWithRunningChunks(RECOVERY_BATCH)) {
            int reset = txRequiresNew.execute(status -> chunkMapper.resetRunningToPending(
                    task.getId(), FoundationConstants.RECOVERY_STALE_CODE,
                    "QUEUED 残留 RUNNING 分片恢复：→PENDING（attempts 保留）"));
            if (reset > 0) {
                recovered++;
                log.warn("QUEUED 任务残留分片恢复: taskId={}, resetChunks={}", task.getId(), reset);
            }
        }
        return recovered;
    }
}
