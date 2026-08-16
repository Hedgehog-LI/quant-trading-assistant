package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 历史回补任务 DO（mdf_backfill_task）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfBackfillTaskDO {
    private Long id;
    private String datasetCode;
    private Long datasetVersionId;
    private String marketCode;
    private String providerCode;
    private String frequency;
    private String adjustType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String symbolsJson;
    private String symbolsHash;
    private Integer chunkSize;
    private String status;
    private Integer plannedCount;
    private Integer successCount;
    private Integer failCount;
    private Integer skipCount;
    private Long insertedCount;
    private Long updatedCount;
    private String claimToken;
    private LocalDateTime claimedAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
