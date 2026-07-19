package com.quant.trade.marketdata.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyncPlanValidationManagerTest {
    private final SyncPlanValidationManager manager = new SyncPlanValidationManager(new ObjectMapper());

    @Test
    void minuteBackfillRequiresManualDatesAndInterval() {
        MarketDataSyncPlanDO plan = base("MINUTE_BAR_BACKFILL", "INTRADAY",
                "{\"symbols\":[\"SH.603308\"]}");
        var result = manager.inspect(plan);
        assertFalse(result.manuallyRunnable());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("只允许 MANUAL")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("startDate")));
        assertThrows(BusinessException.class, () -> manager.validate(plan));
    }

    @Test
    void intradayRequiresFrequencyAndRejectsUnsupportedMarket() {
        MarketDataSyncPlanDO plan = base("INTRADAY_MINUTE_REFRESH", "INTRADAY",
                "{\"symbols\":[\"HK.02498\"]}");
        var result = manager.inspect(plan);
        assertFalse(result.automaticallyRunnable());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("采集频率")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("自动盘中任务当前只支持")));
    }

    @Test
    void validIntradayPlanIsAutomaticallyRunnable() {
        MarketDataSyncPlanDO plan = base("INTRADAY_MINUTE_REFRESH", "INTRADAY",
                "{\"symbols\":[\"SH.603308\"]}");
        plan.setCollectFrequency("60S");
        assertTrue(manager.inspect(plan).automaticallyRunnable());
        assertEquals(60, manager.frequencySeconds("1m"));
    }

    private MarketDataSyncPlanDO base(String taskType, String trigger, String scope) {
        return MarketDataSyncPlanDO.builder().taskType(taskType).triggerType(trigger).provider("LONGPORT")
                .scopeJson(scope).intervalType("5M").adjustType("NONE").build();
    }
}
