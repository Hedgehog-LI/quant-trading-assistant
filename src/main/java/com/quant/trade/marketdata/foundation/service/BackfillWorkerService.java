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

/**
 * 回补后台 worker（Repair R1 §二/§三）。
 *
 * - DB 为事实源：claimQueued 条件 UPDATE 认领（双 worker 仅一个成功）；崩溃后 claim 超时由恢复机制回队。
 * - 短事务纪律：数据库状态短事务（txRequiresNew），provider 网络调用始终在事务外（先例 MarketDataPlanExecutionService）。
 * - 每证券执行单元检查任务状态：PAUSED 即停止（QUEUED/RUNNING 均可暂停）。
 * - 幂等：日 K ODKU（uk 含 data_source）；事实落库后写入版本 manifest（血缘，R1 §六）。
 * - 终态与计数从 chunk 事实确定性汇总；残留 RUNNING chunk 不得判 SUCCEEDED（未全部终态→重新入队）。
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

    /**
     * 认领并执行一个 QUEUED 任务（worker 轮询单元；测试直接调用）。
     *
     * @return true=认领成功并已执行到终态/暂停；false=当前无 QUEUED 任务或争抢失败。
     */
    public boolean claimAndExecuteOne() {
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
    }

    // ---------------------------------------------------------------- 执行

    private void executeTask(MdfBackfillTaskDO task, String token) {
        HistoricalBarProvider provider = providerRegistry.require(task.getProviderCode());
        boolean firstRun = FoundationConstants.VERSION_DRAFT.equals(currentVersionStatus(task.getDatasetVersionId()));
        if (firstRun) {
            txRequiresNew.executeWithoutResult(tx -> versionMapper.updateStatus(
                    task.getDatasetVersionId(), FoundationConstants.VERSION_BACKFILLING, null, null, null));
        }
        List<MdfBackfillChunkDO> chunks = chunkMapper.selectByTaskId(task.getId());
        for (MdfBackfillChunkDO chunk : chunks) {
            if (!FoundationConstants.CHUNK_PENDING.equals(chunk.getStatus())) {
                continue;
            }
            String taskStatus = taskMapper.selectById(task.getId()).getStatus();
            if (!FoundationConstants.TASK_RUNNING.equals(taskStatus)) {
                log.info("回补任务停止（状态={}）: taskId={}, 停于 chunkIndex={}",
                        taskStatus, task.getId(), chunk.getChunkIndex());
                return;
            }
            executeChunk(task, chunk, provider);
        }
        finalizeTask(task.getId());
    }

    private void executeChunk(MdfBackfillTaskDO task, MdfBackfillChunkDO chunk, HistoricalBarProvider provider) {
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
            // 每证券执行单元检查暂停：PAUSED/QUEUED(恢复重派) 即停止本任务执行
            String taskStatus = taskMapper.selectById(task.getId()).getStatus();
            if (!FoundationConstants.TASK_RUNNING.equals(taskStatus)) {
                log.info("回补执行中检测到任务状态={}，停止分片: taskId={}, chunkIndex={}",
                        taskStatus, task.getId(), chunk.getChunkIndex());
                chunk.setStatus(FoundationConstants.CHUNK_PENDING);
                txRequiresNew.executeWithoutResult(status -> chunkMapper.updateById(chunk));
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
        refreshTaskCounters(task.getId());
    }

    /** 终态与计数从 chunk 事实确定性汇总（R1 §二.7/§三.5）。 */
    private void finalizeTask(long taskId) {
        MdfBackfillTaskDO task = taskMapper.selectById(taskId);
        String status = task.getStatus();
        if (FoundationConstants.TASK_QUEUED.equals(status)) {
            // 执行中崩溃后被恢复重派：本 worker 停手，由重新认领继续
            return;
        }
        if (!FoundationConstants.TASK_RUNNING.equals(status)) {
            refreshTaskCounters(taskId);
            return;
        }
        long failedChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_FAILED);
        long succeededChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_SUCCEEDED);
        long runningChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_RUNNING);
        long pendingChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_PENDING);
        String next;
        if (runningChunks > 0 || pendingChunks > 0) {
            // 残留非终态分片（并发恢复等）：不得判 SUCCEEDED，重新入队继续
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
        txRequiresNew.executeWithoutResult(tx -> taskMapper.updateById(done));
        log.info("回补任务阶段结束: taskId={}, status={}, success={}, fail={}, skip={}, inserted={}, updated={}",
                taskId, next, done.getSuccessCount(), done.getFailCount(), done.getSkipCount(),
                done.getInsertedCount(), done.getUpdatedCount());
    }

    private void refreshTaskCounters(long taskId) {
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
        txRequiresNew.executeWithoutResult(status -> taskMapper.updateById(progress));
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
