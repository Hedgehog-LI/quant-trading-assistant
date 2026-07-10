package com.quant.trade.marketdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 行情数据模块配置。 */
@Configuration
public class MarketDataConfig {

    /** 独立新事务模板，用于 sync task / alert 留痕（保证失败时仍可查）。 */
    @Bean("txRequiresNew")
    public TransactionTemplate txRequiresNew(PlatformTransactionManager txManager) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
