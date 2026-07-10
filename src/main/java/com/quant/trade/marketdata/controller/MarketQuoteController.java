package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.dto.CreateSyncTaskDTO;
import com.quant.trade.marketdata.dto.FetchQuotesRequestDTO;
import com.quant.trade.marketdata.service.MarketQuoteService;
import com.quant.trade.marketdata.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 行情 provider / 快照 / 同步 / 提醒 REST 控制器。
 * <p>
 * LongPort 仅作为只读行情源，禁止交易、账户、订单、真实持仓调用。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data")
@RequiredArgsConstructor
public class MarketQuoteController {

    private final MarketQuoteService marketQuoteService;

    // ==================== Provider 状态 ====================

    /** 查看 LongPort provider 配置状态（不暴露密钥）。 */
    @GetMapping("/providers/LONGPORT/status")
    public ApiResponse<ProviderStatusVO> providerStatus() {
        return ApiResponse.ok(marketQuoteService.getProviderStatus());
    }

    /** 触发只读健康检查。 */
    @PostMapping("/providers/LONGPORT/health-check")
    public ApiResponse<ProviderStatusVO> healthCheck() {
        return ApiResponse.ok(marketQuoteService.healthCheck());
    }

    // ==================== 最新价快照 ====================

    /** 获取最新行情，可选落库。 */
    @PostMapping("/quotes/latest")
    public ApiResponse<List<StockQuoteSnapshotVO>> fetchLatestQuotes(@RequestBody FetchQuotesRequestDTO dto) {
        return ApiResponse.ok(marketQuoteService.fetchLatestQuotes(dto));
    }

    /** 查询外部价格快照（分页）。 */
    @GetMapping("/quote-snapshots")
    public ApiResponse<PageResultVO<StockQuoteSnapshotVO>> listSnapshots(
            @RequestParam(required = false) String canonicalSymbol,
            @RequestParam(required = false) String dataSource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(marketQuoteService.listSnapshots(canonicalSymbol, dataSource, page, size));
    }

    // ==================== 历史日 K 同步 ====================

    /** 创建并执行日 K 同步任务（结构化 DTO，不暴露 scopeJson 手写解析）。 */
    @PostMapping("/sync-tasks/daily-bars")
    public ApiResponse<MarketDataSyncTaskVO> createAndExecuteSyncTask(@Valid @RequestBody CreateSyncTaskDTO dto) {
        return ApiResponse.ok(marketQuoteService.createAndExecuteDailyBarSync(dto));
    }

    /** 查询同步任务列表（分页）。 */
    @GetMapping("/sync-tasks")
    public ApiResponse<PageResultVO<MarketDataSyncTaskVO>> listSyncTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(marketQuoteService.listSyncTasks(status, provider, page, size));
    }

    /** 查询同步任务详情。 */
    @GetMapping("/sync-tasks/{id}")
    public ApiResponse<MarketDataSyncTaskVO> getSyncTask(@PathVariable Long id) {
        return ApiResponse.ok(marketQuoteService.getSyncTask(id));
    }

    // ==================== 异常提醒 ====================

    /** 查询行情异常提醒（分页）。 */
    @GetMapping("/alerts")
    public ApiResponse<PageResultVO<MarketDataAlertVO>> listAlerts(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String canonicalSymbol,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(marketQuoteService.listAlerts(resolved, severity, canonicalSymbol, page, size));
    }

    /** 标记提醒已处理。 */
    @PatchMapping("/alerts/{id}/resolve")
    public ApiResponse<MarketDataAlertVO> resolveAlert(@PathVariable Long id) {
        return ApiResponse.ok(marketQuoteService.resolveAlert(id));
    }
}
