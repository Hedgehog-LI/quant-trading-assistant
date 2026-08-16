package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 回补分片 DO（mdf_backfill_chunk）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfBackfillChunkDO {
    private Long id;
    private Long taskId;
    private Integer chunkIndex;
    private String symbolsJson;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer attempts;
    private Long insertedCount;
    private Long updatedCount;
    private Long skippedCount;
    private Long failedCount;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
