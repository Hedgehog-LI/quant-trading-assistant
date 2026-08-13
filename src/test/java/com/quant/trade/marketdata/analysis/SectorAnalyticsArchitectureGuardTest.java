package com.quant.trade.marketdata.analysis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 板块分析层不可污染原始事实，也不可反向依赖外部行情 provider。 */
class SectorAnalyticsArchitectureGuardTest {

    private static final List<String> RAW_FACT_TABLES = List.of(
            "stock_daily_bar", "stock_minute_bar", "stock_quote_snapshot",
            "market_sector_snapshot", "market_sector_member_snapshot",
            "market_sector_ranking_batch", "market_sector_ranking_item", "market_sector_watch");

    @Test
    void analysisJavaDoesNotDependOnProviderOrWatchIdentity() throws IOException {
        String javaSource = readTree(Path.of("src/main/java/com/quant/trade/marketdata/analysis"), ".java");

        assertFalse(javaSource.contains("marketdata.provider"), "分析层不得依赖 provider 包");
        assertFalse(javaSource.matches("(?s).*\\bMarketDataProvider\\b.*"), "分析层不得注入行情 provider");
        assertFalse(javaSource.matches("(?s).*\\bMarketSectorProvider\\b.*"), "分析层不得注入板块 provider");
    }

    @Test
    void analysisMapperDoesNotWriteRawFactTablesOrJoinWatchId() throws IOException {
        String mapperXml = Files.readString(Path.of("src/main/resources/mapper/SectorAnalyticsMapper.xml"))
                .replaceAll("<!--[\\s\\S]*?-->", " ").toLowerCase();
        for (String table : RAW_FACT_TABLES) {
            assertFalse(mapperXml.matches("(?s).*(insert\\s+into|update|delete\\s+from|merge\\s+into)\\s+"
                    + table + ".*"), "分析 Mapper 不得写原始事实表: " + table);
        }
        assertFalse(mapperXml.matches("(?s).*\\bjoin\\b[^<;]*\\bwatch_id\\b.*"),
                "衍生查询不得使用 watch_id 连接历史身份");
        assertTrue(mapperXml.contains("sector_identity_id"), "衍生查询必须使用稳定 sector identity");
    }

    private String readTree(Path root, String suffix) throws IOException {
        try (var files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(suffix)).sorted().map(path -> {
                try {
                    return Files.readString(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).reduce("", (left, right) -> left + "\n" + right);
        }
    }
}
