package com.quant.trade.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建持仓快照请求 DTO。
 *
 * @param snapshotDate   快照日期
 * @param snapshotTime   快照时间
 * @param snapshotName   快照名称
 * @param sourceType     数据来源
 * @param snapshotStatus 初始状态，只允许 DRAFT 或 CONFIRMED
 * @param remark          快照备注
 * @param items           持仓明细，可为空列表以记录空仓状态
 */
public record CreatePositionSnapshotDTO(

        @NotNull(message = "snapshotDate is required")
        LocalDate snapshotDate,

        @NotNull(message = "snapshotTime is required")
        LocalDateTime snapshotTime,

        @Size(max = 128, message = "snapshotName must be at most 128 characters")
        String snapshotName,

        @NotBlank(message = "sourceType is required")
        @Size(max = 32, message = "sourceType must be at most 32 characters")
        String sourceType,

        @NotBlank(message = "snapshotStatus is required")
        @Size(max = 32, message = "snapshotStatus must be at most 32 characters")
        String snapshotStatus,

        @Size(max = 1024, message = "remark must be at most 1024 characters")
        String remark,

        @NotNull(message = "items is required")
        List<@NotNull(message = "item must not be null") @Valid PositionSnapshotItemDTO> items
) {
}
