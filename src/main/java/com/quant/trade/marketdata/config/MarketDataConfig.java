package com.quant.trade.marketdata.config;

import com.quant.trade.marketdata.provider.LongPortMarketDataProvider;
import com.quant.trade.marketdata.provider.LongPortSymbolMapper;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient;
import com.quant.trade.marketdata.provider.longport.ReflectiveLongPortQuoteClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 行情数据模块配置。 */
@Configuration
@EnableConfigurationProperties(LongPortProperties.class)
public class MarketDataConfig {

    /** 独立新事务模板，用于 sync task / alert 留痕（保证失败时仍可查）。 */
    @Bean("txRequiresNew")
    public TransactionTemplate txRequiresNew(PlatformTransactionManager txManager) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /** LongPort Java SDK 反射 client。 */
    @Bean
    @ConditionalOnProperty(prefix = "qta.market-data.longport", name = "enabled", havingValue = "true")
    public LongPortQuoteClient longPortQuoteClient(LongPortProperties properties) {
        return new ReflectiveLongPortQuoteClient(properties);
    }

    /** LongPort 只读行情 provider。 */
    @Bean
    @ConditionalOnProperty(prefix = "qta.market-data.longport", name = "enabled", havingValue = "true")
    public MarketDataProvider longPortMarketDataProvider(LongPortProperties properties,
                                                         LongPortSymbolMapper symbolMapper,
                                                         LongPortQuoteClient quoteClient) {
        return new LongPortMarketDataProvider(properties, symbolMapper, quoteClient);
    }
}
