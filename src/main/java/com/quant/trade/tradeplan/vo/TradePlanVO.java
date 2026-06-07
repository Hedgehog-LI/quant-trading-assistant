package com.quant.trade.tradeplan.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易计划响应 VO。
 */
public record TradePlanVO(
        Long id,
        LocalDate planDate,
        String symbol,
        String name,
        String planStatus,
        String buyCondition,
        String sellCondition,
        BigDecimal stopLossPrice,
        BigDecimal takeProfitPrice,
        BigDecimal plannedPositionRatio,
        BigDecimal maxLossAmount,
        Boolean allowedToTrade,
        String riskNote,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
