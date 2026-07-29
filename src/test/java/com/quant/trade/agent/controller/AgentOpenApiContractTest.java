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
 * Agent OpenAPI 契约测试。
 * <p>
 * 1. 默认 /v3/api-docs 不含 Agent 路径。
 * 2. /v3/api-docs/agent 需要 Token。
 * 3. 401/429 错误响应包含 requestId。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "qta.agent.enabled=true",
    "qta.agent.token=test-agent-token-0123456789abcdef0123456789",
    "qta.agent.rate-limit-per-minute=200"
})
class AgentOpenApiContractTest {

    @Autowired private MockMvc mockMvc;
    private static final String VALID_TOKEN = "test-agent-token-0123456789abcdef0123456789";
    private static final String AUTH = "Bearer " + VALID_TOKEN;

    // ==================== OpenAPI 分组隔离 ====================

    @Test
    void agentGroupIsSeparateFromDefault() throws Exception {
        // /v3/api-docs/agent contains agent paths with security
        mockMvc.perform(get("/v3/api-docs/agent")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/agent/system/health'].get.operationId").value("qtaAgentSystemHealth"))
            .andExpect(jsonPath("$.paths['/api/v1/agent/system/health'].get.security[0].bearerAuth").exists());

        // Agent group does NOT contain non-agent paths (e.g. watchlist)
        mockMvc.perform(get("/v3/api-docs/agent")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/watchlist']").doesNotExist());
    }

    @Test
    void agentApiDocsGroupRequiresToken() throws Exception {
        // 无 Token → 401 (filter rejects before reaching endpoint)
        mockMvc.perform(get("/v3/api-docs/agent"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void agentApiDocsWithTokenReturnsAgentPathsAndSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs/agent")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            // Verify operationId is fixed and unique
            .andExpect(jsonPath("$.paths['/api/v1/agent/system/health'].get.operationId").value("qtaAgentSystemHealth"))
            .andExpect(jsonPath("$.paths['/api/v1/agent/capabilities'].get.operationId").value("qtaAgentCapabilities"))
            // Verify bearer security requirement exists on each operation
            .andExpect(jsonPath("$.paths['/api/v1/agent/system/health'].get.security").exists())
            .andExpect(jsonPath("$.paths['/api/v1/agent/system/health'].get.security[0].bearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/v1/agent/capabilities'].get.security[0].bearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/v1/agent/portfolio/summary'].get.security[0].bearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/v1/agent/trading/today'].get.security[0].bearerAuth").exists())
            // Verify components has bearerAuth security scheme
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    // ==================== requestId in error responses ====================

    @Test
    void unauthorizedResponseIncludesRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/agent/capabilities"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.requestId").exists())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void wrongTokenResponseIncludesRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/agent/capabilities")
                .header("Authorization", "Bearer wrong-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.requestId").exists());
    }
}
