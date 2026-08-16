package com.quant.trade.marketdata.foundation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillChunkMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskSymbolMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper;
import com.quant.trade.marketdata.foundation.dao.MdfUniverseSnapshotMapper;
import com.quant.trade.marketdata.foundation.model.MdfBackfillChunkDO;
import com.quant.trade.marketdata.foundation.model.MdfBackfillTaskDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.provider.HistoricalBarProvider;
import com.quant.trade.marketdata.foundation.provider.HistoricalBarProviderRegistry;
import com.quant.trade.marketdata.foundation.vo.BackfillChunkVO;
import com.quant.trade.marketdata.foundation.vo.BackfillTaskVO;
import com.quant.trade.marketdata.manager.StockBasicRegistrationManager;
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

/**
 * 历史回补引擎（Repair R1：全 A 二维分片 + 持久化后台执行）。
 *
 * - 任务证券范围规范化到 mdf_backfill_task_symbol（R1 §一.3），symbols_json 不再承载全量列表（仅留哈希做 scope 防重）。
 * - 二维分片：证券组（chunkSize≤500）× 日期窗（Provider.safeRequestWindowDays，腾讯 365 天防 640 截断）；
 *   chunk.start/end=实际请求区间。
 * - POST run 只做 QUEUED 转换立即返回（R1 §二）；执行由 BackfillWorkerService 后台认领；
 *   崩溃恢复由 BackfillRecoveryService 处理（R1 §三）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataBackfillService {

    /** VO 返回显式 symbols 的上限（全 A 任务避免巨列表；plannedCount 恒可见）。 */
    private static final int VO_SYMBOL_LIMIT = 50;
    private static final int SYMBOL_INSERT_BATCH = 500;

    private final MdfDatasetMapper datasetMapper;
    private final MdfDatasetVersionMapper versionMapper;
    private final MdfUniverseSnapshotMapper universeMapper;
    private final MdfBackfillTaskMapper taskMapper;
    private final MdfBackfillChunkMapper chunkMapper;
    private final MdfBackfillTaskSymbolMapper taskSymbolMapper;
    private final HistoricalBarProviderRegistry providerRegistry;
    private final StockBasicRegistrationManager registrationManager;
    private final TransactionTemplate txRequiresNew;
    private final ObjectMapper objectMapper;
    private final Clock marketDataClock;

    // ---------------------------------------------------------------- 创建（二维分片规划）

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
        HistoricalBarProvider provider = providerRegistry.require(providerCode);
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
                    "单任务证券数上限 " + FoundationConstants.MAX_TASK_SYMBOLS + "（全 A 股票池可容纳）");
        }
        registrationManager.ensureRegistered(resolvedSymbols);
        String symbolsHash = sha256(String.join("|", resolvedSymbols));

        if (taskMapper.countActiveByScope(datasetCode, providerCode, adjustType, startDate, endDate, symbolsHash) > 0) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_DUPLICATE,
                    "同范围回补任务已存在且未终结，禁止重复创建");
        }

        // 二维分片规划：日期窗（Provider 安全窗）× 证券组（chunkSize）
        List<DateWindow> windows = splitWindows(startDate, endDate, provider.safeRequestWindowDays());
        int symbolGroupCount = (resolvedSymbols.size() + effectiveChunkSize - 1) / effectiveChunkSize;
        int totalChunks = symbolGroupCount * windows.size();
        if (totalChunks > FoundationConstants.MAX_TOTAL_CHUNKS) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "分片总数超上限 " + FoundationConstants.MAX_TOTAL_CHUNKS + "（当前规划 " + totalChunks + "）");
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
                    .symbolsJson(null).symbolsHash(symbolsHash).chunkSize(effectiveChunkSize)
                    .status(FoundationConstants.TASK_PENDING)
                    .plannedCount(resolvedSymbols.size()).successCount(0).failCount(0).skipCount(0)
                    .insertedCount(0L).updatedCount(0L).build();
            taskMapper.insert(task);
            versionMapper.updateStatus(version.getId(), FoundationConstants.VERSION_DRAFT, null, null,
                    "backfill-task#" + task.getId());

            for (int from = 0; from < resolvedSymbols.size(); from += SYMBOL_INSERT_BATCH) {
                taskSymbolMapper.insertBatch(task.getId(),
                        resolvedSymbols.subList(from, Math.min(from + SYMBOL_INSERT_BATCH, resolvedSymbols.size())));
            }

            List<MdfBackfillChunkDO> chunks = new ArrayList<>(totalChunks);
            int chunkIndex = 0;
            for (DateWindow window : windows) {
                for (int from = 0; from < resolvedSymbols.size(); from += effectiveChunkSize) {
                    List<String> part = resolvedSymbols.subList(from,
                            Math.min(from + effectiveChunkSize, resolvedSymbols.size()));
                    chunks.add(MdfBackfillChunkDO.builder()
                            .taskId(task.getId()).chunkIndex(chunkIndex++)
                            .symbolsJson(toJson(part))
                            .startDate(window.start()).endDate(window.end())
                            .status(FoundationConstants.CHUNK_PENDING).attempts(0)
                            .insertedCount(0L).updatedCount(0L).skippedCount(0L).failedCount(0L).build());
                }
            }
            if (!chunks.isEmpty()) {
                chunkMapper.insertBatch(chunks);
            }
            log.info("回补任务规划完成: taskId={}, symbols={}, windows={}, chunks={}",
                    task.getId(), resolvedSymbols.size(), windows.size(), totalChunks);
            return taskMapper.selectById(task.getId());
        });
    }

    // ---------------------------------------------------------------- 调度入口（POST run 快速返回）

    /** R1 §二：只做状态转换（→QUEUED）立即返回；执行归后台 worker。 */
    public MdfBackfillTaskDO run(long taskId) {
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        Integer queued = txRequiresNew.execute(status -> taskMapper.markQueued(taskId, now));
        if (queued != null && queued == 1) {
            return taskMapper.selectById(taskId);
        }
        MdfBackfillTaskDO task = getTask(taskId);
        if (FoundationConstants.TASK_QUEUED.equals(task.getStatus())
                || FoundationConstants.TASK_RUNNING.equals(task.getStatus())) {
            // 已入队/已被 worker 认领：幂等返回当前状态
            return task;
        }
        throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID,
                "回补任务不可执行（状态=" + task.getStatus() + "）");
    }

    /** R1 §二.6：QUEUED/RUNNING 均可暂停（释放 claim；worker 执行循环逐证券检查后停止）。 */
    public void pause(long taskId) {
        int updated = txRequiresNew.execute(status ->
                taskMapper.pauseIfActive(taskId, LocalDateTime.now(marketDataClock)));
        if (updated == 0) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID,
                    "只有 QUEUED/RUNNING 状态的任务可以暂停");
        }
    }

    /** R1：重试失败分片（FAILED→PENDING）后入队（不再同步执行）。 */
    public MdfBackfillTaskDO retryFailedChunks(long taskId) {
        MdfBackfillTaskDO task = getTask(taskId);
        if (!FoundationConstants.TASK_PARTIAL_FAILED.equals(task.getStatus())
                && !FoundationConstants.TASK_FAILED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID,
                    "只有 PARTIAL_FAILED/FAILED 状态的任务可以重试失败分片");
        }
        txRequiresNew.executeWithoutResult(status ->
                chunkMapper.resetFailedToPending(taskId, LocalDateTime.now(marketDataClock)));
        return run(taskId);
    }

    // ---------------------------------------------------------------- 读取与 VO

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
        List<String> symbols = taskSymbolMapper.selectByTask(task.getId());
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
                .symbols(symbols.size() <= VO_SYMBOL_LIMIT ? symbols : symbols.subList(0, VO_SYMBOL_LIMIT))
                .totalChunks(chunks.size()).succeededChunks(succeeded).failedChunks(failed)
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

    /** 日期窗拆分（左闭右闭、无空窗；windowDays 为 Provider 安全窗自然日）。测试断言直接复用。 */
    public static List<DateWindow> splitWindows(LocalDate start, LocalDate end, int windowDays) {
        int safeDays = Math.max(1, windowDays);
        List<DateWindow> windows = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            LocalDate windowEnd = cursor.plusDays(safeDays - 1L);
            if (windowEnd.isAfter(end)) {
                windowEnd = end;
            }
            windows.add(new DateWindow(cursor, windowEnd));
            cursor = windowEnd.plusDays(1);
        }
        return windows;
    }

    public record DateWindow(LocalDate start, LocalDate end) {
    }

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

    String toJson(List<String> symbols) {
        try {
            return objectMapper.writeValueAsString(symbols);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "symbols 序列化失败");
        }
    }

    List<String> fromJson(String json) {
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
}
