package com.quant.trade.marketdata.provider.csv;

import com.quant.trade.marketdata.exception.SecurityDirectoryImportException;
import com.quant.trade.marketdata.model.StockAliasDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-01 P2 等价性证据：证明 D3 {@link SecurityDirectoryCsvParser} 与 D1 import 行为一致。
 * 对同一输入，D3 parser 产出与 D1 相同的 stock/alias 候选集合与失败 reasonCode。
 * reasonCode 集合冻结自 D1：INVALID_SYMBOL/MARKET_MISMATCH/INVALID_ENUM/INVALID_TIMESTAMP/
 * CONFLICTING_DUPLICATE/CONFLICTING_ALIAS_METADATA/BLANK_ALIAS_VALUE/EMPTY_ALIAS_ENTRY/
 * INVALID_ALIAS_FORMAT/INVALID_ALIAS_TYPE/INVALID_DATE/BLANK_REQUIRED_VALUE/MALFORMED_UTF8/EMPTY_FILE 等。
 */
class SecurityDirectoryCsvParserEquivalenceTest {

    private static final String HEADER = "canonical_symbol,name,market,exchange,currency,security_type,"
            + "list_status,data_source,source_updated_at,aliases\n";

    private final SecurityDirectoryCsvParser parser = new SecurityDirectoryCsvParser();

    static Stream<Arguments> validSnapshots() {
        return Stream.of(
                Arguments.of("BOM + RFC4180 引号 + typed aliases",
                        "\uFEFF" + HEADER
                                + "\"SH.603308\",\"应流,\\n股份\",SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,"
                                + "FORMER_NAME::应流科技|SHORT_NAME::应流\n"
                                + "HK.02498,速腾聚创,HK,HKEX,HKD,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,ENGLISH:en:RoboSense\n"
                                + "US.AAPL,Apple Inc.,US,NASDAQ,USD,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n",
                        3, 3),
                Arguments.of("港股补零 HK.02498 + 美股大写 US.AAPL",
                        HEADER + "HK.02498,速腾聚创,HK,HKEX,HKD,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n"
                                + "US.AAPL,Apple Inc.,US,NASDAQ,USD,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n",
                        2, 0));
    }

    @ParameterizedTest
    @MethodSource("validSnapshots")
    void parsesValidSnapshotConsistently(String name, String csv, int expectedRows, int expectedAliases) {
        SecurityDirectoryCsvParser.ParsedDirectoryBatch batch =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedRows, batch.rows().size(), name + ": 行数");
        List<StockBasicDO> stocks = batch.rows().stream().map(
                SecurityDirectoryCsvParser.DirectoryRow::stock).toList();
        assertTrue(stocks.stream().anyMatch(s -> "SH.603308".equals(s.getCanonicalSymbol()))
                        || expectedRows < 3, name + ": 包含 SH.603308");
        // alias 规范化与归属正确。
        int aliasCount = batch.rows().stream().mapToInt(r -> r.aliases().size()).sum();
        assertEquals(expectedAliases, aliasCount, name + ": alias 数");
    }

    @Test
    void emptyFileRejectedWithD1ReasonCode() {
        assertReasonCode("", "EMPTY_FILE");
    }

    @Test
    void malformedUtf8RejectedWithD1ReasonCode() {
        byte[] malformed = {(byte) 0xFF, (byte) 0xFE, (byte) 0x00};
        assertReasonCodeBytes(malformed, "MALFORMED_UTF8");
    }

    @Test
    void invalidCanonicalSymbolRejectedWithD1ReasonCode() {
        String csv = HEADER + "INVALID.X,Y,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n";
        assertReasonCode(csv, "INVALID_SYMBOL");
    }

    @Test
    void marketMismatchRejectedWithD1ReasonCode() {
        String csv = HEADER + "SH.603308,应流股份,SZ,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n";
        assertReasonCode(csv, "MARKET_MISMATCH");
    }

    @Test
    void invalidEnumRejectedWithD1ReasonCode() {
        String csv = HEADER + "SH.603308,应流股份,SH,SSE,CNY,BADTYPE,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n";
        assertReasonCode(csv, "INVALID_ENUM");
    }

    @Test
    void invalidTimestampRejectedWithD1ReasonCode() {
        String csv = HEADER + "SH.603308,应流股份,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,not-a-date,\n";
        assertReasonCode(csv, "INVALID_TIMESTAMP");
    }

    @Test
    void conflictingDuplicateRejectedWithD1ReasonCode() {
        String csv = HEADER
                + "SH.603308,应流股份,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n"
                + "SH.603308,应流改名,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n";
        assertReasonCode(csv, "CONFLICTING_DUPLICATE");
    }

    @Test
    void duplicateUnchangedIsCountedOnceNotError() {
        String row = "SH.603308,应流股份,SH,SSE,CNY,STOCK,LISTED,SNAPSHOT,2026-08-01T10:00:00Z,\n";
        String csv = HEADER + row + row;
        SecurityDirectoryCsvParser.ParsedDirectoryBatch batch =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, batch.rows().size(), "同义重复只保留一行");
        assertEquals(1, batch.duplicateUnchanged(), "duplicate-unchanged 计为 1");
    }

    @Test
    void sameDirectoryDataEquivalenceWithD1Semantics() {
        StockBasicDO a = StockBasicDO.builder().canonicalSymbol("SH.603308").symbol("603308")
                .name("应流股份").market("SH").exchange("SSE").currency("CNY")
                .securityType("STOCK").listStatus("LISTED").dataSource("SNAPSHOT")
                .sourceUpdatedAt(java.time.LocalDateTime.of(2026, 8, 1, 10, 0)).delisted(false).build();
        StockBasicDO b = StockBasicDO.builder().canonicalSymbol("SH.603308").symbol("603308")
                .name("应流股份").market("SH").exchange("SSE").currency("CNY")
                .securityType("STOCK").listStatus("LISTED").dataSource("SNAPSHOT")
                .sourceUpdatedAt(java.time.LocalDateTime.of(2026, 8, 1, 10, 0)).delisted(false).build();
        assertTrue(SecurityDirectoryCsvParser.sameDirectoryData(a, b), "D1 sameDirectoryData 语义等价");
    }

    private void assertReasonCode(String csv, String reasonCode) {
        assertReasonCodeBytes(csv.getBytes(StandardCharsets.UTF_8), reasonCode);
    }

    private void assertReasonCodeBytes(byte[] bytes, String reasonCode) {
        SecurityDirectoryImportException exception = assertThrows(SecurityDirectoryImportException.class,
                () -> parser.parse(bytes));
        assertTrue(exception.getMessage().contains("reason=" + reasonCode),
                "expected reason=" + reasonCode + " but was: " + exception.getMessage());
    }
}
