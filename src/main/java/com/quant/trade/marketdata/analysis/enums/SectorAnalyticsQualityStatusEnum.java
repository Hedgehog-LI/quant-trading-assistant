package com.quant.trade.marketdata.analysis.enums;

/**
 * 板块分析质量状态（设计 §6.1 / §9）。
 *
 * <p>统一用于 readiness 门禁、衍生结果行和 read model。降级状态必须携带非空原因码，
 * 不得伪造全市场或衍生结论。</p>
 */
public enum SectorAnalyticsQualityStatusEnum {

    /** 数据完整、新鲜、口径一致，可进入衍生计算。 */
    OK,

    /** 部分可用但存在可解释缺陷（截断、延迟等），可降级展示但不产 HIGH。 */
    DEGRADED,

    /** 尚无任何衍生计算结果；雷达拒绝返回衍生结论，保留模块位置。 */
    NO_DERIVED_DATA,

    /** 原始事实不足（权威交易日历缺失、来源时间未知等），fail closed。 */
    INSUFFICIENT_RAW,

    /** 有效样本数低于门禁，不产 RS-rank。 */
    INSUFFICIENT_SAMPLE,

    /** 数据陈旧，可展示最后值但必须同时显示 asOfDate/sourceQuoteTime 与"已过期"。 */
    STALE,

    /** provider 口径变更，在变更点断档，不跨 taxonomy 拼接。 */
    ORIGIN_CHANGED,

    /** 上游鉴权阻断，不自动重试。 */
    BLOCKED_AUTH,

    /** 上游权限阻断，不自动重试。 */
    BLOCKED_PERMISSION,

    /** 退避中，展示下次允许重试时间。 */
    BACKOFF;

    /**
     * 安全解析：未知值回落到 {@link #DEGRADED}（fail-open 仅用于解析历史脏值；新写入必须用枚举名）。
     */
    public static SectorAnalyticsQualityStatusEnum safeParse(String value) {
        if (value == null || value.isBlank()) {
            return DEGRADED;
        }
        try {
            return SectorAnalyticsQualityStatusEnum.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DEGRADED;
        }
    }
}
