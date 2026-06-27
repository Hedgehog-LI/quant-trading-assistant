package com.quant.trade.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新持仓快照草稿请求 DTO。
 *
 * @param snapshotDate 快照日期
 * @param snapshotTime 快照时间
 * @param snapshotName 快照名称
 * @param remark        快照备注
 * @param items         完整持仓明细；更新时按整批覆盖处理
 */
public record UpdatePositionSnapshotDTO(

        @NotNull(message = "snapshotDate is required")
        LocalDate snapshotDate,

        @NotNull(message = "snapshotTime is required")
        LocalDateTime snapshotTime,

        @Size(max = 128, message = "snapshotName must be at most 128 characters")
        String snapshotName,

        @Size(max = 1024, message = "remark must be at most 1024 characters")
        String remark,

        @NotNull(message = "items is required")
        List<@NotNull(message = "item must not be null") @Valid PositionSnapshotItemDTO> items
) {
}
