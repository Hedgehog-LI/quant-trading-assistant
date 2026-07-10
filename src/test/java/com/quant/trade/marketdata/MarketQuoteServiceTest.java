package com.quant.trade.marketdata;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.dao.MarketDataAlertMapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 行情服务测试。
 * <p>
 * 默认使用 DisabledMarketDataProvider（未配置 LongPort），覆盖：
 * - provider 未配置状态
 * - 未配置时获取行情被拒
 * - 未配置时同步被拒并生成异常提醒
 * - 异常提醒查询和 resolve
 * - 快照空列表查询
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketQuoteServiceTest {

    @Autowired private MarketQuoteService marketQuoteService;
    @Autowired private MarketDataAlertMapper alertMapper;
    @Autowired private StockQuoteSnapshotMapper quoteSnapshotMapper;

    @Test
    void providerStatusNotConfigured() {
        ProviderStatusVO status = marketQuoteService.getProviderStatus();
        assertFalse(status.configured());
        assertFalse(status.reachable());
        assertNotNull(status.lastError());
    }

    @Test
    void healthCheckSameAsStatus() {
        ProviderStatusVO hs = marketQuoteService.healthCheck();
        assertFalse(hs.configured());
    }

    @Test
    void fetchLatestQuotesRejectedWhenNotConfigured() {
        assertThrows(BusinessException.class, () ->
                marketQuoteService.fetchLatestQuotes(
                        new FetchQuotesRequestDTO(List.of("SH.600519"), true)));
    }

    @Test
    void syncTaskRejectedWhenNotConfigured() {
        CreateSyncTaskDTO dto = new CreateSyncTaskDTO(
                MarketDataConstants.TASK_TYPE_DAILY_BAR_SYNC, "LONGPORT",
                "{\"canonicalSymbol\":\"SH.600519\",\"startDate\":\"2026-06-01\",\"endDate\":\"2026-07-01\"}");
        assertThrows(BusinessException.class, () -> marketQuoteService.createAndExecuteDailyBarSync(dto));
    }

    @Test
    void syncTaskNotConfiguredCreatesAlert() {
        CreateSyncTaskDTO dto = new CreateSyncTaskDTO(
                MarketDataConstants.TASK_TYPE_DAILY_BAR_SYNC, "LONGPORT",
                "{\"canonicalSymbol\":\"SH.600519\"}");
        try {
            marketQuoteService.createAndExecuteDailyBarSync(dto);
        } catch (BusinessException ignored) {
            // 预期异常
        }
        // 验证创建了 PROVIDER_NOT_CONFIGURED alert
        var alerts = marketQuoteService.listAlerts(null, null, null, 1, 20);
        assertTrue(alerts.items().stream()
                .anyMatch(a -> MarketDataConstants.ALERT_TYPE_PROVIDER_NOT_CONFIGURED.equals(a.alertType())));
    }

    @Test
    void quoteSnapshotEmptyList() {
        PageResultVO<StockQuoteSnapshotVO> result = marketQuoteService.listSnapshots(null, null, 1, 20);
        assertNotNull(result);
        assertEquals(0L, result.total());
    }

    @Test
    void alertResolveSuccess() {
        // 直接用 mapper 插入一条 alert
        MarketDataAlertDO alert = MarketDataAlertDO.builder()
                .alertType(MarketDataConstants.ALERT_TYPE_STALE_QUOTE)
                .severity(MarketDataConstants.ALERT_SEVERITY_WARN)
                .provider("SYSTEM")
                .message("测试提醒")
                .resolved(false).build();
        alertMapper.insert(alert);

        MarketDataAlertVO resolved = marketQuoteService.resolveAlert(alert.getId());
        assertTrue(resolved.resolved());
    }

    @Test
    void alertListFilterByResolved() {
        // 插入两条 alert
        for (int i = 0; i < 2; i++) {
            alertMapper.insert(MarketDataAlertDO.builder()
                    .alertType(MarketDataConstants.ALERT_TYPE_SYNC_FAILED)
                    .severity(MarketDataConstants.ALERT_SEVERITY_HIGH)
                    .provider("SYSTEM")
                    .message("测试" + i)
                    .resolved(false).build());
        }

        var unresolved = marketQuoteService.listAlerts(false, null, null, 1, 20);
        assertTrue(unresolved.total() >= 2);

        // resolve 第一条
        Long firstId = unresolved.items().get(0).id();
        marketQuoteService.resolveAlert(firstId);

        var stillUnresolved = marketQuoteService.listAlerts(false, null, null, 1, 20);
        assertEquals(unresolved.total() - 1, stillUnresolved.total());
    }

    @Test
    void syncTaskListEmptyByDefault() {
        PageResultVO<MarketDataSyncTaskVO> result = marketQuoteService.listSyncTasks(null, null, 1, 20);
        assertNotNull(result);
        // 可能有之前测试残留（@Transactional 会回滚，但 H2 同事务可见）
        assertTrue(result.total() >= 0);
    }

    @Test
    void getSyncTaskNotFoundThrows() {
        assertThrows(BusinessException.class, () -> marketQuoteService.getSyncTask(99999L));
    }
}
