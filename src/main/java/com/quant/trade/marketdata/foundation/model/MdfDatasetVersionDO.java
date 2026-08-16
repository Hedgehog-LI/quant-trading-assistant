package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 数据集版本 DO（mdf_dataset_version）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfDatasetVersionDO {
    private Long id;
    private Long datasetId;
    private String versionCode;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String sourceProvider;
    private String sourceNote;
    private Long rowCount;
    private LocalDateTime qualifiedAt;
    private LocalDateTime releasedAt;
    /** R1 血缘：内容哈希（发布前冻结）。 */
    private String contentHash;
    /** R1 血缘：manifest 行数（发布前冻结）。 */
    private Long manifestRowCount;
    /** R1 血缘：FROZEN / DRIFTED（底层事实漂移时阻断"静默可复现"）。 */
    private String lineageStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
