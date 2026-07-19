package com.quant.trade.marketdata.config;

import com.quant.trade.marketdata.provider.LongPortMarketDataProvider;
import com.quant.trade.marketdata.provider.LongPortSymbolMapper;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.LongPortMarketSectorProvider;
import com.quant.trade.marketdata.provider.MarketSectorProvider;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient;
import com.quant.trade.marketdata.provider.longport.LongPortIndustryHttpClient;
import com.quant.trade.marketdata.provider.longport.LongPortSectorClient;
import com.quant.trade.marketdata.provider.longport.ReflectiveLongPortQuoteClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 行情数据模块配置。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(LongPortProperties.class)
public class MarketDataConfig {

    /** 行情调度统一使用上海时区；测试可覆盖为固定 Clock。 */
    @Bean
    public java.time.Clock marketDataClock() {
        return java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai"));
    }

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

    /** Longbridge 行业排行与层级签名 HTTPS client。 */
    @Bean
    @ConditionalOnProperty(prefix = "qta.market-data.longport", name = "enabled", havingValue = "true")
    public LongPortSectorClient longPortSectorClient(LongPortProperties properties, ObjectMapper objectMapper) {
        return new LongPortIndustryHttpClient(properties, objectMapper);
    }

    /** LongPort 行业排行与行业层级 provider。 */
    @Bean
    @ConditionalOnProperty(prefix = "qta.market-data.longport", name = "enabled", havingValue = "true")
    public MarketSectorProvider longPortMarketSectorProvider(LongPortProperties properties,
                                                              LongPortSectorClient sectorClient) {
        return new LongPortMarketSectorProvider(properties, sectorClient);
    }
}
