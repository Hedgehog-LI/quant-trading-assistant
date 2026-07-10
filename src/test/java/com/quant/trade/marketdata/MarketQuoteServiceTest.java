package com.quant.trade.marketdata;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.dao.MarketDataAlertMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper;
import com.quant.trade.marketdata.dao.StockQuoteSnapshotMapper;
import com.quant.trade.marketdata.dto.CreateSyncTaskDTO;
import com.quant.trade.marketdata.dto.FetchQuotesRequestDTO;
import com.quant.trade.marketdata.model.MarketDataAlertDO;
import com.quant.trade.marketdata.service.MarketQuoteService;
import com.quant.trade.marketdata.vo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 行情服务测试。
 * <p>
 * 默认使用 DisabledMarketDataProvider，覆盖：
 * - provider 未配置状态
 * - 未配置时获取行情被拒
 * - 未配置时同步被拒但 sync_task 留痕 FAILED + alert 生成（不回滚）
 * - 异常提醒查询和 resolve
 * - 快照空列表查询
 */
@SpringBootTest
@ActiveProfiles("test")
class MarketQuoteServiceTest {

    @Autowired private MarketQuoteService marketQuoteService;
    @Autowired private MarketDataAlertMapper alertMapper;
    @Autowired private MarketDataSyncTaskMapper taskMapper;
    @Autowired private StockQuoteSnapshotMapper quoteSnapshotMapper;

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
     * 关键测试：同步任务因 provider 未配置失败后，sync_task 必须留痕 FAILED，
     * 且 market_data_alert 必须有 PROVIDER_NOT_CONFIGURED 提醒。
     * <p>
     * 不使用 @Transactional（确保多事务可见），手动清理。
     */
    @Test
    void syncTaskFailureLeavesTraceInDb() {
        CreateSyncTaskDTO dto = new CreateSyncTaskDTO(
                MarketDataConstants.TASK_TYPE_DAILY_BAR_SYNC, "LONGPORT",
                "SH.600519", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), "NONE");

        // 预期抛异常
        assertThrows(BusinessException.class, () -> marketQuoteService.createAndExecuteDailyBarSync(dto));

        // 验证 sync_task 留痕（FAILED）
        var tasks = marketQuoteService.listSyncTasks("FAILED", null, 1, 20);
        assertTrue(tasks.total() >= 1, "应该有一条 FAILED 同步任务");
        MarketDataSyncTaskVO task = tasks.items().get(0);
        assertEquals("FAILED", task.status());
        assertNotNull(task.startedAt());
        assertNotNull(task.finishedAt());
        assertNotNull(task.lastErrorCode());

        // 验证 alert 留痕
        var alerts = marketQuoteService.listAlerts(null, "HIGH", null, 1, 20);
        assertTrue(alerts.items().stream()
                .anyMatch(a -> MarketDataConstants.ALERT_TYPE_PROVIDER_NOT_CONFIGURED.equals(a.alertType())
                        && a.taskId() != null && a.taskId().equals(task.id())),
                "应该有一条关联该任务的 PROVIDER_NOT_CONFIGURED alert");

        // 清理（无 @Transactional 回滚）
        cleanupTask(task.id());
    }

    @Test
    void quoteSnapshotEmptyList() {
        PageResultVO<StockQuoteSnapshotVO> result = marketQuoteService.listSnapshots(null, null, 1, 20);
        assertNotNull(result);
        assertEquals(0L, result.total());
    }

    @Test
    void alertResolveSuccess() {
        // 直接插入
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

    /** 手动清理测试产生的 task 和关联 alert（无 @Transactional 兜底）。 */
    private void cleanupTask(Long taskId) {
        // H2 不支持级联删除，直接用 mapper 清理
        var alerts = alertMapper.selectByFilter(null, null, null, 100, 0);
        for (MarketDataAlertDO a : alerts) {
            if (taskId.equals(a.getTaskId())) {
                alertMapper.updateResolved(a.getId(), true);
            }
        }
    }
}
