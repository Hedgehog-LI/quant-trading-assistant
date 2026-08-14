package com.quant.trade.marketdata.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import com.quant.trade.marketdata.poc.Mr0PocIngestService.IngestCommand;
import com.quant.trade.marketdata.poc.Mr0PocIngestService.IngestResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-04 聚焦测试（冻结六用例，TEST-04）。
 * <p>
 * client 全部打桩（fixture 来自 F5 真实探针摘录，见 src/test/resources/mr0/mr0-public-probe-fixtures.json），
 * 零联网：桩只覆写 {@link PublicMarketDataClient#httpGet}，响应仍走真实解析器。
 * 每个用例事务内回滚，互不污染 H2 库。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Mr0PocIngestServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonNode FIXTURE = loadFixture();
    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 15);
    private static final LocalDateTime FETCHED_AT = LocalDateTime.of(2026, 8, 15, 10, 0);

    @Autowired
    private Mr0PocIngestService service;
    @Autowired
    private StockDailyBarMapper stockDailyBarMapper;
    @Autowired
    private StockBasicMapper stockBasicMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FixtureBackedPublicClient fixtureClient;

    private static IngestCommand command() {
        return IngestCommand.builder()
                .sampleSize(2)
                .asOfDate(AS_OF)
                .fetchedAt(FETCHED_AT)
                .build();
    }

    // ==================== M1 日 K 幂等 ====================

    @Test
    void ingestDailyBarsTwiceWritesNoDuplicates() {
        IngestResult first = service.ingest(command());
        Long countAfterFirst = tencentBarCount();
        // 样本(sh600519 4 行 + sz000001 2 行) + 基准 sh000001 1 行（CR-1 恒抓）
        assertThat(first.getDailyBar().getInserted()).isEqualTo(7L);
        // 基准不算样本：sampleSymbols 不含 SH.000001
        assertThat(first.getSampleSymbols()).doesNotContain("SH.000001");

        // 值可更新：变更 fixture 中 2026-07-02 成交额后重跑，行数不变、值更新
        fixtureClient.overrideDailyBarAmount("sh600519", "2026-07-02", "450000.00");
        IngestResult second = service.ingest(command());

        assertThat(tencentBarCount()).isEqualTo(countAfterFirst);
        assertThat(second.getDailyBar().getInserted()).isZero();
        assertThat(second.getDailyBar().getUpdated()).isEqualTo(7L);
        BigDecimal amount = jdbcTemplate.queryForObject(
                "SELECT amount FROM stock_daily_bar WHERE canonical_symbol='SH.600519'"
                        + " AND trade_date='2026-07-02' AND adjust_type='NONE' AND data_source='TENCENT_PUBLIC'",
                BigDecimal.class);
        assertThat(amount).isEqualByComparingTo("4500000000");

        // CR-1：基准 SH.000001 日 K 行存在（TENCENT_PUBLIC/NONE）且二次导入幂等不新增；
        // 收盘价来自 fixture sh000001 真实探针行（3424.612 指数点位）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_daily_bar WHERE canonical_symbol='SH.000001'"
                        + " AND adjust_type='NONE' AND data_source='TENCENT_PUBLIC'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT close_price FROM stock_daily_bar WHERE canonical_symbol='SH.000001'"
                        + " AND trade_date='2026-07-01' AND adjust_type='NONE' AND data_source='TENCENT_PUBLIC'",
                BigDecimal.class)).isEqualByComparingTo("3424.612");
    }

    // ==================== M2 Provider 标签 ====================

    @Test
    void ingestRowsCarryPublicProviderLabel() {
        service.ingest(command());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_daily_bar WHERE data_source='TENCENT_PUBLIC'"
                        + " AND adjust_type='NONE'", Long.class)).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_universe_snapshot WHERE provider_code<>'SINA_PUBLIC'", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_stock_money_flow_daily WHERE provider_code<>'SINA_PUBLIC'", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_industry_membership WHERE taxonomy_code<>'SINA_INDUSTRY'"
                        + " OR provider_code<>'SINA_PUBLIC'", Long.class)).isZero();
    }

    // ==================== M3 既有 Provider 行保留 ====================

    @Test
    void ingestPreservesExistingProviderRows() {
        stockDailyBarMapper.insert(StockDailyBarDO.builder()
                .canonicalSymbol("SH.600519")
                .tradeDate(LocalDate.of(2026, 7, 1))
                .adjustType("NONE")
                .dataSource("CSV")
                .openPrice(new BigDecimal("1680.00"))
                .highPrice(new BigDecimal("1695.00"))
                .lowPrice(new BigDecimal("1678.00"))
                .closePrice(new BigDecimal("1690.00"))
                .volume(25000L)
                .amount(new BigDecimal("42250000.00"))
                .fetchedAt(LocalDateTime.of(2026, 7, 2, 8, 0))
                .build());

        service.ingest(command());

        Map<String, Object> csvRow = jdbcTemplate.queryForMap(
                "SELECT * FROM stock_daily_bar WHERE canonical_symbol='SH.600519'"
                        + " AND trade_date='2026-07-01' AND data_source='CSV'");
        assertThat((BigDecimal) csvRow.get("open_price")).isEqualByComparingTo("1680.00");
        assertThat((BigDecimal) csvRow.get("close_price")).isEqualByComparingTo("1690.00");
        assertThat(csvRow.get("volume")).isEqualTo(25000L);
        assertThat((BigDecimal) csvRow.get("amount")).isEqualByComparingTo("42250000.00");
        // 同日 TENCENT_PUBLIC 行并存（uk 含 data_source）
        Long tencentRowsSameDay = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_daily_bar WHERE canonical_symbol='SH.600519'"
                        + " AND trade_date='2026-07-01' AND data_source='TENCENT_PUBLIC'", Long.class);
        assertThat(tencentRowsSameDay).isEqualTo(1L);
    }

    // ==================== M4 单位换算（字典冻结） ====================

    @Test
    void ingestConvertsUnitsPerFrozenDictionary() {
        service.ingest(command());

        Map<String, Object> bar = jdbcTemplate.queryForMap(
                "SELECT amount, volume, low_price, high_price FROM stock_daily_bar"
                        + " WHERE canonical_symbol='SH.600519' AND trade_date='2026-07-01'"
                        + " AND adjust_type='NONE' AND data_source='TENCENT_PUBLIC'");
        // fixture 原始值 amount=503383.82(万元)、volume=42474(手)
        assertThat((BigDecimal) bar.get("amount")).isEqualByComparingTo("5033838200");
        assertThat((Long) bar.get("volume")).isEqualTo(4247400L);

        BigDecimal vwap = ((BigDecimal) bar.get("amount"))
                .divide(new BigDecimal(((Long) bar.get("volume")).toString()), 6,
                        java.math.RoundingMode.HALF_UP);
        assertThat(vwap).isGreaterThanOrEqualTo((BigDecimal) bar.get("low_price"));
        assertThat(vwap).isLessThanOrEqualTo((BigDecimal) bar.get("high_price"));

        // universe：turnoverratio 1.86913(%) → 0.0186913；市值万元×10000
        Map<String, Object> universe = jdbcTemplate.queryForMap(
                "SELECT turnover_rate, circulating_market_cap FROM mr0_universe_snapshot"
                        + " WHERE canonical_symbol='SH.600519' AND as_of_date='2026-08-15'");
        assertThat((BigDecimal) universe.get("turnover_rate")).isEqualByComparingTo("0.0186913");
        assertThat((BigDecimal) universe.get("circulating_market_cap")).isEqualByComparingTo("236145665200");

        // 资金流净额原值元（不再换算）
        Map<String, Object> flow = jdbcTemplate.queryForMap(
                "SELECT main_net_inflow, super_net, industry_net_inflow FROM mr0_stock_money_flow_daily"
                        + " WHERE canonical_symbol='SH.600519' AND trade_date='2026-07-01'");
        assertThat((BigDecimal) flow.get("main_net_inflow")).isEqualByComparingTo("-32102345.67");
        assertThat((BigDecimal) flow.get("super_net")).isEqualByComparingTo("-12345678.90");
        assertThat((BigDecimal) flow.get("industry_net_inflow")).isEqualByComparingTo("-23456789.01");
    }

    // ==================== M5 成分/资金流幂等 + 时点列 ====================

    @Test
    void ingestMembershipAndMoneyFlowAreIdempotentWithPointInTimeColumns() {
        service.ingest(command());
        Long membershipCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_industry_membership", Long.class);
        Long moneyFlowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_stock_money_flow_daily", Long.class);
        // 窗口过滤：资金流只保留分析窗（2026-07）内 2 个样本 × 2 行
        assertThat(moneyFlowCount).isEqualTo(4L);
        // 样本外成分（bj920099）与样本外证券（SH.601398）不落库
        assertThat(membershipCount).isEqualTo(2L);

        IngestResult second = service.ingest(command());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_industry_membership", Long.class)).isEqualTo(membershipCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_stock_money_flow_daily", Long.class)).isEqualTo(moneyFlowCount);
        assertThat(second.getMembership().getInserted()).isZero();
        assertThat(second.getMoneyFlow().getInserted()).isZero();

        // 逐行时点列非空
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_industry_membership WHERE as_of_date IS NULL"
                        + " OR fetched_at IS NULL OR provider_code IS NULL", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_stock_money_flow_daily WHERE trade_date IS NULL"
                        + " OR fetched_at IS NULL OR provider_code IS NULL", Long.class)).isZero();
        // 资金流窗口过滤：无 2026-07 之外的行（fixture 含 2026-03-16 与 2026-08-01 行）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM mr0_stock_money_flow_daily WHERE trade_date NOT BETWEEN"
                        + " '2026-07-01' AND '2026-07-31'", Long.class)).isZero();
    }

    // ==================== M6 最小身份幂等回填 ====================

    @Test
    void ingestBackfillsMinimalStockBasicIdentityIdempotently() {
        stockBasicMapper.insert(StockBasicDO.builder()
                .canonicalSymbol("SH.600519")
                .symbol("600519")
                .name("贵州茅台OLD")
                .market("SH")
                .listDate(LocalDate.of(2001, 8, 27))
                .delisted(false)
                .build());

        service.ingest(command());
        service.ingest(command());

        Map<String, Object> identity = jdbcTemplate.queryForMap(
                "SELECT canonical_symbol, name, list_date FROM stock_basic WHERE canonical_symbol='SH.600519'");
        assertThat(identity.get("canonical_symbol")).isEqualTo("SH.600519");
        assertThat(identity.get("name")).isEqualTo("贵州茅台OLD");
        assertThat((java.sql.Date) identity.get("list_date")).isEqualTo(java.sql.Date.valueOf("2001-08-27"));
        // 样本内另一证券被回填、样本外（SH.601398）不回填；基准不参与 ensureRegistered（CR-1）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_basic WHERE canonical_symbol='SZ.000001'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_basic WHERE canonical_symbol='SH.601398'", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_basic WHERE canonical_symbol='SH.000001'", Long.class)).isZero();
    }

    // ==================== helpers ====================

    private Long tencentBarCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM stock_daily_bar WHERE data_source='TENCENT_PUBLIC'", Long.class);
    }

    private static JsonNode loadFixture() {
        try (InputStream input = Mr0PocIngestServiceTest.class.getResourceAsStream(
                "/mr0/mr0-public-probe-fixtures.json")) {
            if (input == null) {
                throw new IllegalStateException("fixture 不存在: src/test/resources/mr0/mr0-public-probe-fixtures.json");
            }
            return OBJECT_MAPPER.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("fixture 读取失败", exception);
        }
    }

    /** fixture 打桩：只覆写 HTTP 层，解析逻辑走真实实现；零联网。 */
    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        public FixtureBackedPublicClient fixtureBackedPublicClient() {
            return new FixtureBackedPublicClient(FIXTURE);
        }
    }

    static class FixtureBackedPublicClient extends PublicMarketDataClient {
        private final JsonNode fixture;
        private final Map<String, String> amountOverrides = new HashMap<>();

        FixtureBackedPublicClient(JsonNode fixture) {
            this.fixture = fixture;
        }

        /** M1 用：改写某日成交额（万元）证明 upsert 更新路径。 */
        void overrideDailyBarAmount(String tencentCode, String date, String newAmountTenThousand) {
            amountOverrides.put(tencentCode + "|" + date, newAmountTenThousand);
        }

        @Override
        protected String httpGet(String url, Charset charset) {
            if (url.contains("newSinaHy.php")) {
                return fixture.path("sinaIndustryCatalog").asText();
            }
            if (url.contains("node=hs_a")) {
                return pageOf(fixture.path("sinaUniverse"), pageOf(url));
            }
            if (url.contains("node=")) {
                String industryCode = url.substring(url.indexOf("node=") + "node=".length());
                return pageOf(fixture.path("sinaIndustryMembers").path(industryCode), pageOf(url));
            }
            if (url.contains("newfqkline")) {
                String code = url.substring(url.indexOf("param=") + "param=".length(), url.indexOf(','));
                ArrayNode day = (ArrayNode) fixture.path("tencentDailyBars").path(code);
                for (JsonNode row : day) {
                    String override = amountOverrides.get(code + "|" + row.path(0).asText());
                    if (override != null) {
                        ((ArrayNode) row).set(8, OBJECT_MAPPER.getNodeFactory().textNode(override));
                    }
                }
                ObjectNode root = OBJECT_MAPPER.createObjectNode();
                root.put("code", 0);
                root.put("msg", "");
                root.putObject("data").putObject(code).set("day", day);
                return root.toString();
            }
            if (url.contains("ssl_qsfx_zjlrqs")) {
                String code = url.substring(url.indexOf("daima=") + "daima=".length());
                return fixture.path("sinaMoneyFlow").path(code).toString();
            }
            throw new IllegalArgumentException("fixture 未覆盖的请求: " + url);
        }

        private static int pageOf(String url) {
            String marker = "page=";
            int start = url.indexOf(marker) + marker.length();
            return Integer.parseInt(url.substring(start, url.indexOf('&', start)));
        }

        private static String pageOf(JsonNode array, int page) {
            int num = 100;
            int from = (page - 1) * num;
            if (from >= array.size()) {
                return "[]";
            }
            int to = Math.min(from + num, array.size());
            ArrayNode slice = OBJECT_MAPPER.createArrayNode();
            for (int i = from; i < to; i++) {
                slice.add(array.get(i));
            }
            return slice.toString();
        }
    }
}
