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
import com.quant.trade.marketdata.util.SecurityTextNormalizer;
import com.quant.trade.marketdata.vo.SecurityDirectoryImportResultVO;
import com.quant.trade.marketdata.vo.SecuritySearchItemVO;
import com.quant.trade.marketdata.vo.SecuritySearchResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
        jdbcTemplate.update("DELETE FROM stock_quote_snapshot");
        jdbcTemplate.update("DELETE FROM market_data_sync_task");
        jdbcTemplate.update("DELETE FROM market_data_sync_plan");
        jdbcTemplate.update("DELETE FROM portfolio_price_snapshot");
        jdbcTemplate.update("DELETE FROM stock_basic");
    }

    @AfterEach
    void cleanDirectoryAfterTest() {
        cleanDirectory();
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
    void threeConflictingRowsCountUniqueFailedLinesAndErrorEvidenceIsCapped() {
        String conflicts = header()
                + row("HK.02498", "第一名称", "HK", "HKEX", "HKD", "STOCK", "LISTED",
                "first", "f", "")
                + row("HK.2498", "第二名称", "HK", "HKEX", "HKD", "STOCK", "LISTED",
                "second", "s", "")
                + row("HK.02498", "第三名称", "HK", "HKEX", "HKD", "STOCK", "LISTED",
                "third", "t", "");
        SecurityDirectoryImportException conflictError = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(conflicts));
        assertEquals(3, conflictError.getResult().totalRows());
        assertEquals(3, conflictError.getResult().failed());
        assertEquals(4, conflictError.getResult().errors().size());
        assertTrue(conflictError.getResult().errors().stream()
                .allMatch(error -> "CONFLICTING_DUPLICATE".equals(error.reasonCode())));

        StringBuilder invalidRows = new StringBuilder(header());
        for (int index = 0; index < 60; index++) {
            invalidRows.append(row("US.BAD" + index, "Invalid " + index, "US", "NYSE", "USD",
                    "CRYPTO", "LISTED", "invalid", "inv", ""));
        }
        SecurityDirectoryImportException cappedError = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(invalidRows.toString()));
        assertEquals(60, cappedError.getResult().totalRows());
        assertEquals(60, cappedError.getResult().failed());
        assertEquals(50, cappedError.getResult().errors().size());
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
    void fractionalOffsetTimestampPersistsAtMicrosecondPrecisionAndRepeatsUnchanged() {
        String csv = (header()
                + row("US.MICRO", "Microsecond", "US", "NYSE", "USD", "STOCK", "LISTED",
                "microsecond", "micro", ""))
                .replace("2026-07-01T00:00:00+08:00", "2026-07-01T08:00:00.900123+08:00");

        SecurityDirectoryImportResultVO first = importCsv(csv);
        assertEquals(1, first.inserted());
        assertEquals(LocalDateTime.parse("2026-07-01T00:00:00.900123"),
                stockBasicMapper.selectByCanonicalSymbol("US.MICRO").getSourceUpdatedAt());

        SecurityDirectoryImportResultVO repeated = importCsv(csv);
        assertEquals(1, repeated.unchanged());
        assertEquals(0, repeated.updated());
        assertEquals(LocalDateTime.parse("2026-07-01T00:00:00.900123"),
                stockBasicMapper.selectByCanonicalSymbol("US.MICRO").getSourceUpdatedAt());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isolatedCsvNegativeCases")
    void isolatedCsvDefectsReturnStableEvidenceAndRollback(
            String ignoredName,
            String csv,
            String expectedCode,
            long expectedLine,
            String expectedField,
            String expectedReason) {
        SecurityDirectoryImportException exception = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(csv));
        assertEquals(expectedCode, exception.getErrorCode().getCode());
        assertEquals(expectedLine, exception.getResult().errors().get(0).line());
        assertEquals(expectedField, exception.getResult().errors().get(0).field());
        assertEquals(expectedReason, exception.getResult().errors().get(0).reasonCode());
        assertEquals(0, stockBasicMapper.countAll());
        assertEquals(0, stockAliasMapper.countAll());
    }

    @Test
    void rowLimitRejectsActualTwoHundredThousandAndFirstRowWithoutWrites() {
        StringBuilder csv = new StringBuilder("""
                canonical_symbol,name,market,exchange,currency,security_type,list_status,data_source,source_updated_at
                """);
        for (int index = 0; index < 200_001; index++) {
            csv.append("US.R").append(index)
                    .append(",N,US,NYSE,USD,STOCK,LISTED,T,2026-07-01T00:00:00Z\n");
        }

        SecurityDirectoryImportException exception = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(csv.toString()));
        assertEquals("DAILY_BAR_VALIDATION_ERROR", exception.getErrorCode().getCode());
        assertEquals(200_001, exception.getResult().totalRows());
        assertEquals(1, exception.getResult().failed());
        assertEquals(200_002, exception.getResult().errors().get(0).line());
        assertEquals("file", exception.getResult().errors().get(0).field());
        assertEquals("TOO_MANY_ROWS", exception.getResult().errors().get(0).reasonCode());
        assertEquals(0, stockBasicMapper.countAll());
        assertEquals(0, stockAliasMapper.countAll());
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
    void accentDistinctAliasesRemainUniqueAndMatchedByCodePointIdentity() {
        SecurityDirectoryImportResultVO result = importCsv(header()
                + row("US.PLAIN", "Plain Security", "US", "NYSE", "USD", "STOCK", "LISTED",
                "plainsecurity", "plain", "USER:en:resume")
                + row("US.ACCENT", "Accent Security", "US", "NYSE", "USD", "STOCK", "LISTED",
                "accentsecurity", "accent", "USER:fr:résumé"));
        assertEquals(2, result.aliasesInserted());
        assertEquals(2, stockAliasMapper.countAll());

        SecuritySearchItemVO plain = directoryService.search("resume", null, null, false, 20).items().get(0);
        assertEquals("US.PLAIN", plain.canonicalSymbol());
        assertEquals(SecurityMatchedByEnum.ALIAS_EXACT, plain.matchedBy());
        assertEquals(1, directoryService.search("resume", null, null, false, 20).items().size());

        SecuritySearchItemVO accent = directoryService.search("résumé", null, null, false, 20).items().get(0);
        assertEquals("US.ACCENT", accent.canonicalSymbol());
        assertEquals(SecurityMatchedByEnum.ALIAS_EXACT, accent.matchedBy());
        assertEquals(1, directoryService.search("résumé", null, null, false, 20).items().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "USER:en:Resume|USER:en:resume",
            "USER:en:Resume|USER:fr:Resume"
    })
    void conflictingAliasMetadataWithinOneRowIsRejected(String aliases) {
        SecurityDirectoryImportException exception = assertThrows(
                SecurityDirectoryImportException.class,
                () -> importCsv(header() + row("US.ALIAS", "Alias Security", "US", "NYSE", "USD",
                        "STOCK", "LISTED", "aliassecurity", "alias", aliases)));
        assertEquals(2, exception.getResult().errors().get(0).line());
        assertEquals("aliases", exception.getResult().errors().get(0).field());
        assertEquals("CONFLICTING_ALIAS_METADATA", exception.getResult().errors().get(0).reasonCode());
        assertEquals(0, stockBasicMapper.countAll());
        assertEquals(0, stockAliasMapper.countAll());
    }

    @Test
    void conflictingAliasMetadataAcrossDuplicateRowsIsRejectedAtomically() {
        String csv = header()
                + row("US.ALIAS", "Alias Security", "US", "NYSE", "USD", "STOCK", "LISTED",
                "aliassecurity", "alias", "USER:en:Resume")
                + row("US.ALIAS", "Alias Security", "US", "NYSE", "USD", "STOCK", "LISTED",
                "aliassecurity", "alias", "USER:fr:Resume");
        SecurityDirectoryImportException exception = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(csv));
        assertEquals(2, exception.getResult().failed());
        assertEquals(List.of(2L, 3L), exception.getResult().errors().stream()
                .map(error -> error.line()).toList());
        assertTrue(exception.getResult().errors().stream()
                .allMatch(error -> "CONFLICTING_ALIAS_METADATA".equals(error.reasonCode())));
        assertEquals(0, stockBasicMapper.countAll());
        assertEquals(0, stockAliasMapper.countAll());
    }

    @Test
    void conflictingAliasMetadataAgainstPersistedIdentityRollsBack() {
        String english = header() + row("US.ALIAS", "Alias Security", "US", "NYSE", "USD",
                "STOCK", "LISTED", "aliassecurity", "alias", "USER:en:Resume");
        importCsv(english);
        StockBasicDO stock = stockBasicMapper.selectByCanonicalSymbol("US.ALIAS");
        List<StockAliasDO> before = stockAliasMapper.selectByStockBasicId(stock.getId());

        String french = english.replace("USER:en:Resume", "USER:fr:Resume");
        SecurityDirectoryImportException exception = assertThrows(
                SecurityDirectoryImportException.class, () -> importCsv(french));
        assertEquals("CONFLICTING_ALIAS_METADATA", exception.getResult().errors().get(0).reasonCode());
        assertEquals(2, exception.getResult().errors().get(0).line());
        assertEquals(1, stockBasicMapper.countAll());
        assertEquals(before, stockAliasMapper.selectByStockBasicId(stock.getId()));
    }

    @Test
    void literalLikeMetacharactersDoNotExpandCandidateSets() {
        StringBuilder csv = new StringBuilder(header())
                .append(row("US.PCT", "Percent %% Fund", "US", "NYSE", "USD", "ETF", "LISTED",
                        "percent", "pct", ""))
                .append(row("US.UNDER", "Under__Score Fund", "US", "NYSE", "USD", "ETF", "LISTED",
                        "underscore", "under", ""))
                .append(row("US.BANG", "Bang!! Fund", "US", "NYSE", "USD", "ETF", "LISTED",
                        "bang", "bang", ""));
        for (int index = 0; index < 25; index++) {
            csv.append(row("US.DECOY" + index, "Ordinary Fund " + index, "US", "NYSE", "USD",
                    "ETF", "LISTED", "ordinary" + index, "ord" + index, ""));
        }
        importCsv(csv.toString());

        assertEquals(List.of("US.PCT"), symbols(directoryService.search("%%", null, null, false, 20)));
        assertEquals(List.of("US.UNDER"), symbols(directoryService.search("__", null, null, false, 20)));
        assertEquals(List.of("US.BANG"), symbols(directoryService.search("!!", null, null, false, 20)));
        assertEquals(1, candidateCount("%%"));
        assertEquals(1, candidateCount("__"));
        assertEquals(1, candidateCount("!!"));
    }

    @Test
    void rankingComparatorLevelsAndMatchedByPrecedenceAreIsolatedAndRepeatable() {
        importCsv(header()
                + row("US.SCOREHIGH", "Score Duel", "US", "NYSE", "USD", "STOCK", "UNKNOWN",
                "scorehigh", "sch", "")
                + row("US.SCORELOW", "Score Duel Plus", "US", "NYSE", "USD", "STOCK", "LISTED",
                "scorelow", "scl", "")
                + row("US.LISTED", "Listed Duel", "US", "NYSE", "USD", "STOCK", "LISTED",
                "listed", "lis", "")
                + row("US.UNKNOWN", "Listed Duel", "US", "NYSE", "USD", "STOCK", "UNKNOWN",
                "unknown", "unk", "")
                + row("US.MARKET", "Market Duel", "US", "NYSE", "USD", "STOCK", "LISTED",
                "marketus", "mus", "")
                + row("SH.600099", "Market Duel", "SH", "SSE", "CNY", "STOCK", "LISTED",
                "marketsh", "msh", "")
                + row("US.ZNAME", "Ｎａｍｅ Ｄｕｅｌ Ａ", "US", "NYSE", "USD", "STOCK", "LISTED",
                "namea", "na", "")
                + row("US.ANAME", "Name Duel B", "US", "NYSE", "USD", "STOCK", "LISTED",
                "nameb", "nb", "")
                + row("US.ACANON", "Canon Duel", "US", "NYSE", "USD", "STOCK", "LISTED",
                "canona", "ca", "")
                + row("US.ZCANON", "Canon Duel", "US", "NYSE", "USD", "STOCK", "LISTED",
                "canonz", "cz", "")
                + row("US.OMNI", "Omni Match", "US", "NYSE", "USD", "STOCK", "LISTED",
                "omni match", "om", "USER:en:Omni Match"));

        assertRepeatedOrder("Score Duel", null, List.of("US.SCOREHIGH", "US.SCORELOW"));
        assertRepeatedOrder("Listed Duel", null, List.of("US.LISTED", "US.UNKNOWN"));
        assertRepeatedOrder("Market Duel", List.of("US", "SH"), List.of("US.MARKET", "SH.600099"));
        assertRepeatedOrder("Name Duel", null, List.of("US.ZNAME", "US.ANAME"));
        assertRepeatedOrder("Canon Duel", null, List.of("US.ACANON", "US.ZCANON"));
        SecuritySearchItemVO omni = directoryService.search("Omni Match", null, null, false, 20).items().get(0);
        assertEquals("US.OMNI", omni.canonicalSymbol());
        assertEquals(SecurityMatchedByEnum.FORMAL_NAME_EXACT, omni.matchedBy());
    }

    @Test
    void searchCoversChannelsRankingFiltersHkPaddingAndProviderIsolation() {
        seedProtectedTables();
        ProtectedTablesSnapshot protectedBefore = protectedSnapshot();
        assertFalse(protectedBefore.dailyBars().isEmpty());
        assertFalse(protectedBefore.quoteSnapshots().isEmpty());
        assertFalse(protectedBefore.syncTasks().isEmpty());
        assertFalse(protectedBefore.syncPlans().isEmpty());
        assertFalse(protectedBefore.portfolioPrices().isEmpty());
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

        String fractional = (header()
                + row("US.BOUND", "Boundary", "US", "NYSE", "USD", "STOCK", "LISTED",
                "boundary", "bound", ""))
                .replace("2026-07-01T00:00:00+08:00", "2026-07-01T08:00:00.900123+08:00");
        importCsv(fractional);
        Instant updated = Instant.parse("2026-07-01T00:00:00.900123Z");
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
                .normalizedAlias("old").normalizedAliasKey(SecurityTextNormalizer.identityKey("old"))
                .aliasType("FORMER_NAME").dataSource("TEST").build());

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

    private void assertRepeatedOrder(String query, List<String> markets, List<String> expected) {
        for (int repetition = 0; repetition < 3; repetition++) {
            assertEquals(expected, symbols(directoryService.search(query, markets, null, false, 20)));
        }
    }

    private List<String> symbols(SecuritySearchResultVO result) {
        return result.items().stream().map(SecuritySearchItemVO::canonicalSymbol).toList();
    }

    private int candidateCount(String query) {
        String normalized = SecurityTextNormalizer.normalize(query);
        return stockBasicMapper.searchCandidates(
                normalized,
                SecurityTextNormalizer.escapeLikeLiteral(normalized),
                normalized.toUpperCase(java.util.Locale.ROOT),
                null,
                null,
                List.of(),
                List.of(),
                false).size();
    }

    private void seedProtectedTables() {
        jdbcTemplate.update("""
                INSERT INTO stock_daily_bar (
                    id, canonical_symbol, trade_date, adjust_type, data_source,
                    open_price, high_price, low_price, close_price, volume, amount
                ) VALUES (900001, 'US.PROTECTED', '2026-06-30', 'NONE', 'TEST',
                          10.100000, 10.900000, 9.800000, 10.500000, 12345, 129623.250000)
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_quote_snapshot (
                    id, canonical_symbol, quote_time, current_price, open_price, high_price, low_price,
                    pre_close_price, volume, amount, trade_status, data_source, fetched_at, raw_hash
                ) VALUES (900001, 'US.PROTECTED', '2026-07-01 00:00:00', 10.600000, 10.100000,
                          10.900000, 9.800000, 10.500000, 23456, 248633.600000, 'NORMAL', 'TEST',
                          '2026-07-01 00:00:01', 'protected-quote-hash')
                """);
        jdbcTemplate.update("""
                INSERT INTO market_data_sync_task (
                    id, task_type, provider, scope_json, status, idempotency_key,
                    total_count, success_count, fail_count, inserted_count, updated_count, skipped_count
                ) VALUES (900001, 'QUOTE', 'TEST', '{"symbols":["US.PROTECTED"]}', 'SUCCESS',
                          'D1-PROTECTED-TASK', 1, 1, 0, 1, 0, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO market_data_sync_plan (
                    id, plan_name, task_type, provider, scope_json, interval_type, adjust_type,
                    trigger_type, cron_expr, include_auction, collect_frequency, enabled, description,
                    last_run_at, last_task_id, run_claim_token, run_claimed_at, running_task_id
                ) VALUES (
                    900001, 'D1 protected collection', 'DAILY_BAR', 'TEST',
                    '{"symbols":["US.PROTECTED"]}', '1D', 'NONE', 'CRON', '0 0 18 * * ?',
                    FALSE, 'DAILY', TRUE, 'must remain unchanged', '2026-07-01 10:00:00',
                    900001, 'protected-claim', '2026-07-01 09:59:00', 900001
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO portfolio_price_snapshot (
                    id, symbol, name, current_price, price_date, note
                ) VALUES (
                    900001, 'US.PROTECTED', 'Protected Price', 10.700000,
                    '2026-07-01', 'must remain unchanged'
                )
                """);
    }

    private ProtectedTablesSnapshot protectedSnapshot() {
        List<Map<String, Object>> dailyBars =
                jdbcTemplate.queryForList("SELECT * FROM stock_daily_bar ORDER BY id");
        List<Map<String, Object>> quoteSnapshots =
                jdbcTemplate.queryForList("SELECT * FROM stock_quote_snapshot ORDER BY id");
        List<Map<String, Object>> syncTasks =
                jdbcTemplate.queryForList("SELECT * FROM market_data_sync_task ORDER BY id");
        List<Map<String, Object>> syncPlans =
                jdbcTemplate.queryForList("SELECT * FROM market_data_sync_plan ORDER BY id");
        List<Map<String, Object>> portfolioPrices =
                jdbcTemplate.queryForList("SELECT * FROM portfolio_price_snapshot ORDER BY id");
        return new ProtectedTablesSnapshot(dailyBars, quoteSnapshots, syncTasks, syncPlans, portfolioPrices);
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

    private Stream<Arguments> isolatedCsvNegativeCases() {
        String valid = row("US.NEG", "Negative", "US", "NYSE", "USD", "STOCK", "LISTED",
                "negative", "neg", "");
        return Stream.of(
                Arguments.of("missing required header",
                        "canonical_symbol,name,market,exchange,currency,security_type,list_status,data_source\n",
                        "CSV_WRONG_HEADER", 1L, "header", "MISSING_REQUIRED_HEADER"),
                Arguments.of("invalid canonical", header() + valid.replace("US.NEG", "BAD"),
                        "DAILY_BAR_VALIDATION_ERROR", 2L, "canonical_symbol", "INVALID_SYMBOL"),
                Arguments.of("invalid market enum", header() + valid.replace(",US,NYSE,", ",XX,NYSE,"),
                        "DAILY_BAR_VALIDATION_ERROR", 2L, "market", "INVALID_ENUM"),
                Arguments.of("invalid security type", header() + valid.replace(",STOCK,LISTED,", ",CRYPTO,LISTED,"),
                        "DAILY_BAR_VALIDATION_ERROR", 2L, "security_type", "INVALID_ENUM"),
                Arguments.of("invalid list status", header() + valid.replace(",LISTED,TEST,", ",ACTIVE,TEST,"),
                        "DAILY_BAR_VALIDATION_ERROR", 2L, "list_status", "INVALID_ENUM"),
                Arguments.of("invalid list date", header() + valid.replace("2020-01-01", "bad-date"),
                        "DAILY_BAR_VALIDATION_ERROR", 2L, "list_date", "INVALID_DATE"),
                Arguments.of("invalid offset timestamp",
                        header() + valid.replace("2026-07-01T00:00:00+08:00", "2026-07-01T00:00:00"),
                        "DAILY_BAR_VALIDATION_ERROR", 2L, "source_updated_at", "INVALID_TIMESTAMP"),
                Arguments.of("invalid alias grammar",
                        header() + valid.replace(",hash-US-NEG,\n", ",hash-US-NEG,USER:missing\n"),
                        "DAILY_BAR_VALIDATION_ERROR", 2L, "aliases", "INVALID_ALIAS_FORMAT"),
                Arguments.of("invalid alias type",
                        header() + valid.replace(",hash-US-NEG,\n", ",hash-US-NEG,UNKNOWN:en:value\n"),
                        "DAILY_BAR_VALIDATION_ERROR", 2L, "aliases", "INVALID_ALIAS_TYPE")
        );
    }

    private record ProtectedTablesSnapshot(
            List<Map<String, Object>> dailyBars,
            List<Map<String, Object>> quoteSnapshots,
            List<Map<String, Object>> syncTasks,
            List<Map<String, Object>> syncPlans,
            List<Map<String, Object>> portfolioPrices) {
    }
}
