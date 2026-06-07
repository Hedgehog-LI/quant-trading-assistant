package com.quant.trade.tradeplan.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.tradeplan.dto.CreateTradePlanDTO;
import com.quant.trade.tradeplan.dto.UpdatePlanStatusDTO;
import com.quant.trade.tradeplan.dto.UpdateTradePlanDTO;
import com.quant.trade.tradeplan.service.TradePlanService;
import com.quant.trade.tradeplan.vo.TradePlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易计划管理接口。
 * <p>
 * 提供盘前计划的增删改查和状态流转。
 * 注意：计划只是纪律记录，不是交易建议。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/trade-plans")
@RequiredArgsConstructor
public class TradePlanController {

    private final TradePlanService tradePlanService;

    @PostMapping
    public ApiResponse<TradePlanVO> create(@Valid @RequestBody CreateTradePlanDTO dto) {
        return ApiResponse.ok(tradePlanService.create(dto));
    }

    @GetMapping
    public ApiResponse<List<TradePlanVO>> list(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String symbol) {
        return ApiResponse.ok(tradePlanService.list(date, symbol));
    }

    @GetMapping("/{id}")
    public ApiResponse<TradePlanVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(tradePlanService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TradePlanVO> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateTradePlanDTO dto) {
        return ApiResponse.ok(tradePlanService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<TradePlanVO> updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody UpdatePlanStatusDTO dto) {
        return ApiResponse.ok(tradePlanService.updateStatus(id, dto));
    }
}
