package com.quant.trade.agent.security;

import com.quant.trade.agent.config.AgentProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * Agent Bearer Token 认证 Filter。
 * <p>
 * 仅拦截 /api/v1/agent/** 路径。
 * 使用 MessageDigest.isEqual 进行恒定时间比较，防止时序攻击。
 * 认证失败返回 401，Agent 关闭返回 404。
 */
@Component
public class AgentTokenAuthFilter extends OncePerRequestFilter {

    private static final String AGENT_PATH_PREFIX = "/api/v1/agent";
    private static final String AGENT_OPENAPI_PATH = "/v3/api-docs/agent";

    private final AgentProperties properties;

    public AgentTokenAuthFilter(AgentProperties properties) {
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
        // Agent 未启用时返回 404
        if (!properties.isEnabled()) {
            writeError(request, response, HttpServletResponse.SC_NOT_FOUND, "Agent API is disabled");
            return;
        }

        // Token 强度不足时返回 503
        if (!properties.isTokenValid()) {
            writeError(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Agent token not configured");
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String presentedToken = authHeader.substring(7);

        // 恒定时间比较：SHA-256 hash + MessageDigest.isEqual
        if (!constantTimeTokenCompare(presentedToken, properties.getToken())) {
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }

        // 认证成功
        var auth = new UsernamePasswordAuthenticationToken(
            "agent-client", null,
            List.of(new SimpleGrantedAuthority("ROLE_AGENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    /** 统一错误响应，包含 requestId（复用 AgentAuditFilter 设置的 requestId）。 */
    private void writeError(HttpServletRequest request, HttpServletResponse response, int status, String message) throws IOException {
        String requestId = resolveRequestId(request);
        response.setStatus(status);
        response.setHeader("X-Request-ID", requestId);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"error\":\"" + message + "\",\"requestId\":\"" + requestId + "\"}");
    }

    /** 复用 AgentAuditFilter 的 requestId；防御性兜底仅在 filter 未运行时使用。 */
    private String resolveRequestId(HttpServletRequest request) {
        Object attr = request.getAttribute("agentRequestId");
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 恒定时间 Token 比较。
     * 对两个 Token 分别做 SHA-256 哈希后用 MessageDigest.isel 比较，
     * 避免明文字符串比较引入的时序侧信道。
     */
    private boolean constantTimeTokenCompare(String presented, String expected) {
        if (presented == null || expected == null) return false;
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] presentedHash = md.digest(presented.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] expectedHash = md.digest(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return MessageDigest.isEqual(presentedHash, expectedHash);
        } catch (Exception e) {
            return false;
        }
    }
}
