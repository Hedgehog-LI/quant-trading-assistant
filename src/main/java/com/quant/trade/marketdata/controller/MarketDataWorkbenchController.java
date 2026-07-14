package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.dto.CreateSyncPlanDTO;
import com.quant.trade.marketdata.dto.MinuteBarUpsertDTO;
import com.quant.trade.marketdata.dto.UpdateSyncPlanDTO;
import com.quant.trade.marketdata.service.MarketDataWorkbenchService;
import com.quant.trade.marketdata.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行情工作台 / 采集计划 / 分钟 K / 任务明细 / 水位 REST 控制器。
 * <p>
 * LongPort 仅作为只读行情源，禁止交易、账户、订单、真实持仓调用。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data")
@RequiredArgsConstructor
public class MarketDataWorkbenchController {

    private final MarketDataWorkbenchService workbenchService;

    // ==================== 工作台概览 ====================

    /** 行情工作台概览：provider 状态、重点标的、最近同步、失败任务、未处理提醒、数据水位。 */
    @GetMapping("/workbench/overview")
    public ApiResponse<WorkbenchOverviewVO> overview() {
        return ApiResponse.ok(workbenchService.getOverview());
    }

    // ==================== 采集计划 CRUD ====================

    @PostMapping("/sync-plans")
    public ApiResponse<MarketDataSyncPlanVO> createPlan(@Valid @RequestBody CreateSyncPlanDTO dto) {
        return ApiResponse.ok(workbenchService.createPlan(dto));
    }

    @GetMapping("/sync-plans")
    public ApiResponse<PageResultVO<MarketDataSyncPlanVO>> listPlans(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(workbenchService.listPlans(taskType, provider, enabled, page, size));
    }

    @GetMapping("/sync-plans/{id}")
    public ApiResponse<MarketDataSyncPlanVO> getPlan(@PathVariable Long id) {
        return ApiResponse.ok(workbenchService.getPlan(id));
    }

    @PutMapping("/sync-plans/{id}")
    public ApiResponse<MarketDataSyncPlanVO> updatePlan(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateSyncPlanDTO dto) {
        return ApiResponse.ok(workbenchService.updatePlan(id, dto));
    }

    @PostMapping("/sync-plans/{id}/toggle")
    public ApiResponse<MarketDataSyncPlanVO> togglePlan(@PathVariable Long id,
                                                         @RequestParam boolean enabled) {
        return ApiResponse.ok(workbenchService.togglePlan(id, enabled));
    }

    /** 手动执行采集计划（生成 sync_task + task_item + 更新 lastRunAt/lastTaskId）。 */
    @PostMapping("/sync-plans/{id}/run")
    public ApiResponse<MarketDataSyncPlanVO> runPlan(@PathVariable Long id) {
        return ApiResponse.ok(workbenchService.runPlan(id));
    }

    // ==================== 任务明细 ====================

    @GetMapping("/sync-tasks/{taskId}/items")
    public ApiResponse<PageResultVO<MarketDataSyncTaskItemVO>> listTaskItems(
            @PathVariable Long taskId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(workbenchService.listTaskItems(taskId, status, page, size));
    }

    // ==================== 分钟 K ====================

    @GetMapping("/minute-bars")
    public ApiResponse<PageResultVO<StockMinuteBarVO>> listMinuteBars(
            @RequestParam(required = false) String canonicalSymbol,
            @RequestParam(required = false) String intervalType,
            @RequestParam(required = false) String adjustType,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime,
            @RequestParam(required = false) String tradeDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(workbenchService.listMinuteBars(canonicalSymbol, intervalType, adjustType,
                dataSource, fromTime, toTime, tradeDate, page, size));
    }

    /** 写入单条分钟 K（带质量校验 + 幂等 + 水位）。 */
    @PostMapping("/minute-bars")
    public ApiResponse<MarketDataWorkbenchService.MinuteBarUpsertResult> upsertMinuteBar(
            @Valid @RequestBody MinuteBarUpsertDTO dto) {
        return ApiResponse.ok(workbenchService.upsertMinuteBar(dto));
    }

    // ==================== 交易时段 / 日历 ====================

    @GetMapping("/trading-sessions")
    public ApiResponse<List<MarketTradingSessionVO>> tradingSessions() {
        return ApiResponse.ok(workbenchService.getTradingSessions());
    }

    @GetMapping("/trading-sessions/is-trading-day")
    public ApiResponse<Boolean> isTradingDay(
            @RequestParam(defaultValue = "CN_A") String marketCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(workbenchService.isTradingDay(marketCode, date));
    }

    // ==================== 水位 ====================

    @GetMapping("/watermarks")
    public ApiResponse<PageResultVO<MarketDataWatermarkVO>> listWatermarks(
            @RequestParam(required = false) String canonicalSymbol,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) String intervalType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = MarketDataConstants.DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(workbenchService.listWatermarks(canonicalSymbol, dataSource, intervalType, page, size));
    }
}
