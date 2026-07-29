package com.quant.trade.agent.config;

import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc OpenAPI 配置 — 独立 agent 分组。
 * <p>
 * 只包含 /api/v1/agent/** 路径，不暴露其他 API。
 * Swagger UI 默认关闭。
 */
@Configuration
public class AgentOpenApiConfig {

    @Bean
    public GroupedOpenApi agentGroup() {
        return GroupedOpenApi.builder()
            .group("agent")
            .pathsToMatch("/api/v1/agent/**")
            .addOpenApiCustomizer(openApi -> {
                openApi.getComponents().addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("Token")
                        .description("Agent Bearer Token (from QTA_AGENT_TOKEN env)"));
            })
            .build();
    }
}
