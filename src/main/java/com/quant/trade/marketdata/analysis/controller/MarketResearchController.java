package com.quant.trade.marketdata.analysis.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.analysis.constant.SectorAnalyticsConstants;
import com.quant.trade.marketdata.analysis.service.MarketResearchQueryService;
import com.quant.trade.marketdata.analysis.service.SectorAnalyticsCalculationService;
import com.quant.trade.marketdata.analysis.vo.MarketResearchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 市场雷达、排行历史、板块详情与显式重算接口。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-research")
@RequiredArgsConstructor
public class MarketResearchController {

    private final SectorAnalyticsCalculationService calculationService;
    private final MarketResearchQueryService queryService;

    @PostMapping("/calculations")
    public ApiResponse<MarketResearchVO.Calculation> calculate(
            @RequestParam(defaultValue = "CN") String market,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "20") int window) {
        return ApiResponse.ok(calculationService.calculate(market, asOfDate, window));
    }

    @GetMapping("/radar")
    public ApiResponse<MarketResearchVO.Radar> radar(
            @RequestParam(defaultValue = "CN") String market,
            @RequestParam(defaultValue = "20") int window) {
        return ApiResponse.ok(queryService.radar(market, window));
    }

    @GetMapping("/sectors/ranking-history")
    public ApiResponse<MarketResearchVO.RankingHistory> rankingHistory(
            @RequestParam(defaultValue = "CN") String market,
            @RequestParam(defaultValue = "20") int window,
            @RequestParam(defaultValue = "20") int days) {
        return ApiResponse.ok(queryService.rankingHistory(market, window, days));
    }

    @GetMapping("/sectors/{sectorId}")
    public ApiResponse<MarketResearchVO.SectorDetail> sectorDetail(
            @PathVariable Long sectorId,
            @RequestParam(defaultValue = "CN") String market,
            @RequestParam(defaultValue = "20") int window,
            @RequestParam(defaultValue = "20") int days) {
        return ApiResponse.ok(queryService.sectorDetail(market, sectorId, window, days));
    }
}
