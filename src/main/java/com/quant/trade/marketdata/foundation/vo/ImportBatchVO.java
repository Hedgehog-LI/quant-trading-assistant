package com.quant.trade.marketdata.foundation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 导入批次 VO（错误报告为原始 JSON 字符串，前端解析展示）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatchVO {
    private Long id;
    private String importKind;
    private String providerCode;
    private String fileName;
    private String fileHash;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private Integer rejectedCount;
    private String status;
    private String errorReportJson;
    /** R1：批次血缘关联版本（DAILY_BAR 必填；其余 kind 可空）。 */
    private Long datasetVersionId;
    private LocalDateTime createdAt;
}
