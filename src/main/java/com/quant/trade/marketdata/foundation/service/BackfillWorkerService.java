package com.quant.trade.marketdata.foundation.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillChunkMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBarWriteMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper;
import com.quant.trade.marketdata.foundation.model.MdfBackfillChunkDO;
import com.quant.trade.marketdata.foundation.model.MdfBackfillTaskDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.provider.HistoricalBarProvider;
import com.quant.trade.marketdata.foundation.provider.HistoricalBarProviderRegistry;
import com.quant.trade.marketdata.foundation.provider.ProviderDailyBar;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * 回补后台 worker（Repair R1 §二/§三 + R2 §一/§二/§五：所有权 fencing、心跳续租、有界并发）。
 *
 * - DB 为事实源：claimQueued 条件 UPDATE 认领；执行全程持 token。
 * - 心跳/所有权：每个 chunk 开始、每个证券执行前、每次 chunk/task 终态写入前调用
 *   heartbeat(id, token)（id+status=RUNNING+token 三重校验）。返回 0 = 所有权已丢失
 *   （暂停/回队/恢复/新 worker 抢占）→ 旧 worker 立即停止：不再请求 Provider、不写任何状态。
 *   心跳同时刷新 claimed_at，长任务不会被恢复器按 stale 误判。
 * - 写入栅栏：task 终态/计数经 updateByIdIfOwner（WHERE status='RUNNING' AND claim_token=token），
 *   旧 token 永远不能覆盖新 owner 的任务与分片状态。
 * - 有界并发：dataFoundationWorkerSlots 信号量（=worker.concurrency）；hasCapacity 供调度器
 *   跳过饱和提交，避免向已满线程池持续堆积空轮询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillWorkerService {

    private static final int BAR_SELECT_LIMIT = 1_000;

    private final MdfBackfillTaskMapper taskMapper;
    private final MdfBackfillChunkMapper chunkMapper;
    private final MdfDatasetVersionMapper versionMapper;
    private final MdfBarWriteMapper barWriteMapper;
    private final StockDailyBarMapper stockDailyBarMapper;
    private final HistoricalBarProviderRegistry providerRegistry;
    private final VersionLineageService lineageService;
    private final TransactionTemplate txRequiresNew;
    private final Clock marketDataClock;
    private final Semaphore dataFoundationWorkerSlots;

    /**
     * 认领并执行一个 QUEUED 任务（worker 轮询单元；测试直接调用）。
     *
     * @return true=认领成功并已执行到终态/停止点；false=无 QUEUED 任务、争抢失败或并发槽已满。
     */
    public boolean claimAndExecuteOne() {
        if (!dataFoundationWorkerSlots.tryAcquire()) {
            return false;
        }
        try {
            MdfBackfillTaskDO queued = taskMapper.selectNextQueued(LocalDateTime.now(marketDataClock));
            if (queued == null) {
                return false;
            }
            String token = UUID.randomUUID().toString();
            Integer claimed = txRequiresNew.execute(status ->
                    taskMapper.claimQueued(queued.getId(), token, LocalDateTime.now(marketDataClock)));
            if (claimed == null || claimed != 1) {
                return false;
            }
            MdfBackfillTaskDO task = taskMapper.selectById(queued.getId());
            try {
                executeTask(task, token);
                return true;
            } finally {
                txRequiresNew.executeWithoutResult(status -> taskMapper.releaseClaim(task.getId(), token));
            }
        } finally {
            dataFoundationWorkerSlots.release();
        }
    }

    /** R2 §五：调度器提交前检查是否有空闲并发槽（饱和时跳过本轮，不堆积空轮询）。 */
    public boolean hasCapacity() {
        return dataFoundationWorkerSlots.availablePermits() > 0;
    }

    /** 心跳续租；false=所有权已丢失（暂停/回队/恢复/新 owner），调用方必须立即停止。 */
    private boolean owns(long taskId, String token) {
        Integer beat = txRequiresNew.execute(status ->
                taskMapper.heartbeat(taskId, token, LocalDateTime.now(marketDataClock)));
        return beat != null && beat == 1;
    }

    // ---------------------------------------------------------------- 执行（全程所有权栅栏）

    private void executeTask(MdfBackfillTaskDO task, String token) {
        HistoricalBarProvider provider = providerRegistry.require(task.getProviderCode());
        boolean firstRun = FoundationConstants.VERSION_DRAFT.equals(currentVersionStatus(task.getDatasetVersionId()));
        if (firstRun) {
            // 新 owner 才允许推进版本状态（认领后立即校验一次所有权）
            if (!owns(task.getId(), token)) {
                return;
            }
            txRequiresNew.executeWithoutResult(tx -> versionMapper.updateStatus(
                    task.getDatasetVersionId(), FoundationConstants.VERSION_BACKFILLING, null, null, null));
        }
        List<MdfBackfillChunkDO> chunks = chunkMapper.selectByTaskId(task.getId());
        for (MdfBackfillChunkDO chunk : chunks) {
            if (!FoundationConstants.CHUNK_PENDING.equals(chunk.getStatus())) {
                continue;
            }
            // 每个 chunk 执行前：所有权校验（丢失→立即停止，不写任何状态）
            if (!owns(task.getId(), token)) {
                log.info("worker 失去任务所有权，停止执行: taskId={}, 停于 chunkIndex={}",
                        task.getId(), chunk.getChunkIndex());
                return;
            }
            executeChunk(task, chunk, provider, token);
        }
        finalizeTask(task.getId(), token);
    }

    private void executeChunk(MdfBackfillTaskDO task, MdfBackfillChunkDO chunk,
                              HistoricalBarProvider provider, String token) {
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        chunk.setStatus(FoundationConstants.CHUNK_RUNNING);
        chunk.setAttempts(chunk.getAttempts() + 1);
        chunk.setStartedAt(now);
        txRequiresNew.executeWithoutResult(status -> chunkMapper.updateById(chunk));

        List<String> symbols = new DataBackfillJson().read(chunk.getSymbolsJson());
        long inserted = 0, updated = 0, skipped = 0, failed = 0;
        int failedSymbols = 0;
        String firstErrorCode = null, firstErrorMessage = null;

        for (String symbol : symbols) {
            // 每个证券执行前：心跳续租 + 所有权校验（兼防恢复器误判长任务）。
            // 丢失（暂停/回队/新 owner）→ 立即停止，不再请求 Provider、不写 chunk 状态。
            if (!owns(task.getId(), token)) {
                log.info("worker 失去任务所有权，停止分片执行: taskId={}, chunkIndex={}",
                        task.getId(), chunk.getChunkIndex());
                return;
            }
            try {
                List<ProviderDailyBar> bars = provider.getDailyBars(symbol, chunk.getStartDate(), chunk.getEndDate())
                        .stream()
                        .filter(bar -> !bar.tradeDate().isBefore(chunk.getStartDate()))
                        .filter(bar -> !bar.tradeDate().isAfter(chunk.getEndDate()))
                        .toList();
                if (bars.isEmpty()) {
                    skipped++;
                    continue;
                }
                long existingBefore = stockDailyBarMapper.countByFilter(symbol, chunk.getStartDate(),
                        chunk.getEndDate(), task.getAdjustType(), task.getProviderCode());
                List<StockDailyBarDO> barDOs = toBarDOs(task, bars);
                txRequiresNew.executeWithoutResult(status -> barWriteMapper.upsertBatch(barDOs));
                // R1 §六：事实落库后纳入版本 manifest（血缘）；重读以取得 bar 主键 id。
                List<StockDailyBarDO> persisted = stockDailyBarMapper.selectByFilter(symbol,
                        chunk.getStartDate(), chunk.getEndDate(), task.getAdjustType(),
                        task.getProviderCode(), BAR_SELECT_LIMIT, 0);
                lineageService.recordBars(task.getDatasetVersionId(), persisted,
                        FoundationConstants.LINEAGE_SOURCE_BACKFILL, task.getId());
                inserted += Math.max(0, bars.size() - existingBefore);
                updated += Math.min(existingBefore, bars.size());
            } catch (BusinessException exception) {
                failed++;
                failedSymbols++;
                if (firstErrorCode == null) {
                    firstErrorCode = exception.getErrorCode().getCode();
                    firstErrorMessage = exception.getMessage();
                }
            } catch (Exception exception) {
                failed++;
                failedSymbols++;
                if (firstErrorCode == null) {
                    firstErrorCode = ErrorCodeEnum.INTERNAL_ERROR.getCode();
                    firstErrorMessage = safe(exception.getMessage());
                }
            }
        }

        // chunk 终态写入前：所有权校验（丢失→放弃写入，chunk 留待新 owner/恢复器处理）
        if (!owns(task.getId(), token)) {
            log.info("worker 失去任务所有权，放弃 chunk 终态写入: taskId={}, chunkIndex={}",
                    task.getId(), chunk.getChunkIndex());
            return;
        }
        chunk.setInsertedCount(inserted);
        chunk.setUpdatedCount(updated);
        chunk.setSkippedCount(skipped);
        chunk.setFailedCount(failed);
        chunk.setLastErrorCode(firstErrorCode);
        chunk.setLastErrorMessage(safe(firstErrorMessage));
        chunk.setFinishedAt(LocalDateTime.now(marketDataClock));
        chunk.setStatus(failedSymbols == 0 ? FoundationConstants.CHUNK_SUCCEEDED
                : FoundationConstants.CHUNK_FAILED);
        txRequiresNew.executeWithoutResult(status -> chunkMapper.updateById(chunk));
        refreshTaskCounters(task.getId(), token);
    }

    /** 终态与计数从 chunk 事实确定性汇总；写入经所有权栅栏（旧 token 不可覆盖新 owner 状态）。 */
    private void finalizeTask(long taskId, String token) {
        MdfBackfillTaskDO task = taskMapper.selectById(taskId);
        String status = task.getStatus();
        if (FoundationConstants.TASK_QUEUED.equals(status)) {
            return;
        }
        if (!FoundationConstants.TASK_RUNNING.equals(status)) {
            refreshTaskCounters(taskId, token);
            return;
        }
        if (!owns(taskId, token)) {
            log.info("worker 失去任务所有权，放弃终态写入: taskId={}", taskId);
            return;
        }
        long failedChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_FAILED);
        long succeededChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_SUCCEEDED);
        long runningChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_RUNNING);
        long pendingChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_PENDING);
        String next;
        if (runningChunks > 0 || pendingChunks > 0) {
            next = FoundationConstants.TASK_QUEUED;
        } else if (failedChunks == 0) {
            next = FoundationConstants.TASK_SUCCEEDED;
        } else {
            next = succeededChunks > 0 ? FoundationConstants.TASK_PARTIAL_FAILED
                    : FoundationConstants.TASK_FAILED;
        }
        MdfBackfillTaskDO done = taskMapper.selectById(taskId);
        done.setStatus(next);
        if (FoundationConstants.TASK_SUCCEEDED.equals(next)
                || FoundationConstants.TASK_PARTIAL_FAILED.equals(next)
                || FoundationConstants.TASK_FAILED.equals(next)) {
            done.setFinishedAt(LocalDateTime.now(marketDataClock));
        }
        txRequiresNew.executeWithoutResult(tx -> taskMapper.updateByIdIfOwner(done, token));
        log.info("回补任务阶段结束: taskId={}, status={}, success={}, fail={}, skip={}, inserted={}, updated={}",
                taskId, next, done.getSuccessCount(), done.getFailCount(), done.getSkipCount(),
                done.getInsertedCount(), done.getUpdatedCount());
    }

    private void refreshTaskCounters(long taskId, String token) {
        List<MdfBackfillChunkDO> chunks = chunkMapper.selectByTaskId(taskId);
        int success = 0, fail = 0, skip = 0;
        long inserted = 0, updated = 0;
        String errorCode = null, errorMessage = null;
        DataBackfillJson json = new DataBackfillJson();
        for (MdfBackfillChunkDO chunk : chunks) {
            int symbolCount = json.read(chunk.getSymbolsJson()).size();
            int chunkFailed = chunk.getFailedCount() == null ? 0 : chunk.getFailedCount().intValue();
            int chunkSkipped = chunk.getSkippedCount() == null ? 0 : chunk.getSkippedCount().intValue();
            fail += chunkFailed;
            skip += chunkSkipped;
            success += Math.max(0, symbolCount - chunkFailed - chunkSkipped);
            inserted += chunk.getInsertedCount() == null ? 0 : chunk.getInsertedCount();
            updated += chunk.getUpdatedCount() == null ? 0 : chunk.getUpdatedCount();
            if (errorCode == null && chunk.getLastErrorCode() != null) {
                errorCode = chunk.getLastErrorCode();
                errorMessage = chunk.getLastErrorMessage();
            }
        }
        MdfBackfillTaskDO progress = taskMapper.selectById(taskId);
        progress.setSuccessCount(success);
        progress.setFailCount(fail);
        progress.setSkipCount(skip);
        progress.setInsertedCount(inserted);
        progress.setUpdatedCount(updated);
        progress.setLastErrorCode(errorCode);
        progress.setLastErrorMessage(errorMessage);
        // 所有权栅栏：非当前 owner 不写（WHERE status='RUNNING' AND claim_token=token）
        txRequiresNew.executeWithoutResult(status -> taskMapper.updateByIdIfOwner(progress, token));
    }

    private String currentVersionStatus(Long versionId) {
        MdfDatasetVersionDO version = versionMapper.selectById(versionId);
        return version == null ? null : version.getStatus();
    }

    private List<StockDailyBarDO> toBarDOs(MdfBackfillTaskDO task, List<ProviderDailyBar> bars) {
        LocalDateTime fetchedAt = LocalDateTime.now(marketDataClock);
        List<StockDailyBarDO> result = new ArrayList<>(bars.size());
        for (ProviderDailyBar bar : bars) {
            result.add(StockDailyBarDO.builder()
                    .canonicalSymbol(bar.canonicalSymbol()).tradeDate(bar.tradeDate())
                    .adjustType(task.getAdjustType()).dataSource(task.getProviderCode())
                    .openPrice(bar.open()).highPrice(bar.high()).lowPrice(bar.low()).closePrice(bar.close())
                    .volume(bar.volumeShares()).amount(bar.amountYuan()).fetchedAt(fetchedAt)
                    .build());
        }
        return result;
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    /** chunk.symbols_json 解析（静态内部，避免依赖服务实例）。 */
    private static final class DataBackfillJson {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

        List<String> read(String json) {
            try {
                List<String> symbols = mapper.readValue(
                        json == null || json.isBlank() ? "[]" : json,
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
                return symbols == null ? List.of() : symbols;
            } catch (Exception exception) {
                return List.of();
            }
        }
    }
}
