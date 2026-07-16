package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskItemMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.model.MarketDataSyncTaskDO;
import com.quant.trade.marketdata.model.MarketDataSyncTaskItemDO;
import com.quant.trade.marketdata.vo.MarketDataSyncTaskVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TaskReconcileService 单测。独立 Bean 测试，确保 @Transactional 通过代理生效。
 */
@ExtendWith(MockitoExtension.class)
class TaskReconcileServiceTest {

    @Mock private MarketDataSyncTaskMapper syncTaskMapper;
    @Mock private MarketDataSyncTaskItemMapper taskItemMapper;

    @InjectMocks private TaskReconcileService service;

    @Test
    void reconcileAccumulatesAllSixCountFieldsFromChild() {
        MarketDataSyncTaskDO mainTask = MarketDataSyncTaskDO.builder()
                .id(1L).status("RUNNING").taskType("DAILY_BAR_BACKFILL").provider("LONGPORT").build();
        MarketDataSyncTaskItemDO item = MarketDataSyncTaskItemDO.builder()
                .id(10L).taskId(1L).subTaskId(100L).canonicalSymbol("SH.600519")
                .status("PENDING").insertedCount(0).updatedCount(0).skippedCount(0).rowCount(0).build();
        MarketDataSyncTaskDO child = MarketDataSyncTaskDO.builder()
                .id(100L).status("SUCCEEDED")
                .totalCount(10).successCount(8).failCount(2)
                .insertedCount(5).updatedCount(2).skippedCount(1).build();

        when(syncTaskMapper.selectById(1L)).thenReturn(mainTask);
        when(taskItemMapper.selectAllByTaskId(1L)).thenReturn(List.of(item));
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(1L);
        when(syncTaskMapper.selectById(100L)).thenReturn(child);

        var result = service.reconcileTask(1L);

        assertEquals(10, result.totalCount());
        assertEquals(8, result.successCount());
        assertEquals(2, result.failCount());
        assertEquals(5, result.insertedCount());
        assertEquals(2, result.updatedCount());
        assertEquals(1, result.skippedCount());
    }

    @Test
    void reconcileConvergesPendingItemToSucceeded() {
        MarketDataSyncTaskDO mainTask = runningTask(1L);
        MarketDataSyncTaskItemDO pendingItem = pendingItem(10L, 1L, 100L);
        MarketDataSyncTaskDO child = MarketDataSyncTaskDO.builder()
                .id(100L).status("SUCCEEDED")
                .totalCount(5).successCount(4).failCount(1)
                .insertedCount(3).updatedCount(1).skippedCount(0).build();

        when(syncTaskMapper.selectById(1L)).thenReturn(mainTask);
        when(taskItemMapper.selectAllByTaskId(1L)).thenReturn(List.of(pendingItem));
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(1L);
        when(syncTaskMapper.selectById(100L)).thenReturn(child);

        var result = service.reconcileTask(1L);

        verify(taskItemMapper).updateById(argThat(i ->
                "SUCCEEDED".equals(i.getStatus()) && i.getInsertedCount() == 3 && i.getFinishedAt() != null));
        assertEquals("SUCCEEDED", result.status());
        assertEquals(5, result.totalCount());
        assertEquals(4, result.successCount());
        assertEquals(1, result.failCount());
        assertNotNull(result.finishedAt());
    }

    @Test
    void reconcileKeepsRunningWhenSubTaskStillNonTerminal() {
        MarketDataSyncTaskDO mainTask = runningTask(1L);
        MarketDataSyncTaskItemDO pendingItem = pendingItem(10L, 1L, 100L);
        MarketDataSyncTaskDO child = MarketDataSyncTaskDO.builder().id(100L).status("RUNNING").build();

        when(syncTaskMapper.selectById(1L)).thenReturn(mainTask);
        when(taskItemMapper.selectAllByTaskId(1L)).thenReturn(List.of(pendingItem));
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(1L);
        when(syncTaskMapper.selectById(100L)).thenReturn(child);

        var result = service.reconcileTask(1L);

        assertEquals("RUNNING", result.status());
        assertNull(result.finishedAt());
        verify(taskItemMapper, never()).updateById(any());
    }

    @Test
    void reconcileIsIdempotent() {
        MarketDataSyncTaskDO doneTask = MarketDataSyncTaskDO.builder()
                .id(1L).status("SUCCEEDED").totalCount(5).successCount(4).failCount(1)
                .insertedCount(3).updatedCount(1).skippedCount(0).build();
        when(syncTaskMapper.selectById(1L)).thenReturn(doneTask);

        var result1 = service.reconcileTask(1L);
        var result2 = service.reconcileTask(1L);

        assertEquals("SUCCEEDED", result1.status());
        assertEquals("SUCCEEDED", result2.status());
        assertEquals(5, result2.totalCount());
        assertEquals(4, result2.successCount());
    }

    @Test
    void reconcileConvergesToFailed() {
        MarketDataSyncTaskDO mainTask = runningTask(1L);
        MarketDataSyncTaskItemDO pendingItem = pendingItem(10L, 1L, 100L);
        MarketDataSyncTaskDO child = MarketDataSyncTaskDO.builder()
                .id(100L).status("FAILED").lastErrorCode("PROVIDER_ERROR")
                .totalCount(0).successCount(0).failCount(0)
                .insertedCount(0).updatedCount(0).skippedCount(0).build();

        when(syncTaskMapper.selectById(1L)).thenReturn(mainTask);
        when(taskItemMapper.selectAllByTaskId(1L)).thenReturn(List.of(pendingItem));
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(1L);
        when(syncTaskMapper.selectById(100L)).thenReturn(child);

        var result = service.reconcileTask(1L);

        assertEquals("FAILED", result.status());
        assertNotNull(result.finishedAt());
        verify(taskItemMapper).updateById(argThat(i ->
                "FAILED".equals(i.getStatus()) && "PROVIDER_ERROR".equals(i.getErrorCode())));
    }

    @Test
    void reconcileMixedChildStatuses() {
        MarketDataSyncTaskDO mainTask = runningTask(1L);
        MarketDataSyncTaskItemDO item1 = pendingItem(10L, 1L, 100L);
        MarketDataSyncTaskItemDO item2 = pendingItem(11L, 1L, 101L);
        MarketDataSyncTaskDO child1 = MarketDataSyncTaskDO.builder()
                .id(100L).status("SUCCEEDED").totalCount(3).successCount(3).failCount(0)
                .insertedCount(2).updatedCount(1).skippedCount(0).build();
        MarketDataSyncTaskDO child2 = MarketDataSyncTaskDO.builder()
                .id(101L).status("FAILED").lastErrorCode("ERR").totalCount(0).successCount(0).failCount(0)
                .insertedCount(0).updatedCount(0).skippedCount(0).build();

        when(syncTaskMapper.selectById(1L)).thenReturn(mainTask);
        when(taskItemMapper.selectAllByTaskId(1L)).thenReturn(List.of(item1, item2));
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(2L);
        when(syncTaskMapper.selectById(100L)).thenReturn(child1);
        when(syncTaskMapper.selectById(101L)).thenReturn(child2);

        var result = service.reconcileTask(1L);

        assertEquals("PARTIAL_FAILED", result.status());
        assertEquals(3, result.totalCount());
        assertEquals(3, result.successCount());
    }

    @Test
    void reconcileNullCountsFromChildHandledAsZero() {
        MarketDataSyncTaskDO mainTask = runningTask(1L);
        MarketDataSyncTaskItemDO item = pendingItem(10L, 1L, 100L);
        MarketDataSyncTaskDO child = MarketDataSyncTaskDO.builder().id(100L).status("SUCCEEDED").build();

        when(syncTaskMapper.selectById(1L)).thenReturn(mainTask);
        when(taskItemMapper.selectAllByTaskId(1L)).thenReturn(List.of(item));
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(1L);
        when(syncTaskMapper.selectById(100L)).thenReturn(child);

        var result = service.reconcileTask(1L);

        assertEquals("SUCCEEDED", result.status());
        assertEquals(0, result.totalCount());
        assertEquals(0, result.successCount());
        assertEquals(0, result.failCount());
    }

    @Test
    void reconcileChildTaskMissingMarksItemFailed() {
        MarketDataSyncTaskDO mainTask = runningTask(1L);
        MarketDataSyncTaskItemDO item = pendingItem(10L, 1L, 999L);

        when(syncTaskMapper.selectById(1L)).thenReturn(mainTask);
        when(taskItemMapper.selectAllByTaskId(1L)).thenReturn(List.of(item));
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(1L);
        when(syncTaskMapper.selectById(999L)).thenReturn(null);

        var result = service.reconcileTask(1L);

        verify(taskItemMapper).updateById(argThat(i ->
                "FAILED".equals(i.getStatus()) && i.getErrorMessage() != null));
        assertEquals("FAILED", result.status());
    }

    @Test
    void reconcileHandlesMoreThan500Items() {
        MarketDataSyncTaskDO mainTask = runningTask(1L);
        List<MarketDataSyncTaskItemDO> items = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            items.add(MarketDataSyncTaskItemDO.builder()
                    .id((long) (i + 1)).taskId(1L).subTaskId((long) (i + 100))
                    .canonicalSymbol("SH.60051" + (i % 10))
                    .status("SUCCEEDED").insertedCount(1).updatedCount(0).skippedCount(0).rowCount(1).build());
        }
        MarketDataSyncTaskDO child = MarketDataSyncTaskDO.builder()
                .status("SUCCEEDED").totalCount(1).successCount(1).failCount(0)
                .insertedCount(1).updatedCount(0).skippedCount(0).build();

        when(syncTaskMapper.selectById(1L)).thenReturn(mainTask);
        when(taskItemMapper.selectAllByTaskId(1L)).thenReturn(items);
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(501L);
        when(syncTaskMapper.selectById(argThat(l -> l != null && l.longValue() != 1L))).thenReturn(child);

        var result = service.reconcileTask(1L);

        assertEquals("SUCCEEDED", result.status());
        assertEquals(501, result.totalCount());
        assertEquals(501, result.successCount());
    }

    @Test
    void reconcileRepeatIsIdempotentNoDoubleCount() {
        MarketDataSyncTaskDO doneTask = MarketDataSyncTaskDO.builder()
                .id(1L).status("SUCCEEDED").totalCount(5).successCount(4).failCount(1)
                .insertedCount(3).updatedCount(1).skippedCount(0).build();
        when(syncTaskMapper.selectById(1L)).thenReturn(doneTask);

        var result1 = service.reconcileTask(1L);
        var result2 = service.reconcileTask(1L);

        assertEquals("SUCCEEDED", result1.status());
        assertEquals("SUCCEEDED", result2.status());
        assertEquals(5, result2.totalCount());
        assertEquals(4, result2.successCount());
    }

    @Test
    void reconcileTaskNotFoundThrows() {
        when(syncTaskMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.reconcileTask(999L));
    }

    // Helpers
    private MarketDataSyncTaskDO runningTask(long id) {
        return MarketDataSyncTaskDO.builder()
                .id(id).status("RUNNING").taskType("DAILY_BAR_BACKFILL").provider("LONGPORT").build();
    }

    private MarketDataSyncTaskItemDO pendingItem(long id, long taskId, long subTaskId) {
        return MarketDataSyncTaskItemDO.builder()
                .id(id).taskId(taskId).subTaskId(subTaskId).canonicalSymbol("SH.600519")
                .status("PENDING").insertedCount(0).updatedCount(0).skippedCount(0).rowCount(0).build();
    }
}
