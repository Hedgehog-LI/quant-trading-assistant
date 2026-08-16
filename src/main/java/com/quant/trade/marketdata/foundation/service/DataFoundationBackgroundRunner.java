package com.quant.trade.marketdata.foundation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据底座后台调度入口（Repair R1 §二/§三）：
 * - 轮询 QUEUED 任务并提交线程池执行（claimQueued 条件认领，多实例/多线程安全）；
 * - 启动时与定时执行崩溃恢复（stale RUNNING→QUEUED）；
 * - 新部署幂等初始化默认数据集 CN_DAILY_BAR（R1 §八.1）。
 *
 * 测试环境经 qta.data-foundation.worker.enabled=false / recovery.enabled=false 关闭自动驱动，
 * 由测试直接调用 BackfillWorkerService/BackfillRecoveryService。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataFoundationBackgroundRunner implements ApplicationRunner {

    private final BackfillWorkerService workerService;
    private final BackfillRecoveryService recoveryService;
    private final DataFoundationDatasetService datasetService;
    private final org.springframework.core.task.TaskExecutor dataFoundationWorkerExecutor;

    @Value("${qta.data-foundation.worker.enabled:true}")
    private boolean workerEnabled;

    @Value("${qta.data-foundation.recovery.enabled:true}")
    private boolean recoveryEnabled;

    @Value("${qta.data-foundation.recovery.stale-minutes:60}")
    private int recoveryStaleMinutes;

    @Value("${qta.data-foundation.init-default-dataset:true}")
    private boolean initDefaultDataset;

    @Override
    public void run(ApplicationArguments args) {
        // 默认数据集幂等初始化（新部署即有 CN_DAILY_BAR，不依赖人工 curl）
        if (initDefaultDataset) {
            try {
                datasetService.ensureDefaultDataset();
                log.info("默认数据集已就绪: {}", datasetService.getDataset(
                        com.quant.trade.marketdata.constant.FoundationConstants.DATASET_CN_DAILY).getId());
            } catch (Exception exception) {
                log.error("默认数据集初始化失败（不阻断启动）: {}", exception.getMessage());
            }
        }
        if (recoveryEnabled) {
            safelyRecover();
        }
    }

    /** worker 轮询：仅在有空闲并发槽时提交（R2 §五：饱和线程池不再持续堆入空轮询任务）。 */
    @Scheduled(fixedDelayString = "${qta.data-foundation.worker.poll-ms:2000}")
    public void pollQueuedTasks() {
        if (!workerEnabled) {
            return;
        }
        try {
            if (!workerService.hasCapacity()) {
                return;
            }
            dataFoundationWorkerExecutor.execute(() -> {
                try {
                    workerService.claimAndExecuteOne();
                } catch (Exception exception) {
                    log.error("回补 worker 执行异常: {}", exception.getMessage());
                }
            });
        } catch (Exception exception) {
            log.error("回补 worker 轮询提交异常: {}", exception.getMessage());
        }
    }

    /** 定时崩溃恢复（stale RUNNING→QUEUED；重复恢复幂等）。 */
    @Scheduled(fixedDelayString = "${qta.data-foundation.recovery.poll-ms:60000}")
    public void recoverStaleTasks() {
        if (!recoveryEnabled) {
            return;
        }
        safelyRecover();
    }

    private void safelyRecover() {
        try {
            int recovered = recoveryService.recoverStaleTasks(recoveryStaleMinutes);
            if (recovered > 0) {
                log.warn("崩溃恢复完成: {} 个僵尸任务已回队", recovered);
            }
        } catch (Exception exception) {
            log.error("回补崩溃恢复异常: {}", exception.getMessage());
        }
    }
}
