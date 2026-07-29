package com.quant.trade.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Agent 模块配置入口。 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {
}
