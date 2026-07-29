package com.quant.trade.agent.security;

import com.quant.trade.agent.config.AgentProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Agent 限流 Filter（per-client per-minute 内存滑动窗口）。
 * 超限返回 429 + Retry-After header。
 */
@Component
public class AgentRateLimitFilter extends OncePerRequestFilter {

    private static final String AGENT_PATH_PREFIX = "/api/v1/agent";
    private static final String AGENT_OPENAPI_PATH = "/v3/api-docs/agent";

    private final AgentProperties properties;
    private final Map<String, Queue<Long>> timestamps = new ConcurrentHashMap<>();

    public AgentRateLimitFilter(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(AGENT_PATH_PREFIX) && !uri.equals(AGENT_OPENAPI_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Use remote IP as clientId — prevents bypass via forged Authorization values
        String clientId = request.getRemoteAddr();

        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        Queue<Long> queue = timestamps.computeIfAbsent(clientId, k -> new ConcurrentLinkedQueue<>());
        queue.removeIf(ts -> ts < windowStart);

        if (queue.size() >= properties.getRateLimitPerMinute()) {
            String requestId = resolveRequestId(request);
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setHeader("X-Request-ID", requestId);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"Rate limit exceeded\",\"retryAfterSeconds\":60,\"requestId\":\"" + requestId + "\"}");
            return;
        }

        queue.add(now);
        filterChain.doFilter(request, response);
    }

    /** 复用 AgentAuditFilter 的 requestId；防御性兜底仅在 filter 未运行时使用。 */
    private String resolveRequestId(HttpServletRequest request) {
        Object attr = request.getAttribute("agentRequestId");
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
