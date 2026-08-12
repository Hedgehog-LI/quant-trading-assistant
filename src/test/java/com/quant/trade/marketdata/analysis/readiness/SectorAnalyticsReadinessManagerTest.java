package com.quant.trade.marketdata.analysis.readiness;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 板块分析就绪门禁聚焦测试（AC-01 / TEST-01）。
 *
 * <p>覆盖 happy（is_truncated 回显、scope=RANKED_UNIVERSE）、no-batch（NO_DERIVED_DATA + reasonCodes 非空）、
 * single-batch、stale、source-time-null（SOURCE_TIME_UNKNOWN）、scope-forgery（expected≠rowcount，永不 VERIFIED_FULL_MARKET）、
 * hk-us-inferred-calendar（INSUFFICIENT_RAW fail closed）。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SectorAnalyticsReadinessManagerTest {

    @Autowired
    private SectorAnalyticsReadinessManager readinessManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM market_sector_ranking_item");
        jdbcTemplate.update("DELETE FROM market_sector_ranking_batch");
        jdbcTemplate.update("DELETE FROM market_calendar");
    }

    private Long insertCloseBatch(String market, String tradeDate, String snapshotTime,
                                  Integer itemCount, String qualityStatus) {
        return insertCloseBatch(market, tradeDate, snapshotTime, itemCount, qualityStatus, snapshotTime);
    }

    private Long insertCloseBatch(String market, String tradeDate, String snapshotTime,
                                  Integer itemCount, String qualityStatus, String providerQuoteTime) {
        jdbcTemplate.update("""
                INSERT INTO market_sector_ranking_batch (provider_code, market_code, trade_date, snapshot_type,
                    snapshot_bucket_time, snapshot_time, provider_quote_time, item_count, quality_status)
                VALUES ('LONGPORT', ?, ?, 'CLOSE', ?, ?, ?, ?, ?)
                """, market, tradeDate, snapshotTime, snapshotTime, providerQuoteTime, itemCount, qualityStatus);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM market_sector_ranking_batch WHERE market_code=? AND snapshot_type='CLOSE' "
                        + "AND snapshot_bucket_time=? ORDER BY id DESC LIMIT 1",
                Long.class, market, snapshotTime);
    }

    private void insertRankingItems(Long batchId, int count) {
        for (int i = 1; i <= count; i++) {
            jdbcTemplate.update("""
                    INSERT INTO market_sector_ranking_item (batch_id, rank_no, provider_sector_id, sector_name, change_rate)
                    VALUES (?, ?, ?, ?, ?)
                    """, batchId, i, "sec-" + i, "板块" + i, new java.math.BigDecimal("0.0100"));
        }
    }

    private void insertVerifiedCalendar(String market, String from, String to) {
        LocalDate date = LocalDate.parse(from);
        LocalDate end = LocalDate.parse(to);
        while (!date.isAfter(end)) {
            jdbcTemplate.update(
                    "INSERT INTO market_calendar (market_code, trade_date, is_trading_day, verification_status, source_code) "
                            + "VALUES (?, ?, TRUE, 'EXCHANGE_FILE', 'SSE')",
                    market, date);
            date = date.plusDays(1);
        }
    }

    private void insertInferredCalendar(String market, String from, String to) {
        LocalDate date = LocalDate.parse(from);
        LocalDate end = LocalDate.parse(to);
        while (!date.isAfter(end)) {
            jdbcTemplate.update(
                    "INSERT INTO market_calendar (market_code, trade_date, is_trading_day) VALUES (?, ?, TRUE)",
                    market, date);
            date = date.plusDays(1);
        }
    }

    @Test
    void readinessHappyReturnsRankedUniverseScopeWithTruncationEchoed() {
        Long batchId = insertCloseBatch("CN", "2026-07-16", "2026-07-16 15:00:00", 100, "VALID");
        insertRankingItems(batchId, 100);

        SectorAnalyticsReadinessVO vo = readinessManager.evaluate("CN");

        assertThat(vo.getMarket()).isEqualTo("CN");
        assertThat(vo.getLatestCloseBatchId()).isEqualTo(batchId);
        assertThat(vo.getAsOfDate()).isEqualTo(LocalDate.parse("2026-07-16"));
        assertThat(vo.getSourceQuoteTime()).isNotNull();
        assertThat(vo.getScope()).as("scope 固定 RANKED_UNIVERSE").isEqualTo("RANKED_UNIVERSE");
        assertThat(vo.getScopeDescription()).as("中文说明明确非全市场").isEqualTo("排行样本，不代表全市场");
        assertThat(vo.getActualItemCount()).isEqualTo(100);
        assertThat(vo.getExpectedItemCount()).as("expected 固定 100，不从 actual 反填").isEqualTo(100);
        assertThat(vo.getIsTruncated()).as("actual>=expected 时 is_truncated 回显").isTrue();
        assertThat(vo.getCoverageRate()).isEqualTo(1.0);
        assertThat(vo.getQualityStatus()).isEqualTo("OK");
        assertThat(vo.getReasonCodes()).as("OK 时无降级原因").isEmpty();
    }

    @Test
    void readinessNoBatchReturnsNoDerivedDataWithNonEmptyReasonCodes() {
        SectorAnalyticsReadinessVO vo = readinessManager.evaluate("CN");

        assertThat(vo.getLatestCloseBatchId()).isNull();
        assertThat(vo.getAsOfDate()).isNull();
        assertThat(vo.getQualityStatus()).isEqualTo("NO_DERIVED_DATA");
        assertThat(vo.getReasonCodes()).as("无 CLOSE 批次时 reasonCodes 非空，雷达拒绝衍生").isNotEmpty();
        assertThat(vo.getReasonCodes()).contains("NO_DERIVED_DATA");
        // scope 仍为 RANKED_UNIVERSE，绝不伪造全市场
        assertThat(vo.getScope()).isEqualTo("RANKED_UNIVERSE");
    }

    @Test
    void readinessSingleBatchReturnsThinOverviewQualityOk() {
        Long batchId = insertCloseBatch("CN", "2026-07-16", "2026-07-16 15:00:00", 30, "VALID");
        insertRankingItems(batchId, 30);

        SectorAnalyticsReadinessVO vo = readinessManager.evaluate("CN");

        assertThat(vo.getLatestCloseBatchId()).isEqualTo(batchId);
        assertThat(vo.getActualItemCount()).isEqualTo(30);
        assertThat(vo.getExpectedItemCount()).as("expected 固定 100，单批不覆盖").isEqualTo(100);
        assertThat(vo.getIsTruncated()).as("actual<expected 时非截断").isFalse();
        assertThat(vo.getCoverageRate()).isEqualTo(0.3);
        assertThat(vo.getQualityStatus()).isEqualTo("OK");
    }

    @Test
    void readinessStaleBatchEchoesAsOfDateAndSourceQuoteTime() {
        // 旧批次（asOfDate 远早于今天），readiness 仍回显 asOfDate/sourceQuoteTime，不沿用旧衍生
        Long batchId = insertCloseBatch("CN", "2026-01-05", "2026-01-05 15:00:00", 80, "VALID");
        insertRankingItems(batchId, 80);

        SectorAnalyticsReadinessVO vo = readinessManager.evaluate("CN");

        assertThat(vo.getAsOfDate()).isEqualTo(LocalDate.parse("2026-01-05"));
        assertThat(vo.getSourceQuoteTime()).isNotNull();
        // readiness 不在门禁层把"陈旧"判定为 STALE（那是衍生层职责），门禁只回显时间；
        // 但 asOfDate 必须是实际批次日期，不能伪造为今天
        assertThat(vo.getActualItemCount()).isEqualTo(80);
    }

    @Test
    void readinessSourceTimeUnknownWhenBatchHasNoSnapshotTime() {
        // provider_quote_time 为 NULL → SOURCE_TIME_UNKNOWN，qualityStatus≠OK
        // snapshot_time 保持非空（系统落库时间），provider_quote_time 独立可空（设计 §10）
        Long batchId = insertCloseBatch("CN", "2026-07-16", "2026-07-16 15:00:00", 50, "VALID", null);
        insertRankingItems(batchId, 50);

        SectorAnalyticsReadinessVO vo = readinessManager.evaluate("CN");

        assertThat(vo.getSourceQuoteTime()).as("provider quote time 缺失").isNull();
        assertThat(vo.getReasonCodes()).contains("SOURCE_TIME_UNKNOWN");
        assertThat(vo.getQualityStatus()).as("quote time 缺失时 qualityStatus≠OK").isNotEqualTo("OK");
    }

    @Test
    void readinessScopeForgeryGuardExpectedNeverEqualsForgedRowcount() {
        // 伪造场景：批次只有 5 行，但试图让 expected 跟着 actual 走。
        // 门禁守卫：expected 固定 100，永远不等于伪造的 actual=5，coverage_rate≠1，永不 VERIFIED_FULL_MARKET
        Long batchId = insertCloseBatch("CN", "2026-07-16", "2026-07-16 15:00:00", 5, "VALID");
        insertRankingItems(batchId, 5);

        SectorAnalyticsReadinessVO vo = readinessManager.evaluate("CN");

        assertThat(vo.getExpectedItemCount()).as("expected 不来自响应行数").isEqualTo(100);
        assertThat(vo.getActualItemCount()).isEqualTo(5);
        assertThat(vo.getExpectedItemCount()).as("expected≠actual，禁止伪造 coverage=1").isNotEqualTo(vo.getActualItemCount());
        assertThat(vo.getCoverageRate()).isEqualTo(0.05);
        assertThat(vo.getScope()).as("永不 VERIFIED_FULL_MARKET").isEqualTo("RANKED_UNIVERSE");
    }

    @Test
    void readinessHkUsInferredCalendarReturnsInsufficientRawFailClosed() {
        // HK 有 CLOSE 批次但日历 verification_status=INFERRED → INSUFFICIENT_RAW，不静默接受
        Long batchId = insertCloseBatch("HK", "2026-07-16", "2026-07-16 16:00:00", 60, "VALID");
        insertRankingItems(batchId, 60);
        insertInferredCalendar("HK", "2026-06-01", "2026-07-16");

        SectorAnalyticsReadinessVO vo = readinessManager.evaluate("HK");

        assertThat(vo.getQualityStatus()).as("HK/US INFERRED 日历 fail closed").isEqualTo("INSUFFICIENT_RAW");
        assertThat(vo.getReasonCodes()).contains("INSUFFICIENT_RAW", "CALENDAR_INFERRED");
    }

    @Test
    void readinessHkUsVerifiedCalendarPassesGate() {
        Long batchId = insertCloseBatch("HK", "2026-07-16", "2026-07-16 16:00:00", 60, "VALID");
        insertRankingItems(batchId, 60);
        // 覆盖 readiness 长窗口查询区间（asOfDate - 50 天起）
        insertVerifiedCalendar("HK", "2026-05-01", "2026-07-16");

        SectorAnalyticsReadinessVO vo = readinessManager.evaluate("HK");

        assertThat(vo.getQualityStatus()).as("HK 日历 EXCHANGE_FILE 时放行").isEqualTo("OK");
        assertThat(vo.getReasonCodes()).doesNotContain("INSUFFICIENT_RAW");
    }

    @Test
    void readinessCnInferredCalendarDoesNotFailClosed() {
        // CN 既有日历默认 INFERRED 但 readiness 接受（不阻断既有逻辑）
        Long batchId = insertCloseBatch("CN", "2026-07-16", "2026-07-16 15:00:00", 90, "VALID");
        insertRankingItems(batchId, 90);
        insertInferredCalendar("CN", "2026-06-01", "2026-07-16");

        SectorAnalyticsReadinessVO vo = readinessManager.evaluate("CN");

        assertThat(vo.getQualityStatus()).as("CN INFERRED 日历不 fail closed").isEqualTo("OK");
        assertThat(vo.getReasonCodes()).doesNotContain("INSUFFICIENT_RAW");
    }
}
