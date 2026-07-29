package com.quant.trade.agent.vo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 可信回答契约。所有 Agent 业务响应统一包含这些字段。
 * <p>
 * 区分"尚未采集""确实无结果""查询失败"和"Provider不可用"。
 */
public record TrustedAnswer(
    String conclusion,
    OffsetDateTime generatedAt,
    OffsetDateTime dataAsOf,
    String freshnessStatus,
    List<Evidence> evidence,
    List<String> warnings,
    Object data
) {
    /** 新鲜度枚举 */
    public static final String FRESH = "FRESH";
    public static final String DELAYED = "DELAYED";
    public static final String STALE = "STALE";
    public static final String UNKNOWN = "UNKNOWN";

    /** 证据条目 */
    public record Evidence(
        String type,
        String id,
        OffsetDateTime observedAt
    ) {}

    /** 快速构建器 */
    public static TrustedAnswer of(String conclusion, Object data, String freshnessStatus,
                                    OffsetDateTime dataAsOf, List<Evidence> evidence) {
        return new TrustedAnswer(conclusion, OffsetDateTime.now(), dataAsOf,
            freshnessStatus, evidence != null ? evidence : List.of(), List.of(), data);
    }

    public static TrustedAnswer empty(String conclusion, String freshnessStatus) {
        return new TrustedAnswer(conclusion, OffsetDateTime.now(), null,
            freshnessStatus, List.of(), List.of(), null);
    }

    public static TrustedAnswer fail(String message) {
        return new TrustedAnswer(message, OffsetDateTime.now(), null,
            UNKNOWN, List.of(), List.of(message), null);
    }
}
