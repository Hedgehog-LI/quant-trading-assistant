package com.quant.trade.dashboard.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.dashboard.service.DashboardService;
import com.quant.trade.dashboard.vo.DashboardTodayVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 今日工作台接口。
 * <p>
 * 聚合自选股、交易计划、交易记录、复盘、风险提醒等数据，
 * 提供今日看板视图。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/today")
    public ApiResponse<DashboardTodayVO> getToday(@RequestParam(required = false) LocalDate date) {
        return ApiResponse.ok(dashboardService.getToday(date));
    }
}
