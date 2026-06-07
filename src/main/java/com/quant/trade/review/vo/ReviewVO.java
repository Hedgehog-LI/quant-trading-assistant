package com.quant.trade.review.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 复盘记录响应 VO。
 */
public record ReviewVO(
        Long id,
        LocalDate reviewDate,
        String symbol,
        String title,
        String marketContext,
        String planSummary,
        String actionSummary,
        String rightThings,
        String wrongThings,
        String ruleChanges,
        String nextActions,
        List<Long> linkedJournalIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
