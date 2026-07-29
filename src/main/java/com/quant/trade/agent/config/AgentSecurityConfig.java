package com.quant.trade.agent.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.UUID;

import com.quant.trade.agent.security.AgentTokenAuthFilter;
import com.quant.trade.agent.security.AgentRateLimitFilter;
import com.quant.trade.agent.security.AgentAuditFilter;

/**
 * Spring Security 配置。
 * <p>
 * 只保护 /api/v1/agent/** 和 /v3/api-docs/agent 路径。
 * 现有前端普通 API 保持无认证兼容。
 * <p>
 * Filter 顺序（外→内）：
 * <pre>
 *   AgentAuditFilter (最外，覆盖整个链路，单一审计入口)
 *     → AgentTokenAuthFilter (Bearer token 认证)
 *       → AgentRateLimitFilter (限流)
 *         → Spring Security (authorize + 401 entry point)
 *           → DispatcherServlet / Controller
 * </pre>
 * AgentAuditFilter 生成 requestId 并写入 request 属性与 X-Request-ID 响应头，
 * 下游所有 Filter / EntryPoint / Controller 复用同一 requestId。
 */
@Configuration
@EnableWebSecurity
public class AgentSecurityConfig {

    private final AgentProperties properties;

    public AgentSecurityConfig(AgentProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AgentTokenAuthFilter tokenFilter,
                                           AgentRateLimitFilter rateLimitFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Agent 路径需要认证
                .requestMatchers("/api/v1/agent/**").authenticated()
                .requestMatchers("/v3/api-docs/agent").authenticated()
                // 其他所有路径允许访问（保持现有 API 兼容）
                .anyRequest().permitAll()
            )
            // Custom 401 entry point for unauthenticated Agent requests.
            // Reuse requestId from AgentAuditFilter when present; fall back to a fresh one
            // only if the audit filter somehow didn't run (defensive).
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
                String requestId = resolveRequestId(request);
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader("X-Request-ID", requestId);
                response.getWriter().write("{\"success\":false,\"error\":\"Authentication required\",\"requestId\":\"" + requestId + "\"}");
            }))
            // Security-chain ordering: tokenFilter → rateLimitFilter → Spring Security.
            // (The outermost AgentAuditFilter is registered separately as a servlet
            // FilterRegistrationBean so its finally wraps every outcome, including
            // token/rate-limit short-circuits — see agentAuditFilterRegistration.)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(tokenFilter, AgentRateLimitFilter.class);

        return http.build();
    }

    /**
     * Register AgentAuditFilter as the OUTERMOST servlet filter (runs before the
     * Spring Security filter chain). This guarantees its finally-block executes
     * regardless of whether the request is later short-circuited by token/rate-limit
     * filters, rejected by Spring Security's entry point, or handled by the controller.
     */
    @Bean
    public FilterRegistrationBean<AgentAuditFilter> agentAuditFilterRegistration(AgentAuditFilter auditFilter) {
        FilterRegistrationBean<AgentAuditFilter> reg = new FilterRegistrationBean<>(auditFilter);
        reg.setFilter(auditFilter);
        reg.addUrlPatterns("/api/v1/agent/*", "/v3/api-docs/agent");
        // Run before Spring Security's default order (SecurityProperties.DEFAULT_FILTER_ORDER = -100).
        reg.setOrder(org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER - 1);
        reg.setName("agentAuditFilter");
        return reg;
    }

    /** Reuse the audit filter's requestId; never invent a second one for the same request. */
    private String resolveRequestId(jakarta.servlet.http.HttpServletRequest request) {
        Object attr = request.getAttribute("agentRequestId");
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Disable servlet-level auto-registration for token/rate-limit filters so they
     * live ONLY in the Spring Security filter chain. (AgentAuditFilter is the
     * exception: it IS registered as a servlet filter via
     * {@link #agentAuditFilterRegistration} to be the outermost wrapper.)
     */
    @Bean
    public FilterRegistrationBean<AgentTokenAuthFilter> tokenFilterRegistrationDisable(AgentTokenAuthFilter filter) {
        FilterRegistrationBean<AgentTokenAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<AgentRateLimitFilter> rateLimitFilterRegistrationDisable(AgentRateLimitFilter filter) {
        FilterRegistrationBean<AgentRateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /** Bean for AgentAuditFilter (not @Component-annotated). */
    @Bean
    public AgentAuditFilter agentAuditFilter(com.quant.trade.agent.service.AgentAuditService auditService) {
        return new AgentAuditFilter(auditService);
    }
}
