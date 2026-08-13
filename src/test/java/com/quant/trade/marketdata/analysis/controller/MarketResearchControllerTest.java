package com.quant.trade.marketdata.analysis.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 板块研究完整链路：CLOSE 事实 -> 重算发布 -> 雷达/历史/详情。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketResearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sector_analytics_publication_member");
        jdbcTemplate.update("DELETE FROM sector_analytics_publication_batch");
        jdbcTemplate.update("DELETE FROM sector_rotation_sector_persistence");
        jdbcTemplate.update("DELETE FROM sector_relative_strength_snapshot");
        jdbcTemplate.update("DELETE FROM sector_analytics_calculation_run");
        jdbcTemplate.update("DELETE FROM market_sector_ranking_item");
        jdbcTemplate.update("DELETE FROM market_sector_ranking_batch");
        jdbcTemplate.update("DELETE FROM market_sector_identity_lock");
        jdbcTemplate.update("DELETE FROM market_sector_identity");
        jdbcTemplate.update("DELETE FROM market_calendar");
        seedFiveDayCloseFacts();
    }

    @Test
    void calculatesAndReadsOneConsistentPublishedBatch() throws Exception {
        String response = mockMvc.perform(post("/api/v1/market-research/calculations")
                        .param("market", "CN").param("asOfDate", "2026-01-05").param("window", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.sectorCount").value(5))
                .andReturn().getResponse().getContentAsString();
        Long publicationId = Long.valueOf(response.replaceAll(
                ".*\\\"publicationBatchId\\\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/v1/market-research/radar")
                        .param("market", "CN").param("window", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicationBatchId").value(publicationId))
                .andExpect(jsonPath("$.data.scope").value("RANKED_UNIVERSE"))
                .andExpect(jsonPath("$.data.scopeDescription").value("排行样本，不代表全市场"))
                .andExpect(jsonPath("$.data.strengthCalculationRunId").isNumber())
                .andExpect(jsonPath("$.data.momentumCalculationRunId").isNumber())
                .andExpect(jsonPath("$.data.strengthFormulaCode").value("RELATIVE_STRENGTH"))
                .andExpect(jsonPath("$.data.momentumFormulaCode").value("ROTATION_PERSISTENCE"))
                .andExpect(jsonPath("$.data.flowMetricNature").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.capitalFlow").isEmpty())
                .andExpect(jsonPath("$.data.actualItemCount").value(5))
                .andExpect(jsonPath("$.data.expectedItemCount").value(100))
                .andExpect(jsonPath("$.data.reasonCodes[0]").value("RANKED_UNIVERSE_LIMITED_COVERAGE"))
                .andExpect(jsonPath("$.data.sectors.length()").value(5))
                .andExpect(jsonPath("$.data.sectors[0].evidence.length()").value(3));

        Long sectorId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM market_sector_identity", Long.class);
        mockMvc.perform(get("/api/v1/market-research/sectors/ranking-history")
                        .param("market", "CN").param("window", "5").param("days", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectors.length()").value(5));
        mockMvc.perform(get("/api/v1/market-research/sectors/{sectorId}", sectorId)
                        .param("market", "CN").param("window", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectorId").value(sectorId))
                .andExpect(jsonPath("$.data.history.length()").value(1));

        mockMvc.perform(post("/api/v1/market-research/calculations")
                        .param("market", "CN").param("asOfDate", "2026-01-05").param("window", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicationBatchId").value(publicationId))
                .andExpect(jsonPath("$.data.reused").value(true));
    }

    @Test
    void defaultRadarPublishesTwentyDayStrengthWithFiveDayMomentumAndRejectsCrossMarketMember()
            throws Exception {
        seedAdditionalCloseFacts(6, 20);
        String response = mockMvc.perform(post("/api/v1/market-research/calculations")
                        .param("market", "CN").param("asOfDate", "2026-01-20").param("window", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strengthWindowDays").value(20))
                .andExpect(jsonPath("$.data.momentumWindowDays").value(5))
                .andReturn().getResponse().getContentAsString();
        Long publicationId = Long.valueOf(response.replaceAll(
                ".*\\\"publicationBatchId\\\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/v1/market-research/radar")
                        .param("market", "CN").param("window", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strengthWindowDays").value(20))
                .andExpect(jsonPath("$.data.momentumWindowDays").value(5));
        java.util.List<Integer> windows = jdbcTemplate.queryForList("""
                SELECT run.window_days FROM sector_analytics_publication_member member
                JOIN sector_analytics_calculation_run run ON run.id = member.calculation_run_id
                WHERE member.publication_batch_id = ? ORDER BY run.window_days
                """, Integer.class, publicationId);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(5, 20), windows);

        jdbcTemplate.update("""
                INSERT INTO sector_analytics_calculation_run
                    (provider_code, market_code, as_of_date, formula_code, formula_version, window_days,
                     parameter_hash, source_manifest_hash, source_manifest, status, quality_status,
                     sample_size, started_at)
                VALUES ('LONGPORT', 'HK', '2026-01-20', 'CROSS_MARKET_TEST', 'v1', 5,
                        'cross-param', 'cross-source', 'cross-manifest', 'SUCCEEDED', 'OK', 5, CURRENT_TIMESTAMP)
                """);
        Long hkRunId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM sector_analytics_calculation_run", Long.class);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO sector_analytics_publication_member
                    (publication_batch_id, calculation_run_id, formula_code,
                     provider_code, market_code, as_of_date)
                VALUES (?, ?, 'CROSS_MARKET_TEST', 'LONGPORT', 'HK', '2026-01-20')
                """, publicationId, hkRunId));
    }

    @Test
    void returnsExplicitNoDerivedDataInsteadOfInventingRadar() throws Exception {
        mockMvc.perform(get("/api/v1/market-research/radar")
                        .param("market", "HK").param("window", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void refusesPublicationWhenProviderResponseTimeIsUnknown() throws Exception {
        jdbcTemplate.update("UPDATE market_sector_ranking_batch SET provider_quote_time = NULL "
                + "WHERE trade_date = '2026-01-03'");

        mockMvc.perform(post("/api/v1/market-research/calculations")
                        .param("market", "CN").param("asOfDate", "2026-01-05").param("window", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "SOURCE_TIME_UNKNOWN")));
        Integer publications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sector_analytics_publication_batch", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, publications);
    }

    @Test
    void refusesHkLongWindowWithoutAuthoritativeCalendar() throws Exception {
        LocalDate tradeDate = LocalDate.of(2026, 1, 5);
        LocalDateTime time = tradeDate.atTime(16, 0);
        jdbcTemplate.update("""
                INSERT INTO market_sector_ranking_batch
                    (provider_code, market_code, trade_date, snapshot_type, snapshot_bucket_time,
                     snapshot_time, provider_quote_time, item_count, quality_status)
                VALUES ('LONGPORT', 'HK', ?, 'CLOSE', ?, ?, ?, 5, 'VALID')
                """, tradeDate, time, time, time);

        mockMvc.perform(post("/api/v1/market-research/calculations")
                        .param("market", "HK").param("asOfDate", "2026-01-05").param("window", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "INSUFFICIENT_RAW")));
        Integer publications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sector_analytics_publication_batch WHERE market_code = 'HK'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, publications);
    }

    private void seedFiveDayCloseFacts() {
        seedAdditionalCloseFacts(1, 5);
    }

    private void seedAdditionalCloseFacts(int fromDay, int toDay) {
        for (int day = fromDay; day <= toDay; day++) {
            LocalDate tradeDate = LocalDate.of(2026, 1, day);
            LocalDateTime time = tradeDate.atTime(15, 0);
            jdbcTemplate.update("""
                    INSERT INTO market_sector_ranking_batch
                        (provider_code, market_code, trade_date, snapshot_type, snapshot_bucket_time,
                         snapshot_time, provider_quote_time, item_count, quality_status)
                    VALUES ('LONGPORT', 'CN', ?, 'CLOSE', ?, ?, ?, 5, 'VALID')
                    """, tradeDate, time, time, time);
            Long batchId = jdbcTemplate.queryForObject(
                    "SELECT MAX(id) FROM market_sector_ranking_batch", Long.class);
            for (int sector = 1; sector <= 5; sector++) {
                String providerSectorId = "BK/SH/IN" + sector;
                if (day == 1) {
                    jdbcTemplate.update("""
                            INSERT INTO market_sector_identity
                                (provider_code, market_code, provider_sector_id, taxonomy_version,
                                 sector_name, valid_from, archived)
                            VALUES ('LONGPORT', 'CN', ?, 'LONGPORT_INDUSTRY_V1', ?, '2026-01-01', FALSE)
                            """, providerSectorId, "板块" + sector);
                }
                Long sectorId = jdbcTemplate.queryForObject("""
                        SELECT id FROM market_sector_identity
                        WHERE provider_code='LONGPORT' AND market_code='CN' AND provider_sector_id=?
                        """, Long.class, providerSectorId);
                jdbcTemplate.update("""
                        INSERT INTO market_sector_ranking_item
                            (batch_id, sector_identity_id, rank_no, provider_sector_id, sector_name,
                             change_rate, leading_name, leading_symbol)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, batchId, sectorId, sector, providerSectorId, "板块" + sector,
                        (sector * day) / 1000.0, "领涨" + sector, "SH.60000" + sector);
            }
        }
    }
}
