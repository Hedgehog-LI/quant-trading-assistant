package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.service.MarketQuoteService;
import com.quant.trade.marketdata.vo.MarketDataVOs.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 行情 provider / 快照 / 同步 / 提醒 REST 控制器。LongPort 只读，禁止交易/账户/订单。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data")
@RequiredArgsConstructor
public class MarketQuoteController {
    private final MarketQuoteService marketQuoteService;

    @GetMapping("/providers/LONGPORT/status")
    public ApiResponse<ProviderStatusVO> providerStatus() { return ApiResponse.ok(marketQuoteService.getProviderStatus()); }

    @PostMapping("/providers/LONGPORT/health-check")
    public ApiResponse<ProviderStatusVO> healthCheck() { return ApiResponse.ok(marketQuoteService.healthCheck()); }

    @PostMapping("/quotes/latest")
    public ApiResponse<List<StockQuoteSnapshotVO>> fetchLatestQuotes(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked") List<String> symbols = (List<String>) body.get("canonicalSymbols");
        boolean persist = Boolean.TRUE.equals(body.get("persist"));
        return ApiResponse.ok(marketQuoteService.fetchLatestQuotes(symbols != null ? symbols : List.of(), persist));
    }

    @GetMapping("/quote-snapshots")
    public ApiResponse<Map<String, Object>> listSnapshots(
            @RequestParam(required = false) String canonicalSymbol,
            @RequestParam(required = false) String dataSource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(marketQuoteService.listSnapshots(canonicalSymbol, dataSource, page, size));
    }

    @PostMapping("/sync-tasks/daily-bars")
    public ApiResponse<MarketDataSyncTaskVO> createSyncTask(@RequestBody CreateSyncTaskDTO dto) {
        return ApiResponse.ok(marketQuoteService.createSyncTask(dto));
    }

    @GetMapping("/sync-tasks")
    public ApiResponse<Map<String, Object>> listSyncTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(marketQuoteService.listSyncTasks(status, provider, page, size));
    }

    @GetMapping("/sync-tasks/{id}")
    public ApiResponse<MarketDataSyncTaskVO> getSyncTask(@PathVariable Long id) {
        return ApiResponse.ok(marketQuoteService.getSyncTask(id));
    }

    @GetMapping("/alerts")
    public ApiResponse<Map<String, Object>> listAlerts(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String canonicalSymbol,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(marketQuoteService.listAlerts(resolved, severity, canonicalSymbol, page, size));
    }

    @PatchMapping("/alerts/{id}/resolve")
    public ApiResponse<MarketDataAlertVO> resolveAlert(@PathVariable Long id) {
        return ApiResponse.ok(marketQuoteService.resolveAlert(id));
    }
}
