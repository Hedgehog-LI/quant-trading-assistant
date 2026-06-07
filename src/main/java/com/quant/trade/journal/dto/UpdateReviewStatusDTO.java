package com.quant.trade.journal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新复盘状态请求 DTO。
 */
public record UpdateReviewStatusDTO(

        @NotBlank(message = "reviewStatus is required")
        String reviewStatus
) {}
