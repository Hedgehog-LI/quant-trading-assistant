package com.quant.trade.review.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 更新复盘记录请求 DTO。
 */
public record UpdateReviewDTO(

        @NotBlank(message = "title is required")
        @Size(max = 128, message = "title must be at most 128 characters")
        String title,

        @Size(max = 2048, message = "marketContext must be at most 2048 characters")
        String marketContext,

        @Size(max = 2048, message = "planSummary must be at most 2048 characters")
        String planSummary,

        @Size(max = 2048, message = "actionSummary must be at most 2048 characters")
        String actionSummary,

        @Size(max = 2048, message = "rightThings must be at most 2048 characters")
        String rightThings,

        @Size(max = 2048, message = "wrongThings must be at most 2048 characters")
        String wrongThings,

        @Size(max = 2048, message = "ruleChanges must be at most 2048 characters")
        String ruleChanges,

        @Size(max = 2048, message = "nextActions must be at most 2048 characters")
        String nextActions,

        List<Long> linkedJournalIds
) {}
