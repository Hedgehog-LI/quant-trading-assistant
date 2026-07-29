package com.quant.trade.agent.security;

import com.quant.trade.agent.service.AgentAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentAuditFilter 单元测试 — 验证单一审计入口覆盖所有 HTTP 状态码，
 * 并保证 requestId 一致、只审计一次、非 Agent 路径跳过。
 * <p>
 * 取代原 AgentAuditInterceptorTest（D3：MVC 拦截器已删除）。
 */
@ExtendWith(MockitoExtension.class)
class AgentAuditInterceptorTest {

    @Mock private AgentAuditService auditService;
    @InjectMocks private AgentAuditFilter filter;

    /** 构造 request mock，setAttribute/getAttribute 走内存 map。 */
    private HttpServletRequest mockRequest(String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        Map<String, Object> attributes = new HashMap<>();
        lenient().when(req.getRequestURI()).thenReturn(uri);
        lenient().when(req.getMethod()).thenReturn("GET");
        lenient().when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(req.getQueryString()).thenReturn(null);
        lenient().doAnswer(inv -> {
            attributes.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(req).setAttribute(anyString(), any());
        lenient().when(req.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.getArgument(0)));
        return req;
    }

    private HttpServletResponse mockResponse(int status) {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        lenient().when(resp.getStatus()).thenReturn(status);
        return resp;
    }

    private FilterChain noopChain() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        lenient().doNothing().when(chain).doFilter(any(), any());
        return chain;
    }

    private FilterChain chainThatSetsStatus(HttpServletResponse resp, int status) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        lenient().doAnswer(inv -> {
            when(resp.getStatus()).thenReturn(status);
            return null;
        }).when(chain).doFilter(any(), any());
        return chain;
    }

    @Test
    void audits200Success() throws Exception {
        var req = mockRequest("/api/v1/agent/capabilities");
        var resp = mockResponse(200);
        var chain = chainThatSetsStatus(resp, 200);

        filter.doFilter(req, resp, chain);

        verify(auditService).record(anyString(), eq("127.0.0.1"), isNull(),
            eq("qtaAgentCapabilities"), eq("GET"), eq("/api/v1/agent/capabilities"),
            anyString(), eq(200), isNull(), eq(0), anyLong(), any());
    }

    @Test
    void audits401Unauthorized() throws Exception {
        var req = mockRequest("/api/v1/agent/capabilities");
        var resp = mockResponse(401);
        var chain = chainThatSetsStatus(resp, 401);

        filter.doFilter(req, resp, chain);

        verify(auditService).record(anyString(), anyString(), isNull(),
            anyString(), eq("GET"), anyString(), anyString(),
            eq(401), eq("UNAUTHORIZED"), eq(0), anyLong(), any());
    }

    @Test
    void audits403Forbidden() throws Exception {
        var req = mockRequest("/api/v1/agent/system/health");
        var resp = mockResponse(403);
        var chain = chainThatSetsStatus(resp, 403);

        filter.doFilter(req, resp, chain);

        verify(auditService).record(anyString(), anyString(), isNull(),
            eq("qtaAgentSystemHealth"), eq("GET"), anyString(), anyString(),
            eq(403), eq("FORBIDDEN"), eq(0), anyLong(), any());
    }

    @Test
    void audits404NotFound() throws Exception {
        var req = mockRequest("/api/v1/agent/capabilities");
        var resp = mockResponse(404);
        var chain = chainThatSetsStatus(resp, 404);

        filter.doFilter(req, resp, chain);

        verify(auditService).record(anyString(), anyString(), isNull(),
            anyString(), eq("GET"), anyString(), anyString(),
            eq(404), eq("NOT_FOUND"), eq(0), anyLong(), any());
    }

    @Test
    void audits429RateLimited() throws Exception {
        var req = mockRequest("/api/v1/agent/capabilities");
        var resp = mockResponse(429);
        var chain = chainThatSetsStatus(resp, 429);

        filter.doFilter(req, resp, chain);

        verify(auditService).record(anyString(), anyString(), isNull(),
            anyString(), eq("GET"), anyString(), anyString(),
            eq(429), eq("RATE_LIMITED"), eq(0), anyLong(), any());
    }

    @Test
    void audits500InternalError() throws Exception {
        var req = mockRequest("/api/v1/agent/capabilities");
        var resp = mockResponse(500);
        var chain = chainThatSetsStatus(resp, 500);

        filter.doFilter(req, resp, chain);

        verify(auditService).record(anyString(), anyString(), isNull(),
            anyString(), eq("GET"), anyString(), anyString(),
            eq(500), eq("INTERNAL_ERROR"), eq(0), anyLong(), any());
    }

    @Test
    void doesNotAuditNonAgentPaths() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getRequestURI()).thenReturn("/api/v1/watchlist");
        var resp = mockResponse(200);
        var chain = noopChain();

        filter.doFilter(req, resp, chain);

        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), anyInt(), anyLong(), any());
    }

    @Test
    void setsRequestIdInRequestAttributeAndResponseHeader() throws Exception {
        var req = mockRequest("/api/v1/agent/capabilities");
        var resp = mockResponse(200);
        var chain = chainThatSetsStatus(resp, 200);

        filter.doFilter(req, resp, chain);

        // requestId written to request attribute so downstream filters/controllers reuse it
        String attrRequestId = (String) req.getAttribute("agentRequestId");
        assertNotNull(attrRequestId);
        assertFalse(attrRequestId.isBlank());
        // X-Request-ID response header set
        verify(resp).setHeader(eq("X-Request-ID"), eq(attrRequestId));
        // audit recorded with the SAME requestId
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(captor.capture(), any(), any(), any(), any(), any(), any(), anyInt(), any(), anyInt(), anyLong(), any());
        assertEquals(attrRequestId, captor.getValue());
    }

    @Test
    void auditsEvenWhenDownstreamThrows() throws Exception {
        var req = mockRequest("/api/v1/agent/capabilities");
        var resp = mockResponse(500);
        FilterChain chain = mock(FilterChain.class);
        lenient().doAnswer(inv -> {
            when(resp.getStatus()).thenReturn(500);
            throw new RuntimeException("downstream boom");
        }).when(chain).doFilter(any(), any());

        // The filter must NOT swallow the exception, but it MUST still audit.
        assertThrows(RuntimeException.class, () -> filter.doFilter(req, resp, chain));
        verify(auditService).record(anyString(), anyString(), isNull(),
            anyString(), eq("GET"), anyString(), anyString(),
            eq(500), eq("INTERNAL_ERROR"), eq(0), anyLong(), any());
    }
}
