package com.quant.trade.marketdata;

import com.quant.trade.marketdata.provider.DisabledSecurityMetadataEnricher;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D3-03：longport 默认 disabled 时，Spring 上下文正常启动、目录搜索可用、
 * {@link DisabledSecurityMetadataEnricher} 被装配且 {@code isEnabled()=false}。
 * 同时验证 disabled 路径下 enrich 端点返回 400 BUSINESS_RULE_VIOLATION，不泄露凭据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityMetadataEnricherDisabledContextTest {

    @Autowired
    private SecurityMetadataEnricher enricher;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDirectory() {
        jdbcTemplate.update("DELETE FROM stock_alias");
        jdbcTemplate.update("DELETE FROM stock_basic");
    }

    @Test
    void disabledEnricherIsWiredAndContextStarts() {
        assertTrue(enricher instanceof DisabledSecurityMetadataEnricher,
                "longport disabled 时装配 DisabledSecurityMetadataEnricher 兜底");
        assertFalse(enricher.isEnabled(), "disabled enricher isEnabled()=false");
    }

    @Test
    void directorySearchRemainsAvailableWhenEnricherDisabled() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (
                    canonical_symbol, symbol, name, market, exchange, currency, security_type,
                    list_status, data_source, source_updated_at
                ) VALUES ('SH.600519', '600519', '贵州茅台', 'SH', 'SSE', 'CNY', 'STOCK',
                          'LISTED', 'TEST', '2026-07-01 00:00:00')
                """);

        // 目录搜索在 enricher disabled 时仍可用（200）。
        mockMvc.perform(get("/api/v1/market-data/securities/search").param("q", "贵州茅台"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].canonicalSymbol").value("SH.600519"));

        // enrich 端点在 disabled 时返回 400 BUSINESS_RULE_VIOLATION（不泄露凭据）。
        mockMvc.perform(post("/api/v1/market-data/security-directory/enrich")
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }
}
