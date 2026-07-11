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
     * 关键测试：连续 3 次同 scope 失败重试，生成 3 条 FAILED，parentTaskId 链路正确，无 500。
     * <p>
     * 第一次失败 → task1=FAILED(parentTaskId=null) + alert(taskId=task1)
     * 第二次重试 → task2=FAILED(parentTaskId=task1) + alert(taskId=task2)
     * 第三次重试 → task3=FAILED(parentTaskId=task2) + alert(taskId=task3)
     * 三条任务都可追溯，alert 都关联有效 taskId，不产生唯一键冲突。
     */
    @Test
    void syncTaskThreeConsecutiveRetriesKeepsFullHistory() {
        String uniqueSymbol = "SH.999907";
        CreateSyncTaskDTO dto = new CreateSyncTaskDTO(
                MarketDataConstants.TASK_TYPE_DAILY_BAR_SYNC, "LONGPORT",
                uniqueSymbol, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), "NONE");

        // 第一次失败
        assertThrows(BusinessException.class, () -> marketQuoteService.createAndExecuteDailyBarSync(dto));
        // 第二次重试
        assertThrows(BusinessException.class, () -> marketQuoteService.createAndExecuteDailyBarSync(dto));
        // 第三次重试
        assertThrows(BusinessException.class, () -> marketQuoteService.createAndExecuteDailyBarSync(dto));

        // 查询同 scope 所有 FAILED 任务
        var allTasks = marketQuoteService.listSyncTasks("FAILED", null, 1, 100);
        var sameScopeTasks = allTasks.items().stream()
                .filter(t -> t.scopeJson().contains(uniqueSymbol))
                .sorted((a, b) -> Long.compare(a.id() instanceof Number ? ((Number) a.id()).longValue() : 0,
                                               b.id() instanceof Number ? ((Number) b.id()).longValue() : 0))
                .toList();

        assertTrue(sameScopeTasks.size() >= 3,
                "同 scope 应有至少 3 条 FAILED，实际: " + sameScopeTasks.size());

        // 验证 parentTaskId 链路：task1.parentTaskId=null, task2.parentTaskId=task1, task3.parentTaskId=task2
        // 按 id 升序排序后验证链
        var sorted = sameScopeTasks.stream()
                .sorted((a, b) -> Long.compare(
                        ((Number) a.id()).longValue(),
                        ((Number) b.id()).longValue()))
                .toList();
        assertNull(sorted.get(0).parentTaskId(), "第一条 parentTaskId 应为 null");
        for (int i = 1; i < sorted.size(); i++) {
            assertEquals(sorted.get(i - 1).id(), sorted.get(i).parentTaskId(),
                    "第 " + (i + 1) + " 条 parentTaskId 应指向前一条");
        }

        // 验证 3 条 alert 都关联有效 taskId
        var alerts = marketQuoteService.listAlerts(null, "HIGH", null, 1, 100);
        for (var task : sameScopeTasks) {
            assertTrue(alerts.items().stream()
                    .anyMatch(a -> task.id().equals(a.taskId())),
                    "每个 task 都应有对应 alert，task " + task.id() + " 缺失");
        }
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
