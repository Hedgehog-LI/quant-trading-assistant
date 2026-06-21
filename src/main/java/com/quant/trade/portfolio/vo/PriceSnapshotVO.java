package com.quant.trade.portfolio.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手工当前价快照响应 VO。
 */
public record PriceSnapshotVO(
        Long id,
        String symbol,
        String name,
        BigDecimal currentPrice,
        LocalDate priceDate,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
