package com.quant.trade.marketdata.asset.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.asset.service.MarketDataAssetQueryService;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetAvailabilityVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetCatalogItemVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetRelatedTasksVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetSeriesVO;
import com.quant.trade.marketdata.exception.MarketDataAssetNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import com.quant.trade.marketdata.vo.PageResultVO;

/**
 * P1.9-A 行情资产只读查询 REST 控制器。
 * <p>
 * 只读现有表，不调用 provider、不写 DB；证券不存在返回 404（controller 级 handler），
 * 参数/范围错误返回 400。缺失参数与类型不匹配在本控制器内映射为稳定的 PARAM_ERROR 400
 * （controller 级 handler 优先于全局 handler，不受包级 advice 范围限制）。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data/assets")
@RequiredArgsConstructor
public class MarketDataAssetController {

    private final MarketDataAssetQueryService assetQueryService;

    /** 分页查询已实际落入日 K 或分钟 K 的行情资产。 */
    @GetMapping
    public ApiResponse<PageResultVO<MarketDataAssetCatalogItemVO>> listAssets(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(assetQueryService.listAssets(market, keyword, page, size));
    }

    /** 查询该证券已存在的 interval/dataSource/adjustType 组合及覆盖概况。 */
    @GetMapping("/{canonicalSymbol}/availability")
    public ApiResponse<MarketDataAssetAvailabilityVO> availability(@PathVariable String canonicalSymbol) {
        return ApiResponse.ok(assetQueryService.getAvailability(canonicalSymbol));
    }

    /** 查询指定组合区间的 K 线（有界 2000 条）+ 摘要 + 质量。 */
    @GetMapping("/{canonicalSymbol}/series")
    public ApiResponse<MarketDataAssetSeriesVO> series(
            @PathVariable String canonicalSymbol,
            @RequestParam String interval,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam String adjustType,
            @RequestParam String dataSource) {
        return ApiResponse.ok(assetQueryService.getSeries(canonicalSymbol, interval, from, to, adjustType, dataSource));
    }

    /** 查询与该证券相关的采集计划与采集记录。 */
    @GetMapping("/{canonicalSymbol}/related-tasks")
    public ApiResponse<MarketDataAssetRelatedTasksVO> relatedTasks(
            @PathVariable String canonicalSymbol,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(assetQueryService.getRelatedTasks(canonicalSymbol, interval, from, to, page, size));
    }

    @ExceptionHandler(MarketDataAssetNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(MarketDataAssetNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return badRequest("缺少必需参数: " + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return badRequest("参数类型不合法: " + exception.getName());
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        ApiResponse<Void> body = new ApiResponse<>(
                false, ErrorCodeEnum.PARAM_ERROR.getCode(), message, null, LocalDateTime.now());
        return ResponseEntity.badRequest().body(body);
    }
}
