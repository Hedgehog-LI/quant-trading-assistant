package com.quant.trade.marketdata.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 板块稳定身份 DO（market_sector_identity）。
 *
 * <p>数值 {@code id} 是内部/API 唯一的 {@code sectorId}。自然唯一键为
 * {@code (providerCode, marketCode, providerSectorId, taxonomyVersion)}（设计 §6.1）。
 * {@code watchId} 只是关注关系，永远不参与历史身份、幂等键或跨表 JOIN。</p>
 *
 * <p>{@code validFrom}/{@code validTo} 使用左闭右开区间；soft-archive（{@code archived=true}）
 * 保留历史，不物理删除。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSectorIdentityDO {

    /** 内部/API 稳定板块 ID（= sectorId）。 */
    private Long id;

    /** 来源 provider 代码（如 LONGPORT）。 */
    private String providerCode;

    /** 市场代码（CN/HK/US）。 */
    private String marketCode;

    /** provider 侧板块 ID。 */
    private String providerSectorId;

    /** provider 分类版本（用于跨 taxonomy 区间断档）。 */
    private String taxonomyVersion;

    /** 板块展示名（可空，仅作展示）。 */
    private String sectorName;

    /** 身份生效日（含，左闭）。 */
    private LocalDate validFrom;

    /** 身份失效日（不含，右开；NULL 表示至今有效）。 */
    private LocalDate validTo;

    /** soft-archive 标记（true=已归档，历史保留，不参与新写入）。 */
    private Boolean archived;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
