package com.quant.trade.marketdata.foundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 版本血缘 manifest 行（mdf_dataset_version_manifest；不可变，双唯一键 bar_id/业务键）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MdfVersionManifestDO {
    private Long id;
    private Long datasetVersionId;
    private Long barId;
    private String canonicalSymbol;
    private LocalDate tradeDate;
    private String rowHash;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime includedAt;
    private LocalDateTime createdAt;
}
