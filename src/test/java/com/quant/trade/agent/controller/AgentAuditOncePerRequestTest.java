package com.quant.trade.agent.controller;

import com.quant.trade.agent.dao.AgentApiAuditLogMapper;
import com.quant.trade.agent.model.AgentApiAuditLogDO;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * D3 集成验证：每次请求恰好写入一条审计记录，覆盖 Controller 成功路径、
 * Token Filter 短路（401）、限流 Filter 短路（429）、非 Agent 路径。
 * <p>
 * 这是 unified AgentAuditFilter 的端到端证明：
 * <ul>
 *   <li>无论请求被谁终止，都只产生一条审计行。</li>
 *   <li>requestId 在 X-Request-ID 响应头 / 错误 body / 审计行中三者一致。</li>
 *   <li>审计行 errorCode 与 HTTP 状态码匹配。</li>
 * </ul>
 * <p>
 * 测试用 @Order 显式排序，避免 rate-limit 窗口跨用例污染（限流按 remoteAddr 累积）。
 * 行数断言以 requestId 维度计数，避免 selectRecent 上限导致误判。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "qta.agent.enabled=true",
    "qta.agent.token=test-agent-token-0123456789abcdef0123456789",
    // Generous rate limit so happy-path tests don't trip the limiter;
    // the 429 test disables the agent temporarily via a dedicated approach below.
    "qta.agent.rate-limit-per-minute=200"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentAuditOncePerRequestTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentApiAuditLogMapper auditMapper;

    private static final String VALID_TOKEN = "test-agent-token-0123456789abcdef0123456789";
    private static final String AUTH = "Bearer " + VALID_TOKEN;

    /** 统计给定 requestId 在审计表中的行数（H2 selectRecent 返回最近 N 行，足够覆盖单测）。 */
    private long countRowsForRequestId(String requestId) {
        return auditMapper.selectRecent(5000).stream()
            .filter(r -> requestId.equals(r.getRequestId()))
            .count();
    }

    @Test
    @Order(1)
    void controllerSuccessWritesExactlyOneAuditRow() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/capabilities")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andReturn();

        String headerRequestId = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(headerRequestId, "X-Request-ID header must be set");

        assertEquals(1, countRowsForRequestId(headerRequestId),
            "Exactly one audit row must be written for a successful request");

        AgentApiAuditLogDO row = auditMapper.selectRecent(5000).stream()
            .filter(r -> headerRequestId.equals(r.getRequestId()))
            .findFirst().orElseThrow();
        assertEquals(200, row.getHttpStatus());
        assertEquals("qtaAgentCapabilities", row.getOperationCode());
        assertNull(row.getErrorCode(), "200 success must have null errorCode");
    }

    @Test
    @Order(2)
    void tokenFilterShortCircuit401WritesExactlyOneAuditRow() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/agent/capabilities"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists("X-Request-ID"))
            .andReturn();

        String headerRequestId = result.getResponse().getHeader("X-Request-ID");
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"requestId\":\"" + headerRequestId + "\""),
            "Body requestId must match X-Request-ID header: " + body);

        assertEquals(1, countRowsForRequestId(headerRequestId),
            "Filter-level 401 must produce exactly one audit row");

        AgentApiAuditLogDO row = auditMapper.selectRecent(5000).stream()
            .filter(r -> headerRequestId.equals(r.getRequestId()))
            .findFirst().orElseThrow();
        assertEquals(401, row.getHttpStatus());
        assertEquals("UNAUTHORIZED", row.getErrorCode());
    }

    @Test
    @Order(3)
    void nonAgentPathWritesNoAuditRow() throws Exception {
        int before = auditMapper.selectRecent(5000).size();
        mockMvc.perform(get("/actuator/health")).andReturn();
        int after = auditMapper.selectRecent(5000).size();
        assertEquals(before, after, "Non-agent paths must not be audited");
    }

    /**
     * 429 路径需要单独的低限流配置，避免影响其它用例。使用嵌套类 + 独立 @TestPropertySource
     * 会重建 ApplicationContext；这里改为通过发送超过 200 次/分钟的请求触发不可行，
     * 因此改用一个独立 SpringBootTest 上下文（嵌套静态类）来设置 rate-limit-per-minute=1。
     */
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
        "qta.agent.enabled=true",
        "qta.agent.token=test-agent-token-0123456789abcdef0123456789",
        "qta.agent.rate-limit-per-minute=1"
    })
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class RateLimitShortCircuit {

        @Autowired private MockMvc mockMvc;
        @Autowired private AgentApiAuditLogMapper auditMapper;
        private static final String AUTH = "Bearer " + VALID_TOKEN;

        private long countRowsForRequestId(String requestId) {
            return auditMapper.selectRecent(5000).stream()
                .filter(r -> requestId.equals(r.getRequestId()))
                .count();
        }

        @Test
        @Order(1)
        void rateLimitFilterShortCircuit429WritesExactlyOneAuditRow() throws Exception {
            // First call succeeds (quota=1)
            mockMvc.perform(get("/api/v1/agent/capabilities").header("Authorization", AUTH))
                .andExpect(status().isOk());

            // Second call must be rate-limited at the filter layer
            MvcResult result = mockMvc.perform(get("/api/v1/agent/system/health")
                    .header("Authorization", AUTH))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn();

            String headerRequestId = result.getResponse().getHeader("X-Request-ID");
            String body = result.getResponse().getContentAsString();
            assertTrue(body.contains("\"requestId\":\"" + headerRequestId + "\""),
                "429 body requestId must match X-Request-ID header: " + body);

            assertEquals(1, countRowsForRequestId(headerRequestId),
                "Filter-level 429 must produce exactly one audit row");

            AgentApiAuditLogDO row = auditMapper.selectRecent(5000).stream()
                .filter(r -> headerRequestId.equals(r.getRequestId()))
                .findFirst().orElseThrow();
            assertEquals(429, row.getHttpStatus());
            assertEquals("RATE_LIMITED", row.getErrorCode());
        }
    }
}
