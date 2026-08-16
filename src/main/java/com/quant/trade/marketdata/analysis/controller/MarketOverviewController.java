package com.quant.trade.marketdata.analysis.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.analysis.service.MarketOverviewService;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;

/**
 * MR-1A 市场全景只读入口：GET /api/v1/market-research/overview?market=CN&amp;start=&amp;end=。
 * 首期仅支持 CN。Controller 只负责协议、参数绑定与响应；业务校验与编排在
 * {@link MarketOverviewService}。参数缺失/非法（含日期类型不匹配）统一 400 envelope；
 * 窗口无数据、基准缺失等数据不足状态由 metadata.qualityStatus（NO_DATA/DEGRADED）与
 * quality.qualityFindings 表达，不抛 500。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-research")
@RequiredArgsConstructor
public class MarketOverviewController {

    private final MarketOverviewService marketOverviewService;

    /** 市场全景（五类核心证据 + 覆盖率/Provider/质量/不可用指标）。 */
    @GetMapping("/overview")
    public ApiResponse<MarketOverviewVO.Overview> overview(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResponse.ok(marketOverviewService.overview(market, start, end));
    }

    /** 畸形日期等类型不匹配 → 400 VALIDATION_ERROR envelope（非 500），不透出 Spring 内部细节。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(
                ApiResponse.fail(ErrorCodeEnum.VALIDATION_ERROR, "参数类型不合法: " + exception.getName()));
    }
}
