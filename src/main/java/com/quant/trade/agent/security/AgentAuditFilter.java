package com.quant.trade.agent.security;

import com.quant.trade.agent.service.AgentAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 统一审计 Filter（单一审计入口）。
 * <p>
 * 覆盖整个 FilterChain，无论请求被下游 Filter（token/rate-limit）短路，
 * 还是被 Controller 处理或抛异常，都只会产生一条审计记录。
 * <p>
 * 设计要点（D3）：
 * <ul>
 *   <li>以 servlet 级 FilterRegistrationBean 注册在最外层（在 Spring Security
 *       之前），确保其 doFilter 的 finally 块能在任何下游短路/异常后仍执行。</li>
 *   <li>请求进入时生成 requestId，写入 request 属性与 X-Request-ID 响应头，
 *       下游所有 Filter / Controller / EntryPoint 共用此 requestId（不再各自生成）。</li>
 *   <li>严禁记录 Token / Authorization / 明文密钥。</li>
 * </ul>
 * 取代 AgentAuditInterceptor（已删除）与 Controller 手动审计调用。
 * <p>
 * 注意：不使用 @Component，避免被 Spring Boot 自动注册到 servlet 链导致顺序失控；
 * 由 AgentSecurityConfig 通过 FilterRegistrationBean 显式注册。
 */
public class AgentAuditFilter extends OncePerRequestFilter {

    private static final String AGENT_PATH_PREFIX = "/api/v1/agent";
    private static final String AGENT_OPENAPI_PATH = "/v3/api-docs/agent";
    private static final String REQUEST_ID_ATTR = "agentRequestId";
    private static final String START_TIME_ATTR = "agentRequestStartTime";
    private static final String START_MS_ATTR = "agentRequestStartMs";

    private final AgentAuditService auditService;

    public AgentAuditFilter(AgentAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(AGENT_PATH_PREFIX) && !uri.equals(AGENT_OPENAPI_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Single source of requestId for the whole request lifecycle.
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        request.setAttribute(REQUEST_ID_ATTR, requestId);
        request.setAttribute(START_TIME_ATTR, LocalDateTime.now());
        request.setAttribute(START_MS_ATTR, System.currentTimeMillis());
        // Surface requestId on the response so clients can correlate header/body/audit row.
        response.setHeader("X-Request-ID", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always audit, even when downstream filters short-circuited or threw.
            // OncePerRequestFilter guarantees this runs once per request.
            recordAudit(request, response, requestId);
        }
    }

    private void recordAudit(HttpServletRequest request, HttpServletResponse response, String requestId) {
        LocalDateTime startTime = (LocalDateTime) request.getAttribute(START_TIME_ATTR);
        Long startMs = (Long) request.getAttribute(START_MS_ATTR);
        if (startTime == null || startMs == null) {
            return;
        }

        int httpStatus = response.getStatus();
        long durationMs = System.currentTimeMillis() - startMs;
        String path = request.getRequestURI();
        String operationCode = deriveOperationCode(path);
        String paramSummary = request.getQueryString() != null
            ? truncate(request.getQueryString(), 500)
            : path;
        String errorCode = deriveErrorCode(httpStatus);
        String clientId = request.getRemoteAddr();
        // resultCount not available at filter layer; Controller is the only place that
        // knows it, and we explicitly removed Controller-side audit to keep a single
        // audit writer. resultCount is recorded as 0 here.
        int resultCount = 0;

        auditService.record(
            requestId,
            clientId,
            null, // senderHash — not available at this layer
            operationCode,
            request.getMethod(),
            path,
            paramSummary,
            httpStatus,
            errorCode,
            resultCount,
            durationMs,
            startTime
        );
    }

    private String deriveErrorCode(int httpStatus) {
        if (httpStatus < 400) return null;
        switch (httpStatus) {
            case 401: return "UNAUTHORIZED";
            case 403: return "FORBIDDEN";
            case 404: return "NOT_FOUND";
            case 429: return "RATE_LIMITED";
            default:
                return httpStatus >= 500 ? "INTERNAL_ERROR" : "ERROR";
        }
    }

    private String deriveOperationCode(String path) {
        if (path.contains("/capabilities")) return "qtaAgentCapabilities";
        if (path.contains("/system/health")) return "qtaAgentSystemHealth";
        if (path.contains("/trading/today")) return "qtaAgentTodayOverview";
        if (path.contains("/portfolio/summary")) return "qtaAgentPortfolioSummary";
        if (path.contains("/collection-overview")) return "qtaAgentCollectionOverview";
        if (path.contains("/failures")) return "qtaAgentCollectionFailures";
        if (path.contains("/alerts")) return "qtaAgentDataQualityAlerts";
        if (path.contains("/ranking-summary")) return "qtaAgentSectorRankingSummary";
        if (path.contains("/market-summary")) return "qtaAgentSecurityMarketSummary";
        if (path.equals(AGENT_OPENAPI_PATH)) return "agentOpenApiDocs";
        return "agent:" + path;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
