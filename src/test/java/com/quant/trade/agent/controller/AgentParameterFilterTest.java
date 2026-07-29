package com.quant.trade.agent.controller;

import com.quant.trade.agent.vo.TrustedAnswer;
import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.dashboard.service.DashboardService;
import com.quant.trade.dashboard.vo.DashboardTodayVO;
import com.quant.trade.marketdata.service.MarketDataWorkbenchService;
import com.quant.trade.marketdata.service.MarketQuoteService;
import com.quant.trade.marketdata.service.MarketSectorRankingService;
import com.quant.trade.marketdata.vo.PageResultVO;
import com.quant.trade.marketdata.vo.WorkbenchOverviewVO;
import com.quant.trade.marketdata.vo.MarketDataAlertVO;
import com.quant.trade.marketdata.vo.MarketSectorRankingBatchVO;
import com.quant.trade.marketdata.vo.ProviderStatusVO;
import com.quant.trade.portfolio.service.PortfolioService;
import com.quant.trade.portfolio.vo.PortfolioSummaryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.quant.trade.agent.service.AgentQueryService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Agent 参数过滤数据差异测试。
 * <p>
 * 插入不同 CN/HK 数据后断言返回结果不同。
 */
@ExtendWith(MockitoExtension.class)
class AgentParameterFilterTest {

    @Mock private DashboardService dashboardService;
    @Mock private PortfolioService portfolioService;
    @Mock private MarketDataWorkbenchService marketDataWorkbenchService;
    @Mock private MarketQuoteService marketQuoteService;
    @Mock private MarketSectorRankingService marketSectorRankingService;

    @InjectMocks private AgentQueryService service;

    @Test
    void sectorRankingReturnsDifferentDataForCNvsHK() {
        // CN batch
        var cnBatch = new MarketSectorRankingBatchVO(
            1L, "LONGPORT", "CN", LocalDate.of(2026, 7, 26),
            "DAILY", LocalDateTime.now(), LocalDateTime.now(),
            10, 7, 2, 1,
            "cn-leader", "白酒", new java.math.BigDecimal("3.5"),
            "cn-laggard", "地产", new java.math.BigDecimal("-2.1"),
            "VALID"
        );
        // HK batch
        var hkBatch = new MarketSectorRankingBatchVO(
            2L, "LONGPORT", "HK", LocalDate.of(2026, 7, 26),
            "DAILY", LocalDateTime.now(), LocalDateTime.now(),
            8, 5, 2, 1,
            "hk-leader", "科技", new java.math.BigDecimal("2.8"),
            "hk-laggard", "金融", new java.math.BigDecimal("-1.5"),
            "VALID"
        );

        // Mock CN vs HK returning different data
        when(marketSectorRankingService.history(eq("CN"), any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageResultVO<>(List.of(cnBatch), 1, 1, 10));
        when(marketSectorRankingService.history(eq("HK"), any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageResultVO<>(List.of(hkBatch), 1, 1, 10));

        TrustedAnswer cnResult = service.sectorRankingSummary("CN", 10);
        TrustedAnswer hkResult = service.sectorRankingSummary("HK", 10);

        // Assert CN and HK return DIFFERENT data
        assertTrue(cnResult.data() != null);
        assertTrue(hkResult.data() != null);

        @SuppressWarnings("unchecked")
        var cnData = (java.util.Map<String, Object>) cnResult.data();
        @SuppressWarnings("unchecked")
        var hkData = (java.util.Map<String, Object>) hkResult.data();

        assertEquals("CN", cnData.get("market"));
        assertEquals("HK", hkData.get("market"));

        @SuppressWarnings("unchecked")
        var cnBatchMap = (java.util.Map<String, Object>) cnData.get("latestBatch");
        @SuppressWarnings("unchecked")
        var hkBatchMap = (java.util.Map<String, Object>) hkData.get("latestBatch");

        assertEquals("白酒", cnBatchMap.get("leaderSectorName"));
        assertEquals("科技", hkBatchMap.get("leaderSectorName"));
        assertNotEquals(cnBatchMap.get("leaderSectorName"), hkBatchMap.get("leaderSectorName"),
            "CN and HK must return different sector rankings");
    }

    @Test
    void dataQualityAlertsResolvedVsUnresolvedReturnDifferentResults() {
        var resolvedAlert = new MarketDataAlertVO(
            1L, "SYNC_FAILED", "WARN", "SH.600519",
            LocalDateTime.now(), LocalDate.now(), "LONGPORT", 100L,
            "resolved alert", null, true, LocalDateTime.now()
        );
        var unresolvedAlert = new MarketDataAlertVO(
            2L, "PROVIDER_NOT_CONFIGURED", "HIGH", "SZ.000858",
            LocalDateTime.now(), LocalDate.now(), "LONGPORT", 101L,
            "unresolved alert", null, false, LocalDateTime.now()
        );

        when(marketQuoteService.listAlerts(eq(true), isNull(), isNull(), eq(1), anyInt()))
            .thenReturn(new PageResultVO<>(List.of(resolvedAlert), 1, 1, 10));
        when(marketQuoteService.listAlerts(eq(false), isNull(), isNull(), eq(1), anyInt()))
            .thenReturn(new PageResultVO<>(List.of(unresolvedAlert), 1, 1, 10));

        TrustedAnswer resolvedResult = service.dataQualityAlerts("resolved", null, 10);
        TrustedAnswer unresolvedResult = service.dataQualityAlerts("unresolved", null, 10);

        assertTrue(resolvedResult.data() != null);
        assertTrue(unresolvedResult.data() != null);

        @SuppressWarnings("unchecked")
        var resolvedData = (java.util.Map<String, Object>) resolvedResult.data();
        @SuppressWarnings("unchecked")
        var unresolvedData = (java.util.Map<String, Object>) unresolvedResult.data();

        assertEquals(1L, resolvedData.get("total"));
        assertEquals(1L, unresolvedData.get("total"));

        @SuppressWarnings("unchecked")
        var resolvedAlerts = (List<?>) resolvedData.get("alerts");
        @SuppressWarnings("unchecked")
        var unresolvedAlerts = (List<?>) unresolvedData.get("alerts");

        assertEquals(1, resolvedAlerts.size());
        assertEquals(1, unresolvedAlerts.size());
        assertNotEquals(resolvedAlerts.get(0), unresolvedAlerts.get(0),
            "resolved and unresolved must return different alert sets");
    }

    @Test
    void dataQualityAlertsStatusNullDoesNotNPE() {
        when(marketQuoteService.listAlerts(isNull(), isNull(), isNull(), eq(1), anyInt()))
            .thenReturn(new PageResultVO<>(List.of(), 0, 1, 10));

        TrustedAnswer result = service.dataQualityAlerts(null, null, 10);

        assertTrue(result.data() != null || result.warnings() != null);
        assertNotNull(result.conclusion());
        assertNotNull(result.data());
    }

    @Test
    void collectionFailuresLimitParamChangesResultCount() {
        // limit=2 returns 2 items, limit=5 returns 5 items
        var taskVOs2 = List.of(
            new com.quant.trade.marketdata.vo.MarketDataSyncTaskVO(
                1L, "DAILY_BAR_SYNC", "LONGPORT", "{}", "FAILED",
                0, 0, 0, 0, 0, 0, null, null, null, null, null, null),
            new com.quant.trade.marketdata.vo.MarketDataSyncTaskVO(
                2L, "DAILY_BAR_SYNC", "LONGPORT", "{}", "FAILED",
                0, 0, 0, 0, 0, 0, null, null, null, null, null, null)
        );
        var taskVOs5 = java.util.stream.Stream.concat(taskVOs2.stream(),
            java.util.stream.Stream.of(
                new com.quant.trade.marketdata.vo.MarketDataSyncTaskVO(
                    3L, "MINUTE_BAR_BACKFILL", "LONGPORT", "{}", "FAILED",
                    0, 0, 0, 0, 0, 0, null, null, null, null, null, null),
                new com.quant.trade.marketdata.vo.MarketDataSyncTaskVO(
                    4L, "MINUTE_BAR_BACKFILL", "LONGPORT", "{}", "FAILED",
                    0, 0, 0, 0, 0, 0, null, null, null, null, null, null),
                new com.quant.trade.marketdata.vo.MarketDataSyncTaskVO(
                    5L, "INTRADAY_MINUTE_REFRESH", "LONGPORT", "{}", "FAILED",
                    0, 0, 0, 0, 0, 0, null, null, null, null, null, null)
            )
        ).toList();

        var emptyAlerts = new PageResultVO<MarketDataAlertVO>(List.of(), 0, 1, 50);

        // collectionFailures now queries syncTasks with 50 then applies limit in-memory;
        // alerts are queried with safeLimit (varies by call), so match anyInt()
        when(marketQuoteService.listSyncTasks(eq("FAILED"), isNull(), eq(1), eq(50)))
            .thenReturn(new PageResultVO<>(taskVOs5, 5, 1, 50));
        when(marketQuoteService.listAlerts(eq(false), eq("HIGH"), isNull(), eq(1), anyInt()))
            .thenReturn(emptyAlerts);

        TrustedAnswer result2 = service.collectionFailures(null, null, 2);
        TrustedAnswer result5 = service.collectionFailures(null, null, 5);

        assertNotNull(result2.data());
        assertNotNull(result5.data());

        @SuppressWarnings("unchecked")
        var data2 = (java.util.Map<String, Object>) result2.data();
        @SuppressWarnings("unchecked")
        var data5 = (java.util.Map<String, Object>) result5.data();

        // limit=2 should return only 2 tasks (but total count is 5)
        assertEquals(5, data2.get("failedTaskCount"));
        assertEquals(5, data5.get("failedTaskCount"));
        // The actual returned list length differs by limit
        var tasks2 = (List<?>) data2.get("failedTasks");
        var tasks5 = (List<?>) data5.get("failedTasks");
        assertEquals(2, tasks2.size(), "limit=2 must return 2 tasks");
        assertEquals(5, tasks5.size(), "limit=5 must return 5 tasks");
        assertNotEquals(tasks2.size(), tasks5.size(),
            "Different limit must return different result sizes");
    }

    @Test
    void collectionOverviewMarketFilterReturnsDifferentWatermarks() {
        // Create watermarks with CN (SH/SZ) and HK symbols
        var cnWm1 = new com.quant.trade.marketdata.vo.MarketDataWatermarkVO();
        cnWm1.setCanonicalSymbol("SH.600519");
        cnWm1.setDataSource("LONGPORT");
        cnWm1.setIntervalType("1D");
        cnWm1.setTotalRows(100L);
        var hkWm1 = new com.quant.trade.marketdata.vo.MarketDataWatermarkVO();
        hkWm1.setCanonicalSymbol("HK.00700");
        hkWm1.setDataSource("LONGPORT");
        hkWm1.setIntervalType("1D");
        hkWm1.setTotalRows(50L);

        var overview = new com.quant.trade.marketdata.vo.WorkbenchOverviewVO();
        overview.setProviderStatus(new com.quant.trade.marketdata.vo.ProviderStatusVO("LONGPORT", true, true, null, null));
        overview.setTotalSymbols(2);
        overview.setTotalDailyBars(150);
        overview.setTotalMinuteBars(0);
        overview.setFailedTasksToday(0);
        overview.setUnresolvedHighAlerts(0);
        overview.setUnresolvedWarnAlerts(0);
        overview.setRecentWatermarks(List.of(cnWm1, hkWm1));

        when(marketDataWorkbenchService.getOverview()).thenReturn(overview);

        // Query with market=CN
        TrustedAnswer cnResult = service.collectionOverview("CN", null);
        // Query with market=HK
        TrustedAnswer hkResult = service.collectionOverview("HK", null);

        assertNotNull(cnResult.data());
        assertNotNull(hkResult.data());

        @SuppressWarnings("unchecked")
        var cnData = (java.util.Map<String, Object>) cnResult.data();
        @SuppressWarnings("unchecked")
        var hkData = (java.util.Map<String, Object>) hkResult.data();

        // Market filter must be applied
        assertEquals(true, cnData.get("marketFilterApplied"));
        assertEquals(true, hkData.get("marketFilterApplied"));

        // CN watermarks should only contain SH symbols
        @SuppressWarnings("unchecked")
        var cnWms = (List<com.quant.trade.marketdata.vo.MarketDataWatermarkVO>) cnData.get("recentWatermarks");
        assertEquals(1, cnWms.size(), "CN filter should return only 1 watermark (SH.600519)");
        assertEquals("SH.600519", cnWms.get(0).getCanonicalSymbol());

        // HK watermarks should only contain HK symbols
        @SuppressWarnings("unchecked")
        var hkWms = (List<com.quant.trade.marketdata.vo.MarketDataWatermarkVO>) hkData.get("recentWatermarks");
        assertEquals(1, hkWms.size(), "HK filter should return only 1 watermark (HK.00700)");
        assertEquals("HK.00700", hkWms.get(0).getCanonicalSymbol());

        // Different markets return different watermark counts and symbols
        assertNotEquals(cnWms.get(0).getCanonicalSymbol(), hkWms.get(0).getCanonicalSymbol(),
            "CN and HK must return different watermarks");
    }
}
