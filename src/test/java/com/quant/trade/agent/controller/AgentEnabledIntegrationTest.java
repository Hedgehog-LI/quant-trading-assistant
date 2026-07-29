package com.quant.trade.agent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Agent API 启用状态集成测试。
 * <p>
 * 断言 success/code/data，不只断言 HTTP 状态码。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "qta.agent.enabled=true",
    "qta.agent.token=test-agent-token-0123456789abcdef0123456789",
    "qta.agent.rate-limit-per-minute=200"
})
class AgentEnabledIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private static final String VALID_TOKEN = "test-agent-token-0123456789abcdef0123456789";
    private static final String AUTH = "Bearer " + VALID_TOKEN;

    @Test
    void missingTokenReturns401WithRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/agent/capabilities"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.requestId").exists())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void wrongTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/agent/capabilities")
                .header("Authorization", "Bearer wrong"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void capabilitiesReturnsSuccessWithData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/capabilities")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"), "success must be true");
        assertTrue(body.contains("\"code\":\"SUCCESS\""), "code must be SUCCESS");
        assertTrue(body.contains("\"data\""), "data must exist");
        assertTrue(body.contains("qta_system_health"), "capabilities must list tools");
    }

    @Test
    void systemHealthReturnsSuccessWithData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/system/health")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("providerReachable"));
    }

    @Test
    void todayOverviewReturnsSuccessWithData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/trading/today")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("enabledWatchlistCount"));
    }

    @Test
    void portfolioSummaryReturnsSuccessWithData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/portfolio/summary")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("positionCount"));
    }

    @Test
    void collectionOverviewReturnsSuccessWithData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/market-data/collection-overview")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("totalSymbols"));
    }

    @Test
    void collectionFailuresReturnsSuccessWithConclusion() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/market-data/failures")
                .header("Authorization", AUTH))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("conclusion"));
    }

    @Test
    void dataQualityAlertsReturnsSuccessWithConclusion() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/market-data/alerts")
                .header("Authorization", AUTH))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("conclusion"));
    }

    @Test
    void sectorRankingReturnsSuccessWithConclusion() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/market-sectors/ranking-summary")
                .header("Authorization", AUTH))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("conclusion"));
    }

    @Test
    void securitySummaryReturnsSuccessWithConclusion() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/securities/SH.600519/market-summary")
                .header("Authorization", AUTH))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("conclusion"));
    }

    // ==================== Parameter effectiveness ====================

    @Test
    void collectionOverviewMarketParameterIsPassed() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/market-data/collection-overview?market=CN")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"market\":\"CN\""), "market parameter must be reflected in data");
    }

    @Test
    void failuresLimitParameterIsPassed() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/market-data/failures?limit=5")
                .header("Authorization", AUTH))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("conclusion"));
    }

    @Test
    void alertsStatusParameterIsPassed() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/market-data/alerts?status=unresolved&limit=3")
                .header("Authorization", AUTH))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("conclusion"));
    }

    @Test
    void sectorRankingMarketParameterIsPassed() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/market-sectors/ranking-summary?market=HK&limit=5")
                .header("Authorization", AUTH))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"market\":\"HK\""), "market=HK must be in response");
    }
}
