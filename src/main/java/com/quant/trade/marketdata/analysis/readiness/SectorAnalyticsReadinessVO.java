package com.quant.trade.marketdata.analysis.readiness;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 板块分析就绪门禁 read model（设计 §9 / AC-01）。
 *
 * <p>描述一个市场的板块分析前置数据状态：最新成功 CLOSE 批次、研究范围（固定 RANKED_UNIVERSE）、
 * 样本量、覆盖率、完整性、新鲜度和质量状态。雷达据此决定是否返回衍生结论。</p>
 *
 * <p>scope 固定为 {@code RANKED_UNIVERSE}（中文说明"排行样本，不代表全市场"）；
 * {@code expectedItemCount} 不得来自响应行数（scope-forgery 守卫）。</p>
 */
@Data
@Builder
public class SectorAnalyticsReadinessVO {

    /** 市场代码（CN/HK/US）。 */
    private String market;

    /** 研究范围（MVP 固定 RANKED_UNIVERSE）。 */
    private String scope;

    /** 研究范围中文说明（"排行样本，不代表全市场"）。 */
    private String scopeDescription;

    /** 最新成功 CLOSE 批次 ID（null = 无 CLOSE 批次 → NO_DERIVED_DATA）。 */
    private Long latestCloseBatchId;

    /** 最新成功 CLOSE 批次的基准交易日（asOfDate）。 */
    private LocalDate asOfDate;

    /** provider 行情时间（sourceQuoteTime；null → SOURCE_TIME_UNKNOWN）。 */
    private LocalDateTime sourceQuoteTime;

    /** 实际样本量（= 该 CLOSE 批次排行项数 actual_item_count）。 */
    private Integer actualItemCount;

    /**
     * 期望样本量。MVP 固定 100（LongPort 排行上限）。
     * <b>不得</b>来自响应行数（禁止用 actual 反填 expected 伪造 coverage_rate=1）。
     */
    private Integer expectedItemCount;

    /** 是否被 provider 上限截断（is_truncated）。 */
    private Boolean isTruncated;

    /** 覆盖率（actual / expected，0~1）；不可用时为 null。 */
    private Double coverageRate;

    /** 质量状态（SectorAnalyticsQualityStatusEnum 名）。 */
    private String qualityStatus;

    /** 质量原因码（结构化，可空；NO_DERIVED_DATA/INSUFFICIENT_RAW/STALE/SOURCE_TIME_UNKNOWN 等）。 */
    private List<String> reasonCodes;
}
