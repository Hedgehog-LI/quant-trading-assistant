package com.quant.trade.marketdata.foundation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillChunkMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBarWriteMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper;
import com.quant.trade.marketdata.foundation.dao.MdfUniverseSnapshotMapper;
import com.quant.trade.marketdata.foundation.model.MdfBackfillChunkDO;
import com.quant.trade.marketdata.foundation.model.MdfBackfillTaskDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.provider.HistoricalBarProvider;
import com.quant.trade.marketdata.foundation.provider.HistoricalBarProviderRegistry;
import com.quant.trade.marketdata.foundation.provider.ProviderDailyBar;
import com.quant.trade.marketdata.foundation.vo.BackfillChunkVO;
import com.quant.trade.marketdata.foundation.vo.BackfillTaskVO;
import com.quant.trade.marketdata.manager.StockBasicRegistrationManager;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 历史回补引擎（契约 AC-02/AC-03）。
 *
 * 机制沿用既有采集引擎纪律（先例 MarketDataPlanExecutionService）：
 * - 数据库状态短事务（txRequiresNew），provider 网络调用始终在事务外；
 * - claim token 防并发（tryClaim 条件 UPDATE），finally 释放；
 * - 分片断点：按 chunk 状态续跑（终态分片跳过），失败分片可重试（FAILED→PENDING 后重跑）；
 * - 幂等：日 K 走 stock_daily_bar ODKU（uk 含 data_source），重复执行 inserted=0；
 * - 每分片证券数与任务证券总数有上限（FoundationConstants）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataBackfillService {

    private static final int CLAIM_STALE_MINUTES = 60;

    private final MdfDatasetMapper datasetMapper;
    private final MdfDatasetVersionMapper versionMapper;
    private final MdfUniverseSnapshotMapper universeMapper;
    private final MdfBackfillTaskMapper taskMapper;
    private final MdfBackfillChunkMapper chunkMapper;
    private final MdfBarWriteMapper barWriteMapper;
    private final StockDailyBarMapper stockDailyBarMapper;
    private final HistoricalBarProviderRegistry providerRegistry;
    private final StockBasicRegistrationManager registrationManager;
    private final TransactionTemplate txRequiresNew;
    private final ObjectMapper objectMapper;
    private final Clock marketDataClock;

    // ---------------------------------------------------------------- 创建

    public MdfBackfillTaskDO createTask(String datasetCode, String marketCode, String providerCode,
                                        String frequency, String adjustType, LocalDate startDate, LocalDate endDate,
                                        List<String> symbols, Integer chunkSize) {
        MdfDatasetDO dataset = datasetMapper.selectByCode(datasetCode);
        if (dataset == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_NOT_FOUND, "数据集不存在: " + datasetCode);
        }
        if (!dataset.getMarketCode().equals(marketCode) || !dataset.getProviderCode().equals(providerCode)
                || !dataset.getFrequency().equals(frequency) || !dataset.getAdjustType().equals(adjustType)) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_CONFLICT,
                    "回补请求与数据集定义不一致（market/provider/frequency/adjust 须完全一致）");
        }
        providerRegistry.require(providerCode);
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "startDate 不能晚于 endDate");
        }
        if (startDate.isBefore(LocalDate.parse(FoundationConstants.EARLIEST_START_DATE))) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "startDate 不能早于 " + FoundationConstants.EARLIEST_START_DATE);
        }
        if (endDate.isAfter(LocalDate.now(marketDataClock))) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "endDate 不能晚于今天");
        }
        int effectiveChunkSize = chunkSize == null ? 50 : chunkSize;
        if (effectiveChunkSize < 1 || effectiveChunkSize > FoundationConstants.MAX_CHUNK_SIZE) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "chunkSize 须在 1-" + FoundationConstants.MAX_CHUNK_SIZE + " 之间");
        }

        List<String> resolvedSymbols = resolveSymbols(symbols);
        if (resolvedSymbols.size() > FoundationConstants.MAX_TASK_SYMBOLS) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "单任务证券数上限 " + FoundationConstants.MAX_TASK_SYMBOLS);
        }
        registrationManager.ensureRegistered(resolvedSymbols);
        String symbolsJson = toJson(resolvedSymbols);
        String symbolsHash = sha256(symbolsJson);

        if (taskMapper.countActiveByScope(datasetCode, providerCode, adjustType, startDate, endDate, symbolsHash) > 0) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_DUPLICATE,
                    "同范围回补任务已存在且未终结，禁止重复创建");
        }

        int nextSeq = versionMapper.selectMaxVersionSeq(dataset.getId()) + 1;
        return txRequiresNew.execute(status -> {
            MdfDatasetVersionDO version = MdfDatasetVersionDO.builder()
                    .datasetId(dataset.getId()).versionCode("v" + nextSeq)
                    .status(FoundationConstants.VERSION_DRAFT)
                    .startDate(startDate).endDate(endDate)
                    .sourceProvider(providerCode).rowCount(0L)
                    .sourceNote("backfill-task 创建").build();
            versionMapper.insert(version);

            MdfBackfillTaskDO task = MdfBackfillTaskDO.builder()
                    .datasetCode(datasetCode).datasetVersionId(version.getId())
                    .marketCode(marketCode).providerCode(providerCode).frequency(frequency)
                    .adjustType(adjustType).startDate(startDate).endDate(endDate)
                    .symbolsJson(symbolsJson).symbolsHash(symbolsHash).chunkSize(effectiveChunkSize)
                    .status(FoundationConstants.TASK_PENDING)
                    .plannedCount(resolvedSymbols.size()).successCount(0).failCount(0).skipCount(0)
                    .insertedCount(0L).updatedCount(0L).build();
            taskMapper.insert(task);
            versionMapper.updateStatus(version.getId(), FoundationConstants.VERSION_DRAFT, null, null,
                    "backfill-task#" + task.getId());

            List<MdfBackfillChunkDO> chunks = new ArrayList<>();
            for (int index = 0; index < resolvedSymbols.size(); index += effectiveChunkSize) {
                List<String> part = resolvedSymbols.subList(index,
                        Math.min(index + effectiveChunkSize, resolvedSymbols.size()));
                chunks.add(MdfBackfillChunkDO.builder()
                        .taskId(task.getId()).chunkIndex(chunks.size())
                        .symbolsJson(toJson(part)).startDate(startDate).endDate(endDate)
                        .status(FoundationConstants.CHUNK_PENDING).attempts(0)
                        .insertedCount(0L).updatedCount(0L).skippedCount(0L).failedCount(0L).build());
            }
            if (!chunks.isEmpty()) {
                chunkMapper.insertBatch(chunks);
            }
            return taskMapper.selectById(task.getId());
        });
    }

    // ---------------------------------------------------------------- 执行

    /** 启动或继续任务（断点续跑：跳过终态分片，从首个 PENDING 继续）。 */
    public MdfBackfillTaskDO run(long taskId) {
        getTask(taskId);
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        String token = UUID.randomUUID().toString();
        Integer claimed = txRequiresNew.execute(status ->
                taskMapper.tryClaim(taskId, token, now, now.minusMinutes(CLAIM_STALE_MINUTES)));
        if (claimed == null || claimed != 1) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_RUNNING,
                    "回补任务不可执行（正在执行或状态不允许）");
        }
        try {
            MdfBackfillTaskDO task = taskMapper.selectById(taskId);
            HistoricalBarProvider provider = providerRegistry.require(task.getProviderCode());
            boolean firstRun = FoundationConstants.VERSION_DRAFT.equals(
                    currentVersionStatus(task.getDatasetVersionId()));
            if (firstRun) {
                txRequiresNew.executeWithoutResult(tx -> versionMapper.updateStatus(
                        task.getDatasetVersionId(), FoundationConstants.VERSION_BACKFILLING, null, null, null));
            }
            executeChunks(task, provider);
            return finalizeTask(taskId, token);
        } catch (BusinessException exception) {
            markTaskFailed(taskId, exception.getErrorCode().getCode(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            markTaskFailed(taskId, ErrorCodeEnum.INTERNAL_ERROR.getCode(), safe(exception.getMessage()));
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "回补执行异常: " + safe(exception.getMessage()));
        } finally {
            txRequiresNew.executeWithoutResult(status -> taskMapper.releaseClaim(taskId, token));
        }
    }

    /** 暂停：RUNNING → PAUSED（执行循环每个分片前检查任务状态并停止）。 */
    public void pause(long taskId) {
        int updated = txRequiresNew.execute(status -> taskMapper.pauseIfRunning(taskId, LocalDateTime.now(marketDataClock)));
        if (updated == 0) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID,
                    "只有 RUNNING 状态的任务可以暂停");
        }
    }

    /** 重试失败分片：FAILED→PENDING 后按断点续跑。 */
    public MdfBackfillTaskDO retryFailedChunks(long taskId) {
        MdfBackfillTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "回补任务不存在");
        }
        if (!FoundationConstants.TASK_PARTIAL_FAILED.equals(task.getStatus())
                && !FoundationConstants.TASK_FAILED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID,
                    "只有 PARTIAL_FAILED/FAILED 状态的任务可以重试失败分片");
        }
        txRequiresNew.executeWithoutResult(status ->
                chunkMapper.resetFailedToPending(taskId, LocalDateTime.now(marketDataClock)));
        return run(taskId);
    }

    private void executeChunks(MdfBackfillTaskDO task, HistoricalBarProvider provider) {
        List<MdfBackfillChunkDO> chunks = chunkMapper.selectByTaskId(task.getId());
        for (MdfBackfillChunkDO chunk : chunks) {
            MdfBackfillTaskDO current = taskMapper.selectById(task.getId());
            if (!FoundationConstants.TASK_RUNNING.equals(current.getStatus())) {
                log.info("回补任务停止（状态={}）: taskId={}, 已完成至 chunkIndex={}",
                        current.getStatus(), task.getId(), chunk.getChunkIndex());
                return;
            }
            if (!FoundationConstants.CHUNK_PENDING.equals(chunk.getStatus())) {
                continue;
            }
            executeChunk(current, chunk, provider);
        }
    }

    private void executeChunk(MdfBackfillTaskDO task, MdfBackfillChunkDO chunk, HistoricalBarProvider provider) {
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        chunk.setStatus(FoundationConstants.CHUNK_RUNNING);
        chunk.setAttempts(chunk.getAttempts() + 1);
        chunk.setStartedAt(now);
        txRequiresNew.executeWithoutResult(status -> chunkMapper.updateById(chunk));

        List<String> symbols = fromJson(chunk.getSymbolsJson());
        long inserted = 0, updated = 0, skipped = 0, failed = 0;
        int failedSymbols = 0;
        String firstErrorCode = null, firstErrorMessage = null;

        for (String symbol : symbols) {
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
                txRequiresNew.executeWithoutResult(status ->
                        barWriteMapper.upsertBatch(toBarDOs(task, bars)));
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

        // 任务计数从分片表确定性重算（重试/续跑后不重复累计）：每片 success = 片内证券数 - failed - skipped。
        refreshTaskCounters(task.getId(), firstErrorCode, firstErrorMessage);
    }

    private void refreshTaskCounters(long taskId, String firstErrorCode, String firstErrorMessage) {
        List<MdfBackfillChunkDO> chunks = chunkMapper.selectByTaskId(taskId);
        int success = 0, fail = 0, skip = 0;
        long inserted = 0, updated = 0;
        String errorCode = null, errorMessage = null;
        for (MdfBackfillChunkDO chunk : chunks) {
            int symbolCount = fromJson(chunk.getSymbolsJson()).size();
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

    private MdfBackfillTaskDO finalizeTask(long taskId, String token) {
        MdfBackfillTaskDO task = taskMapper.selectById(taskId);
        if (!FoundationConstants.TASK_RUNNING.equals(task.getStatus())) {
            return task;
        }
        long failedChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_FAILED);
        long succeededChunks = chunkMapper.countByTaskAndStatus(taskId, FoundationConstants.CHUNK_SUCCEEDED);
        String status = failedChunks == 0 ? FoundationConstants.TASK_SUCCEEDED
                : succeededChunks > 0 ? FoundationConstants.TASK_PARTIAL_FAILED
                : FoundationConstants.TASK_FAILED;
        task.setStatus(status);
        task.setFinishedAt(LocalDateTime.now(marketDataClock));
        txRequiresNew.executeWithoutResult(tx -> taskMapper.updateById(task));
        log.info("回补任务结束: taskId={}, status={}, success={}, fail={}, skip={}, inserted={}, updated={}",
                taskId, status, task.getSuccessCount(), task.getFailCount(), task.getSkipCount(),
                task.getInsertedCount(), task.getUpdatedCount());
        return taskMapper.selectById(taskId);
    }

    private void markTaskFailed(long taskId, String errorCode, String message) {
        txRequiresNew.executeWithoutResult(status -> {
            MdfBackfillTaskDO task = taskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            task.setStatus(FoundationConstants.TASK_FAILED);
            task.setLastErrorCode(errorCode);
            task.setLastErrorMessage(safe(message));
            task.setFinishedAt(LocalDateTime.now(marketDataClock));
            taskMapper.updateById(task);
        });
    }

    // ---------------------------------------------------------------- 读取

    public MdfBackfillTaskDO getTask(long taskId) {
        MdfBackfillTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "回补任务不存在");
        }
        return task;
    }

    public List<MdfBackfillTaskDO> listTasks(String status, int page, int pageSize) {
        return taskMapper.selectList(status, Math.max(0, (page - 1) * pageSize), pageSize);
    }

    public long countTasks(String status) {
        return taskMapper.countAll(status);
    }

    public List<MdfBackfillChunkDO> listChunks(long taskId) {
        getTask(taskId);
        return chunkMapper.selectByTaskId(taskId);
    }

    public BackfillTaskVO toTaskVO(MdfBackfillTaskDO task) {
        List<String> symbols = fromJson(task.getSymbolsJson());
        List<MdfBackfillChunkDO> chunks = chunkMapper.selectByTaskId(task.getId());
        int succeeded = (int) chunks.stream().filter(c -> FoundationConstants.CHUNK_SUCCEEDED.equals(c.getStatus())).count();
        int failed = (int) chunks.stream().filter(c -> FoundationConstants.CHUNK_FAILED.equals(c.getStatus())).count();
        return BackfillTaskVO.builder()
                .id(task.getId()).datasetCode(task.getDatasetCode())
                .datasetVersionId(task.getDatasetVersionId())
                .marketCode(task.getMarketCode()).providerCode(task.getProviderCode())
                .frequency(task.getFrequency()).adjustType(task.getAdjustType())
                .startDate(task.getStartDate()).endDate(task.getEndDate())
                .chunkSize(task.getChunkSize()).status(task.getStatus())
                .plannedCount(task.getPlannedCount()).successCount(task.getSuccessCount())
                .failCount(task.getFailCount()).skipCount(task.getSkipCount())
                .insertedCount(task.getInsertedCount()).updatedCount(task.getUpdatedCount())
                .lastErrorCode(task.getLastErrorCode()).lastErrorMessage(task.getLastErrorMessage())
                .startedAt(task.getStartedAt()).finishedAt(task.getFinishedAt()).createdAt(task.getCreatedAt())
                .symbols(symbols).totalChunks(chunks.size()).succeededChunks(succeeded).failedChunks(failed)
                .build();
    }

    public BackfillChunkVO toChunkVO(MdfBackfillChunkDO chunk) {
        return BackfillChunkVO.builder()
                .id(chunk.getId()).taskId(chunk.getTaskId()).chunkIndex(chunk.getChunkIndex())
                .symbols(fromJson(chunk.getSymbolsJson()))
                .startDate(chunk.getStartDate()).endDate(chunk.getEndDate())
                .status(chunk.getStatus()).attempts(chunk.getAttempts())
                .insertedCount(chunk.getInsertedCount()).updatedCount(chunk.getUpdatedCount())
                .skippedCount(chunk.getSkippedCount()).failedCount(chunk.getFailedCount())
                .lastErrorCode(chunk.getLastErrorCode()).lastErrorMessage(chunk.getLastErrorMessage())
                .startedAt(chunk.getStartedAt()).finishedAt(chunk.getFinishedAt())
                .build();
    }

    // ---------------------------------------------------------------- 内部

    private List<String> resolveSymbols(List<String> symbols) {
        if (symbols != null && !symbols.isEmpty()) {
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String symbol : symbols) {
                unique.add(symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT));
            }
            unique.remove("");
            return new ArrayList<>(unique);
        }
        LocalDate latestAsOf = universeMapper.selectLatestAsOfDate();
        if (latestAsOf == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_UNIVERSE_EMPTY,
                    "股票池快照为空：请先导入证券池快照或显式指定 symbols");
        }
        return universeMapper.selectSymbolsByAsOf(latestAsOf);
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

    private String toJson(List<String> symbols) {
        try {
            return objectMapper.writeValueAsString(symbols);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "symbols 序列化失败");
        }
    }

    private List<String> fromJson(String json) {
        try {
            List<String> symbols = objectMapper.readValue(json == null || json.isBlank() ? "[]" : json,
                    new TypeReference<List<String>>() { });
            return symbols == null ? List.of() : symbols;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "symbols_hash 计算失败");
        }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
