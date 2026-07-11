package com.quant.trade.marketdata;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.dao.MarketDataAlertMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.dto.CreateSyncTaskDTO;
import com.quant.trade.marketdata.dto.FetchQuotesRequestDTO;
import com.quant.trade.marketdata.model.MarketDataAlertDO;
import com.quant.trade.marketdata.service.MarketQuoteService;
import com.quant.trade.marketdata.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 行情服务测试。
 * <p>
 * 默认使用 DisabledMarketDataProvider，覆盖：
 * - provider 未配置状态
 * - 未配置时获取行情被拒
 * - 未配置时同步被拒但 sync_task 留痕 FAILED + alert 生成
 * - FAILED 可重试（新任务 parentTaskId 指向旧任务，两条记录都可追溯）
 * - 异常提醒查询和 resolve
 */
@SpringBootTest
@ActiveProfiles("test")
class MarketQuoteServiceTest {

    @Autowired private MarketQuoteService marketQuoteService;
    @Autowired private MarketDataAlertMapper alertMapper;
    @Autowired private MarketDataSyncTaskMapper taskMapper;

    @Test
    void providerStatusNotConfigured() {
        ProviderStatusVO status = marketQuoteService.getProviderStatus();
        assertFalse(status.configured());
        assertFalse(status.reachable());
        assertNotNull(status.lastError());
    }

    @Test
    void fetchLatestQuotesRejectedWhenNotConfigured() {
        assertThrows(BusinessException.class, () ->
                marketQuoteService.fetchLatestQuotes(
                        new FetchQuotesRequestDTO(List.of("SH.600519"), true)));
    }

    /**
     * 关键测试：同步任务因 provider 未配置失败后，sync_task 留痕 FAILED，
     * market_data_alert 有 PROVIDER_NOT_CONFIGURED 提醒，不回滚。
     */
    @Test
    void syncTaskFailureLeavesTraceInDb() {
        CreateSyncTaskDTO dto = new CreateSyncTaskDTO(
                MarketDataConstants.TASK_TYPE_DAILY_BAR_SYNC, "LONGPORT",
                "SH.600519", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), "NONE");

        assertThrows(BusinessException.class, () -> marketQuoteService.createAndExecuteDailyBarSync(dto));

        var tasks = marketQuoteService.listSyncTasks("FAILED", null, 1, 20);
        assertTrue(tasks.total() >= 1);
        MarketDataSyncTaskVO task = tasks.items().get(0);
        assertEquals("FAILED", task.status());
        assertNotNull(task.startedAt());
        assertNotNull(task.finishedAt());
        assertNotNull(task.lastErrorCode());

        var alerts = marketQuoteService.listAlerts(null, "HIGH", null, 1, 20);
        assertTrue(alerts.items().stream()
                .anyMatch(a -> MarketDataConstants.ALERT_TYPE_PROVIDER_NOT_CONFIGURED.equals(a.alertType())
                        && a.taskId() != null && a.taskId().equals(task.id())));
    }

    /**
     * 关键测试：FAILED 后同 scope 重试，旧任务保留可追溯，新任务 parentTaskId 指向旧任务。
     * <p>
     * 第一次失败 → task1=FAILED + alert1(taskId=task1.id)
     * 第二次重试 → task2=FAILED + alert2(taskId=task2.id)，task2.parentTaskId=task1.id
     * 两条 FAILED 任务都可查到，alert 都关联有效 task。
     */
    @Test
    void syncTaskFailedRetryKeepsHistoryWithParentLink() {
        // 用唯一 scope 避免和其他测试冲突
        String uniqueSymbol = "SH.999901";
        CreateSyncTaskDTO dto = new CreateSyncTaskDTO(
                MarketDataConstants.TASK_TYPE_DAILY_BAR_SYNC, "LONGPORT",
                uniqueSymbol, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), "NONE");

        // 第一次失败
        assertThrows(BusinessException.class, () -> marketQuoteService.createAndExecuteDailyBarSync(dto));
        var tasksAfter1 = marketQuoteService.listSyncTasks("FAILED", null, 1, 50);
        MarketDataSyncTaskVO task1 = tasksAfter1.items().stream()
                .filter(t -> t.scopeJson().contains(uniqueSymbol))
                .findFirst().orElse(null);
        assertNotNull(task1, "第一次 FAILED 任务应存在");
        assertNull(task1.parentTaskId(), "第一次任务 parentTaskId 应为 null");

        // 第二次重试
        assertThrows(BusinessException.class, () -> marketQuoteService.createAndExecuteDailyBarSync(dto));
        var tasksAfter2 = marketQuoteService.listSyncTasks("FAILED", null, 1, 50);
        // 同 scope 应有 2 条 FAILED
        var sameScopeTasks = tasksAfter2.items().stream()
                .filter(t -> t.scopeJson().contains(uniqueSymbol))
                .toList();
        assertTrue(sameScopeTasks.size() >= 2,
                "同 scope 应有至少 2 条 FAILED（旧+新），实际: " + sameScopeTasks.size());

        // 新任务 parentTaskId 指向旧任务
        MarketDataSyncTaskVO task2 = sameScopeTasks.get(0); // 最新的在前
        assertEquals(task1.id(), task2.parentTaskId(),
                "新 retry 任务 parentTaskId 应指向旧 FAILED 任务");

        // 旧任务仍可查（未被删除）
        assertNotNull(taskMapper.selectById(task1.id()),
                "旧 FAILED 任务不应被删除");

        // 两次的 alert 都关联有效 taskId
        var alerts = marketQuoteService.listAlerts(null, "HIGH", null, 1, 50);
        assertTrue(alerts.items().stream()
                .anyMatch(a -> MarketDataConstants.ALERT_TYPE_PROVIDER_NOT_CONFIGURED.equals(a.alertType())
                        && task2.id().equals(a.taskId())),
                "新任务的 alert 应关联新 taskId");
    }

    @Test
    void quoteSnapshotEmptyList() {
        PageResultVO<StockQuoteSnapshotVO> result = marketQuoteService.listSnapshots(null, null, 1, 20);
        assertNotNull(result);
        assertEquals(0L, result.total());
    }

    @Test
    void alertResolveSuccess() {
        MarketDataAlertDO alert = MarketDataAlertDO.builder()
                .alertType(MarketDataConstants.ALERT_TYPE_STALE_QUOTE)
                .severity(MarketDataConstants.ALERT_SEVERITY_WARN)
                .provider("SYSTEM").message("测试提醒").resolved(false).build();
        alertMapper.insert(alert);

        MarketDataAlertVO resolved = marketQuoteService.resolveAlert(alert.getId());
        assertTrue(resolved.resolved());
    }

    @Test
    void getSyncTaskNotFoundThrows() {
        assertThrows(BusinessException.class, () -> marketQuoteService.getSyncTask(99999L));
    }
}
