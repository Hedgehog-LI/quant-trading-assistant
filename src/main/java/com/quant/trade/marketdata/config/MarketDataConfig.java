package com.quant.trade.marketdata.config;

import com.quant.trade.marketdata.provider.LongPortMarketDataProvider;
import com.quant.trade.marketdata.provider.LongPortSymbolMapper;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.LongPortMarketSectorProvider;
import com.quant.trade.marketdata.provider.MarketSectorProvider;
import com.quant.trade.marketdata.provider.SecurityDirectoryProvider;
import com.quant.trade.marketdata.provider.DisabledSecurityDirectoryProvider;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher;
import com.quant.trade.marketdata.provider.DisabledSecurityMetadataEnricher;
import com.quant.trade.marketdata.provider.longport.LongPortSecurityMetadataEnricher;
import com.quant.trade.marketdata.provider.csv.CsvSnapshotSecurityDirectoryProvider;
import com.quant.trade.marketdata.provider.csv.SecurityDirectoryCsvParser;
import com.quant.trade.marketdata.provider.longport.LongPortQuoteClient;
import com.quant.trade.marketdata.provider.longport.LongPortIndustryHttpClient;
import com.quant.trade.marketdata.provider.longport.LongPortSectorClient;
import com.quant.trade.marketdata.provider.longport.ReflectiveLongPortQuoteClient;
import com.quant.trade.marketdata.service.SecurityDirectorySyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

/** 行情数据模块配置。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({LongPortProperties.class, SecurityDirectoryProperties.class})
public class MarketDataConfig {

    /** 目录导入允许 50 MiB；业务服务仍负责精确文件上限和稳定错误码。 */
    @Bean
    public MultipartConfigElement marketDataMultipartConfig() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(50));
        factory.setMaxRequestSize(DataSize.ofMegabytes(51));
        return factory.createMultipartConfig();
    }

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

    /**
     * LongPort 元数据补全 enricher（D3-03）。沿用既有 longport 装配条件：enabled 时注入；
     * provider 未配置凭据时 {@code isEnabled()} 返回 false，但 bean 仍存在，便于调用方区分
     * 「未启用」与「未配置」。禁用时不装配本 bean，由 disabled 兜底接管。
     */
    @Bean
    @ConditionalOnProperty(prefix = "qta.market-data.longport", name = "enabled", havingValue = "true")
    public SecurityMetadataEnricher longPortSecurityMetadataEnricher(MarketDataProvider provider) {
        return new LongPortSecurityMetadataEnricher(provider);
    }

    /**
     * 元数据补全 disabled 兜底 bean（D3-03）。longport 未启用时保证应用可启动、D1 搜索/导入
     * 不受影响；调用 enrich 端点时经 GlobalExceptionHandler 呈现为 400 BUSINESS_RULE_VIOLATION。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(SecurityMetadataEnricher.class)
    public SecurityMetadataEnricher disabledSecurityMetadataEnricher() {
        return new DisabledSecurityMetadataEnricher();
    }

    /** 证券目录 CSV 解析器（D3 路径 P2，复用 D1 冻结口径）。 */
    @Bean
    public SecurityDirectoryCsvParser securityDirectoryCsvParser() {
        return new SecurityDirectoryCsvParser();
    }

    /** 证券目录同步 provider：enabled 时装配 CSV 快照 provider，否则 disabled 兜底。 */
    @Bean
    @ConditionalOnProperty(prefix = "qta.market-data.security-directory", name = "enabled",
            havingValue = "true")
    public SecurityDirectoryProvider csvSecurityDirectoryProvider(SecurityDirectoryProperties properties,
                                                                  SecurityDirectoryCsvParser csvParser) {
        Path path = properties.getSnapshotPath() == null || properties.getSnapshotPath().isBlank()
                ? null : Path.of(properties.getSnapshotPath());
        return new CsvSnapshotSecurityDirectoryProvider(true, properties.getProviderCode(), path, csvParser);
    }

    /** provider disabled 时的兜底 bean，保证应用可启动且 D1 本地搜索可用。 */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(SecurityDirectoryProvider.class)
    public SecurityDirectoryProvider disabledSecurityDirectoryProvider() {
        return new DisabledSecurityDirectoryProvider();
    }

    /** 证券目录同步服务。注入当前 provider（CSV 或 disabled）与行数波动阈值。 */
    @Bean
    public SecurityDirectorySyncService securityDirectorySyncService(
            SecurityDirectoryProvider provider,
            com.quant.trade.marketdata.dao.SecurityDirectorySyncStateMapper syncStateMapper,
            com.quant.trade.marketdata.dao.MarketDataSyncTaskMapper taskMapper,
            com.quant.trade.marketdata.dao.SyncScopeLockMapper syncScopeLockMapper,
            com.quant.trade.marketdata.dao.StockBasicMapper stockBasicMapper,
            com.quant.trade.marketdata.dao.StockAliasMapper stockAliasMapper,
            ObjectMapper objectMapper,
            java.time.Clock marketDataClock,
            @Autowired @org.springframework.beans.factory.annotation.Qualifier("txRequiresNew")
                    TransactionTemplate txRequiresNew,
            SecurityDirectoryProperties properties) {
        return new SecurityDirectorySyncService(provider, syncStateMapper, taskMapper, syncScopeLockMapper,
                stockBasicMapper, stockAliasMapper, objectMapper, marketDataClock, txRequiresNew,
                properties.getRowCountSwingThreshold());
    }
}
