package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** CSV/快照导入批次 DO（mdf_import_batch；file_hash 幂等，错误行报告 JSON）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfImportBatchDO {
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
    private LocalDateTime createdAt;
}
