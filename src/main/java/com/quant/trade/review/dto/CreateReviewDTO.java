package com.quant.trade.review.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 创建复盘记录请求 DTO。
 */
public record CreateReviewDTO(

        @NotNull(message = "reviewDate is required")
        LocalDate reviewDate,

        /** 股票代码（为空表示每日总复盘） */
        @Size(max = 32, message = "symbol must be at most 32 characters")
        String symbol,

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

        /** 关联的交易记录 ID 列表 */
        List<Long> linkedJournalIds
) {}
