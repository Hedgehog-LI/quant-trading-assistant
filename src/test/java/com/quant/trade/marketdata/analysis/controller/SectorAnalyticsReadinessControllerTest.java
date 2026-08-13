package com.quant.trade.marketdata.analysis.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 板块分析就绪门禁 MockMvc 聚焦测试（AC-01 / TEST-02）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SectorAnalyticsReadinessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM market_sector_ranking_item");
        jdbcTemplate.update("DELETE FROM market_sector_ranking_batch");
    }

    @Test
    void readinessHappyReturnsRankedUniverseScopeAndQuality() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO market_sector_ranking_batch (provider_code, market_code, trade_date, snapshot_type,
                    snapshot_bucket_time, snapshot_time, provider_quote_time, item_count, quality_status)
                VALUES ('LONGPORT', 'CN', '2026-07-16', 'CLOSE', '2026-07-16 15:00:00', '2026-07-16 15:00:00', '2026-07-16 15:00:00', 100, 'VALID')
                """);
        Long batchId = jdbcTemplate.queryForObject(
                "SELECT id FROM market_sector_ranking_batch WHERE market_code='CN'", Long.class);
        for (int i = 1; i <= 100; i++) {
            jdbcTemplate.update("""
                    INSERT INTO market_sector_ranking_item (batch_id, rank_no, provider_sector_id, sector_name, change_rate)
                    VALUES (?, ?, ?, ?, ?)
                    """, batchId, i, "sec-" + i, "板块" + i, new java.math.BigDecimal("0.0100"));
        }

        mockMvc.perform(get("/api/v1/market-research/readiness").param("market", "CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.market").value("CN"))
                .andExpect(jsonPath("$.data.scope").value("RANKED_UNIVERSE"))
                .andExpect(jsonPath("$.data.scopeDescription").value("排行样本，不代表全市场"))
                .andExpect(jsonPath("$.data.latestCloseBatchId").value(batchId.intValue()))
                .andExpect(jsonPath("$.data.asOfDate").value("2026-07-16"))
                .andExpect(jsonPath("$.data.actualItemCount").value(100))
                .andExpect(jsonPath("$.data.expectedItemCount").value(100))
                .andExpect(jsonPath("$.data.isTruncated").value(true))
                .andExpect(jsonPath("$.data.qualityStatus").value("OK"));
    }

    @Test
    void readinessNoBatchReturnsNoDerivedDataWithReasonCodes() throws Exception {
        mockMvc.perform(get("/api/v1/market-research/readiness").param("market", "CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.market").value("CN"))
                .andExpect(jsonPath("$.data.latestCloseBatchId").doesNotExist())
                .andExpect(jsonPath("$.data.asOfDate").doesNotExist())
                .andExpect(jsonPath("$.data.scope").value("RANKED_UNIVERSE"))
                .andExpect(jsonPath("$.data.qualityStatus").value("NO_DERIVED_DATA"))
                .andExpect(jsonPath("$.data.reasonCodes").isNotEmpty())
                .andExpect(jsonPath("$.data.reasonCodes[0]").value("NO_DERIVED_DATA"));
    }
}
