package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 行情相关 VO 和 DTO 统一容器（嵌套 record）。 */
public final class MarketDataVOs {
    private MarketDataVOs() {}

    public record StockQuoteSnapshotVO(Long id, String canonicalSymbol, LocalDateTime quoteTime,
        BigDecimal currentPrice, BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
        BigDecimal preClosePrice, Long volume, BigDecimal amount, String tradeStatus,
        String dataSource, LocalDateTime fetchedAt) {}

    public record MarketDataSyncTaskVO(Long id, String taskType, String provider, String scopeJson,
        String status, Integer totalCount, Integer successCount, Integer failCount,
        Integer insertedCount, Integer updatedCount, Integer skippedCount,
        LocalDateTime startedAt, LocalDateTime finishedAt, String lastErrorCode, String errorSummaryJson,
        LocalDateTime createdAt) {}

    public record MarketDataAlertVO(Long id, String alertType, String severity, String canonicalSymbol,
        LocalDateTime quoteTime, LocalDate tradeDate, String provider, Long taskId,
        String message, String triggerValueJson, Boolean resolved, LocalDateTime createdAt) {}

    public record ProviderStatusVO(String providerCode, boolean configured, boolean reachable,
        String lastError, LocalDateTime lastSuccessAt) {}

    public record CreateSyncTaskDTO(String taskType, String provider, String scopeJson) {}
}
