package com.quant.trade.marketdata.foundation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 回补任务 VO（详情含 symbol 摘要与统计）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackfillTaskVO {
    private Long id;
    private String datasetCode;
    private Long datasetVersionId;
    private String marketCode;
    private String providerCode;
    private String frequency;
    private String adjustType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer chunkSize;
    private String status;
    private Integer plannedCount;
    private Integer successCount;
    private Integer failCount;
    private Integer skipCount;
    private Long insertedCount;
    private Long updatedCount;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    /** 显式 symbols（全池任务为空列表）。 */
    private List<String> symbols;
    private Integer totalChunks;
    private Integer succeededChunks;
    private Integer failedChunks;
}
