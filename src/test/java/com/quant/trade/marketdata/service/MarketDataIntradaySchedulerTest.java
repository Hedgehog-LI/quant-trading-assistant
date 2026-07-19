package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dao.MarketDataSyncPlanMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskItemMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.manager.SyncPlanValidationManager;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.transaction.TransactionStatus;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataIntradaySchedulerTest {
    @Mock MarketDataSyncPlanMapper planMapper;
    @Mock MarketDataSyncTaskMapper taskMapper;
    @Mock MarketDataSyncTaskItemMapper itemMapper;
    @Mock SyncPlanValidationManager validationManager;
    @Mock TradingSessionManager tradingSessionManager;
    @Mock MarketDataPlanExecutionService executionService;
    @Mock TransactionTemplate tx;
    MarketDataIntradayScheduler scheduler;
    MarketDataSyncPlanDO plan;

    @BeforeEach
    void setUp() {
        scheduler = new MarketDataIntradayScheduler(planMapper, taskMapper, itemMapper, validationManager,
                tradingSessionManager, executionService, tx,
                Clock.fixed(java.time.Instant.parse("2026-07-10T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        plan = MarketDataSyncPlanDO.builder().id(1L).taskType("INTRADAY_MINUTE_REFRESH")
                .provider("LONGPORT").scopeJson("{\"symbols\":[\"SH.603308\"]}")
                .intervalType("5M").adjustType("NONE").triggerType("INTRADAY")
                .collectFrequency("60S").includeAuction(false).enabled(true).build();
        lenient().when(planMapper.selectAutoTriggerPlans("INTRADAY", true)).thenReturn(List.of(plan));
        lenient().when(validationManager.inspect(plan)).thenReturn(new SyncPlanValidationManager.ValidationResult(
                new SyncPlanValidationManager.PlanScope(List.of("SH.603308"), null, null), List.of(), false, true));
        lenient().when(validationManager.frequencySeconds("60S")).thenReturn(60);
        lenient().when(tradingSessionManager.getSessionWindows("CN_A", false))
                .thenReturn(List.of(new int[]{930, 1130, 0}, new int[]{1300, 1500, 0}));
    }

    @Test
    void triggersInsideTradingSession() {
        when(tradingSessionManager.isTradingDay("CN_A", LocalDate.of(2026, 7, 10))).thenReturn(true);
        scheduler.scanAt(LocalDateTime.of(2026, 7, 10, 10, 0));
        verify(executionService).executeMinutePlan(plan, LocalDateTime.of(2026, 7, 10, 10, 0));
    }

    @Test
    void skipsWeekendLunchAndAfterClose() {
        when(tradingSessionManager.isTradingDay(eq("CN_A"), any())).thenAnswer(inv ->
                !LocalDate.of(2026, 7, 11).equals(inv.getArgument(1)));
        scheduler.scanAt(LocalDateTime.of(2026, 7, 11, 10, 0));
        scheduler.scanAt(LocalDateTime.of(2026, 7, 10, 12, 0));
        scheduler.scanAt(LocalDateTime.of(2026, 7, 10, 15, 1));
        verifyNoInteractions(executionService);
    }

    @Test
    void skipsUntilFirstBarClosesAndPreventsFrequencyOverlap() {
        when(tradingSessionManager.isTradingDay(eq("CN_A"), any())).thenReturn(true);
        scheduler.scanAt(LocalDateTime.of(2026, 7, 10, 9, 34));
        plan.setLastRunAt(LocalDateTime.of(2026, 7, 10, 9, 35, 30));
        scheduler.scanAt(LocalDateTime.of(2026, 7, 10, 9, 36));
        verifyNoInteractions(executionService);
    }

    @Test
    void runClaimConflictDoesNotStopFollowingSchedulerCycle() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 10, 10, 0);
        when(tradingSessionManager.isTradingDay("CN_A", now.toLocalDate())).thenReturn(true);
        when(executionService.executeMinutePlan(plan, now))
                .thenThrow(new BusinessException(ErrorCodeEnum.MARKET_DATA_PLAN_RUNNING, "plan is already running"))
                .thenReturn(null);

        scheduler.scanAt(now);
        scheduler.scanAt(now);

        verify(executionService, times(2)).executeMinutePlan(plan, now);
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationReadyRecoversAbandonedTaskItemsAndClaim() {
        plan.setRunClaimToken("stale-token");
        plan.setRunningTaskId(88L);
        when(planMapper.selectClaimedPlans()).thenReturn(List.of(plan));
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());

        scheduler.recoverAbandonedRuns();

        verify(taskMapper).markFailedIfNonTerminal(eq(88L),
                eq(ErrorCodeEnum.MARKET_DATA_RUN_RECOVERED.getCode()), contains("服务重启"), any());
        verify(itemMapper).markFailedIfNonTerminal(eq(88L),
                eq(ErrorCodeEnum.MARKET_DATA_RUN_RECOVERED.getCode()), contains("服务重启"), any());
        verify(planMapper).releaseRunClaim(1L, "stale-token");
    }
}
