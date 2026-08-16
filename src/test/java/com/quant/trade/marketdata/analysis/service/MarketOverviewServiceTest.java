package com.quant.trade.marketdata.analysis.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MR-1A 市场全景 Service 聚焦测试：Spring + H2 直插种子（事务回滚、零联网），覆盖完整响应、
 * 数据边界标签完整性、官方资金流 UNAVAILABLE、无数据/空样本/行业映射缺失的业务状态与全部参数
 * 异常。断言对照冻结公式与 MR-0 指标字典口径（不复制生产实现）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketOverviewServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 26);
    private static final LocalDate END = LocalDate.of(2026, 7, 1);
    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 8, 15, 10, 0);

    @Autowired
    private MarketOverviewService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 1/14/15：完整响应、数据范围与 Provider 标签、资金流 UNAVAILABLE ====================

    @Test
    void overviewReturnsFullResponseWithScopeAndProviderLabels() {
        seedBenchmarkAndStocks();  // 120 个合格交易日 + 样本 bar：门禁全过
        seedUniverse("SH.600519", "SZ.000001", "SH.601318");
        seedMembership("new_blhy", "玻璃行业", "SH.600519", "SZ.000001");
        seedMembership("new_yysw", "医药生物", "SH.601318");

        MarketOverviewVO.Overview overview = service.overview("CN", START, END);

        MarketOverviewVO.Metadata metadata = overview.metadata();
        assertThat(metadata.market()).isEqualTo("CN");
        assertThat(metadata.startDate()).isEqualTo(START);
        assertThat(metadata.endDate()).isEqualTo(END);
        assertThat(metadata.dataAsOf()).isEqualTo(END);
        assertThat(metadata.dataScope()).isEqualTo("SAMPLE");       // 必须是 SAMPLE，不是全市场
        assertThat(metadata.sampleSize()).isEqualTo(3);
        assertThat(metadata.benchmarkSymbol()).isEqualTo("SH.000001");
        assertThat(metadata.providerCodes()).containsExactly("SINA_PUBLIC", "TENCENT_PUBLIC");
        assertThat(metadata.taxonomyCode()).isEqualTo("SINA_INDUSTRY");
        assertThat(metadata.barCoverage()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(metadata.membershipCoverage()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(metadata.qualifiedTradingDays()).isEqualTo(120);
        assertThat(metadata.qualityStatus()).isEqualTo("OK");
        // limitations 四条边界声明逐条在位
        assertThat(metadata.limitations()).anyMatch(text -> text.contains("不是全市场"));
        assertThat(metadata.limitations()).anyMatch(text -> text.contains("SINA_INDUSTRY") && text.contains("PIT 申万"));
        assertThat(metadata.limitations()).anyMatch(text -> text.contains("时点穿越"));
        assertThat(metadata.limitations()).anyMatch(text -> text.contains("官方口径"));
        // 官方资金流 UNAVAILABLE：只声明不可用，绝不出现推算资金流字段
        assertThat(metadata.unavailableMetrics()).containsExactly("OFFICIAL_MONEY_FLOW");

        // 五类核心证据序列非空；基准序列含预热均线（120 个合格交易日：MA60/60 日基线均可计算）
        assertThat(overview.benchmarkSeries()).hasSize(4);
        assertThat(overview.benchmarkSeries().get(3).ma20()).isNotNull();
        assertThat(overview.benchmarkSeries().get(3).ma60()).isNotNull();
        assertThat(overview.activitySeries()).hasSize(4);
        assertThat(overview.activitySeries().get(3).turnoverMedian60()).isNotNull();
        assertThat(overview.breadthSeries()).hasSize(4);
        assertThat(overview.liquidityProxySeries().days()).hasSize(4);
        assertThat(overview.liquidityProxySeries().unit()).isEqualTo("1/元");
        assertThat(overview.industryTurnoverMigration()).isNotEmpty();

        // 质量块：Provider 归属 4 项、假设声明与不可用指标
        MarketOverviewVO.QualityBlock quality = overview.quality();
        assertThat(quality.providerAttribution()).hasSize(4);
        assertThat(quality.providerAttribution()).allSatisfy(attribution ->
                assertThat(attribution.providers()).isNotEmpty());
        assertThat(quality.assumptions()).anyMatch(text -> text.contains("INDEX_KLINE_DERIVED"));
        assertThat(quality.assumptions()).anyMatch(text -> text.contains("NONE 复权"));
        assertThat(quality.unavailableMetrics()).containsExactly("OFFICIAL_MONEY_FLOW");
        // 响应中不存在任何资金流数值字段（不可用不得伪装为 0）
        assertThat(overview).hasNoNullFieldsOrProperties();
    }

    @Test
    void industryMigrationExcludesUnmappedStockFromDenominator() {
        seedBenchmarkAndStocks();
        seedUniverse("SH.600519", "SZ.000001", "SH.601398");  // SH.601398 无映射
        seedMembership("new_blhy", "玻璃行业", "SH.600519", "SZ.000001");

        MarketOverviewVO.Overview overview = service.overview("CN", START, END);

        assertThat(overview.industryTurnoverMigration()).isNotEmpty();
        assertThat(overview.quality().coverageGap().uncoveredSampleStocks()).isEqualTo(1);
        assertThat(overview.quality().coverageGap().symbols()).containsExactly("SH.601398");
        // 未映射股成交额 = 窗口 4 日 × 1000
        assertThat(overview.quality().coverageGap().uncoveredTurnoverAmount())
                .isEqualByComparingTo(new BigDecimal("4000"));
        // 占比分母只含覆盖域：每日行业占比（含 OTHER）之和 = 1±1e-6
        List<LocalDate> days = overview.industryTurnoverMigration().stream()
                .map(MarketOverviewVO.IndustryMigrationRow::tradeDate).distinct().toList();
        for (LocalDate day : days) {
            BigDecimal sum = overview.industryTurnoverMigration().stream()
                    .filter(row -> row.tradeDate().equals(day))
                    .map(MarketOverviewVO.IndustryMigrationRow::turnoverShare)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum.subtract(BigDecimal.ONE).abs()).isLessThanOrEqualTo(new BigDecimal("0.000001"));
        }
        assertThat(overview.metadata().qualityStatus()).isEqualTo("DEGRADED");
        // 2/3=0.666667 < 告警阈值 0.90：M-22 覆盖门禁 WARN，整体状态降级（绝不返回 OK）
        assertThat(overview.metadata().membershipCoverage())
                .isEqualByComparingTo(new BigDecimal("0.666667"));
        assertThat(overview.quality().qualityFindings())
                .anyMatch(finding -> "LOW_MEMBERSHIP_COVERAGE".equals(finding.code())
                        && "WARN".equals(finding.severity()));
    }

    // ==================== 16：无数据 / 空样本 / 行业映射整体缺失 ====================

    @Test
    void returnsNoDataStatusWhenWindowHasNoBenchmark() {
        MarketOverviewVO.Overview overview = service.overview("CN",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        assertThat(overview.metadata().qualityStatus()).isEqualTo("NO_DATA");
        assertThat(overview.metadata().dataAsOf()).isNull();
        assertThat(overview.benchmarkSeries()).isEmpty();
        assertThat(overview.activitySeries()).isEmpty();
        assertThat(overview.breadthSeries()).isEmpty();
        assertThat(overview.liquidityProxySeries().days()).isEmpty();
        assertThat(overview.industryTurnoverMigration()).isEmpty();
        assertThat(overview.quality().qualityFindings())
                .anyMatch(finding -> "BENCHMARK_DATA_MISSING".equals(finding.code()));
    }

    @Test
    void reportsDegradedWhenSampleUniverseMissing() {
        seedBenchmarkAndStocks();  // 只有基准与个股 bar，无证券池快照
        MarketOverviewVO.Overview overview = service.overview("CN", START, END);
        assertThat(overview.metadata().sampleSize()).isZero();
        assertThat(overview.metadata().qualityStatus()).isEqualTo("DEGRADED");
        assertThat(overview.quality().qualityFindings())
                .anyMatch(finding -> "EMPTY_SAMPLE".equals(finding.code()));
        assertThat(overview.benchmarkSeries()).hasSize(4);  // 基准仍可用
        assertThat(overview.activitySeries().get(0).validStocks()).isZero();
    }

    @Test
    void reportsIndustryMappingMissingWhenNoMembershipAtAll() {
        seedBenchmarkAndStocks();
        seedUniverse("SH.600519", "SZ.000001");
        MarketOverviewVO.Overview overview = service.overview("CN", START, END);
        assertThat(overview.metadata().membershipCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(overview.industryTurnoverMigration()).isEmpty();
        assertThat(overview.quality().qualityFindings())
                .anyMatch(finding -> "INDUSTRY_MAPPING_MISSING".equals(finding.code()));
        assertThat(overview.quality().qualityFindings())
                .anyMatch(finding -> "INDUSTRY_MIGRATION_BLOCKED".equals(finding.code()));
        assertThat(overview.quality().coverageGap().uncoveredSampleStocks()).isEqualTo(2);
        assertThat(overview.metadata().qualityStatus()).isEqualTo("DEGRADED");
    }

    @Test
    void degradesWhenQualifiedTradingDaysBelowMidTermGate() {
        // 短预热夹具：窗口内基准+样本齐备但仅 25 个真实交易日（跳过周末）→ INSUFFICIENT_WARMUP WARN + DEGRADED
        for (LocalDate date : tradingDaysBack(END, 25)) {
            seedBar("SH.000001", date, "3000", "300000");
            seedBar("SH.600519", date, "10", "1000");
        }
        seedUniverse("SH.600519");
        seedMembership("new_blhy", "玻璃行业", "SH.600519");
        MarketOverviewVO.Overview overview = service.overview("CN", START, END);
        assertThat(overview.metadata().qualifiedTradingDays()).isEqualTo(25);
        assertThat(overview.quality().qualityFindings())
                .anyMatch(finding -> "INSUFFICIENT_WARMUP".equals(finding.code())
                        && "WARN".equals(finding.severity()));
        assertThat(overview.metadata().qualityStatus()).isEqualTo("DEGRADED");
        // 短期序列保留：MA60/60 日基线不足继续为 null，不得填 0
        assertThat(overview.benchmarkSeries().get(3).ma20()).isNotNull();
        assertThat(overview.benchmarkSeries().get(3).ma60()).isNull();
        assertThat(overview.activitySeries().get(3).turnoverMedian60()).isNull();
        assertThat(overview.activitySeries().get(3).activityRatio()).isNotNull();
    }

    // ==================== 16（参数异常）：全部 400 VALIDATION_ERROR，禁止 500 ====================

    @Test
    void rejectsInvalidParametersWithValidationError() {
        assertValidationError(null, START, END, "market 不能为空");
        assertValidationError(" ", START, END, "market 不能为空");
        assertValidationError("US", START, END, "仅支持 CN");
        assertValidationError("HK", START, END, "仅支持 CN");
        assertValidationError("CN", null, END, "start 不能为空");
        assertValidationError("CN", START, null, "end 不能为空");
        assertValidationError("CN", END, START, "start 不能晚于 end");
        assertValidationError("CN", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 6, 1), "跨度不能超过");
    }

    private void assertValidationError(String market, LocalDate start, LocalDate end, String messagePart) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.overview(market, start, end));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCodeEnum.VALIDATION_ERROR);
        assertThat(exception.getMessage()).contains(messagePart);
    }

    // ==================== helpers（H2 直插种子，事务回滚；交易日跳过周末） ====================

    /** 自 end 起向前取 count 个交易日（跳过周六/周日；测试日历不含节假日，升序返回）。 */
    private static List<LocalDate> tradingDaysBack(LocalDate end, int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate date = end;
        while (days.size() < count) {
            java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek != java.time.DayOfWeek.SATURDAY && dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                days.add(date);
            }
            date = date.minusDays(1);
        }
        java.util.Collections.reverse(days);
        return days;
    }

    /** 基准 + 3 只样本股：END 前 120 个真实交易日（跳过周末，恰好满足中期结论预热门禁下限）。 */
    private void seedBenchmarkAndStocks() {
        for (LocalDate date : tradingDaysBack(END, 120)) {
            seedBar("SH.000001", date, "3000", "300000");
            seedBar("SH.600519", date, "10", "1000");
            seedBar("SZ.000001", date, "20", "1000");
            seedBar("SH.601318", date, "30", "1000");
            seedBar("SH.601398", date, "8", "1000");
        }
    }

    private void seedBar(String symbol, LocalDate date, String close, String amount) {
        BigDecimal closePrice = new BigDecimal(close);
        jdbcTemplate.update("INSERT INTO stock_daily_bar(canonical_symbol, trade_date, adjust_type,"
                        + " data_source, open_price, high_price, low_price, close_price, volume, amount, fetched_at)"
                        + " VALUES (?, ?, 'NONE', 'TENCENT_PUBLIC', ?, ?, ?, ?, 100000, ?, ?)", symbol, date,
                closePrice.subtract(BigDecimal.ONE), closePrice.add(BigDecimal.ONE),
                closePrice.subtract(BigDecimal.ONE), closePrice, new BigDecimal(amount), FETCHED);
    }

    private void seedUniverse(String... symbols) {
        for (String symbol : symbols) {
            jdbcTemplate.update("INSERT INTO mr0_universe_snapshot(provider_code, canonical_symbol, symbol,"
                            + " name, market, turnover_rate, circulating_market_cap, as_of_date, fetched_at)"
                            + " VALUES ('SINA_PUBLIC', ?, ?, ?, 'SH', 0.01, 1000000, '2026-08-15', ?)",
                    symbol, symbol.substring(3), "股票" + symbol.substring(3), FETCHED);
        }
    }

    private void seedMembership(String industryCode, String industryName, String... symbols) {
        for (String symbol : symbols) {
            jdbcTemplate.update("INSERT INTO mr0_industry_membership(taxonomy_code, provider_code,"
                            + " industry_code, industry_name, canonical_symbol, as_of_date, fetched_at)"
                            + " VALUES ('SINA_INDUSTRY', 'SINA_PUBLIC', ?, ?, ?, '2026-05-01', ?)",
                    industryCode, industryName, symbol, FETCHED);
        }
    }
}
