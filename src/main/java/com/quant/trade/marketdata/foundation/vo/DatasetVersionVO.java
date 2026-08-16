package com.quant.trade.marketdata.foundation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 数据集版本 VO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetVersionVO {
    private Long id;
    private Long datasetId;
    private String datasetCode;
    private String versionCode;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String sourceProvider;
    private String sourceNote;
    private Long rowCount;
    private LocalDateTime qualifiedAt;
    private LocalDateTime releasedAt;
    /** R1 血缘：内容哈希（发布冻结后非空）。 */
    private String contentHash;
    /** R1 血缘：manifest 行数（发布冻结后非空）。 */
    private Long manifestRowCount;
    /** R1 血缘：FROZEN / DRIFTED（漂移阻断复现声明）。 */
    private String lineageStatus;
    private LocalDateTime createdAt;
    private Boolean isCurrentReleased;
}
