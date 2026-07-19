package com.quant.trade.marketdata.service;

import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.*;
import com.quant.trade.marketdata.dto.CreateSyncPlanDTO;
import com.quant.trade.marketdata.dto.MinuteBarUpsertDTO;
import com.quant.trade.marketdata.dto.UpdateSyncPlanDTO;
import com.quant.trade.marketdata.manager.MinuteBarQualityManager;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.manager.SyncPlanValidationManager;
import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import com.quant.trade.marketdata.model.MarketDataSyncTaskDO;
import com.quant.trade.marketdata.model.MarketDataSyncTaskItemDO;
import com.quant.trade.marketdata.model.MarketDataWatermarkDO;
import com.quant.trade.marketdata.model.StockMinuteBarDO;
import com.quant.trade.marketdata.vo.MarketDataSyncPlanVO;
import com.quant.trade.marketdata.vo.MarketDataSyncTaskVO;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/** 行情工作台 service 单测：分钟 K 写入幂等/冲突/质量校验 + 水位更新 + 计划 CRUD。 */
@ExtendWith(MockitoExtension.class)
class MarketDataWorkbenchServiceTest {

    @Mock private MarketQuoteService marketQuoteService;
    @Mock private StockMinuteBarMapper minuteBarMapper;
    @Mock private MarketTradingSessionMapper tradingSessionMapper;
    @Mock private MarketDataSyncPlanMapper syncPlanMapper;
    @Mock private MarketDataSyncTaskItemMapper taskItemMapper;
    @Mock private MarketDataWatermarkMapper watermarkMapper;
    @Mock private MarketDataAlertMapper alertMapper;
    @Mock private MarketDataSyncTaskMapper syncTaskMapper;
    @Spy private MinuteBarQualityManager qualityManager = new MinuteBarQualityManager();
    @Mock private TradingSessionManager tradingSessionManager;
    @Spy private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    @Mock private TaskReconcileService taskReconcileService;
    @Mock private SyncPlanValidationManager syncPlanValidationManager;
    @Mock private MarketDataPlanExecutionService planExecutionService;

    @InjectMocks private MarketDataWorkbenchService service;

    @BeforeEach
    void stubTradingSession() {
        // 默认 stub：2026-07-10 是交易日，bar 时间 10:00 在 AM 窗口内
        lenient().when(tradingSessionManager.isTradingDay(anyString(), any())).thenReturn(true);
        lenient().when(tradingSessionManager.getSessionWindows(anyString(), anyBoolean()))
                .thenReturn(List.of(new int[]{930, 1130, 0}, new int[]{1300, 1500, 0}));
        var valid = new SyncPlanValidationManager.ValidationResult(
                new SyncPlanValidationManager.PlanScope(List.of("SH.600519"),
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 10)),
                List.of(), true, false);
        lenient().when(syncPlanValidationManager.validate(any())).thenReturn(valid);
        lenient().when(syncPlanValidationManager.inspect(any())).thenReturn(valid);
    }

    @Test
    void upsertMinuteBarInsertsWhenNew() {
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(null);
        when(watermarkMapper.selectByUniqueKey(any(), any(), any(), any())).thenReturn(null);

        var result = service.upsertMinuteBar(validDto());

        assertEquals("INSERTED", result.result());
        verify(minuteBarMapper).insert(any());
        verify(watermarkMapper).insert(any());
    }

    @Test
    void upsertMinuteBarSkipsWhenDuplicate() {
        StockMinuteBarDO existing = validBar();
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(existing);

        var result = service.upsertMinuteBar(validDto());

        assertEquals("SKIPPED", result.result());
        verify(minuteBarMapper, never()).insert(any());
    }

    @Test
    void upsertMinuteBarConflictProducesAlertNotOverwrite() {
        StockMinuteBarDO existing = validBar();
        existing.setClosePrice(new BigDecimal("99.99")); // 与 incoming 不同
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(existing);

        var result = service.upsertMinuteBar(validDto());

        assertEquals("CONFLICT", result.result());
        verify(minuteBarMapper, never()).insert(any());
        verify(alertMapper).insert(any()); // 产生冲突 alert
    }

    @Test
    void upsertMinuteBarRejectedDoesNotInsert() {
        var dto = validDto();
        dto.setHighPrice(new BigDecimal("1.00")); // high < open → REJECTED
        dto.setOpenPrice(new BigDecimal("10.00"));

        var result = service.upsertMinuteBar(dto);

        assertEquals("REJECTED", result.result());
        verify(minuteBarMapper, never()).insert(any());
        verify(alertMapper).insert(any());
    }

    @Test
    void upsertUpdatesExistingWatermark() {
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(null);
        MarketDataWatermarkDO existingWm = MarketDataWatermarkDO.builder()
                .totalRows(10L).build();
        when(watermarkMapper.selectByUniqueKey(any(), any(), any(), any())).thenReturn(existingWm);

        service.upsertMinuteBar(validDto());

        verify(watermarkMapper).updateByUniqueKey(any());
        assertEquals(11L, existingWm.getTotalRows());
    }

    @Test
    void upsertRejectsNonTradingDay() {
        // 2026-07-11 周六非交易日
        lenient().when(tradingSessionManager.isTradingDay(anyString(), eq(java.time.LocalDate.of(2026, 7, 11))))
                .thenReturn(false);
        var dto = validDto();
        dto.setBarStartTime(LocalDateTime.of(2026, 7, 11, 10, 0));
        dto.setBarEndTime(LocalDateTime.of(2026, 7, 11, 10, 30));

        var result = service.upsertMinuteBar(dto);

        assertEquals("REJECTED", result.result());
        verify(minuteBarMapper, never()).insert(any());
        verify(alertMapper).insert(any());
    }

    @Test
    void upsertMarksSuspectWhenBarOutOfSession() {
        // 12:00 午休不在交易窗口
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(null);
        var dto = validDto();
        dto.setBarStartTime(LocalDateTime.of(2026, 7, 10, 12, 0));
        dto.setBarEndTime(LocalDateTime.of(2026, 7, 10, 12, 30));

        var result = service.upsertMinuteBar(dto);

        assertEquals("INSERTED", result.result());
        assertEquals("SUSPECT", result.qualityStatus());
        verify(minuteBarMapper).insert(any());
        verify(alertMapper).insert(any());
    }

    @Test
    void createPlanPersistsAndReturnsVO() {
        CreateSyncPlanDTO dto = new CreateSyncPlanDTO();
        dto.setPlanName("茅台30M补档");
        dto.setTaskType("MINUTE_BAR_BACKFILL");
        dto.setProvider("LONGPORT");
        dto.setScopeJson("{\"symbols\":[\"SH.600519\"]}");
        dto.setIntervalType("30M");
        dto.setAdjustType("NONE");
        dto.setTriggerType("MANUAL");

        MarketDataSyncPlanVO vo = service.createPlan(dto);

        assertNotNull(vo);
        assertEquals("茅台30M补档", vo.getPlanName());
        verify(syncPlanMapper).insert(any());
    }

    @Test
    void togglePlanUpdatesEnabled() {
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder().id(1L).enabled(true).build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);

        MarketDataSyncPlanVO vo = service.togglePlan(1L, false);

        assertFalse(vo.getEnabled());
        verify(syncPlanMapper).updateEnabled(eq(1L), eq(false), any());
    }

    @Test
    void runPlanExecutesMinuteBackfill() {
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("MINUTE_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson("{\"symbols\":[\"SH.600519\"]}").adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);

        service.runPlan(1L);
        verify(planExecutionService).executeMinutePlan(eq(plan), any());
    }

    @Test
    void runPlanRejectsEmptyScope() {
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("DAILY_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson("{}").adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);

        assertThrows(com.quant.trade.common.exception.BusinessException.class,
                () -> service.runPlan(1L));
    }

    @Test
    void runPlanRejectsInvalidJson() {
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("DAILY_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson("not valid json{{{").adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);

        assertThrows(com.quant.trade.common.exception.BusinessException.class,
                () -> service.runPlan(1L));
    }

    @Test
    void runPlanRejectsInvalidDateRange() {
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("DAILY_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson("{\"canonicalSymbol\":\"SH.600519\",\"startDate\":\"2026-07-10\",\"endDate\":\"2026-01-01\"}")
                .adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);

        BusinessException ex = assertThrows(com.quant.trade.common.exception.BusinessException.class,
                () -> service.runPlan(1L));
        assertTrue(ex.getMessage().contains("startDate"));
    }

    @Test
    void runPlanRejectsInvalidSymbolFormat() {
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("DAILY_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson("{\"canonicalSymbol\":\"INVALID\"}").adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);

        assertThrows(com.quant.trade.common.exception.BusinessException.class,
                () -> service.runPlan(1L));
    }

    @Test
    void runPlanSucceededUsesSubTaskCounts() {
        // 子任务返回 SUCCEEDED + inserted=5, updated=2, skipped=1, total=8
        MarketDataSyncTaskVO subTask = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "SUCCEEDED",
                8, 1, 0, 5, 2, 1, null, null, null, null, null, null);
        setupRunPlanSuccess("SH.600519", "2026-01-01", "2026-07-10", subTask);

        service.runPlan(1L);

        // 验证子任务传入了正确的参数
        verify(marketQuoteService).createAndExecuteDailyBarSync(argThat(dto ->
                "SH.600519".equals(dto.canonicalSymbol()) &&
                dto.startDate().equals(LocalDate.of(2026, 1, 1)) &&
                dto.endDate().equals(LocalDate.of(2026, 7, 10)) &&
                "NONE".equals(dto.adjustType())));
        // 验证主任务状态 SUCCEEDED，count 全部使用行单位
        verify(syncTaskMapper).updateById(argThat(t ->
                "SUCCEEDED".equals(t.getStatus()) &&
                t.getTotalCount() == 8 &&      // 行数
                t.getInsertedCount() == 5 &&   // 行数
                t.getUpdatedCount() == 2 &&    // 行数
                t.getSkippedCount() == 1 &&    // 行数
                t.getFinishedAt() != null));   // 终态有 finishedAt
        // 验证 item 保存了子任务 ID（主子追踪）
        verify(taskItemMapper).updateById(argThat(item ->
                "SUCCEEDED".equals(item.getStatus()) &&
                item.getSubTaskId() != null && item.getSubTaskId() == 100L));
        // 验证 plan 更新了 lastRun
        verify(syncPlanMapper).updateLastRun(eq(1L), any(), any());
    }

    @Test
    void runPlanPendingSubTaskKeepsNonTerminal() {
        // 子任务返回 PENDING（幂等复用）→ item PENDING，主任务 RUNNING（不 SUCCEEDED，无 finishedAt）
        MarketDataSyncTaskVO subTask = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "PENDING",
                0, 0, 0, 0, 0, 0, null, null, null, null, null, null);
        setupRunPlanSuccess("SH.600519", null, null, subTask);

        service.runPlan(1L);

        verify(syncTaskMapper).updateById(argThat(t ->
                "RUNNING".equals(t.getStatus()) &&
                t.getFinishedAt() == null));  // 非终态不设 finishedAt
        verify(taskItemMapper).updateById(argThat(item ->
                "PENDING".equals(item.getStatus()) &&
                item.getFinishedAt() == null));
    }

    @Test
    void runPlanRunningSubTaskKeepsNonTerminal() {
        // 子任务返回 RUNNING → item RUNNING，主任务 RUNNING
        MarketDataSyncTaskVO subTask = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "RUNNING",
                0, 0, 0, 0, 0, 0, null, null, null, null, null, null);
        setupRunPlanSuccess("SH.600519", null, null, subTask);

        service.runPlan(1L);

        verify(syncTaskMapper).updateById(argThat(t ->
                "RUNNING".equals(t.getStatus()) && t.getFinishedAt() == null));
        verify(taskItemMapper).updateById(argThat(item ->
                "RUNNING".equals(item.getStatus()) && item.getFinishedAt() == null));
    }

    @Test
    void runPlanPartialFailedSubTaskMapsToPartialFailed() {
        // 子任务 PARTIAL_FAILED → item PARTIAL_FAILED，主任务 PARTIAL_FAILED
        MarketDataSyncTaskVO subTask = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "PARTIAL_FAILED",
                10, 0, 3, 5, 2, 1, null, null, null, null, null, null);
        setupRunPlanSuccess("SH.600519", null, null, subTask);

        service.runPlan(1L);

        verify(syncTaskMapper).updateById(argThat(t ->
                "PARTIAL_FAILED".equals(t.getStatus()) &&
                t.getTotalCount() == 10 &&
                t.getInsertedCount() == 5));
        verify(taskItemMapper).updateById(argThat(item ->
                "PARTIAL_FAILED".equals(item.getStatus())));
    }

    @Test
    void runPlanSubTaskFailedMapsToFailed() {
        // createAndExecuteDailyBarSync 抛 BusinessException → item FAILED, task FAILED，保留原错误码
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("DAILY_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson("{\"canonicalSymbol\":\"SH.600519\"}").adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);
        when(marketQuoteService.createAndExecuteDailyBarSync(any()))
                .thenThrow(new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "provider 未配置"));

        service.runPlan(1L);

        verify(syncTaskMapper).updateById(argThat(t ->
                "FAILED".equals(t.getStatus())));
        // 业务异常保留原错误码 BUSINESS_RULE_VIOLATION，不降级成 INTERNAL_ERROR
        verify(taskItemMapper).updateById(argThat(item ->
                "FAILED".equals(item.getStatus()) &&
                "BUSINESS_RULE_VIOLATION".equals(item.getErrorCode())));
    }

    @Test
    void runPlanSubTaskReturnedFailedStatus() {
        // 子任务返回 FAILED 状态 → item FAILED
        MarketDataSyncTaskVO subTask = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "FAILED",
                5, 0, 5, 0, 0, 0, null, null, "PROVIDER_ERROR", null, null, null);
        setupRunPlanSuccess("SH.600519", null, null, subTask);

        service.runPlan(1L);

        verify(syncTaskMapper).updateById(argThat(t -> "FAILED".equals(t.getStatus())));
        verify(taskItemMapper).updateById(argThat(item ->
                "FAILED".equals(item.getStatus()) &&
                "PROVIDER_ERROR".equals(item.getErrorCode())));
    }

    @Test
    void runPlanUnknownSubTaskStatusMapsToFailed() {
        // 子任务返回未知状态 → item FAILED
        MarketDataSyncTaskVO subTask = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "WEIRD_STATUS",
                0, 0, 0, 0, 0, 0, null, null, null, null, null, null);
        setupRunPlanSuccess("SH.600519", null, null, subTask);

        service.runPlan(1L);

        verify(syncTaskMapper).updateById(argThat(t -> "FAILED".equals(t.getStatus())));
        verify(taskItemMapper).updateById(argThat(item ->
                "FAILED".equals(item.getStatus())));
    }

    @Test
    void runPlanMultiSymbolPartialFailed() {
        // 2 个 symbol：第一个 SUCCEEDED，第二个抛异常 → 主任务 PARTIAL_FAILED
        MarketDataSyncTaskVO okSub = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "SUCCEEDED",
                3, 1, 0, 2, 0, 1, null, null, null, null, null, null);
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("DAILY_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson("{\"symbols\":[\"SH.600519\",\"SZ.000858\"]}").adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);
        when(marketQuoteService.createAndExecuteDailyBarSync(any()))
                .thenReturn(okSub)
                .thenThrow(new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "失败"));

        service.runPlan(1L);

        // 主任务 PARTIAL_FAILED，totalCount 是行数=3（第一个子任务 3 行，第二个 0 行）
        verify(syncTaskMapper).updateById(argThat(t ->
                "PARTIAL_FAILED".equals(t.getStatus()) &&
                t.getTotalCount() == 3 &&
                t.getInsertedCount() == 2));
    }

    @Test
    void runPlanAllPartialFailedMainIsPartialFailed() {
        // 全部子任务 PARTIAL_FAILED → 主任务 PARTIAL_FAILED（不是 SUCCEEDED）
        MarketDataSyncTaskVO partialSub = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "PARTIAL_FAILED",
                5, 0, 2, 2, 1, 0, null, null, null, null, null, null);
        setupRunPlanSuccess("SH.600519", null, null, partialSub);

        service.runPlan(1L);

        verify(syncTaskMapper).updateById(argThat(t ->
                "PARTIAL_FAILED".equals(t.getStatus())));
    }

    @Test
    void runPlanDedupDuplicateSymbols() {
        // scope 中有重复 symbol，应去重后只执行一次
        MarketDataSyncTaskVO okSub = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "SUCCEEDED",
                1, 1, 0, 1, 0, 0, null, null, null, null, null, null);
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("DAILY_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson("{\"symbols\":[\"SH.600519\",\"SH.600519\"]}").adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);
        when(marketQuoteService.createAndExecuteDailyBarSync(any())).thenReturn(okSub);

        service.runPlan(1L);

        // 去重后只调用 1 次
        verify(marketQuoteService, times(1)).createAndExecuteDailyBarSync(any());
        verify(syncTaskMapper).updateById(argThat(t -> t.getTotalCount() == 1));
    }

    @Test
    void runPlanAccumulatesSuccessAndFailFromSubTasks() {
        // 子任务返回显式 successCount=7, failCount=1, inserted=3, updated=2, skipped=2, total=8
        MarketDataSyncTaskVO subTask = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "SUCCEEDED",
                8, 7, 1, 3, 2, 2, null, null, null, null, null, null);
        setupRunPlanSuccess("SH.600519", null, null, subTask);

        service.runPlan(1L);

        // successCount 和 failCount 直接使用子任务返回值，不用 inserted 代替 success，不反推 fail
        verify(syncTaskMapper).updateById(argThat(t ->
                "SUCCEEDED".equals(t.getStatus()) &&
                t.getTotalCount() == 8 &&
                t.getSuccessCount() == 7 &&
                t.getFailCount() == 1 &&
                t.getInsertedCount() == 3 &&
                t.getUpdatedCount() == 2 &&
                t.getSkippedCount() == 2));
    }

    @Test
    void runPlanUpdatedAndSkippedCountAsSuccess() {
        // 子任务有 updated=4, skipped=3 但 inserted=0, successCount=7 → 主任务 successCount=7
        MarketDataSyncTaskVO subTask = new MarketDataSyncTaskVO(
                100L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "SUCCEEDED",
                7, 7, 0, 0, 4, 3, null, null, null, null, null, null);
        setupRunPlanSuccess("SH.600519", null, null, subTask);

        service.runPlan(1L);

        verify(syncTaskMapper).updateById(argThat(t ->
                t.getSuccessCount() == 7 &&  // 不是 insertedCount=0
                t.getInsertedCount() == 0 &&
                t.getUpdatedCount() == 4 &&
                t.getSkippedCount() == 3));
    }

    @Test
    void runPlanMultiSymbolAccumulatesCounts() {
        // 2 symbol 各返回不同计数
        MarketDataSyncTaskVO sub1 = new MarketDataSyncTaskVO(
                101L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "SUCCEEDED",
                5, 4, 1, 3, 1, 0, null, null, null, null, null, null);
        MarketDataSyncTaskVO sub2 = new MarketDataSyncTaskVO(
                102L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "SUCCEEDED",
                3, 3, 0, 1, 1, 1, null, null, null, null, null, null);
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("DAILY_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson("{\"symbols\":[\"SH.600519\",\"SZ.000858\"]}").adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);
        when(marketQuoteService.createAndExecuteDailyBarSync(any()))
                .thenReturn(sub1).thenReturn(sub2);

        service.runPlan(1L);

        // 汇总: total=8, success=7, fail=1, inserted=4, updated=2, skipped=1
        verify(syncTaskMapper).updateById(argThat(t ->
                "SUCCEEDED".equals(t.getStatus()) &&
                t.getTotalCount() == 8 &&
                t.getSuccessCount() == 7 &&
                t.getFailCount() == 1 &&
                t.getInsertedCount() == 4 &&
                t.getUpdatedCount() == 2 &&
                t.getSkippedCount() == 1));
    }

    // reconcile 测试已移至 TaskReconcileServiceTest（独立 Bean，确保 @Transactional 通过代理生效）

    @Test
    void listTaskItemsLazyReconcileCallsTaskReconcileService() {
        // RUNNING 主任务 → listTaskItems 调用 taskReconcileService.reconcileTask（通过代理，事务生效）
        MarketDataSyncTaskDO runningTask = MarketDataSyncTaskDO.builder()
                .id(1L).status("RUNNING").build();
        when(syncTaskMapper.selectById(1L)).thenReturn(runningTask);
        when(taskItemMapper.selectByTaskId(eq(1L), isNull(), anyInt(), anyInt()))
                .thenReturn(java.util.Collections.emptyList());
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(0L);
        // reconcileTask 返回 mock（不需要真实收敛，只验证调用）
        when(taskReconcileService.reconcileTask(1L)).thenReturn(
                new MarketDataSyncTaskVO(1L, "DAILY_BAR_BACKFILL", "LONGPORT", "{}", "SUCCEEDED",
                        0, 0, 0, 0, 0, 0, null, null, null, null, null, null));

        service.listTaskItems(1L, null, 1, 20);

        // 验证通过独立 Bean（代理）调用 reconcile，不是 self-invocation
        verify(taskReconcileService).reconcileTask(1L);
    }

    @Test
    void listTaskItemsSkipsReconcileForTerminalTask() {
        // SUCCEEDED 主任务 → 不触发 reconcile
        MarketDataSyncTaskDO doneTask = MarketDataSyncTaskDO.builder()
                .id(1L).status("SUCCEEDED").build();
        when(syncTaskMapper.selectById(1L)).thenReturn(doneTask);
        when(taskItemMapper.selectByTaskId(eq(1L), isNull(), anyInt(), anyInt()))
                .thenReturn(java.util.Collections.emptyList());
        when(taskItemMapper.countByTaskId(eq(1L), isNull())).thenReturn(0L);

        service.listTaskItems(1L, null, 1, 20);

        verify(taskReconcileService, never()).reconcileTask(any());
    }

    private void setupRunPlanSuccess(String symbol, String startDate, String endDate,
                                      MarketDataSyncTaskVO subTaskResult) {
        String scope = startDate != null
                ? String.format("{\"canonicalSymbol\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\"}", symbol, startDate, endDate)
                : String.format("{\"canonicalSymbol\":\"%s\"}", symbol);
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder()
                .id(1L).taskType("DAILY_BAR_BACKFILL").provider("LONGPORT")
                .scopeJson(scope).adjustType("NONE").build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);
        when(marketQuoteService.createAndExecuteDailyBarSync(any())).thenReturn(subTaskResult);
    }

    private MinuteBarUpsertDTO validDto() {
        var dto = new MinuteBarUpsertDTO();
        dto.setCanonicalSymbol("SH.600519");
        dto.setBarStartTime(LocalDateTime.of(2026, 7, 10, 10, 0));
        dto.setBarEndTime(LocalDateTime.of(2026, 7, 10, 10, 30));
        dto.setIntervalType("30M");
        dto.setAdjustType("NONE");
        dto.setDataSource("LONGPORT");
        dto.setOpenPrice(new BigDecimal("10.50"));
        dto.setHighPrice(new BigDecimal("11.00"));
        dto.setLowPrice(new BigDecimal("10.20"));
        dto.setClosePrice(new BigDecimal("10.80"));
        dto.setVolume(5000L);
        dto.setAmount(new BigDecimal("54000"));
        dto.setTurnoverRate(new BigDecimal("0.05"));
        return dto;
    }

    private StockMinuteBarDO validBar() {
        return StockMinuteBarDO.builder()
                .canonicalSymbol("SH.600519")
                .tradeDate(java.time.LocalDate.of(2026, 7, 10))
                .barStartTime(LocalDateTime.of(2026, 7, 10, 10, 0))
                .barEndTime(LocalDateTime.of(2026, 7, 10, 10, 30))
                .intervalType(WorkbenchConstants.INTERVAL_30M)
                .openPrice(new BigDecimal("10.50"))
                .highPrice(new BigDecimal("11.00"))
                .lowPrice(new BigDecimal("10.20"))
                .closePrice(new BigDecimal("10.80"))
                .volume(5000L)
                .amount(new BigDecimal("54000"))
                .turnoverRate(new BigDecimal("0.05"))
                .adjustType("NONE")
                .dataSource("LONGPORT")
                .qualityStatus(WorkbenchConstants.QUALITY_VALID)
                .build();
    }
}
