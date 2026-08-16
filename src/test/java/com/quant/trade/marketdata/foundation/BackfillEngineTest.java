package com.quant.trade.marketdata.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskMapper;
import com.quant.trade.marketdata.foundation.model.MdfBackfillChunkDO;
import com.quant.trade.marketdata.foundation.model.MdfBackfillTaskDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetDO;
import com.quant.trade.marketdata.foundation.service.BackfillWorkerService;
import com.quant.trade.marketdata.foundation.service.DataBackfillService;
import com.quant.trade.marketdata.foundation.service.DataFoundationDatasetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T03/T04/T05/T06（R1 异步版）：回补引擎。
 * POST run → QUEUED 快速返回，worker 认领执行；chunk 拆分边界与创建校验、完整执行计数、
 * 幂等重跑（ODKU）、断点续跑（终态分片跳过）、失败分片重试（SUCCEEDED 分片不重跑）、
 * 同 scope 防重与状态机守卫。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(StubHistoricalBarProviderConfig.class)
class BackfillEngineTest {

    private static final LocalDate START = LocalDate.of(2021, 1, 4);
    private static final LocalDate END = LocalDate.of(2021, 1, 8);

    @Autowired
    private DataFoundationDatasetService datasetService;
    @Autowired
    private DataBackfillService backfillService;
    @Autowired
    private BackfillWorkerService workerService;
    @Autowired
    private MdfBackfillTaskMapper taskMapper;
    @Autowired
    private StubHistoricalBarProvider stubProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void cleanFoundationTables() {
        FoundationTestTables.cleanAll(jdbcTemplate);
        stubProvider.reset();
    }

    private MdfDatasetDO createStubDataset() {
        return datasetService.createDataset("STUB_DS", "回补测试数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "测试");
    }

    private List<String> symbols(int count) {
        return IntStream.range(0, count).mapToObj(i -> String.format("SH.6%05d", 10000 + i)).toList();
    }

    private List<String> chunkSymbols(MdfBackfillChunkDO chunk) {
        try {
            return objectMapper.readValue(chunk.getSymbolsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long barRows() {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_daily_bar WHERE data_source = ?", Long.class,
                StubHistoricalBarProvider.PROVIDER_CODE);
        return rows == null ? 0 : rows;
    }

    private String errorCode(Runnable runnable) {
        return assertThrows(BusinessException.class, runnable::run).getErrorCode().getCode();
    }

    /** R1 异步链路：run 快速返回 QUEUED（或已认领 RUNNING）→ 驱动 worker 至终态。 */
    private MdfBackfillTaskDO runAsync(long taskId) {
        MdfBackfillTaskDO queued = backfillService.run(taskId);
        assertTrue("QUEUED".equals(queued.getStatus()) || "RUNNING".equals(queued.getStatus()),
                "POST run 必须快速返回 QUEUED/RUNNING，实际=" + queued.getStatus());
        workerService.claimAndExecuteOne();
        return taskMapper.selectById(taskId);
    }

    // ---------------------------------------------------------------- T03 chunk 拆分与创建校验

    @Test
    void chunkBoundariesAndCreateValidation() {
        createStubDataset();

        // 7 symbols / chunkSize=3 → 3/3/1（窗口 5 天 < stub 安全窗 365 → 单日期窗）
        var task = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, symbols(7), 3);
        List<MdfBackfillChunkDO> chunks = backfillService.listChunks(task.getId());
        assertEquals(3, chunks.size());
        assertEquals(List.of(3, 3, 1), chunks.stream().map(chunk -> chunkSymbols(chunk).size()).toList());
        assertEquals(7, task.getPlannedCount());
        assertTrue(chunks.stream().allMatch(chunk -> "PENDING".equals(chunk.getStatus())));

        // chunkSize > symbol 数 → 1 片（换 symbol 集避免同 scope 活跃防重）
        var single = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, List.of("SZ.000001", "SZ.000002"), 500);
        assertEquals(1, backfillService.listChunks(single.getId()).size());

        // 校验拒绝（以下均发生在落库前，不产生任务/分片）
        assertEquals(ErrorCodeEnum.VALIDATION_ERROR.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", START, END, symbols(7), 0)));
        assertEquals(ErrorCodeEnum.VALIDATION_ERROR.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", START, END, symbols(7), 501)));
        List<String> tooMany = symbols(10_001);
        assertEquals(ErrorCodeEnum.VALIDATION_ERROR.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", START, END, tooMany, 50)));
        assertEquals(ErrorCodeEnum.VALIDATION_ERROR.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", LocalDate.of(2020, 12, 31), END, symbols(3), 50)));
        assertEquals(ErrorCodeEnum.VALIDATION_ERROR.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", END, START, symbols(3), 50)));
        assertEquals(ErrorCodeEnum.VALIDATION_ERROR.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", START,
                        LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1), symbols(3), 50)));

        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_DATASET_NOT_FOUND.getCode(),
                errorCode(() -> backfillService.createTask("NO_SUCH_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", START, END, symbols(3), 50)));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_DATASET_CONFLICT.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "US", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", START, END, symbols(3), 50)));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_DATASET_CONFLICT.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "HFQ", START, END, symbols(3), 50)));

        // 导入类数据集无回补 Provider：创建任务必须被拒（不外联）
        datasetService.createDataset("STUB_IMPORT_DS", "导入数据集", "CN", "DAILY", "1D", "IMPORT_CSV_DAILY",
                "NONE", "导入");
        assertEquals(ErrorCodeEnum.MARKET_DATA_PLAN_INVALID.getCode(),
                errorCode(() -> backfillService.createTask("STUB_IMPORT_DS", "CN", "IMPORT_CSV_DAILY",
                        "1D", "NONE", START, END, symbols(3), 50)));

        // symbols 缺省且无池快照 → 明确失败（不隐式全市场）
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_UNIVERSE_EMPTY.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", START, END, null, 50)));
    }

    // ---------------------------------------------------------------- T04 完整执行 + 幂等重跑

    @Test
    void runCompletesTaskWithCorrectCounters() {
        createStubDataset();
        stubProvider.putBars("SH.600519", START, 3);
        stubProvider.putBars("SZ.000001", START, 3);

        var task = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, List.of("SH.600519", "SZ.000001"), 50);
        var done = runAsync(task.getId());

        assertEquals("SUCCEEDED", done.getStatus());
        assertEquals(2, done.getPlannedCount());
        assertEquals(2, done.getSuccessCount());
        assertEquals(0, done.getFailCount());
        assertEquals(0, done.getSkipCount());
        assertEquals(6L, done.getInsertedCount());
        assertEquals(0L, done.getUpdatedCount());
        assertEquals(6L, barRows());
        assertEquals(2, stubProvider.fetchCount());
        assertNull(done.getLastErrorCode());
        assertEquals(6L, jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mdf_dataset_version_manifest WHERE dataset_version_id = ?",
                Long.class, task.getDatasetVersionId()), "回补事实已入版本 manifest（血缘）");
    }

    @Test
    void rerunSameScopeInsertsNothingNew() {
        createStubDataset();
        stubProvider.putBars("SH.600519", START, 3);

        var first = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, List.of("SH.600519"), 50);
        assertEquals("SUCCEEDED", runAsync(first.getId()).getStatus());

        // 首任务终态后允许同 scope 再建任务（防重只针对活跃任务）
        var second = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, List.of("SH.600519"), 50);
        var rerun = runAsync(second.getId());

        assertEquals("SUCCEEDED", rerun.getStatus());
        assertEquals(0L, rerun.getInsertedCount(), "ODKU 幂等：重复执行不得新增行");
        assertEquals(3L, rerun.getUpdatedCount());
        assertEquals(3L, barRows());
    }

    // ---------------------------------------------------------------- T04 断点续跑

    @Test
    void resumeSkipsTerminalChunks() {
        createStubDataset();
        List<String> three = List.of("SH.600100", "SH.600101", "SH.600102");
        three.forEach(symbol -> stubProvider.putBars(symbol, START, 3));

        var task = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, three, 2);
        // 手工制造断点：chunk0 已成功（attempts=1），task 处于 PAUSED
        jdbcTemplate.update(
                "UPDATE mdf_backfill_chunk SET status = 'SUCCEEDED', attempts = 1, inserted_count = 0 "
                        + "WHERE task_id = ? AND chunk_index = 0", task.getId());
        jdbcTemplate.update("UPDATE mdf_backfill_task SET status = 'PAUSED' WHERE id = ?", task.getId());

        var resumed = runAsync(task.getId());

        assertEquals("SUCCEEDED", resumed.getStatus());
        assertEquals(1, stubProvider.fetchCount(), "只执行 PENDING 分片（SUCCEEDED 分片不重拉）");
        assertEquals(3L, barRows());

        List<MdfBackfillChunkDO> chunks = backfillService.listChunks(task.getId());
        assertEquals("SUCCEEDED", chunks.get(0).getStatus());
        assertEquals(1, chunks.get(0).getAttempts(), "终态分片 attempts 不得变化");
        assertEquals(0L, chunks.get(0).getInsertedCount());
        assertEquals("SUCCEEDED", chunks.get(1).getStatus());
        assertEquals(1, chunks.get(1).getAttempts());
        assertEquals(3, resumed.getSuccessCount());
        assertEquals(3L, resumed.getInsertedCount());
    }

    // ---------------------------------------------------------------- T06 失败重试

    @Test
    void retryFailedChunksWithoutRerunningSucceededOnes() {
        createStubDataset();
        List<String> three = List.of("SH.600200", "SH.600201", "SH.600202");
        three.forEach(symbol -> stubProvider.putBars(symbol, START, 3));
        stubProvider.failSymbol("SH.600202");

        var task = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, three, 2);
        var partial = runAsync(task.getId());

        assertEquals("PARTIAL_FAILED", partial.getStatus());
        assertEquals(1, partial.getFailCount());
        assertEquals(2, partial.getSuccessCount());
        assertEquals(6L, partial.getInsertedCount());
        assertEquals("MARKET_DATA_PROVIDER_TIMEOUT", partial.getLastErrorCode());

        List<MdfBackfillChunkDO> chunks = backfillService.listChunks(task.getId());
        assertEquals("SUCCEEDED", chunks.get(0).getStatus());
        assertEquals(1, chunks.get(0).getAttempts());
        assertEquals("FAILED", chunks.get(1).getStatus());
        assertEquals("MARKET_DATA_PROVIDER_TIMEOUT", chunks.get(1).getLastErrorCode());

        stubProvider.clearFailing();
        var queued = backfillService.retryFailedChunks(task.getId());
        assertEquals("QUEUED", queued.getStatus(), "R1：重试=FAILED→PENDING 后入队，立即返回");
        var retried = runAsync(task.getId());

        assertEquals("SUCCEEDED", retried.getStatus());
        assertEquals(3, retried.getSuccessCount());
        assertEquals(0, retried.getFailCount());
        assertEquals(9L, retried.getInsertedCount());
        assertEquals(9L, barRows());
        assertNull(retried.getLastErrorCode());

        List<MdfBackfillChunkDO> afterRetry = backfillService.listChunks(task.getId());
        assertEquals(1, afterRetry.get(0).getAttempts(), "原 SUCCEEDED 分片不被重跑");
        assertEquals(2, afterRetry.get(1).getAttempts(), "失败分片重试保留并累计尝试次数");
        assertEquals("SUCCEEDED", afterRetry.get(1).getStatus());
    }

    // ---------------------------------------------------------------- T05 同 scope 防重与状态机守卫

    @Test
    void claimGuardsDuplicateRunAndScope() {
        createStubDataset();
        stubProvider.putBars("SH.600519", START, 3);

        var task = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, List.of("SH.600519"), 50);

        // 同 scope 活跃任务重复创建 → 拒绝
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_DUPLICATE.getCode(),
                errorCode(() -> backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                        "1D", "NONE", START, END, List.of("SH.600519"), 50)));

        // 他人持有 claim（有效未超时）：run 幂等返回 RUNNING（不重复入队、不误报错误；双 worker 互斥见 RecoveryTest）
        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, taskMapper.tryClaim(task.getId(), "holder-token", now, now.minusHours(1)));
        assertEquals("RUNNING", taskMapper.selectById(task.getId()).getStatus());
        assertEquals("RUNNING", backfillService.run(task.getId()).getStatus());
        // 持有者不释放 claim 时可暂停（QUEUED/RUNNING 均可暂停）
        backfillService.pause(task.getId());
        assertEquals("PAUSED", taskMapper.selectById(task.getId()).getStatus());
    }

    @Test
    void terminalAndNonRunningStateGuards() {
        createStubDataset();
        stubProvider.putBars("SH.600519", START, 3);

        // PENDING 不允许暂停
        var pending = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, List.of("SH.600519"), 50);
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID.getCode(),
                errorCode(() -> backfillService.pause(pending.getId())));

        // 终态任务不允许直接 run / retry / pause
        assertEquals("SUCCEEDED", runAsync(pending.getId()).getStatus());
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID.getCode(),
                errorCode(() -> backfillService.run(pending.getId())));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID.getCode(),
                errorCode(() -> backfillService.retryFailedChunks(pending.getId())));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID.getCode(),
                errorCode(() -> backfillService.pause(pending.getId())));

        // 不存在任务 → RESOURCE_NOT_FOUND
        assertEquals(ErrorCodeEnum.RESOURCE_NOT_FOUND.getCode(),
                errorCode(() -> backfillService.getTask(999999L)));
        assertEquals(ErrorCodeEnum.RESOURCE_NOT_FOUND.getCode(),
                errorCode(() -> backfillService.retryFailedChunks(999999L)));
    }

    // ---------------------------------------------------------------- 补充：跳过语义

    @Test
    void providerEmptyWindowCountsAsSkippedNotFailure() {
        createStubDataset();
        // 未配置任何日 K：窗口内无数据 → skipped（provider 语义：无数据≠失败）
        var task = backfillService.createTask("STUB_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, List.of("SH.600519"), 50);
        var done = runAsync(task.getId());

        assertEquals("SUCCEEDED", done.getStatus());
        assertEquals(1, done.getSkipCount());
        assertEquals(0, done.getSuccessCount());
        assertEquals(0L, barRows());
    }
}
