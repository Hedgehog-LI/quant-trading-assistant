package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskItemMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.model.MarketDataSyncTaskDO;
import com.quant.trade.marketdata.model.MarketDataSyncTaskItemDO;
import com.quant.trade.marketdata.vo.MarketDataSyncTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 独立任务收敛 Service，避免 {@code MarketDataWorkbenchService} 内 self-invocation
 * 导致 {@code @Transactional} 失效。
 * <p>
 * 由 Spring 代理调用，确保事务边界生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskReconcileService {

    private final MarketDataSyncTaskMapper syncTaskMapper;
    private final MarketDataSyncTaskItemMapper taskItemMapper;

    /**
     * 收敛非终态 task item：查询 RUNNING/PENDING item 对应子任务的最终状态，同步 item 和主任务。
     * <p>
     * 统计口径：直接使用每个 child task 的 totalCount/successCount/failCount/insertedCount/updatedCount/skippedCount，
     * 不推导、不截断、null 按 0。全量查询所有 item。幂等。
     *
     * @param taskId 主任务 ID
     * @return 更新后的主任务 VO（如果主任务已是终态则无操作）
     */
    @Transactional
    public MarketDataSyncTaskVO reconcileTask(Long taskId) {
        MarketDataSyncTaskDO task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "任务不存在: " + taskId);
        }
        if (!MarketDataConstants.TASK_STATUS_RUNNING.equals(task.getStatus())) {
            return toTaskVO(task);
        }

        List<MarketDataSyncTaskItemDO> items = taskItemMapper.selectAllByTaskId(taskId);
        long expectedCount = taskItemMapper.countByTaskId(taskId, null);
        if (items.size() != expectedCount) {
            log.warn("任务 {} item 数量不匹配: 查询={}, 计数={}", taskId, items.size(), expectedCount);
        }

        TaskAggregate agg = new TaskAggregate();

        for (MarketDataSyncTaskItemDO item : items) {
            if (WorkbenchConstants.ITEM_PENDING.equals(item.getStatus())
                    || WorkbenchConstants.ITEM_RUNNING.equals(item.getStatus())) {
                if (item.getSubTaskId() != null) {
                    MarketDataSyncTaskDO subTask = syncTaskMapper.selectById(item.getSubTaskId());
                    if (subTask != null && isTerminalStatus(subTask.getStatus())) {
                        mapSubTaskToItem(item, subTask);
                        item.setFinishedAt(LocalDateTime.now());
                        taskItemMapper.updateById(item);
                    } else if (subTask == null) {
                        item.setStatus(WorkbenchConstants.ITEM_FAILED);
                        item.setErrorCode(ErrorCodeEnum.INTERNAL_ERROR.getCode());
                        item.setErrorMessage("子任务不存在: subTaskId=" + item.getSubTaskId());
                        item.setFinishedAt(LocalDateTime.now());
                        taskItemMapper.updateById(item);
                    }
                } else {
                    item.setStatus(WorkbenchConstants.ITEM_FAILED);
                    item.setErrorCode(ErrorCodeEnum.INTERNAL_ERROR.getCode());
                    item.setErrorMessage("子任务 ID 为空，无法收敛");
                    item.setFinishedAt(LocalDateTime.now());
                    taskItemMapper.updateById(item);
                }
            }
            accumulateFromItem(agg, item);
        }

        if (items.size() < expectedCount) {
            log.error("任务 {} 收敛时 item 丢失: 查询={}, 预期={}", taskId, items.size(), expectedCount);
            task.setTotalCount(agg.totalRows);
            task.setSuccessCount(agg.totalSuccess);
            task.setFailCount(agg.totalFail);
            task.setInsertedCount(agg.totalInserted);
            task.setUpdatedCount(agg.totalUpdated);
            task.setSkippedCount(agg.totalSkipped);
            task.setErrorSummaryJson("{\"reconcileError\":\"item count mismatch: queried=" + items.size() + " expected=" + expectedCount + "\"}");
            syncTaskMapper.updateById(task);
            return toTaskVO(task);
        }

        if (agg.symbolsNonTerminal > 0) {
            task.setTotalCount(agg.totalRows);
            task.setSuccessCount(agg.totalSuccess);
            task.setFailCount(agg.totalFail);
            task.setInsertedCount(agg.totalInserted);
            task.setUpdatedCount(agg.totalUpdated);
            task.setSkippedCount(agg.totalSkipped);
            syncTaskMapper.updateById(task);
            return toTaskVO(task);
        }

        String mainStatus;
        if (agg.symbolsFailed > 0 || agg.symbolsPartialFailed > 0) {
            mainStatus = (agg.symbolsSucceeded > 0 || agg.symbolsPartialFailed > 0)
                    ? MarketDataConstants.TASK_STATUS_PARTIAL_FAILED
                    : MarketDataConstants.TASK_STATUS_FAILED;
        } else {
            mainStatus = MarketDataConstants.TASK_STATUS_SUCCEEDED;
        }

        task.setStatus(mainStatus);
        task.setTotalCount(agg.totalRows);
        task.setSuccessCount(agg.totalSuccess);
        task.setFailCount(agg.totalFail);
        task.setInsertedCount(agg.totalInserted);
        task.setUpdatedCount(agg.totalUpdated);
        task.setSkippedCount(agg.totalSkipped);
        task.setFinishedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);

        log.info("任务 {} 收敛完成: status={}, items={}(succ/partial/fail/nonTerm)={}/{}/{}/{}, rows(total/succ/fail)={}/{}/{}",
                taskId, mainStatus, items.size(), agg.symbolsSucceeded, agg.symbolsPartialFailed,
                agg.symbolsFailed, agg.symbolsNonTerminal, agg.totalRows, agg.totalSuccess, agg.totalFail);
        return toTaskVO(task);
    }

    private void accumulateFromItem(TaskAggregate agg, MarketDataSyncTaskItemDO item) {
        String itemStatus = item.getStatus();
        if (WorkbenchConstants.ITEM_PENDING.equals(itemStatus)
                || WorkbenchConstants.ITEM_RUNNING.equals(itemStatus)) {
            agg.symbolsNonTerminal++;
        } else if (WorkbenchConstants.ITEM_SUCCEEDED.equals(itemStatus)) {
            agg.symbolsSucceeded++;
        } else if (WorkbenchConstants.ITEM_PARTIAL_FAILED.equals(itemStatus)) {
            agg.symbolsPartialFailed++;
        } else {
            agg.symbolsFailed++;
        }

        if (item.getSubTaskId() != null) {
            MarketDataSyncTaskDO child = syncTaskMapper.selectById(item.getSubTaskId());
            if (child != null) {
                agg.totalRows += safeInt(child.getTotalCount());
                agg.totalSuccess += safeInt(child.getSuccessCount());
                agg.totalFail += safeInt(child.getFailCount());
                agg.totalInserted += safeInt(child.getInsertedCount());
                agg.totalUpdated += safeInt(child.getUpdatedCount());
                agg.totalSkipped += safeInt(child.getSkippedCount());
                return;
            }
        }
        agg.totalRows += safeInt(item.getRowCount());
        int itemSuccess = safeInt(item.getInsertedCount()) + safeInt(item.getUpdatedCount()) + safeInt(item.getSkippedCount());
        agg.totalSuccess += itemSuccess;
        agg.totalFail += Math.max(0, safeInt(item.getRowCount()) - itemSuccess);
        agg.totalInserted += safeInt(item.getInsertedCount());
        agg.totalUpdated += safeInt(item.getUpdatedCount());
        agg.totalSkipped += safeInt(item.getSkippedCount());
    }

    private int safeInt(Integer val) {
        return val != null ? val : 0;
    }

    private boolean isTerminalStatus(String status) {
        return MarketDataConstants.TASK_STATUS_SUCCEEDED.equals(status)
                || MarketDataConstants.TASK_STATUS_PARTIAL_FAILED.equals(status)
                || MarketDataConstants.TASK_STATUS_FAILED.equals(status);
    }

    private void mapSubTaskToItem(MarketDataSyncTaskItemDO item, MarketDataSyncTaskDO subTask) {
        item.setInsertedCount(subTask.getInsertedCount() != null ? subTask.getInsertedCount() : 0);
        item.setUpdatedCount(subTask.getUpdatedCount() != null ? subTask.getUpdatedCount() : 0);
        item.setSkippedCount(subTask.getSkippedCount() != null ? subTask.getSkippedCount() : 0);
        item.setRowCount(subTask.getTotalCount() != null ? subTask.getTotalCount() : 0);
        String subStatus = subTask.getStatus();
        if (MarketDataConstants.TASK_STATUS_SUCCEEDED.equals(subStatus)) {
            item.setStatus(WorkbenchConstants.ITEM_SUCCEEDED);
        } else if (MarketDataConstants.TASK_STATUS_PARTIAL_FAILED.equals(subStatus)) {
            item.setStatus(WorkbenchConstants.ITEM_PARTIAL_FAILED);
            item.setErrorMessage("子任务部分失败");
        } else {
            item.setStatus(WorkbenchConstants.ITEM_FAILED);
            item.setErrorCode(subTask.getLastErrorCode() != null ? subTask.getLastErrorCode()
                    : ErrorCodeEnum.INTERNAL_ERROR.getCode());
            item.setErrorMessage("子任务终态: " + subStatus);
        }
    }

    private MarketDataSyncTaskVO toTaskVO(MarketDataSyncTaskDO d) {
        return new MarketDataSyncTaskVO(d.getId(), d.getTaskType(), d.getProvider(), d.getScopeJson(),
                d.getStatus(), d.getTotalCount(), d.getSuccessCount(), d.getFailCount(),
                d.getInsertedCount(), d.getUpdatedCount(), d.getSkippedCount(),
                d.getStartedAt(), d.getFinishedAt(), d.getLastErrorCode(), d.getErrorSummaryJson(),
                d.getParentTaskId(), d.getCreatedAt());
    }

    private static class TaskAggregate {
        int symbolsSucceeded = 0;
        int symbolsFailed = 0;
        int symbolsPartialFailed = 0;
        int symbolsNonTerminal = 0;
        int totalRows = 0;
        int totalSuccess = 0;
        int totalFail = 0;
        int totalInserted = 0;
        int totalUpdated = 0;
        int totalSkipped = 0;
    }
}
