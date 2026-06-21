package com.quant.trade.journal.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易记录响应 VO。
 */
public record TradeJournalVO(
        Long id,
        LocalDate tradeDate,
        LocalDateTime tradeTime,
        String symbol,
        String name,
        String side,
        BigDecimal price,
        Long quantity,
        BigDecimal amount,
        BigDecimal commissionFee,
        BigDecimal stampTax,
        BigDecimal transferFee,
        BigDecimal otherFee,
        BigDecimal totalFee,
        BigDecimal positionRatio,
        Long planId,
        String reason,
        BigDecimal planStopLoss,
        BigDecimal planTakeProfit,
        Boolean followedPlan,
        List<String> emotionTags,
        List<String> mistakeTags,
        String actualResult,
        String reviewStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 创建时返回的风险提示（不持久化） */
        List<String> warnings
) {}
