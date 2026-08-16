package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 回补任务证券范围行（mdf_backfill_task_symbol；全 A 范围不再塞 task.symbols_json）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfBackfillTaskSymbolDO {
    private Long id;
    private Long taskId;
    private String canonicalSymbol;
    private LocalDateTime createdAt;
}
