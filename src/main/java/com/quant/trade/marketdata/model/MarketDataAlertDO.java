package com.quant.trade.marketdata.model;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 行情异常提醒 DO。 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketDataAlertDO {
    private Long id;
    private String alertType;
    private String severity;
    private String canonicalSymbol;
    private LocalDateTime quoteTime;
    private LocalDate tradeDate;
    private String provider;
    private Long taskId;
    private String message;
    private String triggerValueJson;
    private Boolean resolved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
