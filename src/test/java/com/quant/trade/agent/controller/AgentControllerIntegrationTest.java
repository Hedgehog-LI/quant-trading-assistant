package com.quant.trade.agent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Agent API MockMvc 集成测试。
 * <p>
 * 覆盖：disabled 模式、无 Token、错 Token、正确 Token、功能调用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    // ==================== Disabled 模式（测试 profile 默认关闭）====================

    @Test
    void disabledReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/agent/capabilities"))
            .andExpect(status().isNotFound());
    }

    @Test
    void disabledRejectsEvenWithToken() throws Exception {
        mockMvc.perform(get("/api/v1/agent/system/health")
                .header("Authorization", "Bearer any"))
            .andExpect(status().isNotFound());
    }

    @Test
    void nonAgentPathsNotBlocked() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    // ==================== Enabled 模式 ====================

    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
        "qta.agent.enabled=true",
        "qta.agent.token=test-agent-token-0123456789abcdef0123456789",
        "qta.agent.rate-limit-per-minute=100"
    })
    static class EnabledTests {

        @Autowired private MockMvc mockMvc;
        private static final String VALID_TOKEN = "test-agent-token-0123456789abcdef0123456789";

        @Test
        void missingTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/agent/capabilities"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void wrongTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/agent/capabilities")
                    .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void validTokenReturnsCapabilities() throws Exception {
            mockMvc.perform(get("/api/v1/agent/capabilities")
                    .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conclusion").exists())
                .andExpect(jsonPath("$.data.freshnessStatus").value("FRESH"));
        }

        @Test
        void validTokenReturnsSystemHealth() throws Exception {
            mockMvc.perform(get("/api/v1/agent/system/health")
                    .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.providerReachable").exists());
        }

        @Test
        void validTokenReturnsTodayOverview() throws Exception {
            mockMvc.perform(get("/api/v1/agent/trading/today")
                    .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.enabledWatchlistCount").exists());
        }

        @Test
        void validTokenReturnsPortfolioSummary() throws Exception {
            mockMvc.perform(get("/api/v1/agent/portfolio/summary")
                    .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.positionCount").exists());
        }

        @Test
        void validTokenReturnsCollectionOverview() throws Exception {
            mockMvc.perform(get("/api/v1/agent/market-data/collection-overview")
                    .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.totalSymbols").exists());
        }

        @Test
        void validTokenReturnsCollectionFailures() throws Exception {
            mockMvc.perform(get("/api/v1/agent/market-data/failures")
                    .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.failedTasks").exists());
        }

        @Test
        void validTokenReturnsDataQualityAlerts() throws Exception {
            mockMvc.perform(get("/api/v1/agent/market-data/alerts")
                    .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.alerts").exists());
        }

        @Test
        void validTokenReturnsSectorRanking() throws Exception {
            mockMvc.perform(get("/api/v1/agent/market-sectors/ranking-summary")
                    .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
        }

        @Test
        void validTokenReturnsSecuritySummary() throws Exception {
            mockMvc.perform(get("/api/v1/agent/securities/SH.600519/market-summary")
                    .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
        }
    }
}
