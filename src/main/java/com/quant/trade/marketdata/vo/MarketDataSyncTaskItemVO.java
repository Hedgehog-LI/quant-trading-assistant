package com.quant.trade.marketdata.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 同步任务明细 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataSyncTaskItemVO {
    private Long id;
    private Long taskId;
    private Long planId;
    private Long subTaskId;
    private String canonicalSymbol;
    private String scopeDetail;
    private String status;
    private Integer rowCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
