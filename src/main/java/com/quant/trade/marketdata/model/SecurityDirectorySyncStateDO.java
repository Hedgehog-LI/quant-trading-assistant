package com.quant.trade.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 证券目录同步状态（security_directory_sync_state）。按 provider 维护最近成功时间/快照/计数/错误，不回写 stock_basic。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityDirectorySyncStateDO {
    private Long id;
    /** provider code（唯一）。 */
    private String provider;
    /** 最近一次成功同步的快照标识（snapshotHash 派生）。 */
    private String lastSnapshotId;
    /** 最近一次成功同步的快照内容 hash。 */
    private String lastSnapshotHash;
    /** 最近一次成功同步的同步模式 FULL/INCREMENTAL。 */
    private String lastMode;
    /** 最近一次成功同步时间。 */
    private LocalDateTime lastSuccessAt;
    /** 最近一次成功同步的发布计数。 */
    private Integer lastInsertedCount;
    private Integer lastUpdatedCount;
    private Integer lastUnchangedCount;
    /** 最近一次失败的错误码（成功后保留或清空）。 */
    private String lastErrorCode;
    /** 最近一次失败的错误摘要 JSON。 */
    private String lastErrorSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
