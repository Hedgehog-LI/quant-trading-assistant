package com.quant.trade.marketdata.vo;

import java.time.LocalDateTime;

/** 行情同步任务响应 VO。 */
public record MarketDataSyncTaskVO(
    Long id, String taskType, String provider, String scopeJson, String status,
    Integer totalCount, Integer successCount, Integer failCount,
    Integer insertedCount, Integer updatedCount, Integer skippedCount,
    LocalDateTime startedAt, LocalDateTime finishedAt,
    String lastErrorCode, String errorSummaryJson, LocalDateTime createdAt
) {}
