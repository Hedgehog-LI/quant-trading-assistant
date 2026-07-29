package com.quant.trade.agent.controller;

import com.quant.trade.agent.dao.AgentApiAuditLogMapper;
import com.quant.trade.agent.model.AgentApiAuditLogDO;
import com.quant.trade.agent.service.AgentQueryService;
import com.quant.trade.agent.vo.TrustedAnswer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * D4 验证：HTTP 500 错误响应语义。
 * <p>
 * 断言：
 * <ul>
 *   <li>HTTP 状态码为 500（不伪装 200）。</li>
 *   <li>response body success=false，code != SUCCESS。</li>
 *   <li>requestId 在 X-Request-ID 响应头 / body / 审计行中三者一致。</li>
 *   <li>body 不泄露内部异常类名/堆栈。</li>
 *   <li>审计行记录 httpStatus=500、errorCode=INTERNAL_ERROR。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "qta.agent.enabled=true",
    "qta.agent.token=test-agent-token-0123456789abcdef0123456789",
    "qta.agent.rate-limit-per-minute=200"
})
class AgentErrorSemanticsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentApiAuditLogMapper auditMapper;
    @MockBean private AgentQueryService queryService;

    private static final String VALID_TOKEN = "test-agent-token-0123456789abcdef0123456789";
    private static final String AUTH = "Bearer " + VALID_TOKEN;

    @Test
    void serviceThrowProduces500WithSuccessFalseAndNoInternalLeak() throws Exception {
        // Force the service layer to throw a internal exception.
        when(queryService.systemHealth())
            .thenThrow(new RuntimeException("database connection lost: jdbc:mysql://internal-host:3306/secret"));

        MvcResult result = mockMvc.perform(get("/api/v1/agent/system/health")
                .header("Authorization", AUTH))
            .andExpect(status().isInternalServerError())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        String headerRequestId = result.getResponse().getHeader("X-Request-ID");

        // success=false and code != SUCCESS
        assertTrue(body.contains("\"success\":false"), "500 body must have success=false: " + body);
        assertFalse(body.contains("\"code\":\"SUCCESS\""), "500 body must not have code=SUCCESS: " + body);
        assertTrue(body.contains("\"code\":\"INTERNAL_ERROR\""), "500 body must have code=INTERNAL_ERROR: " + body);

        // No internal leak: the exception message and class name must NOT appear in the body
        assertFalse(body.contains("database connection lost"), "body must not leak exception message: " + body);
        assertFalse(body.contains("RuntimeException"), "body must not leak exception class name: " + body);
        assertFalse(body.contains("internal-host"), "body must not leak internal host: " + body);
        assertFalse(body.contains("3306"), "body must not leak internal port: " + body);

        // requestId consistency across header, body, and audit row
        assertNotNull(headerRequestId, "X-Request-ID header must be present on 500");
        assertTrue(body.contains("\"requestId\":\"" + headerRequestId + "\"") ||
                body.contains(headerRequestId),
            "500 body must reference the same requestId as X-Request-ID header: " + body);

        AgentApiAuditLogDO row = auditMapper.selectRecent(5000).stream()
            .filter(r -> headerRequestId.equals(r.getRequestId()))
            .findFirst().orElseThrow(() -> new AssertionError("audit row missing for requestId=" + headerRequestId));
        assertEquals(500, row.getHttpStatus());
        assertEquals("INTERNAL_ERROR", row.getErrorCode());
    }

    @Test
    void handledFailureAnswerProduces500WithSuccessFalse() throws Exception {
        // TrustedAnswer.fail(...) returns data=null with a warning — the controller
        // treats this as a handled failure (HTTP 500).
        when(queryService.portfolioSummary())
            .thenReturn(TrustedAnswer.fail("持仓查询失败"));

        MvcResult result = mockMvc.perform(get("/api/v1/agent/portfolio/summary")
                .header("Authorization", AUTH))
            .andExpect(status().isInternalServerError())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":false"), "handled-failure body must have success=false: " + body);
        assertTrue(body.contains("\"code\":\"INTERNAL_ERROR\""), "handled-failure body must have code=INTERNAL_ERROR: " + body);

        String headerRequestId = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(headerRequestId);
        AgentApiAuditLogDO row = auditMapper.selectRecent(5000).stream()
            .filter(r -> headerRequestId.equals(r.getRequestId()))
            .findFirst().orElseThrow();
        assertEquals(500, row.getHttpStatus());
        assertEquals("INTERNAL_ERROR", row.getErrorCode());
    }

    @Test
    void successfulResponseHasSuccessTrueAndRequestIdConsistency() throws Exception {
        when(queryService.capabilities())
            .thenReturn(TrustedAnswer.of("ok", java.util.Map.of("tools", java.util.List.of()),
                TrustedAnswer.FRESH, null, null));

        MvcResult result = mockMvc.perform(get("/api/v1/agent/capabilities")
                .header("Authorization", AUTH))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("\"code\":\"SUCCESS\""));

        String headerRequestId = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(headerRequestId);
        AgentApiAuditLogDO row = auditMapper.selectRecent(5000).stream()
            .filter(r -> headerRequestId.equals(r.getRequestId()))
            .findFirst().orElseThrow();
        assertEquals(200, row.getHttpStatus());
        assertNull(row.getErrorCode(), "200 must have null errorCode");
    }
}
