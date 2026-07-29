package com.quant.trade.marketdata;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.dao.StockAliasMapper;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.dto.CreateStockBasicDTO;
import com.quant.trade.marketdata.dto.UpdateStockBasicDTO;
import com.quant.trade.marketdata.enums.SecurityCatalogStatusEnum;
import com.quant.trade.marketdata.enums.SecurityMatchedByEnum;
import com.quant.trade.marketdata.exception.SecurityDirectoryImportException;
import com.quant.trade.marketdata.model.StockAliasDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.provider.MarketDataProvider;
import com.quant.trade.marketdata.service.SecurityDirectoryService;
import com.quant.trade.marketdata.service.StockDataService;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportResultVO;
import com.quant.trade.marketdata.vo.SecuritySearchItemVO;
import com.quant.trade.marketdata.vo.SecuritySearchResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
class SecurityDirectoryIntegrationTest {

    @Autowired
    private SecurityDirectoryService directoryService;
    @Autowired
    private StockDataService stockDataService;
    @Autowired
    private StockBasicMapper stockBasicMapper;
    @SpyBean
    private StockAliasMapper stockAliasMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockBean
    private MarketDataProvider marketDataProvider;

    @BeforeEach
    void cleanDirectory() {
        Mockito.reset(stockAliasMapper, marketDataProvider);
        jdbcTemplate.update("DELETE FROM stock_alias");
        jdbcTemplate.update("DELETE FROM stock_daily_bar");
        jdbcTemplate.update("DELETE FROM stock_basic");
    }

    @Test
    void bomQuotedMultilineImportIsIdempotentAndNameChangeCreatesFormerName() {
        String csv = "\uFEFF" + header() + """
                SH.603308,"应流,
                股份",SH,SSE,CNY,STOCK,LISTED,TEST,2026-07-01T00:00:00+08:00,应流股份,,,,yingliugufen,ylgf,2011-01-13,h1,"SHORT_NAME:zh:应流|ENGLISH:en:Yingliu"
                """;
        SecurityDirectoryImportResultVO first = importCsv(csv);
        assertEquals(1, first.totalRows());
        assertEquals(1, first.inserted());
        assertEquals(2, first.aliasesInserted());
        assertEquals(0, first.failed());

        SecurityDirectoryImportResultVO repeat = importCsv(csv);
        assertEquals(1, repeat.unchanged());
        assertEquals(2, repeat.aliasesUnchanged());
        assertEquals(0, repeat.aliasesInserted());

        String renamed = csv.replace("\"应流,\n股份\"", "应流科技");
        SecurityDirectoryImportResultVO changed = importCsv(renamed);
        assertEquals(1, changed.updated());
        assertEquals(1, changed.formerNamesAdded());
        assertEquals(1, changed.aliasesInserted());
        assertEquals(2, changed.aliasesUnchanged());
        assertEquals(3, stockAliasMapper.selectByStockBasicId(
                stockBasicMapper.selectByCanonicalSymbol("SH.603308").getId()).size());
    }

    @Test
    void identicalDuplicateCountsUnchangedButConflictingDuplicateRejectsWholeFile() {
        String row = row("US.AAPL", "Apple Inc.", "US", "NASDAQ", "USD", "STOCK", "LISTED",
                "appleinc", "aapl", "ENGLISH:en:Apple");
        SecurityDirectoryImportResultVO duplicate = importCsv(header() + row + row);
        assertEquals(2, duplicate.totalRows());
        assertEquals(1, duplicate.inserted());
        assertEquals(1, duplicate.unchanged());

        String conflicting = header()
                + row("HK.02498", "速腾聚创", "HK", "HKEX", "HKD", "STOCK", "LISTED",
                "sutengjuchuang", "stjc", "")
                + row("HK.2498", "冲突名称", "HK", "HKEX", "HKD", "STOCK", "LISTED",
                "chongtu", "ct", "");
        SecurityDirectoryImportException exception = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(conflicting));
        assertEquals("CONFLICTING_DUPLICATE", exception.getResult().errors().get(0).reasonCode());
        assertNull(stockBasicMapper.selectByCanonicalSymbol("HK.02498"));
    }

    @Test
    void malformedUtf8AndSemanticFailureReturnBoundedStructuredEvidenceWithoutWrites() {
        byte[] malformed = {(byte) 0xC3, (byte) 0x28};
        SecurityDirectoryImportException utf8 = assertThrows(SecurityDirectoryImportException.class,
                () -> directoryService.importCsv(new ByteArrayInputStream(malformed), malformed.length));
        assertEquals("MALFORMED_UTF8", utf8.getResult().errors().get(0).reasonCode());

        String invalid = header() + row("SH.603308", "bad", "HK", "SSE", "CNY", "CRYPTO", "ACTIVE",
                "", "", "");
        SecurityDirectoryImportException semantic = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(invalid));
        assertTrue(semantic.getResult().errors().size() <= 50);
        assertTrue(semantic.getResult().errors().get(0).message().length() <= 240);
        assertEquals(0, stockBasicMapper.countAll());
    }

    @Test
    void duplicateAndUnknownHeadersHaveStableReasons() {
        String duplicate = "canonical_symbol,name,name,market,exchange,currency,security_type,"
                + "list_status,data_source,source_updated_at\n";
        SecurityDirectoryImportException duplicateError = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(duplicate));
        assertEquals("DUPLICATE_HEADER", duplicateError.getResult().errors().get(0).reasonCode());

        String unknown = "canonical_symbol,name,market,exchange,currency,security_type,"
                + "list_status,data_source,source_updated_at,secret_column\n";
        SecurityDirectoryImportException unknownError = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(unknown));
        assertEquals("UNKNOWN_HEADER", unknownError.getResult().errors().get(0).reasonCode());
    }

    @Test
    void lateAliasPersistenceFailureRollsBackStockAndEarlierAlias() {
        String csv = header() + row("US.MSFT", "Microsoft", "US", "NASDAQ", "USD", "STOCK", "LISTED",
                "microsoft", "msft", "ENGLISH:en:Microsoft|USER::MS");
        doThrow(new IllegalStateException("forced late failure"))
                .when(stockAliasMapper).insert(argThat(alias -> "ms".equals(alias.getNormalizedAlias())));

        SecurityDirectoryImportException exception = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(csv));

        assertEquals("PERSISTENCE_FAILED", exception.getResult().errors().get(0).reasonCode());
        assertNull(stockBasicMapper.selectByCanonicalSymbol("US.MSFT"));
        assertEquals(0, stockAliasMapper.countAll());
    }

    @Test
    void searchCoversChannelsRankingFiltersHkPaddingAndProviderIsolation() {
        List<Map<String, Object>> protectedBefore = protectedSnapshot();
        importCsv(header()
                + row("SH.603308", "应流股份", "SH", "SSE", "CNY", "STOCK", "LISTED",
                "yingliugufen", "ylgf", "FORMER_NAME:zh:应流科技")
                + row("HK.02498", "速腾聚创", "HK", "HKEX", "HKD", "STOCK", "LISTED",
                "sutengjuchuang", "stjc", "ENGLISH:en:RoboSense")
                + row("SZ.002498", "汉缆股份", "SZ", "SZSE", "CNY", "STOCK", "LISTED",
                "hanlangufen", "hlgf", "")
                + row("US.AAPL", "Apple Inc.", "US", "NASDAQ", "USD", "STOCK", "LISTED",
                "appleinc", "aapl", "ENGLISH:en:Apple")
                + row("SH.600001", "同名证券", "SH", "SSE", "CNY", "ETF", "UNKNOWN",
                "tongmingzhengquan", "tmzq", "")
                + row("US.SAME", "同名证券", "US", "NYSE", "USD", "STOCK", "LISTED",
                "tongmingzhengquan", "tmzq", "")
                + row("SH.600002", "退市样本", "SH", "SSE", "CNY", "STOCK", "DELISTED",
                "tuishiyangben", "tsyb", ""));
        assertMatch("SH.603308", "SH.603308", SecurityMatchedByEnum.CANONICAL_SYMBOL_EXACT);
        assertMatch("603308", "SH.603308", SecurityMatchedByEnum.RAW_SYMBOL_EXACT);
        assertMatch("应流股份", "SH.603308", SecurityMatchedByEnum.FORMAL_NAME_EXACT);
        assertMatch("应流", "SH.603308", SecurityMatchedByEnum.FORMAL_NAME_PREFIX);
        assertMatch("应流科技", "SH.603308", SecurityMatchedByEnum.ALIAS_EXACT);
        assertMatch("应流科", "SH.603308", SecurityMatchedByEnum.ALIAS_PREFIX);
        assertMatch("yingliu", "SH.603308", SecurityMatchedByEnum.PINYIN_FULL_PREFIX);
        assertMatch("ylg", "SH.603308", SecurityMatchedByEnum.PINYIN_ABBR_PREFIX);
        assertMatch("流股", "SH.603308", SecurityMatchedByEnum.NAME_CONTAINS);
        assertMatch("oboS", "HK.02498", SecurityMatchedByEnum.ALIAS_CONTAINS);

        assertEquals(List.of("HK.02498"), symbols(directoryService.search("2498", null, null, false, 20)));
        assertEquals(List.of("HK.02498"), symbols(directoryService.search("02498", null, null, false, 20)));
        assertEquals(List.of("SZ.002498"), symbols(directoryService.search("002498", null, null, false, 20)));
        assertEquals(List.of("US.SAME", "SH.600001"),
                symbols(directoryService.search("同名证券", List.of("US", "SH"), null, false, 20)));
        assertEquals(List.of("SH.600001"),
                symbols(directoryService.search("同名证券", List.of("SH"), List.of("ETF"), false, 20)));
        assertTrue(directoryService.search("退市", null, null, false, 20).items().isEmpty());
        assertEquals(List.of("SH.600002"),
                symbols(directoryService.search("退市", null, null, true, 20)));
        assertEquals(1, directoryService.search("应流", null, null, false, 20).items().size());
        assertTrue(directoryService.detail("SH.600002").delisted());
        assertEquals(protectedBefore, protectedSnapshot());
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    void catalogEmptyNoMatchAndFixedClockBoundariesAreDistinct() {
        SecuritySearchResultVO empty = directoryService.search("aa", null, null, false, 20);
        assertEquals(SecurityCatalogStatusEnum.EMPTY, empty.catalogStatus());
        assertNull(empty.catalogUpdatedAt());
        assertFalse(empty.stale());

        importCsv(header() + row("US.BOUND", "Boundary", "US", "NYSE", "USD", "STOCK", "LISTED",
                "boundary", "bound", ""));
        Instant updated = Instant.parse("2026-06-30T16:00:00Z");
        SecurityDirectoryService exactService = new SecurityDirectoryService(
                stockBasicMapper, stockAliasMapper, Clock.fixed(updated.plusSeconds(48 * 3600), ZoneOffset.UTC));
        SecuritySearchResultVO exact = exactService.search("zz", null, null, false, 20);
        assertEquals(SecurityCatalogStatusEnum.READY, exact.catalogStatus());
        assertTrue(exact.items().isEmpty());
        assertFalse(exact.stale());
        assertFalse(exact.degraded());

        SecurityDirectoryService staleService = new SecurityDirectoryService(
                stockBasicMapper, stockAliasMapper,
                Clock.fixed(updated.plusSeconds(48 * 3600).plusNanos(1), ZoneOffset.UTC));
        assertTrue(staleService.search("zz", null, null, false, 20).stale());
        assertNotNull(directoryService.detail("us.bound"));
        assertThrows(BusinessException.class, () -> directoryService.detail("US.MISSING"));
    }

    @Test
    void nonEmptyLegacyCatalogWithNullSourceTimeIsReadyAndStale() {
        stockDataService.createStock(new CreateStockBasicDTO("600011", "SH", "legacy-null-time", null, false));
        SecuritySearchResultVO result = directoryService.search("legacy", null, null, false, 20);
        assertEquals(SecurityCatalogStatusEnum.READY, result.catalogStatus());
        assertNull(result.catalogUpdatedAt());
        assertTrue(result.stale());
        assertFalse(result.degraded());
    }

    @Test
    void legacyCrudMapsLifecyclePreservesDirectoryFieldsAndCascadeDeletesAliases() {
        Long id = stockDataService.createStock(
                new CreateStockBasicDTO("600010", "SH", "legacy", null, false)).id();
        StockBasicDO directory = stockBasicMapper.selectById(id);
        assertEquals("UNKNOWN", directory.getListStatus());
        directory.setNameEn("Legacy Name");
        directory.setSecurityType("ETF");
        directory.setListStatus("LISTED");
        directory.setExchange("SSE");
        directory.setCurrency("CNY");
        directory.setDataSource("TEST");
        stockBasicMapper.updateDirectoryById(directory);
        stockAliasMapper.insert(StockAliasDO.builder().stockBasicId(id).alias("old")
                .normalizedAlias("old").aliasType("FORMER_NAME").dataSource("TEST").build());

        stockDataService.updateStock(id, new UpdateStockBasicDTO("legacy2", null, true));
        StockBasicDO delisted = stockBasicMapper.selectById(id);
        assertEquals("DELISTED", delisted.getListStatus());
        assertEquals("Legacy Name", delisted.getNameEn());
        assertEquals("ETF", delisted.getSecurityType());

        stockDataService.updateStock(id, new UpdateStockBasicDTO(null, null, false));
        StockBasicDO active = stockBasicMapper.selectById(id);
        assertEquals("UNKNOWN", active.getListStatus());
        assertEquals("Legacy Name", active.getNameEn());
        stockDataService.deleteStock("SH.600010");
        assertEquals(0, stockAliasMapper.countAll());
    }

    @Test
    void validationBoundariesAreStable() {
        assertThrows(BusinessException.class, () -> directoryService.search(" ", null, null, false, 20));
        assertThrows(BusinessException.class, () -> directoryService.search("a", null, null, false, 20));
        assertNotNull(directoryService.search("中", null, null, false, 1));
        assertThrows(BusinessException.class, () -> directoryService.search("aa", null, null, false, 0));
        assertThrows(BusinessException.class, () -> directoryService.search("aa", null, null, false, 101));
        assertThrows(BusinessException.class, () ->
                directoryService.search("aa", List.of("XX"), null, false, 20));
        assertThrows(BusinessException.class, () ->
                directoryService.search("aa", null, List.of("CRYPTO"), false, 20));
    }

    private void assertMatch(String query, String expectedSymbol, SecurityMatchedByEnum matchedBy) {
        SecuritySearchItemVO first = directoryService.search(query, null, null, false, 20).items().get(0);
        assertEquals(expectedSymbol, first.canonicalSymbol());
        assertEquals(matchedBy, first.matchedBy());
    }

    private List<String> symbols(SecuritySearchResultVO result) {
        return result.items().stream().map(SecuritySearchItemVO::canonicalSymbol).toList();
    }

    private List<Map<String, Object>> protectedSnapshot() {
        return jdbcTemplate.queryForList("""
                SELECT 'BAR' AS kind, id, canonical_symbol AS protected_value FROM stock_daily_bar
                UNION ALL
                SELECT 'QUOTE', id, canonical_symbol FROM stock_quote_snapshot
                UNION ALL
                SELECT 'TASK', id, idempotency_key FROM market_data_sync_task
                ORDER BY kind, id
                """);
    }

    private SecurityDirectoryImportResultVO importCsv(String csv) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return directoryService.importCsv(new ByteArrayInputStream(bytes), bytes.length);
    }

    private String header() {
        return "canonical_symbol,name,market,exchange,currency,security_type,list_status,data_source,"
                + "source_updated_at,name_cn,name_hk,name_en,short_name,pinyin_full,pinyin_abbr,"
                + "list_date,source_hash,aliases\n";
    }

    private String row(String canonical, String name, String market, String exchange, String currency,
                       String type, String status, String pinyinFull, String pinyinAbbr, String aliases) {
        return String.join(",", canonical, name, market, exchange, currency, type, status, "TEST",
                "2026-07-01T00:00:00+08:00", name, "", "", "", pinyinFull, pinyinAbbr,
                "2020-01-01", "hash-" + canonical.replace('.', '-'), aliases) + "\n";
    }
}
