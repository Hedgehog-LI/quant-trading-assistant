package com.quant.trade.marketdata.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 行情异常提醒响应 VO。 */
public record MarketDataAlertVO(
    Long id, String alertType, String severity, String canonicalSymbol,
    LocalDateTime quoteTime, LocalDate tradeDate, String provider, Long taskId,
    String message, String triggerValueJson, Boolean resolved, LocalDateTime createdAt
) {}
