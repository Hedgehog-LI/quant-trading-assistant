package com.quant.trade.marketdata.foundation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 回补分片 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackfillChunkVO {
    private Long id;
    private Long taskId;
    private Integer chunkIndex;
    private List<String> symbols;
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
}
