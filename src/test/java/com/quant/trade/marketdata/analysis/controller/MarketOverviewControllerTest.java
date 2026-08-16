package com.quant.trade.marketdata.analysis.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MR-1A 市场全景 Controller 聚焦测试：MockMvc + H2 种子（事务回滚），覆盖正常 200 响应、
 * 全部参数异常（缺失/非 CN/顺序/跨度/畸形日期 → 400 VALIDATION_ERROR envelope）与
 * 无数据窗口的 200 + NO_DATA 业务状态（禁止 500）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarketOverviewControllerTest {

    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 8, 15, 10, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 120 个真实交易日（自 2026-07-01 周三向前、跳过周末）：预热门禁下限，样本覆盖 1.0
        LocalDate date = LocalDate.of(2026, 7, 1);
        int seeded = 0;
        while (seeded < 120) {
            java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek != java.time.DayOfWeek.SATURDAY && dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                seedBar("SH.000001", date, "3000", "300000");
                seedBar("SH.600519", date, "10", "1000");
                seeded++;
            }
            date = date.minusDays(1);
        }
        jdbcTemplate.update("INSERT INTO mr0_universe_snapshot(provider_code, canonical_symbol, symbol,"
                + " name, market, turnover_rate, circulating_market_cap, as_of_date, fetched_at)"
                + " VALUES ('SINA_PUBLIC', 'SH.600519', '600519', '股票600519', 'SH', 0.01, 1000000,"
                + " '2026-08-15', ?)", FETCHED);
        jdbcTemplate.update("INSERT INTO mr0_industry_membership(taxonomy_code, provider_code, industry_code,"
                + " industry_name, canonical_symbol, as_of_date, fetched_at)"
                + " VALUES ('SINA_INDUSTRY', 'SINA_PUBLIC', 'new_blhy', '玻璃行业', 'SH.600519',"
                + " '2026-05-01', ?)", FETCHED);
    }

    @Test
    void returnsOverviewWithFiveEvidenceSeriesAndScopeLabels() throws Exception {
        mockMvc.perform(get("/api/v1/market-research/overview")
                        .param("market", "CN")
                        .param("start", "2026-06-26")   // 周五：窗口=06-26/06-29/06-30/07-01 共 4 个交易日
                        .param("end", "2026-07-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.metadata.market").value("CN"))
                .andExpect(jsonPath("$.data.metadata.dataScope").value("SAMPLE"))
                .andExpect(jsonPath("$.data.metadata.sampleSize").value(1))
                .andExpect(jsonPath("$.data.metadata.benchmarkSymbol").value("SH.000001"))
                .andExpect(jsonPath("$.data.metadata.providerCodes[0]").value("SINA_PUBLIC"))
                .andExpect(jsonPath("$.data.metadata.providerCodes[1]").value("TENCENT_PUBLIC"))
                .andExpect(jsonPath("$.data.metadata.taxonomyCode").value("SINA_INDUSTRY"))
                .andExpect(jsonPath("$.data.metadata.barCoverage").value(1.0))
                .andExpect(jsonPath("$.data.metadata.membershipCoverage").value(1.0))
                .andExpect(jsonPath("$.data.metadata.qualifiedTradingDays").value(120))
                .andExpect(jsonPath("$.data.metadata.qualityStatus").value("OK"))
                .andExpect(jsonPath("$.data.metadata.unavailableMetrics[0]").value("OFFICIAL_MONEY_FLOW"))
                .andExpect(jsonPath("$.data.metadata.limitations.length()").value(4))
                .andExpect(jsonPath("$.data.benchmarkSeries.length()").value(4))
                .andExpect(jsonPath("$.data.activitySeries.length()").value(4))
                .andExpect(jsonPath("$.data.breadthSeries.length()").value(4))
                .andExpect(jsonPath("$.data.liquidityProxySeries.unit").value("1/元"))
                .andExpect(jsonPath("$.data.liquidityProxySeries.days.length()").value(4))
                .andExpect(jsonPath("$.data.industryTurnoverMigration.length()").value(4))  // 每日 1 行业
                .andExpect(jsonPath("$.data.quality.providerAttribution.length()").value(4))
                .andExpect(jsonPath("$.data.quality.unavailableMetrics[0]").value("OFFICIAL_MONEY_FLOW"));
    }

    @Test
    void returnsNoDataBusinessStatusForEmptyWindow() throws Exception {
        mockMvc.perform(get("/api/v1/market-research/overview")
                        .param("market", "CN")
                        .param("start", "2025-01-01")
                        .param("end", "2025-01-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.metadata.qualityStatus").value("NO_DATA"))
                .andExpect(jsonPath("$.data.benchmarkSeries.length()").value(0));
    }

    @Test
    void rejectsAllInvalidParametersWithBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/market-research/overview")
                        .param("start", "2026-06-28").param("end", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/market-research/overview")
                        .param("market", "US")
                        .param("start", "2026-06-28").param("end", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/market-research/overview")
                        .param("market", "CN").param("end", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/market-research/overview")
                        .param("market", "CN")
                        .param("start", "2026-07-01").param("end", "2026-06-28"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/market-research/overview")
                        .param("market", "CN")
                        .param("start", "2025-01-01").param("end", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMalformedDateAsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/market-research/overview")
                        .param("market", "CN")
                        .param("start", "2026-13-01").param("end", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void seedBar(String symbol, LocalDate date, String close, String amount) {
        BigDecimal closePrice = new BigDecimal(close);
        jdbcTemplate.update("INSERT INTO stock_daily_bar(canonical_symbol, trade_date, adjust_type,"
                        + " data_source, open_price, high_price, low_price, close_price, volume, amount, fetched_at)"
                        + " VALUES (?, ?, 'NONE', 'TENCENT_PUBLIC', ?, ?, ?, ?, 100000, ?, ?)", symbol, date,
                closePrice.subtract(BigDecimal.ONE), closePrice.add(BigDecimal.ONE),
                closePrice.subtract(BigDecimal.ONE), closePrice, new BigDecimal(amount), FETCHED);
    }
}
