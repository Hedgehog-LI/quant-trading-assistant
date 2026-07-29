package com.quant.trade.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 只读助手配置。默认关闭。
 * <p>
 * Token 只从环境变量注入，不写入 Git/文档/日志/DB。
 */
@ConfigurationProperties(prefix = "qta.agent")
public class AgentProperties {

    /** 是否启用 Agent API。默认关闭。 */
    private boolean enabled = false;

    /** Agent Bearer Token（从环境变量 QTA_AGENT_TOKEN 注入）。 */
    private String token = "";

    /** 每分钟每 Client 限流。默认 60。 */
    private int rateLimitPerMinute = 60;

    /** QQ OpenID 白名单（逗号分隔）。为空表示不允许任何 QQ 用户。 */
    private String allowedOpenIds = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    public String getAllowedOpenIds() { return allowedOpenIds; }
    public void setAllowedOpenIds(String allowedOpenIds) { this.allowedOpenIds = allowedOpenIds; }

    /** 校验 Token 强度：启用时不能为空且至少 32 字符。 */
    public boolean isTokenValid() {
        return token != null && token.length() >= 32;
    }
}
