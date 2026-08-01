package com.quant.trade.marketdata.vo;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 目录同步状态。{@code catalogStatus/catalogUpdatedAt/stale/degraded} 沿用 D1 启发式，
 * {@code lastSuccessAt/lastSnapshotId} 来自 security_directory_sync_state；不暴露路径/凭据。
 */
public record SecurityDirectoryStatusVO(
        String providerCode,
        boolean providerEnabled,
        boolean providerConfigured,
        LocalDateTime lastSuccessAt,
        String lastSnapshotId,
        String lastMode,
        String lastErrorCode,
        String catalogStatus,
        Instant catalogUpdatedAt,
        boolean stale,
        boolean degraded) {
}
