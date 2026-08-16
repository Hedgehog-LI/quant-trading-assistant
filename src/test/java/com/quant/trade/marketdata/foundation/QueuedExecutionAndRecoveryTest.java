package com.quant.trade.marketdata.foundation;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskMapper;
import com.quant.trade.marketdata.foundation.model.MdfBackfillTaskDO;
import com.quant.trade.marketdata.foundation.service.BackfillRecoveryService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1 §二/§三：QUEUED 状态机 + 后台 worker + 崩溃恢复。
 * 异步 run 快速返回、暂停/继续、stale RUNNING 恢复（幂等）、双 worker 单认领。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(StubHistoricalBarProviderConfig.class)
class QueuedExecutionAndRecoveryTest {

    private static final LocalDate START = LocalDate.of(2021, 1, 4);
    private static final LocalDate END = LocalDate.of(2021, 1, 8);

    @Autowired
    private DataFoundationDatasetService datasetService;
    @Autowired
    private DataBackfillService backfillService;
    @Autowired
    private BackfillWorkerService workerService;
    @Autowired
    private BackfillRecoveryService recoveryService;
    @Autowired
    private MdfBackfillTaskMapper taskMapper;
    @Autowired
    private StubHistoricalBarProvider stubProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanFoundationTables() {
        FoundationTestTables.cleanAll(jdbcTemplate);
        stubProvider.reset();
    }

    private MdfBackfillTaskDO newTask(String symbol) {
        return backfillService.createTask("Q_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, List.of(symbol), 50);
    }

    // ---------------------------------------------------------------- R1 §二 异步 + 暂停/继续

    @Test
    void runEnqueuesFastAndWorkerDrivesToTerminal() {
        datasetService.createDataset("Q_DS", "异步数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "测试");
        stubProvider.putBars("SH.600519", START, 3);
        MdfBackfillTaskDO task = newTask("SH.600519");

        MdfBackfillTaskDO queued = backfillService.run(task.getId());
        assertEquals("QUEUED", queued.getStatus(), "POST run 快速返回 QUEUED（不等待执行）");
        assertNull(queued.getFinishedAt());

        assertTrue(workerService.claimAndExecuteOne(), "worker 认领成功");
        assertEquals("SUCCEEDED", taskMapper.selectById(task.getId()).getStatus());
        assertEquals(3L, taskMapper.selectById(task.getId()).getInsertedCount());
    }

    @Test
    void queuedTaskCanBePausedAndResumed() {
        datasetService.createDataset("Q_DS", "异步数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "测试");
        stubProvider.putBars("SH.600519", START, 3);
        MdfBackfillTaskDO task = newTask("SH.600519");

        backfillService.run(task.getId());
        assertEquals("QUEUED", taskMapper.selectById(task.getId()).getStatus());

        // QUEUED 允许暂停：worker 不得认领已暂停任务
        backfillService.pause(task.getId());
        assertEquals("PAUSED", taskMapper.selectById(task.getId()).getStatus());
        assertTrue(!workerService.claimAndExecuteOne(), "PAUSED 任务不可被 worker 认领");
        assertEquals("PAUSED", taskMapper.selectById(task.getId()).getStatus());

        // 继续：PAUSED → QUEUED → worker → 终态
        MdfBackfillTaskDO resumed = backfillService.run(task.getId());
        assertEquals("QUEUED", resumed.getStatus());
        workerService.claimAndExecuteOne();
        assertEquals("SUCCEEDED", taskMapper.selectById(task.getId()).getStatus());
    }

    @Test
    void workerRequeuesTaskWhenNonTerminalChunksRemainInsteadOfFakingSuccess() {
        datasetService.createDataset("Q_DS", "异步数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "测试");
        stubProvider.putBars("SH.600519", START, 3);
        MdfBackfillTaskDO task = newTask("SH.600519");
        backfillService.run(task.getId());
        workerService.claimAndExecuteOne();
        assertEquals("SUCCEEDED", taskMapper.selectById(task.getId()).getStatus());

        // 模拟异常中断：chunk 卡 RUNNING、任务被外部回到 QUEUED（恢复机制路径）
        jdbcTemplate.update("UPDATE mdf_backfill_chunk SET status = 'RUNNING', attempts = 1 WHERE task_id = ?",
                task.getId());
        jdbcTemplate.update("UPDATE mdf_backfill_task SET status = 'QUEUED' WHERE id = ?", task.getId());

        // worker 认领但跳过非 PENDING 分片：终态汇总发现 RUNNING 残留 → 不判 SUCCEEDED，重新入队
        assertTrue(workerService.claimAndExecuteOne());
        MdfBackfillTaskDO after = taskMapper.selectById(task.getId());
        assertEquals("QUEUED", after.getStatus(), "残留 RUNNING chunk 不得误判为终态成功（重新入队等待恢复）");

        // 崩溃恢复把 RUNNING chunk 复位 PENDING 后可继续到终态
        recoveryService.recoverStaleTasks(60);
        workerService.claimAndExecuteOne();
        assertEquals("SUCCEEDED", taskMapper.selectById(task.getId()).getStatus());
    }

    // ---------------------------------------------------------------- R1 §三 崩溃恢复

    @Test
    void staleRunningTaskAndChunkAreRecoveredIdempotently() {
        datasetService.createDataset("Q_DS", "异步数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "测试");
        stubProvider.putBars("SH.600519", START, 3);
        MdfBackfillTaskDO task = newTask("SH.600519");

        // 模拟崩溃现场：RUNNING + 过期 claim + RUNNING chunk（attempts=2 已尝试）
        jdbcTemplate.update(
                "UPDATE mdf_backfill_task SET status = 'RUNNING', claim_token = 'dead', claimed_at = ? WHERE id = ?",
                LocalDateTime.now().minusHours(3), task.getId());
        jdbcTemplate.update(
                "UPDATE mdf_backfill_chunk SET status = 'RUNNING', attempts = 2, "
                        + "last_error_code = 'PARTIAL', last_error_message = '中断前错误' WHERE task_id = ?",
                task.getId());

        int recovered = recoveryService.recoverStaleTasks(60);
        assertEquals(1, recovered);
        MdfBackfillTaskDO after = taskMapper.selectById(task.getId());
        assertEquals("QUEUED", after.getStatus(), "claim 超时的 RUNNING 必须回队，不得永久 RUNNING");
        assertNull(after.getClaimToken());
        assertEquals("RECOVERED_STALE_RUNNING", after.getLastErrorCode());

        var chunk = backfillService.listChunks(task.getId()).get(0);
        assertEquals("PENDING", chunk.getStatus(), "RUNNING chunk 恢复为 PENDING");
        assertEquals(2, chunk.getAttempts(), "attempts 保留可追溯");
        assertEquals("RECOVERED_STALE_RUNNING", chunk.getLastErrorCode(), "错误信息保留恢复标记");

        // 重复恢复幂等：已是 QUEUED，无僵尸可恢复
        assertEquals(0, recoveryService.recoverStaleTasks(60));

        // 恢复后可继续执行到终态
        workerService.claimAndExecuteOne();
        assertEquals("SUCCEEDED", taskMapper.selectById(task.getId()).getStatus());
    }

    @Test
    void freshRunningTaskIsNotRecovered() {
        datasetService.createDataset("Q_DS", "异步数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "测试");
        MdfBackfillTaskDO task = newTask("SH.600519");
        // 活跃执行中的任务（claim 未超时）不得被恢复抢占
        jdbcTemplate.update(
                "UPDATE mdf_backfill_task SET status = 'RUNNING', claim_token = 'live', claimed_at = ? WHERE id = ?",
                LocalDateTime.now(), task.getId());
        assertEquals(0, recoveryService.recoverStaleTasks(60));
        assertEquals("RUNNING", taskMapper.selectById(task.getId()).getStatus());
    }

    @Test
    void onlyOneWorkerWinsClaimOnSameQueuedTask() {
        datasetService.createDataset("Q_DS", "异步数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "测试");
        stubProvider.putBars("SH.600519", START, 3);
        MdfBackfillTaskDO task = newTask("SH.600519");
        backfillService.run(task.getId());

        // 两个 worker 并发认领同一 QUEUED：claimQueued 条件更新，仅一个成功
        LocalDateTime now = LocalDateTime.now();
        int first = taskMapper.claimQueued(task.getId(), "worker-a", now);
        int second = taskMapper.claimQueued(task.getId(), "worker-b", now);
        assertEquals(1, first);
        assertEquals(0, second, "第二个 worker 认领必须失败（DB 条件更新防重复执行）");
        assertEquals("worker-a", taskMapper.selectById(task.getId()).getClaimToken());
    }

    // ---------------------------------------------------------------- 默认数据集幂等初始化（R1 §八）

    @Test
    void defaultDatasetInitializedIdempotentlyOnStartup() {
        // 启动 runner 已初始化（本用例 @BeforeEach 清场后再验证幂等逻辑本身）
        datasetService.ensureDefaultDataset();
        datasetService.ensureDefaultDataset();
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mdf_dataset WHERE dataset_code = 'CN_DAILY_BAR'", Long.class);
        assertEquals(1L, rows, "重复初始化幂等（仅一行）");
        assertNotNull(datasetService.getDataset("CN_DAILY_BAR").getId());
    }

    // ---------------------------------------------------------------- 状态机守卫补充

    @Test
    void pauseGuardsForInvalidStates() {
        datasetService.createDataset("Q_DS", "异步数据集", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "测试");
        MdfBackfillTaskDO task = newTask("SH.600519");
        BusinessException rejected = assertThrows(BusinessException.class,
                () -> backfillService.pause(task.getId()));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_STATE_INVALID.getCode(),
                rejected.getErrorCode().getCode());
    }
}
