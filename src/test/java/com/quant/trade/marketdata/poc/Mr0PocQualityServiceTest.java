package com.quant.trade.marketdata.poc;

import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisResult;
import com.quant.trade.marketdata.poc.Mr0PocQualityService.CheckFamily;
import com.quant.trade.marketdata.poc.Mr0PocQualityService.QualityReport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-06 聚焦测试（冻结五用例，TEST-06）。八检查族结构化断言（REC-8）、VWAP 单位异常、
 * 跨源重复与 Provider 混用 FAIL、时点穿越标记、以及嵌套 MockMvc 证明 analyze/report 零外联
 * （PublicMarketDataClient 为 fail-if-invoked 桩）。零联网，事务回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Mr0PocQualityServiceTest {

    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 8, 15, 10, 0);

    @Autowired
    private Mr0PocAnalysisService analysisService;
    @Autowired
    private Mr0PocQualityService qualityService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;

    /** fail-if-invoked 桩：任何 HTTP 级外联立即失败（零外联证明）。 */
    @TestConfiguration
    static class NoPublicClientConfig {
        @Bean
        @Primary
        public PublicMarketDataClient failIfInvokedClient() {
            return new PublicMarketDataClient() {
                @Override
                protected String httpGet(String url, Charset charset) {
                    throw new AssertionError("PublicMarketDataClient must not be invoked: " + url);
                }
            };
        }
    }

    // ==================== M1 八检查族结构化对象（REC-8） ====================

    @Test
    @Transactional
    void qualityReportContainsAllEightCheckFamilies() {
        seedBase();

        QualityReport report = qualityService.generateReport(analysisService.analyze(analyzeCommand()));

        assertThat(report.getFamilies()).hasSize(8);
        assertThat(report.getFamilies()).extracting(CheckFamily::getFamily).containsExactly(
                "COVERAGE", "GAPS", "DUPLICATES", "STALENESS", "TIME_POINT_LOOKAHEAD",
                "PROVIDER_MIXING", "UNIT_ANOMALY", "RECOMPUTE_CONSISTENCY");
        for (CheckFamily family : report.getFamilies()) {
            assertThat(family.getStatus()).isIn("OK", "WARN", "FAIL", "BLOCKED");
            assertThat(family.getReasonCode()).isNotBlank();
            assertThat(family.getDetails()).isNotNull();
        }
        String markdown = report.toMarkdown();
        for (String family : List.of("COVERAGE", "GAPS", "DUPLICATES", "STALENESS",
                "TIME_POINT_LOOKAHEAD", "PROVIDER_MIXING", "UNIT_ANOMALY", "RECOMPUTE_CONSISTENCY")) {
            assertThat(markdown).contains("## " + family);
        }
        // markdown 确定性：同数据两次生成一致，且不含时间戳字段
        assertThat(qualityService.generateReport(analysisService.analyze(analyzeCommand())).toMarkdown())
                .isEqualTo(markdown);
        assertThat(markdown).doesNotContain("generatedAt").doesNotContain("fetchedAt");
    }

    // ==================== M2 VWAP 单位异常（字典 §3，万元误存负例） ====================

    @Test
    @Transactional
    void unitAnomalyDetectsVwapOutsideLowHigh() {
        seedBase();
        // amount=万元误存（未 ×10000）：vwap=503383.82/4247400≈0.12，远低于 low=99 → 异常
        jdbcTemplate.update("INSERT INTO stock_daily_bar(canonical_symbol, trade_date, adjust_type,"
                + " data_source, open_price, high_price, low_price, close_price, volume, amount, fetched_at)"
                + " VALUES ('SH.688001', '2026-07-01', 'NONE', 'TENCENT_PUBLIC', 100, 101, 99, 100, 4247400,"
                + " 503383.82, ?)", FETCHED);

        QualityReport report = qualityService.generateReport(analysisService.analyze(analyzeCommand()));

        CheckFamily unitAnomaly = family(report, "UNIT_ANOMALY");
        assertThat(unitAnomaly.getAffectedCount()).isGreaterThanOrEqualTo(1L);
        assertThat(unitAnomaly.getStatus()).isEqualTo("FAIL");
        assertThat(unitAnomaly.getDetails()).anySatisfy(detail ->
                assertThat(detail).contains("SH.688001").contains("vwap="));
    }

    // ==================== M3 跨源重复 + Provider 标注缺失 FAIL ====================

    @Test
    @Transactional
    void duplicateAndProviderMixingAreFlagged() {
        seedBase();
        jdbcTemplate.update("INSERT INTO stock_daily_bar(canonical_symbol, trade_date, adjust_type,"
                + " data_source, open_price, high_price, low_price, close_price, volume, amount, fetched_at)"
                + " VALUES ('SH.600519', '2026-07-01', 'NONE', 'CSV', 10, 11, 9, 10.5, 100000, 1000000, ?)",
                FETCHED);

        AnalysisResult analysis = analysisService.analyze(analyzeCommand());
        QualityReport report = qualityService.generateReport(analysis);

        // 同键 CSV+TENCENT 两行 → DUPLICATES affectedCount=2（跨源键行数）
        CheckFamily duplicates = family(report, "DUPLICATES");
        assertThat(duplicates.getAffectedCount()).isEqualTo(2L);
        assertThat(duplicates.getDetails()).anySatisfy(detail ->
                assertThat(detail).contains("SH.600519|2026-07-01").contains("CSV"));

        // 完整标注 → PROVIDER_MIXING OK；注入缺失标注 → FAIL
        assertThat(family(report, "PROVIDER_MIXING").getStatus()).isEqualTo("OK");
        analysis.getBreadth().setProviders(null);
        CheckFamily mixing = family(qualityService.generateReport(analysis), "PROVIDER_MIXING");
        assertThat(mixing.getStatus()).isEqualTo("FAIL");
        assertThat(mixing.getAffectedCount()).isPositive();
        assertThat(mixing.getReasonCode()).isEqualTo("MISSING_PROVIDER_ATTRIBUTION");
    }

    // ==================== M4 陈旧成分=非时点（TIME_POINT_LOOKAHEAD） ====================

    @Test
    @Transactional
    void staleMembershipIsFlaggedAsNotPointInTime() {
        seedBase();
        jdbcTemplate.update("INSERT INTO mr0_industry_membership(taxonomy_code, provider_code,"
                + " industry_code, industry_name, canonical_symbol, as_of_date, fetched_at)"
                + " VALUES ('SINA_INDUSTRY', 'SINA_PUBLIC', 'new_blhy', '玻璃行业', 'SH.600519', '2026-08-15', ?)",
                FETCHED);

        AnalysisResult analysis = analysisService.analyze(analyzeCommand());
        QualityReport report = qualityService.generateReport(analysis);

        CheckFamily lookahead = family(report, "TIME_POINT_LOOKAHEAD");
        assertThat(lookahead.getAffectedCount()).isPositive();  // 08-15 成分聚合 07 月窗口
        assertThat(lookahead.getDetails()).anySatisfy(detail ->
                assertThat(detail).contains("时点穿越"));
        // 分析侧同步标记
        assertThat(analysis.getIndustryTurnover().getByIndustry())
                .allSatisfy(industry -> assertThat(industry.getDays())
                        .allSatisfy(day -> assertThat(day.isLookaheadAffected()).isTrue()));
    }

    // ==================== M5 零外联（MockMvc，TD-06-M5b） ====================

    @Nested
    class Mr0PocEndpointIsolation {
        @Test
        void analyzeAndReportDoNotInvokePublicClient() throws Exception {
            mockMvc.perform(post("/api/v1/market-research/mr0-poc/ingest")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

            mockMvc.perform(get("/api/v1/market-research/mr0-poc/analyze")
                            .param("start", "2026-07-01").param("end", "2026-07-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.universe.status").value("EMPTY_VALID_UNIVERSE"))
                    .andExpect(jsonPath("$.data.analysisContentHash").isNotEmpty());

            mockMvc.perform(get("/api/v1/market-research/mr0-poc/report")
                            .param("start", "2026-07-01").param("end", "2026-07-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.families[?(@.family=='RECOMPUTE_CONSISTENCY')].status")
                            .value(org.hamcrest.Matchers.hasItem("BLOCKED")));

            mockMvc.perform(get("/api/v1/market-research/mr0-poc/report")
                            .param("start", "2026-07-01").param("end", "2026-07-31")
                            .param("format", "markdown"))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentType())
                            .contains("text/markdown"))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                            .contains("# MR-0 PoC 质量报告"));
        }
    }

    // ==================== helpers ====================

    private com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisCommand analyzeCommand() {
        return com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisCommand.builder()
                .analysisStart(LocalDate.of(2026, 7, 1)).analysisEnd(LocalDate.of(2026, 7, 31)).build();
    }

    private CheckFamily family(QualityReport report, String name) {
        return report.getFamilies().stream().filter(f -> f.getFamily().equals(name)).findFirst().orElseThrow();
    }

    private void seedBase() {
        jdbcTemplate.update("INSERT INTO stock_daily_bar(canonical_symbol, trade_date, adjust_type,"
                + " data_source, open_price, high_price, low_price, close_price, volume, amount, fetched_at)"
                + " VALUES ('SH.000001', '2026-06-30', 'NONE', 'TENCENT_PUBLIC', 3000, 3100, 2950, 3050, 1000, 3000000, ?)", FETCHED);
        jdbcTemplate.update("INSERT INTO stock_daily_bar(canonical_symbol, trade_date, adjust_type,"
                + " data_source, open_price, high_price, low_price, close_price, volume, amount, fetched_at)"
                + " VALUES ('SH.000001', '2026-07-01', 'NONE', 'TENCENT_PUBLIC', 3000, 3100, 2950, 3060, 1000, 3000000, ?)", FETCHED);
        seedStock("SH.600519", "2026-06-30", "10");
        seedStock("SH.600519", "2026-07-01", "11");
        seedStock("SZ.000001", "2026-06-30", "20");
        seedStock("SZ.000001", "2026-07-01", "20");
        jdbcTemplate.update("INSERT INTO mr0_universe_snapshot(provider_code, canonical_symbol, symbol,"
                + " name, market, turnover_rate, as_of_date, fetched_at) VALUES ('SINA_PUBLIC', 'SH.600519',"
                + " '600519', '股票600519', 'SH', 0.01, '2026-07-01', ?)", FETCHED);
        jdbcTemplate.update("INSERT INTO mr0_universe_snapshot(provider_code, canonical_symbol, symbol,"
                + " name, market, turnover_rate, as_of_date, fetched_at) VALUES ('SINA_PUBLIC', 'SZ.000001',"
                + " '000001', '股票000001', 'SZ', 0.01, '2026-07-01', ?)", FETCHED);
        jdbcTemplate.update("INSERT INTO mr0_industry_membership(taxonomy_code, provider_code, industry_code,"
                + " industry_name, canonical_symbol, as_of_date, fetched_at) VALUES ('SINA_INDUSTRY',"
                + " 'SINA_PUBLIC', 'new_blhy', '玻璃行业', 'SH.600519', '2026-06-01', ?)", FETCHED);
        jdbcTemplate.update("INSERT INTO mr0_industry_membership(taxonomy_code, provider_code, industry_code,"
                + " industry_name, canonical_symbol, as_of_date, fetched_at) VALUES ('SINA_INDUSTRY',"
                + " 'SINA_PUBLIC', 'new_blhy', '玻璃行业', 'SZ.000001', '2026-06-01', ?)", FETCHED);
        jdbcTemplate.update("INSERT INTO mr0_stock_money_flow_daily(canonical_symbol, trade_date,"
                + " provider_code, main_net_inflow, industry_net_inflow, fetched_at) VALUES ('SH.600519',"
                + " '2026-07-01', 'SINA_PUBLIC', 100, 350, ?)", FETCHED);
    }

    private void seedStock(String symbol, String date, String close) {
        jdbcTemplate.update("INSERT INTO stock_daily_bar(canonical_symbol, trade_date, adjust_type,"
                + " data_source, open_price, high_price, low_price, close_price, volume, amount, fetched_at)"
                + " VALUES (?, ?, 'NONE', 'TENCENT_PUBLIC', ?, ?, ?, ?, 100000, 1000000, ?)", symbol,
                LocalDate.parse(date), new java.math.BigDecimal(close).subtract(java.math.BigDecimal.ONE),
                new java.math.BigDecimal(close).add(java.math.BigDecimal.ONE),
                new java.math.BigDecimal(close).subtract(java.math.BigDecimal.ONE),
                new java.math.BigDecimal(close), FETCHED);
    }
}
