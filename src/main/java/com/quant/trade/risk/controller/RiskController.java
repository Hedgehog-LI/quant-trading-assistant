package com.quant.trade.risk.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.risk.dto.PositionSizeCalculateDTO;
import com.quant.trade.risk.service.RiskCalculatorService;
import com.quant.trade.risk.vo.PositionSizeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 风控计算器接口。
 * <p>
 * 提供仓位计算、风险等级评估等辅助功能。
 * 计算结果仅为辅助参考，不构成投资建议。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskCalculatorService riskCalculatorService;

    @PostMapping("/calculations/position-size")
    public ApiResponse<PositionSizeVO> calculatePositionSize(
            @Valid @RequestBody PositionSizeCalculateDTO dto) {
        return ApiResponse.ok(riskCalculatorService.calculatePositionSize(dto));
    }
}
