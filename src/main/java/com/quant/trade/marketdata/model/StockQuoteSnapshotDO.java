package com.quant.trade.marketdata.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 外部最新价快照 DO。 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StockQuoteSnapshotDO {
    private Long id;
    private String canonicalSymbol;
    private LocalDateTime quoteTime;
    private BigDecimal currentPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal preClosePrice;
    private Long volume;
    private BigDecimal amount;
    private String tradeStatus;
    private String dataSource;
    private LocalDateTime fetchedAt;
    private String rawHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
