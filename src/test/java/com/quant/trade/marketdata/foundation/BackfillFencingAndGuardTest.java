package com.quant.trade.marketdata.foundation;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskMapper;
import com.quant.trade.marketdata.foundation.model.MdfBackfillChunkDO;
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
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2 修复收口专项：心跳/所有权 fencing、暂停-恢复竞态、QUEUED 防重、边界分母、轮询有界并发。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(StubHistoricalBarProviderConfig.class)
class BackfillFencingAndGuardTest {

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
    private Semaphore dataFoundationWorkerSlots;
    @Autowired
    private StubHistoricalBarProvider stubProvider;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanFoundationTables() {
        FoundationTestTables.cleanAll(jdbcTemplate);
        stubProvider.reset();
        dataFoundationWorkerSlots.drainPermits();
        dataFoundationWorkerSlots.release(1);
    }

    private void createDataset() {
        datasetService.createDataset("FENCE_DS", "fencing", "CN", "DAILY", "1D",
                StubHistoricalBarProvider.PROVIDER_CODE, "NONE", "测试");
    }

    private MdfBackfillTaskDO newTask(List<String> symbols) {
        return backfillService.createTask("FENCE_DS", "CN", StubHistoricalBarProvider.PROVIDER_CODE,
                "1D", "NONE", START, END, symbols, 50);
    }

    private String claim(String token) {
        LocalDateTime now = LocalDateTime.now();
        return token + ":" + taskMapper.claimQueued(lastTaskId(), token, now);
    }

    private Long lastTaskId() {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM mdf_backfill_task", Long.class);
    }

    // ---------------------------------------------------------------- §一 心跳语义

    @Test
    void heartbeatValidatesTaskStatusAndTokenTogether() {
        createDataset();
        MdfBackfillTaskDO task = newTask(List.of("SH.600519"));
        backfillService.run(task.getId());
        LocalDateTime now = LocalDateTime.now();

        // 正确 token + RUNNING → 续租成功并刷新 claimed_at
        assertEquals(1, taskMapper.claimQueued(task.getId(), "owner", now));
        jdbcTemplate.update("UPDATE mdf_backfill_task SET claimed_at = ? WHERE id = ?",
                now.minusHours(5), task.getId());
        assertEquals(1, taskMapper.heartbeat(task.getId(), "owner", now));
        LocalDateTime renewed = taskMapper.selectById(task.getId()).getClaimedAt();
        assertNotNull(renewed);
        assertTrue(renewed.isAfter(now.minusMinutes(1)), "心跳必须刷新 claimed_at");

        // 错误 token → 0（旧 worker 立即失格）
        assertEquals(0, taskMapper.heartbeat(task.getId(), "intruder", now));
        // 非 RUNNING（PAUSED）→ 0
        backfillService.pause(task.getId());
        assertEquals(0, taskMapper.heartbeat(task.getId(), "owner", now));
        // 不存在 id → 0
        assertEquals(0, taskMapper.heartbeat(999999L, "owner", now));
    }

    @Test
    void heartbeatRenewalKeepsLongRunningTaskSafeFromRecovery() {
        createDataset();
        MdfBackfillTaskDO task = newTask(List.of("SH.600519"));
        backfillService.run(task.getId());
        LocalDateTime now = LocalDateTime.now();
        taskMapper.claimQueued(task.getId(), "owner", now);
        // 合法长任务：已运行"3 小时"（远超 stale 60 分钟），但 worker 持续心跳续租
        jdbcTemplate.update("UPDATE mdf_backfill_task SET claimed_at = ? WHERE id = ?",
                now.minusHours(3), task.getId());
        assertEquals(1, taskMapper.heartbeat(task.getId(), "owner", now));
        assertEquals(0, recoveryService.recoverStaleTasks(60), "心跳续租后的长任务不得被恢复器误判");

        // 对比：同样 3 小时未续租 → 必须被恢复
        jdbcTemplate.update("UPDATE mdf_backfill_task SET status='RUNNING', claim_token='dead', claimed_at = ? "
                + "WHERE id = ?", now.minusHours(3), task.getId());
        assertEquals(1, recoveryService.recoverStaleTasks(60));
    }

    // ---------------------------------------------------------------- §一/§二 丢失 claim 后旧 worker 立即停止

    @Test
    void oldWorkerStopsWhenClaimLostMidRun() {
        createDataset();
        stubProvider.putBars("SH.600100", START, 3);
        stubProvider.putBars("SH.600101", START, 3);
        MdfBackfillTaskDO task = newTask(List.of("SH.600100", "SH.600101"));
        backfillService.run(task.getId());

        // 第 1 次外联时并发抢占 claim（模拟暂停/恢复/新 owner 接管）
        stubProvider.onFetch(() -> jdbcTemplate.update(
                "UPDATE mdf_backfill_task SET claim_token = 'new-owner' WHERE id = ?", task.getId()));

        workerService.claimAndExecuteOne();

        // 旧 worker：第 2 个证券执行前所有权校验失败 → 立即停止（不再外联、不写 chunk/task 终态）
        assertEquals(1, stubProvider.fetchCount(), "失去所有权后不得继续请求 Provider");
        MdfBackfillTaskDO after = taskMapper.selectById(task.getId());
        assertNotEquals("SUCCEEDED", after.getStatus(), "旧 worker 不得写终态");
        assertEquals("new-owner", after.getClaimToken());
        for (MdfBackfillChunkDO chunk : backfillService.listChunks(task.getId())) {
            assertNotEquals("SUCCEEDED", chunk.getStatus(), "旧 worker 不得写 chunk 终态");
        }
    }

    @Test
    void fencedUpdateBlocksStaleTokenFromOverwritingNewOwner() {
        createDataset();
        stubProvider.putBars("SH.600519", START, 3);
        MdfBackfillTaskDO task = newTask(List.of("SH.600519"));
        backfillService.run(task.getId());

        // 旧 worker token=A 持有 → 被 pause 释放 → run 重新入队 → 新 worker token=B 完成任务
        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, taskMapper.claimQueued(task.getId(), "token-A", now));
        backfillService.pause(task.getId());
        backfillService.run(task.getId());
        workerService.claimAndExecuteOne();
        assertEquals("SUCCEEDED", taskMapper.selectById(task.getId()).getStatus());
        String finalFinishedAt = taskMapper.selectById(task.getId()).getFinishedAt().toString();

        // 旧 token A 的迟到写入必须被栅栏拒绝（不能覆盖新 owner 的状态/计数/finished_at）
        assertEquals(0, taskMapper.heartbeat(task.getId(), "token-A", now));
        MdfBackfillTaskDO staleWrite = taskMapper.selectById(task.getId());
        staleWrite.setStatus("FAILED");
        staleWrite.setFinishedAt(now.plusDays(1));
        assertEquals(0, taskMapper.updateByIdIfOwner(staleWrite, "token-A"),
                "旧 token 的终态写入必须 0 行生效");
        MdfBackfillTaskDO after = taskMapper.selectById(task.getId());
        assertEquals("SUCCEEDED", after.getStatus());
        assertEquals(finalFinishedAt, after.getFinishedAt().toString());
    }

    // ---------------------------------------------------------------- §三 QUEUED 防重

    @Test
    void queuedTaskBlocksDuplicateScopeCreation() {
        createDataset();
        MdfBackfillTaskDO task = newTask(List.of("SH.600519"));
        backfillService.run(task.getId());
        assertEquals("QUEUED", taskMapper.selectById(task.getId()).getStatus());

        BusinessException rejected = assertThrows(BusinessException.class,
                () -> newTask(List.of("SH.600519")));
        assertEquals(ErrorCodeEnum.DATA_FOUNDATION_BACKFILL_DUPLICATE.getCode(),
                rejected.getErrorCode().getCode(), "QUEUED 必须计入活动状态阻止同 scope 重复创建");
    }

    // ---------------------------------------------------------------- §五 有界轮询

    @Test
    void saturatedSlotsSkipPollingWithoutQueueingOrOversubscribing() {
        createDataset();
        stubProvider.putBars("SH.600519", START, 3);
        MdfBackfillTaskDO task = newTask(List.of("SH.600519"));
        backfillService.run(task.getId());
        assertEquals("QUEUED", taskMapper.selectById(task.getId()).getStatus());

        // 并发槽占满（模拟 worker 正在执行）：hasCapacity=false，claimAndExecuteOne 不认领不堆积
        dataFoundationWorkerSlots.drainPermits();
        assertEquals(false, workerService.hasCapacity());
        assertEquals(false, workerService.claimAndExecuteOne(), "槽满时轮询必须直接跳过");
        assertEquals("QUEUED", taskMapper.selectById(task.getId()).getStatus(),
                "槽满时不得认领任务（无并行度越界）");

        // 反复调用仍保持跳过（不产生排队副作用）
        for (int i = 0; i < 10; i++) {
            assertEquals(false, workerService.hasCapacity());
        }
        assertEquals("QUEUED", taskMapper.selectById(task.getId()).getStatus());

        // 释放后恢复认领执行
        dataFoundationWorkerSlots.release(1);
        assertEquals(true, workerService.hasCapacity());
        assertEquals(true, workerService.claimAndExecuteOne());
        assertEquals("SUCCEEDED", taskMapper.selectById(task.getId()).getStatus());
    }
}
