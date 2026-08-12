package com.quant.trade.marketdata.analysis.readiness;

import com.quant.trade.marketdata.analysis.enums.SectorAnalyticsQualityStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 板块分析就绪门禁（设计 §9 / §10 / AC-01）。
 *
 * <p>判定一个市场的板块分析前置数据是否满足衍生计算门禁：
 * <ul>
 *   <li>无 CLOSE 批次 → {@code NO_DERIVED_DATA} + 非空 reasonCodes（雷达拒绝衍生结论）</li>
 *   <li>provider quote time 缺失 → {@code SOURCE_TIME_UNKNOWN}，qualityStatus≠OK</li>
 *   <li>HK/US 长窗口且 market_calendar.verification_status=INFERRED → {@code INSUFFICIENT_RAW}（fail closed）</li>
 *   <li>scope 固定 {@code RANKED_UNIVERSE}，expected_item_count 不来自响应行数（scope-forgery 守卫）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SectorAnalyticsReadinessManager {

    /** MVP 固定研究范围（LongPort 排行上限 100，无权威总数）。 */
    public static final String SCOPE_RANKED_UNIVERSE = "RANKED_UNIVERSE";
    public static final String SCOPE_DESCRIPTION = "排行样本，不代表全市场";

    /** MVP 固定期望样本量（LongPort 排行 limit；不从响应行数反填）。 */
    public static final int EXPECTED_ITEM_COUNT_MVP = 100;

    /** RS 固定窗口（长窗口门禁使用）。 */
    public static final int RS_WINDOW = 20;

    /** HK/US 市场代码。 */
    private static final List<String> HK_US_MARKETS = List.of("HK", "US");

    private static final List<String> VERIFIED_CALENDAR_STATUSES = List.of("EXCHANGE_FILE", "MANUAL_VERIFIED");
    private static final String CALENDAR_INFERRED = "INFERRED";

    private final SectorAnalyticsReadinessMapper readinessMapper;

    /**
     * 评估指定市场的板块分析就绪状态。
     *
     * @param marketCode 市场代码（CN/HK/US）
     * @return 就绪 read model（永不抛异常，降级通过 qualityStatus + reasonCodes 表达）
     */
    public SectorAnalyticsReadinessVO evaluate(String marketCode) {
        List<String> reasonCodes = new ArrayList<>();
        String scope = SCOPE_RANKED_UNIVERSE;
        // expected 固定，不从 actual 反填（scope-forgery 守卫）
        Integer expected = EXPECTED_ITEM_COUNT_MVP;

        Map<String, Object> batch = readinessMapper.selectLatestCloseBatch(marketCode);
        if (batch == null || batch.get("id") == null) {
            // 无 CLOSE 批次 → NO_DERIVED_DATA，雷达拒绝衍生结论
            reasonCodes.add("NO_DERIVED_DATA");
            return build(marketCode, scope, null, null, null, null, expected,
                    Boolean.FALSE, null, SectorAnalyticsQualityStatusEnum.NO_DERIVED_DATA, reasonCodes);
        }

        Long batchId = toLong(batch.get("id"));
        LocalDate asOfDate = toDate(batch.get("trade_date"));
        // provider_quote_time 是 provider 行情时间（可空）；未提供 → SOURCE_TIME_UNKNOWN（设计 §10）
        java.time.LocalDateTime sourceQuoteTime = toDateTime(batch.get("provider_quote_time"));
        Integer reportedItemCount = toInt(batch.get("item_count"));

        // actual_item_count 以批次排行项实测为准（不从 expected 反填）
        Integer actualItemCount = reportedItemCount;
        Integer counted = readinessMapper.countBatchItems(batchId);
        if (counted != null) {
            actualItemCount = counted;
        }
        boolean isTruncated = actualItemCount != null && actualItemCount >= expected;
        Double coverageRate = (actualItemCount == null || expected == null || expected == 0)
                ? null : ((double) actualItemCount) / expected;

        // 新鲜度：provider quote time 缺失 → SOURCE_TIME_UNKNOWN
        if (sourceQuoteTime == null) {
            reasonCodes.add("SOURCE_TIME_UNKNOWN");
        }

        // 权威交易日历门禁（HK/US 长窗口 INFERRED → INSUFFICIENT_RAW，fail closed）
        if (isHkOrUs(marketCode) && !hasVerifiedCalendar(marketCode, asOfDate)) {
            reasonCodes.add("CALENDAR_INFERRED");
            reasonCodes.add("INSUFFICIENT_RAW");
            return build(marketCode, scope, batchId, asOfDate, sourceQuoteTime, actualItemCount,
                    expected, isTruncated, coverageRate,
                    SectorAnalyticsQualityStatusEnum.INSUFFICIENT_RAW, reasonCodes);
        }

        // 质量状态综合判定
        SectorAnalyticsQualityStatusEnum status = resolveStatus(sourceQuoteTime, reasonCodes);

        return build(marketCode, scope, batchId, asOfDate, sourceQuoteTime, actualItemCount,
                expected, isTruncated, coverageRate, status, reasonCodes);
    }

    private SectorAnalyticsQualityStatusEnum resolveStatus(java.time.LocalDateTime sourceQuoteTime,
                                                            List<String> reasonCodes) {
        if (sourceQuoteTime == null) {
            // SOURCE_TIME_UNKNOWN → 非 OK
            return SectorAnalyticsQualityStatusEnum.DEGRADED;
        }
        return SectorAnalyticsQualityStatusEnum.OK;
    }

    /** HK/US 市场判定（CN 既有日历默认 INFERRED 但 readiness 接受，不 fail closed）。 */
    private boolean isHkOrUs(String marketCode) {
        return HK_US_MARKETS.contains(marketCode);
    }

    /**
     * HK/US 是否具备权威交易日历（窗口内所有交易日 verification_status ∈ {EXCHANGE_FILE, MANUAL_VERIFIED}）。
     * 无日历行或含 INFERRED → false（fail closed）。
     */
    private boolean hasVerifiedCalendar(String marketCode, LocalDate asOfDate) {
        if (asOfDate == null) {
            return false;
        }
        LocalDate from = asOfDate.minusDays((long) RS_WINDOW * 2 + 10);
        LocalDate to = asOfDate;
        List<Map<String, Object>> dist = readinessMapper.selectCalendarVerificationDistribution(
                marketCode, from, to);
        if (dist == null || dist.isEmpty()) {
            return false;
        }
        for (Map<String, Object> row : dist) {
            String status = (String) row.get("verification_status");
            if (status == null || !VERIFIED_CALENDAR_STATUSES.contains(status)) {
                return false;
            }
        }
        return true;
    }

    private SectorAnalyticsReadinessVO build(String market, String scope, Long batchId, LocalDate asOfDate,
                                             java.time.LocalDateTime sourceQuoteTime, Integer actualItemCount,
                                             Integer expectedItemCount, Boolean isTruncated, Double coverageRate,
                                             SectorAnalyticsQualityStatusEnum status, List<String> reasonCodes) {
        return SectorAnalyticsReadinessVO.builder()
                .market(market)
                .scope(scope)
                .scopeDescription(SCOPE_DESCRIPTION)
                .latestCloseBatchId(batchId)
                .asOfDate(asOfDate)
                .sourceQuoteTime(sourceQuoteTime)
                .actualItemCount(actualItemCount)
                .expectedItemCount(expectedItemCount)
                .isTruncated(isTruncated)
                .coverageRate(coverageRate)
                .qualityStatus(status.name())
                .reasonCodes(reasonCodes)
                .build();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private static LocalDate toDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private static java.time.LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return java.time.LocalDateTime.parse(value.toString());
    }
}
