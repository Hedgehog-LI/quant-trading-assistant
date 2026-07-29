package com.quant.trade.marketdata;

import com.quant.trade.marketdata.service.SecurityDirectoryService;
import com.quant.trade.marketdata.vo.SecuritySearchResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DatabaseMetaData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducible D1 H2 benchmark.
 *
 * Run explicitly:
 * ./mvnw -Dqta.security-directory.benchmark=true
 *   -Dtest=SecurityDirectorySearchBenchmarkTest test
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qta_security_benchmark;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.maximum-pool-size=4"
})
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "qta.security-directory.benchmark", matches = "true")
class SecurityDirectorySearchBenchmarkTest {

    private static final int SECURITY_COUNT = 50_000;
    private static final int ALIAS_COUNT = 100_000;
    private static final int WARMUPS_PER_CLASS = 50;
    private static final int MEASURED_PER_CLASS = 200;
    private static final double P95_LIMIT_MILLIS = 300.0;
    private static final String EXPECTED = "US.B40000";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SecurityDirectoryService service;
    @Autowired
    private DataSource dataSource;

    @Test
    void fixedSeedEightClassMatrixMeetsPerClassAndOverallP95() throws Exception {
        seed();
        List<QueryCase> cases = List.of(
                new QueryCase("CANONICAL_EXACT", "US.B40000"),
                new QueryCase("RAW_SYMBOL_EXACT", "B40000"),
                new QueryCase("FORMAL_NAME_EXACT", "Benchmark Security 40000"),
                new QueryCase("FORMAL_NAME_PREFIX", "Benchmark Security 4000"),
                new QueryCase("ALIAS_EXACT", "alias-one-b40000"),
                new QueryCase("ALIAS_PREFIX", "alias-one-b4000"),
                new QueryCase("PINYIN_PREFIX", "bench40000"),
                new QueryCase("NAME_CONTAINS", "urity 40000"));
        List<RawLatency> raw = new ArrayList<>(400 + 1600);
        for (QueryCase queryCase : cases) {
            for (int iteration = 0; iteration < WARMUPS_PER_CLASS; iteration++) {
                execute(queryCase, "warmup", iteration, raw);
            }
        }
        for (QueryCase queryCase : cases) {
            for (int iteration = 0; iteration < MEASURED_PER_CLASS; iteration++) {
                execute(queryCase, "measured", iteration, raw);
            }
        }

        Map<String, Double> classP95 = new LinkedHashMap<>();
        for (QueryCase queryCase : cases) {
            List<Long> values = raw.stream()
                    .filter(value -> "measured".equals(value.phase()) && queryCase.name().equals(value.queryClass()))
                    .map(RawLatency::latencyNanos).toList();
            assertEquals(MEASURED_PER_CLASS, values.size());
            classP95.put(queryCase.name(), p95Millis(values));
        }
        List<Long> overallValues = raw.stream().filter(value -> "measured".equals(value.phase()))
                .map(RawLatency::latencyNanos).toList();
        assertEquals(1_600, overallValues.size());
        double overallP95 = p95Millis(overallValues);
        writeReports(cases, raw, classP95, overallP95);

        classP95.forEach((queryClass, p95) ->
                assertTrue(p95 < P95_LIMIT_MILLIS, queryClass + " P95=" + p95 + "ms"));
        assertTrue(overallP95 < P95_LIMIT_MILLIS, "overall P95=" + overallP95 + "ms");
    }

    private void seed() {
        jdbcTemplate.update("DELETE FROM stock_alias");
        jdbcTemplate.update("DELETE FROM stock_basic");
        String sql = """
                INSERT INTO stock_basic (
                    canonical_symbol, symbol, name, market, name_en, pinyin_full, pinyin_abbr,
                    exchange, currency, security_type, list_status, data_source, source_updated_at,
                    source_hash, delisted
                ) VALUES (?, ?, ?, 'US', ?, ?, ?, 'BENCH', 'USD', 'STOCK', 'LISTED',
                          'BENCHMARK_FIXED_SEED_20260729', ?, ?, FALSE)
                """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                int number = index + 1;
                String suffix = String.format(Locale.ROOT, "%05d", number);
                statement.setString(1, "US.B" + suffix);
                statement.setString(2, "B" + suffix);
                statement.setString(3, "Benchmark Security " + suffix);
                statement.setString(4, "Benchmark Security " + suffix);
                statement.setString(5, "bench" + suffix);
                statement.setString(6, "bs" + suffix);
                statement.setTimestamp(7, Timestamp.from(Instant.parse("2026-07-01T00:00:00Z")));
                statement.setString(8, "seed-" + suffix);
            }

            @Override
            public int getBatchSize() {
                return SECURITY_COUNT;
            }
        });
        jdbcTemplate.update("""
                INSERT INTO stock_alias (
                    stock_basic_id, alias, normalized_alias, alias_type, data_source
                )
                SELECT id, CONCAT('Alias One ', symbol), CONCAT('alias-one-', LOWER(symbol)),
                       'USER', 'BENCHMARK_FIXED_SEED_20260729'
                FROM stock_basic
                """);
        jdbcTemplate.update("""
                INSERT INTO stock_alias (
                    stock_basic_id, alias, normalized_alias, alias_type, data_source
                )
                SELECT id, CONCAT('Alias Two ', symbol), CONCAT('alias-two-', LOWER(symbol)),
                       'ENGLISH', 'BENCHMARK_FIXED_SEED_20260729'
                FROM stock_basic
                """);
        assertEquals(SECURITY_COUNT, stockCount());
        assertEquals(ALIAS_COUNT, aliasCount());
    }

    private void execute(QueryCase queryCase, String phase, int iteration, List<RawLatency> raw) {
        long started = System.nanoTime();
        SecuritySearchResultVO result = service.search(queryCase.query(), null, null, false, 20);
        long elapsed = System.nanoTime() - started;
        assertFalse(result.items().isEmpty(), queryCase.name() + " miss");
        assertEquals(EXPECTED, result.items().get(0).canonicalSymbol(), queryCase.name() + " wrong first hit");
        raw.add(new RawLatency(queryCase.name(), queryCase.query(), phase, iteration, elapsed, true));
    }

    private double p95Millis(List<Long> values) {
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index) / 1_000_000.0;
    }

    private int stockCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(1) FROM stock_basic", Integer.class);
    }

    private int aliasCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(1) FROM stock_alias", Integer.class);
    }

    private void writeReports(List<QueryCase> cases, List<RawLatency> raw,
                              Map<String, Double> classP95, double overallP95) throws Exception {
        Path outputDirectory = Path.of("target", "security-directory-benchmark");
        Files.createDirectories(outputDirectory);
        Path rawPath = outputDirectory.resolve("raw-latencies.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(rawPath)) {
            writer.write("queryClass,query,phase,iteration,latencyNanos,found\n");
            for (RawLatency value : raw) {
                writer.write(value.queryClass() + "," + value.query() + "," + value.phase() + ","
                        + value.iteration() + "," + value.latencyNanos() + "," + value.found() + "\n");
            }
        }
        String databaseName;
        String databaseVersion;
        String jdbcUrl;
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData database = connection.getMetaData();
            databaseName = database.getDatabaseProductName();
            databaseVersion = database.getDatabaseProductVersion();
            jdbcUrl = database.getURL();
        }
        StringBuilder perClass = new StringBuilder();
        classP95.forEach((name, p95) -> {
            if (!perClass.isEmpty()) {
                perClass.append(',');
            }
            perClass.append("\n    \"").append(name).append("\": ")
                    .append(String.format(Locale.ROOT, "%.6f", p95));
        });
        String report = """
                {
                  "runner": "SecurityDirectorySearchBenchmarkTest",
                  "seed": "BENCHMARK_FIXED_SEED_20260729",
                  "dataset": {"securities": %d, "aliases": %d},
                  "matrix": {"classes": %d, "warmups": %d, "measured": %d, "measuredPerClass": %d},
                  "queries": "%s",
                  "percentileMethod": "nearest-rank: sort ascending; index=ceil(0.95*N)-1",
                  "thresholdMillis": %.1f,
                  "overallP95Millis": %.6f,
                  "perClassP95Millis": {%s
                  },
                  "environment": {
                    "javaVersion": "%s",
                    "jvm": "%s",
                    "os": "%s %s %s",
                    "availableProcessors": %d,
                    "maxHeapBytes": %d,
                    "database": "%s %s",
                    "jdbcUrl": "%s"
                  },
                  "rawLatencies": "target/security-directory-benchmark/raw-latencies.csv"
                }
                """.formatted(
                SECURITY_COUNT, ALIAS_COUNT, cases.size(), 400, 1600, MEASURED_PER_CLASS,
                cases.toString().replace("\"", "\\\""), P95_LIMIT_MILLIS, overallP95, perClass,
                System.getProperty("java.version"), ManagementFactory.getRuntimeMXBean().getVmName(),
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(),
                databaseName, databaseVersion, jdbcUrl);
        Files.writeString(outputDirectory.resolve("report.json"), report);
    }

    private record QueryCase(String name, String query) {
    }

    private record RawLatency(String queryClass, String query, String phase, int iteration,
                              long latencyNanos, boolean found) {
    }
}
