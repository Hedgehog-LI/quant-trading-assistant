package com.quant.trade.marketdata;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.provider.MarketDataProvider.ProviderSecurityInfo;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichFields;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichReason;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichRequest;
import com.quant.trade.marketdata.provider.SecurityMetadataEnricher.EnrichResult;
import com.quant.trade.marketdata.provider.longport.LongPortSecurityMetadataEnricher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D3-03 H2 集成测试：真实 {@code LongPortMarketDataProvider} + test-classpath fake SDK
 * （{@code com.longport.quote.QuoteContext}）返回 Static Info（贵州茅台/100）。
 * <p>
 * 覆盖：persist=false 展示不落库、persist=true 原子条件更新（仅补空字段）、
 * 权威列（data_source/source_hash/source_updated_at）不被污染、幂等 NO_CHANGE、
 * 非法/缺失/校验错误、以及基于 stub bean 的并发写、行删除、身份不一致、provider 无数据、
 * 空白字段与鉴权失败等边界。
 */
@SpringBootTest(properties = {
        "qta.market-data.longport.enabled=true",
        "qta.market-data.longport.app-key=test-app-key",
        "qta.market-data.longport.app-secret=test-app-secret",
        "qta.market-data.longport.access-token=test-access-token"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityMetadataEnrichmentIntegrationTest {

    private static final String ENRICH_URL = "/api/v1/market-data/security-directory/enrich";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SecurityMetadataEnricher enricher;

    @BeforeEach
    void cleanDirectory() {
        jdbcTemplate.update("DELETE FROM stock_alias");
        jdbcTemplate.update("DELETE FROM stock_basic");
    }

    @AfterEach
    void cleanDirectoryAfterTest() {
        cleanDirectory();
    }

    @Test
    void configuredContextWiresLongPortEnricher() {
        assertTrue(enricher instanceof LongPortSecurityMetadataEnricher,
                "longport enabled 且凭据就绪时装配 LongPortSecurityMetadataEnricher");
        assertTrue(enricher.isEnabled(), "provider.isConfigured()=true（fake SDK 在 test classpath）");
    }

    // ==================== persist=false：展示 + 不落库 ====================

    @Test
    void persistFalseReturnsFieldsAndLotSizeAndLeavesDbUnchanged() throws Exception {
        seed("", null, "", null, null);
        String rowBefore = fullRowSnapshot();

        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.canonicalSymbol").value("SH.600519"))
                .andExpect(jsonPath("$.data.enriched").value(true))
                .andExpect(jsonPath("$.data.providerCode").value("LONGPORT"))
                .andExpect(jsonPath("$.data.fields.nameCn").value("贵州茅台"))
                .andExpect(jsonPath("$.data.fields.nameHk").value("貴州茅台"))
                .andExpect(jsonPath("$.data.fields.nameEn").value("Kweichow Moutai"))
                .andExpect(jsonPath("$.data.fields.exchange").value("SSE"))
                .andExpect(jsonPath("$.data.fields.currency").value("CNY"))
                .andExpect(jsonPath("$.data.lotSize").value(100))
                .andExpect(jsonPath("$.data.persisted").value(false))
                .andExpect(jsonPath("$.data.reason").value("OK"));

        // persist=false 数据库完全不变。
        assertEquals(rowBefore, fullRowSnapshot(), "persist=false 不修改 stock_basic 行");
    }

    @Test
    void persistFalseDefaultsWhenFieldOmitted() throws Exception {
        seed("", null, "", null, null);
        String rowBefore = fullRowSnapshot();

        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"SH.600519\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(false))
                .andExpect(jsonPath("$.data.lotSize").value(100));

        assertEquals(rowBefore, fullRowSnapshot());
    }

    // ==================== persist=true：原子条件更新 ====================

    @Test
    void persistTrueFillsEmptyFieldsAndKeepsAuthorityColumns() throws Exception {
        seed("", null, "", null, null);

        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enriched").value(true))
                .andExpect(jsonPath("$.data.persisted").value(true))
                .andExpect(jsonPath("$.data.reason").value("OK"));

        // 空字段被填充。
        assertEquals("贵州茅台", column("name_cn"));
        assertEquals("貴州茅台", column("name_hk"));
        assertEquals("Kweichow Moutai", column("name_en"));
        assertEquals("SSE", column("exchange"));
        assertEquals("CNY", column("currency"));

        // 权威列/来源新鲜度列绝不被补全修改（source_updated_at/data_source/source_hash 恒等）。
        assertEquals("AUTHORITY", column("data_source"), "data_source 不被污染");
        assertEquals("hash-1", column("source_hash"), "source_hash 不被污染");
        assertEquals(LocalDateTime.parse("2020-01-01T00:00:00"), readSourceUpdatedAt(),
                "source_updated_at 不被 LongPort 补全污染");
    }

    @Test
    void localNonEmptyFieldsAreNeverOverwritten() throws Exception {
        // 全部 5 个字段本地已有非空值，且与 provider 返回不同。
        seed("旧中文名", "舊港文名", "Old English", "SSE", "CNY");

        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enriched").value(true))
                .andExpect(jsonPath("$.data.persisted").value(false))
                .andExpect(jsonPath("$.data.reason").value("NO_CHANGE"));

        assertEquals("旧中文名", column("name_cn"), "非空中文名不被覆盖");
        assertEquals("舊港文名", column("name_hk"), "非空港文名不被覆盖");
        assertEquals("Old English", column("name_en"), "非空英文名不被覆盖");
        assertEquals("SSE", column("exchange"));
        assertEquals("CNY", column("currency"));
    }

    @Test
    void persistTrueFillsOnlyEmptyFieldsAndPreservesNonEmpty() throws Exception {
        // name_cn/name_en/exchange 非空，name_hk/currency 为空 → 只补后两者。
        seed("旧中文名", null, "Old English", "SSE", null);

        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true))
                .andExpect(jsonPath("$.data.reason").value("OK"));

        assertEquals("旧中文名", column("name_cn"), "非空字段保留本地值");
        assertEquals("貴州茅台", column("name_hk"), "空字段被填充");
        assertEquals("Old English", column("name_en"), "非空字段保留本地值");
        assertEquals("SSE", column("exchange"), "非空字段保留本地值");
        assertEquals("CNY", column("currency"), "空字段被填充");
    }

    @Test
    void secondEnrichIsIdempotentReturnsNoChange() throws Exception {
        seed("", null, "", null, null);

        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true))
                .andExpect(jsonPath("$.data.reason").value("OK"));

        String rowSnapshot = fullRowSnapshot();

        // 第二次相同结果：所有字段已非空 → 无可补字段 → NO_CHANGE，行字节稳定。
        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enriched").value(true))
                .andExpect(jsonPath("$.data.persisted").value(false))
                .andExpect(jsonPath("$.data.reason").value("NO_CHANGE"));

        assertEquals(rowSnapshot, fullRowSnapshot(), "幂等：数据库行不变");
    }

    // ==================== 校验与错误语义 ====================

    @Test
    void localMissingReturns404() throws Exception {
        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"SH.999999\",\"persist\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    void invalidCanonicalReturns400() throws Exception {
        seed("", null, "", null, null);

        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"BAD.CANONICAL\",\"persist\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_CANONICAL_SYMBOL"));
    }

    @Test
    void blankSymbolReturns400ValidationError() throws Exception {
        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"persist\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void overlongSymbolReturns400ValidationError() throws Exception {
        String overlong = "US." + "A".repeat(33); // 36 字符 > @Size(max=32)
        mockMvc.perform(post(ENRICH_URL)
                        .contentType("application/json")
                        .content("{\"canonicalSymbol\":\"" + overlong + "\",\"persist\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ==================== helpers ====================

    /** 插入 SH.600519 行；name_cn/name_hk/name_en/exchange/currency 由参数控制（null=空）。 */
    private void seed(String nameCn, String nameHk, String nameEn, String exchange, String currency) {
        jdbcTemplate.update("""
                INSERT INTO stock_basic (
                    id, canonical_symbol, symbol, name, market,
                    name_cn, name_hk, name_en, short_name, pinyin_full, pinyin_abbr,
                    exchange, currency, security_type, list_status, data_source, source_updated_at,
                    source_hash, list_date, delisted
                ) VALUES (
                    1, 'SH.600519', '600519', '贵州茅台酒', 'SH',
                    ?, ?, ?, '茅台', 'cn-pinyin-full', 'cpf',
                    ?, ?, 'STOCK', 'LISTED', 'AUTHORITY', '2020-01-01 00:00:00',
                    'hash-1', '2001-08-27', FALSE
                )
                """, nameCn, nameHk, nameEn, exchange, currency);
    }

    private Object column(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT " + name + " FROM stock_basic WHERE canonical_symbol = 'SH.600519'",
                Object.class);
    }

    private LocalDateTime readSourceUpdatedAt() {
        return jdbcTemplate.queryForObject(
                "SELECT source_updated_at FROM stock_basic WHERE canonical_symbol = 'SH.600519'",
                LocalDateTime.class);
    }

    private String fullRowSnapshot() {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM stock_basic WHERE canonical_symbol = 'SH.600519'",
                (rs, rowNum) -> {
                    StringBuilder sb = new StringBuilder();
                    int cols = rs.getMetaData().getColumnCount();
                    for (int i = 1; i <= cols; i++) {
                        sb.append(rs.getMetaData().getColumnName(i)).append('=')
                                .append(rs.getString(i)).append('|');
                    }
                    return sb.toString();
                });
    }

    // ==================== stub 边界场景（独立 Spring 上下文） ====================

    /** provider 返回空白字符串：规范化后视为 null，不写数据库，返回 NO_CHANGE。 */
    @SpringBootTest(properties = {
            "qta.market-data.longport.enabled=true",
            "qta.market-data.longport.app-key=test-app-key",
            "qta.market-data.longport.app-secret=test-app-secret",
            "qta.market-data.longport.access-token=test-access-token"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    static class ProviderBlankFieldsTest {
        @Autowired private MockMvc mockMvc;
        @Autowired private JdbcTemplate jdbcTemplate;

        @TestConfiguration
        static class BlankProviderOverride {
            @Bean
            @Primary
            MarketDataProvider blankFieldsProvider() {
                return new StubProvider(new ProviderSecurityInfo(
                        "SH.600519", "600519.SH", "  ", "", null, " ", "", 0));
            }
        }

        @BeforeEach
        void seedAndClean() {
            jdbcTemplate.update("DELETE FROM stock_alias");
            jdbcTemplate.update("DELETE FROM stock_basic");
            jdbcTemplate.update("""
                    INSERT INTO stock_basic (
                        canonical_symbol, symbol, name, market, exchange, currency, security_type,
                        list_status, data_source, source_updated_at
                    ) VALUES ('SH.600519', '600519', '贵州茅台酒', 'SH', '', '', 'STOCK',
                              'LISTED', 'AUTHORITY', '2020-01-01 00:00:00')
                    """);
        }

        @Test
        void blankFieldsNeverWritten() throws Exception {
            mockMvc.perform(post(ENRICH_URL)
                            .contentType("application/json")
                            .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.persisted").value(false))
                    .andExpect(jsonPath("$.data.reason").value("NO_CHANGE"));

            assertEquals("", jdbcTemplate.queryForObject(
                    "SELECT name_cn FROM stock_basic WHERE canonical_symbol='SH.600519'", String.class),
                    "空白字段不写入数据库");
        }
    }

    /** provider 返回证券身份与请求不一致：拒绝处理 → 400 SECURITY_VERIFICATION_FAILED。 */
    @SpringBootTest(properties = {
            "qta.market-data.longport.enabled=true",
            "qta.market-data.longport.app-key=test-app-key",
            "qta.market-data.longport.app-secret=test-app-secret",
            "qta.market-data.longport.access-token=test-access-token"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    static class IdentityMismatchTest {
        @Autowired private MockMvc mockMvc;
        @Autowired private JdbcTemplate jdbcTemplate;

        @TestConfiguration
        static class MismatchProviderOverride {
            @Bean
            @Primary
            MarketDataProvider mismatchProvider() {
                // provider 返回的是另一只证券（HK.00700）的静态信息。
                return new StubProvider(new ProviderSecurityInfo(
                        "HK.00700", "700.HK", "腾讯控股", "騰訊控股", "Tencent",
                        "SEHK", "HKD", 100));
            }
        }

        @BeforeEach
        void seed() {
            jdbcTemplate.update("DELETE FROM stock_alias");
            jdbcTemplate.update("DELETE FROM stock_basic");
            jdbcTemplate.update("""
                    INSERT INTO stock_basic (
                        canonical_symbol, symbol, name, market, exchange, currency, security_type,
                        list_status, data_source, source_updated_at
                    ) VALUES ('SH.600519', '600519', '贵州茅台酒', 'SH', '', '', 'STOCK',
                              'LISTED', 'AUTHORITY', '2020-01-01 00:00:00')
                    """);
        }

        @Test
        void identityMismatchRejected() throws Exception {
            mockMvc.perform(post(ENRICH_URL)
                            .contentType("application/json")
                            .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("SECURITY_VERIFICATION_FAILED"));
            // 数据库不被写入其他证券的静态信息。
            assertEquals("", jdbcTemplate.queryForObject(
                    "SELECT name_cn FROM stock_basic WHERE canonical_symbol='SH.600519'", String.class));
        }
    }

    /** provider 返回 null（未找到该证券）：200、enriched=false、persisted=false、reason=PROVIDER_NOT_FOUND，不落库。 */
    @SpringBootTest(properties = {
            "qta.market-data.longport.enabled=true",
            "qta.market-data.longport.app-key=test-app-key",
            "qta.market-data.longport.app-secret=test-app-secret",
            "qta.market-data.longport.access-token=test-access-token"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    static class ProviderNotFoundTest {
        @Autowired private MockMvc mockMvc;
        @Autowired private JdbcTemplate jdbcTemplate;

        @TestConfiguration
        static class NullProviderOverride {
            @Bean
            @Primary
            MarketDataProvider nullProvider() {
                return new StubProvider(null);
            }
        }

        @BeforeEach
        void seed() {
            jdbcTemplate.update("DELETE FROM stock_alias");
            jdbcTemplate.update("DELETE FROM stock_basic");
            jdbcTemplate.update("""
                    INSERT INTO stock_basic (
                        canonical_symbol, symbol, name, market, exchange, currency, security_type,
                        list_status, data_source, source_updated_at
                    ) VALUES ('SH.600519', '600519', '贵州茅台酒', 'SH', '', '', 'STOCK',
                              'LISTED', 'AUTHORITY', '2020-01-01 00:00:00')
                    """);
        }

        @Test
        void providerNotFoundReturns200WithExplicitReason() throws Exception {
            mockMvc.perform(post(ENRICH_URL)
                            .contentType("application/json")
                            .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enriched").value(false))
                    .andExpect(jsonPath("$.data.persisted").value(false))
                    .andExpect(jsonPath("$.data.reason").value("PROVIDER_NOT_FOUND"));

            assertEquals("", jdbcTemplate.queryForObject(
                    "SELECT name_cn FROM stock_basic WHERE canonical_symbol='SH.600519'", String.class),
                    "provider 无数据不落库");
        }
    }

    /** 模拟 LongPort 调用期间目录同步先写入字段：补全不得覆盖并发写入的非空值。 */
    @SpringBootTest(properties = {
            "qta.market-data.longport.enabled=true",
            "qta.market-data.longport.app-key=test-app-key",
            "qta.market-data.longport.app-secret=test-app-secret",
            "qta.market-data.longport.access-token=test-access-token"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    static class ConcurrentSyncWriteTest {
        @Autowired private MockMvc mockMvc;
        @Autowired private JdbcTemplate jdbcTemplate;

        @TestConfiguration
        static class ConcurrentEnricherOverride {
            @Bean
            @Primary
            SecurityMetadataEnricher concurrentEnricher(JdbcTemplate jdbcTemplate) {
                return new SecurityMetadataEnricher() {
                    @Override
                    public boolean isEnabled() {
                        return true;
                    }

                    @Override
                    public String getProviderCode() {
                        return "LONGPORT";
                    }

                    @Override
                    public EnrichResult enrich(EnrichRequest request) {
                        // 模拟目录同步在 LongPort 网络调用期间写入了 name_cn 非空值。
                        jdbcTemplate.update(
                                "UPDATE stock_basic SET name_cn = '并发写入' WHERE canonical_symbol = ?",
                                request.canonicalSymbol());
                        EnrichFields fields = new EnrichFields(
                                "贵州茅台", "貴州茅台", "Kweichow Moutai", "SSE", "CNY");
                        return new EnrichResult(request.canonicalSymbol(), true, "LONGPORT",
                                fields, 100, EnrichReason.OK);
                    }
                };
            }
        }

        @BeforeEach
        void seed() {
            jdbcTemplate.update("DELETE FROM stock_alias");
            jdbcTemplate.update("DELETE FROM stock_basic");
            jdbcTemplate.update("""
                    INSERT INTO stock_basic (
                        canonical_symbol, symbol, name, market, exchange, currency, security_type,
                        list_status, data_source, source_updated_at
                    ) VALUES ('SH.600519', '600519', '贵州茅台酒', 'SH', '', '', 'STOCK',
                              'LISTED', 'AUTHORITY', '2020-01-01 00:00:00')
                    """);
        }

        @Test
        void concurrentDirectorySyncValueNotOverwritten() throws Exception {
            mockMvc.perform(post(ENRICH_URL)
                            .contentType("application/json")
                            .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.persisted").value(true))
                    .andExpect(jsonPath("$.data.reason").value("OK"));

            assertEquals("并发写入", jdbcTemplate.queryForObject(
                    "SELECT name_cn FROM stock_basic WHERE canonical_symbol='SH.600519'", String.class),
                    "并发目录同步写入的非空 name_cn 不被补全覆盖");
            assertEquals("貴州茅台", jdbcTemplate.queryForObject(
                    "SELECT name_hk FROM stock_basic WHERE canonical_symbol='SH.600519'", String.class),
                    "其余空字段正常补全");
        }
    }

    /** 行在 LongPort 调用期间被并发删除：返回 STOCK_NOT_FOUND 404，而不是 persisted=true。 */
    @SpringBootTest(properties = {
            "qta.market-data.longport.enabled=true",
            "qta.market-data.longport.app-key=test-app-key",
            "qta.market-data.longport.app-secret=test-app-secret",
            "qta.market-data.longport.access-token=test-access-token"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    static class RowDeletedDuringCallTest {
        @Autowired private MockMvc mockMvc;
        @Autowired private JdbcTemplate jdbcTemplate;

        @TestConfiguration
        static class DeletingEnricherOverride {
            @Bean
            @Primary
            SecurityMetadataEnricher deletingEnricher(JdbcTemplate jdbcTemplate) {
                return new SecurityMetadataEnricher() {
                    @Override
                    public boolean isEnabled() {
                        return true;
                    }

                    @Override
                    public String getProviderCode() {
                        return "LONGPORT";
                    }

                    @Override
                    public EnrichResult enrich(EnrichRequest request) {
                        // 模拟 LongPort 调用期间该行被删除。
                        jdbcTemplate.update(
                                "DELETE FROM stock_basic WHERE canonical_symbol = ?",
                                request.canonicalSymbol());
                        EnrichFields fields = new EnrichFields(
                                "贵州茅台", "貴州茅台", "Kweichow Moutai", "SSE", "CNY");
                        return new EnrichResult(request.canonicalSymbol(), true, "LONGPORT",
                                fields, 100, EnrichReason.OK);
                    }
                };
            }
        }

        @BeforeEach
        void seed() {
            jdbcTemplate.update("DELETE FROM stock_alias");
            jdbcTemplate.update("DELETE FROM stock_basic");
            jdbcTemplate.update("""
                    INSERT INTO stock_basic (
                        canonical_symbol, symbol, name, market, exchange, currency, security_type,
                        list_status, data_source, source_updated_at
                    ) VALUES ('SH.600519', '600519', '贵州茅台酒', 'SH', '', '', 'STOCK',
                              'LISTED', 'AUTHORITY', '2020-01-01 00:00:00')
                    """);
        }

        @Test
        void rowDeletedDuringCallReturns404() throws Exception {
            mockMvc.perform(post(ENRICH_URL)
                            .contentType("application/json")
                            .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":true}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
        }
    }

    /** 鉴权失败：透传具体错误码 400，响应不泄露凭据。 */
    @SpringBootTest(properties = {
            "qta.market-data.longport.enabled=true",
            "qta.market-data.longport.app-key=secret-app-key",
            "qta.market-data.longport.app-secret=secret-app-secret",
            "qta.market-data.longport.access-token=secret-access-token"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    static class AuthFailureStubTest {
        @Autowired private MockMvc mockMvc;
        @Autowired private JdbcTemplate jdbcTemplate;

        @TestConfiguration
        static class AuthFailureEnricherOverride {
            @Bean
            @Primary
            SecurityMetadataEnricher authFailureEnricher() {
                return new SecurityMetadataEnricher() {
                    @Override
                    public boolean isEnabled() {
                        return true;
                    }

                    @Override
                    public String getProviderCode() {
                        return "LONGPORT";
                    }

                    @Override
                    public EnrichResult enrich(EnrichRequest request) {
                        throw new BusinessException(
                                ErrorCodeEnum.MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED,
                                "Longbridge 鉴权失败，请检查凭据状态");
                    }
                };
            }
        }

        @BeforeEach
        void seed() {
            jdbcTemplate.update("DELETE FROM stock_alias");
            jdbcTemplate.update("DELETE FROM stock_basic");
            jdbcTemplate.update("""
                    INSERT INTO stock_basic (
                        canonical_symbol, symbol, name, market, exchange, currency, security_type,
                        list_status, data_source, source_updated_at
                    ) VALUES ('SH.600519', '600519', '贵州茅台酒', 'SH', '', '', 'STOCK',
                              'LISTED', 'AUTHORITY', '2020-01-01 00:00:00')
                    """);
        }

        @Test
        void authFailureReturnsSpecificErrorAndLeaksNoCredentials() throws Exception {
            String body = mockMvc.perform(post(ENRICH_URL)
                            .contentType("application/json")
                            .content("{\"canonicalSymbol\":\"SH.600519\",\"persist\":false}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("MARKET_DATA_PROVIDER_AUTHENTICATION_FAILED"))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("secret-app-key"), "响应不泄露 app-key");
            assertFalse(body.contains("secret-app-secret"), "响应不泄露 app-secret");
            assertFalse(body.contains("secret-access-token"), "响应不泄露 access-token");
        }
    }

    /** 最小 stub provider：仅 Static Info 可控，其余只读能力返回空。 */
    private static class StubProvider implements MarketDataProvider {
        private final ProviderSecurityInfo info;

        StubProvider(ProviderSecurityInfo info) {
            this.info = info;
        }

        @Override
        public String getProviderCode() {
            return "LONGPORT";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public ProviderSecurityInfo getSecurityStaticInfo(String canonicalSymbol) {
            return info;
        }

        @Override
        public com.quant.trade.marketdata.provider.MarketDataProvider.ProviderHealthStatus healthCheck() {
            return null;
        }

        @Override
        public List<com.quant.trade.marketdata.provider.MarketDataProvider.ProviderQuote> getLatestQuotes(
                List<String> canonicalSymbols) {
            return List.of();
        }

        @Override
        public List<com.quant.trade.marketdata.provider.MarketDataProvider.ProviderDailyBar> getDailyBars(
                String canonicalSymbol, java.time.LocalDate startDate, java.time.LocalDate endDate,
                String adjustType) {
            return List.of();
        }

        @Override
        public List<com.quant.trade.marketdata.provider.MarketDataProvider.ProviderMinuteBar> getMinuteBars(
                String canonicalSymbol, java.time.LocalDate startDate, java.time.LocalDate endDate,
                String intervalType, String adjustType) {
            return List.of();
        }
    }
}
