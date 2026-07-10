package com.quant.trade.marketdata.model;

import lombok.*;
import java.time.LocalDateTime;

/** 行情同步任务 DO。 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketDataSyncTaskDO {
    private Long id;
    private String taskType;
    private String provider;
    private String scopeJson;
    private String status;
    private String idempotencyKey;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String lastErrorCode;
    private String errorSummaryJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
