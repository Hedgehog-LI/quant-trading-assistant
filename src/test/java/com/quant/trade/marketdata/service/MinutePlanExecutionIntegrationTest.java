package com.quant.trade.marketdata.service;

import com.quant.trade.marketdata.dao.MarketDataSyncTaskItemMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.dao.MarketDataWatermarkMapper;
import com.quant.trade.marketdata.dao.StockMinuteBarMapper;
import com.quant.trade.marketdata.dto.CreateSyncPlanDTO;
import com.quant.trade.marketdata.provider.FakeMarketDataProvider;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderMinuteBar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"qta.market-data.fake.enabled=true", "qta.market-data.scheduler.enabled=false"})
class MinutePlanExecutionIntegrationTest {
    @Autowired MarketDataWorkbenchService workbenchService;
    @Autowired MarketDataSyncTaskMapper taskMapper;
    @Autowired MarketDataSyncTaskItemMapper itemMapper;
    @Autowired StockMinuteBarMapper minuteBarMapper;
    @Autowired MarketDataWatermarkMapper watermarkMapper;
    @Autowired FakeMarketDataProvider fakeProvider;

    @Test
    void minuteBackfillSucceedsAndRepeatedRunIsIdempotent() {
        var plan = workbenchService.createPlan(plan("minute-success", "SH.603308"));
        var first = workbenchService.runPlan(plan.getId());
        var firstTask = taskMapper.selectById(first.getLastTaskId());
        assertEquals("SUCCEEDED", firstTask.getStatus());
        assertEquals(2, firstTask.getInsertedCount());
        assertEquals(2, itemMapper.selectAllByTaskId(firstTask.getId()).get(0).getRowCount());
        assertEquals(2, minuteBarMapper.countByFilter("SH.603308", "5M", "NONE", "FAKE",
                null, null, null));
        assertNotNull(watermarkMapper.selectByUniqueKey("SH.603308", "FAKE", "5M", "NONE"));

        var second = workbenchService.runPlan(plan.getId());
        var secondTask = taskMapper.selectById(second.getLastTaskId());
        assertEquals("SUCCEEDED", secondTask.getStatus());
        assertEquals(0, secondTask.getInsertedCount());
        assertEquals(2, secondTask.getSkippedCount());
        assertEquals(2, minuteBarMapper.countByFilter("SH.603308", "5M", "NONE", "FAKE",
                null, null, null));
    }

    @Test
    void multiSymbolProviderFailureLeavesPartialFailedTrace() {
        fakeProvider.setFailureSymbol("SZ.000858");
        CreateSyncPlanDTO dto = plan("minute-partial", "SH.600519");
        dto.setScopeJson("{\"symbols\":[\"SH.600519\",\"SZ.000858\"],\"startDate\":\"2026-07-10\",\"endDate\":\"2026-07-10\"}");
        var run = workbenchService.runPlan(workbenchService.createPlan(dto).getId());
        var task = taskMapper.selectById(run.getLastTaskId());
        assertEquals("PARTIAL_FAILED", task.getStatus());
        assertEquals(2, itemMapper.selectAllByTaskId(task.getId()).size());
        assertTrue(itemMapper.selectAllByTaskId(task.getId()).stream()
                .anyMatch(item -> "MARKET_DATA_PROVIDER_TIMEOUT".equals(item.getErrorCode())));
        fakeProvider.setFailureSymbol("");
    }

    @Test
    void providerBarOutsideConfiguredSessionIsSkippedInsteadOfPersisted() {
        fakeProvider.setFakeMinuteBars("SH.600000", List.of(
                bar("SH.600000", LocalDateTime.of(2026, 7, 10, 14, 55)),
                bar("SH.600000", LocalDateTime.of(2026, 7, 10, 15, 0))));

        var run = workbenchService.runPlan(workbenchService.createPlan(plan("minute-session-boundary", "SH.600000")).getId());
        var task = taskMapper.selectById(run.getLastTaskId());

        assertEquals("SUCCEEDED", task.getStatus());
        assertEquals(1, task.getInsertedCount());
        assertEquals(1, task.getSkippedCount());
        assertEquals(1, minuteBarMapper.countByFilter("SH.600000", "5M", "NONE", "FAKE",
                null, null, null));
    }

    private ProviderMinuteBar bar(String symbol, LocalDateTime start) {
        return new ProviderMinuteBar(symbol, start, "5M", "NONE",
                new BigDecimal("10.00"), new BigDecimal("10.20"), new BigDecimal("9.90"),
                new BigDecimal("10.10"), 1000L, new BigDecimal("10000.00"));
    }

    private CreateSyncPlanDTO plan(String name, String symbol) {
        CreateSyncPlanDTO dto = new CreateSyncPlanDTO();
        dto.setPlanName(name);
        dto.setTaskType("MINUTE_BAR_BACKFILL");
        dto.setProvider("FAKE");
        dto.setScopeJson("{\"symbols\":[\"" + symbol + "\"],\"startDate\":\"2026-07-10\",\"endDate\":\"2026-07-10\"}");
        dto.setIntervalType("5M");
        dto.setAdjustType("NONE");
        dto.setTriggerType("MANUAL");
        return dto;
    }
}
