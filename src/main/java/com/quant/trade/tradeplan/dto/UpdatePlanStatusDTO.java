package com.quant.trade.tradeplan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新交易计划状态请求 DTO。
 */
public record UpdatePlanStatusDTO(

        @NotBlank(message = "planStatus is required")
        @Size(max = 32, message = "planStatus must be at most 32 characters")
        String planStatus
) {}
