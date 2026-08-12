package com.quant.trade.marketdata.analysis.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.analysis.readiness.SectorAnalyticsReadinessManager;
import com.quant.trade.marketdata.analysis.readiness.SectorAnalyticsReadinessVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 板块分析就绪门禁 REST 控制器（AC-01）。
 *
 * <p>{@code GET /api/v1/market-research/readiness?market=CN} 返回市场板块分析前置数据状态，
 * 供市场雷达决定是否返回衍生结论。无 CLOSE 批次返回 {@code NO_DERIVED_DATA}，雷达拒绝衍生。</p>
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-research")
@RequiredArgsConstructor
public class SectorAnalyticsReadinessController {

    private final SectorAnalyticsReadinessManager readinessManager;

    @GetMapping("/readiness")
    public ApiResponse<SectorAnalyticsReadinessVO> readiness(@RequestParam("market") String market) {
        return ApiResponse.ok(readinessManager.evaluate(market));
    }
}
