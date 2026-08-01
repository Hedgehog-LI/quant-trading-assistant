package com.quant.trade.marketdata;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.provider.SecurityDirectoryProvider;
import com.quant.trade.marketdata.service.SecurityDirectorySyncScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AC-05：scheduler.enabled=true 时 SecurityDirectorySyncScheduler bean 装配，
 * 测试 seam triggerDailySync/triggerWeeklyReconciliation 可调用；
 * provider disabled 时调度可解释跳过（不抛出，记日志），不破坏目录。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "qta.market-data.security-directory.scheduler.enabled=true",
        "qta.market-data.security-directory.enabled=false"
})
class SecurityDirectorySyncSchedulerTest {

    @Autowired
    private SecurityDirectorySyncScheduler scheduler;

    @Autowired
    private SecurityDirectoryProvider provider;

    @Test
    void schedulerBeanAssembledWhenEnabled() {
        assertNotNull(scheduler, "scheduler.enabled=true 时 bean 装配");
    }

    @Test
    void triggerDailySyncSkipsExplainablyWhenProviderDisabled() {
        // provider disabled → triggerScheduled 抛 BusinessException，scheduler 捕获记日志，不外抛。
        assertDoesNotThrow(() -> scheduler.triggerDailySync(LocalDateTime.now()));
    }

    @Test
    void triggerWeeklyReconciliationSkipsExplainablyWhenProviderDisabled() {
        assertDoesNotThrow(() -> scheduler.triggerWeeklyReconciliation(LocalDateTime.now()));
    }
}
