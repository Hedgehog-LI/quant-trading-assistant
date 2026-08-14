package com.quant.trade.marketdata.poc;

import com.quant.trade.marketdata.model.StockDailyBarDO;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisCommand;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisResult;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.DailyBreadth;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.IndustryTurnoverBlock;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.MoneyFactsBlock;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.UniverseBlock;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.VolatilityBlock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-05 聚焦测试（冻结七用例，TEST-05）。数据经直接 SQL 构造（H2，事务回滚），零联网；
 * Mr0PocAnalysisService 仅依赖只读 mapper，PublicMarketDataClient 零参与。窗口与公式断言
 * 全部对照 docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md 冻结值（AMD-3）。
 * // frozen-selector: ./mvnw -q test -Dtest=Mr0PocAnalysisServiceTest (7 methods: marketBreadthMatchesDictionaryFormulas, industryTurnoverShareSumsWithinCoverage, moneyFlowIndustryDeviationIsReported, analysisBlocksWhenWarmupInsufficient, volatilityAndLiquidityProxyMatchDictionaryFormulas, everyAnalysisMetricCarriesSingleProviderAttribution, analysisRereadsStorageEachCall)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Mr0PocAnalysisServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);
    private static final LocalDate END = LocalDate.of(2026, 7, 31);
    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 8, 15, 10, 0);

    @Autowired
    private Mr0PocAnalysisService service;
    @Autowired
    private Mr0PocMapper mr0PocMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AnalysisCommand command() {
        return AnalysisCommand.builder().analysisStart(START).analysisEnd(END).build();
    }

    // ==================== M1 市场广度（字典 M-06/M-07/M-08） ====================

    @Test
    void marketBreadthMatchesDictionaryFormulas() {
        seedBenchmarks("2026-06-30", "2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04");
        seedStock("SH.600519", new String[] {"2026-06-30", "2026-07-01", "2026-07-02", "2026-07-03"},
                new String[] {"10", "11", "12", "10.5"});
        seedStock("SZ.000001", new String[] {"2026-06-30", "2026-07-01", "2026-07-02", "2026-07-03"},
                new String[] {"20", "19", "20.5", "18"});
        seedStock("SH.601318", new String[] {"2026-06-30", "2026-07-01", "2026-07-02", "2026-07-03"},
                new String[] {"30", "30", "30", "30"});
        seedUniverse("2026-07-01", "SH.600519", "SZ.000001", "SH.601318");

        List<DailyBreadth> daily = service.analyze(command()).getBreadth().getDaily();

        assertThat(daily).extracting(DailyBreadth::getDate)
                .containsExactly("2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04");
        DailyBreadth day1 = daily.get(0);  // A 涨、B 跌、C 平
        assertThat(day1.getAdvancing()).isEqualTo(1L);
        assertThat(day1.getDeclining()).isEqualTo(1L);
        assertThat(day1.getFlat()).isEqualTo(1L);
        assertThat(day1.getValidStocks()).isEqualTo(3L);
        assertThat(day1.getAdvanceRatio()).isEqualByComparingTo(new BigDecimal("0.3333333333"));
        assertThat(day1.getAdLine()).isEqualTo(0L);  // 首日种子 adv(t0)−dec(t0)
        DailyBreadth day2 = daily.get(1);  // A 涨、B 涨、C 平 → 累加 0+2−0
        assertThat(day2.getAdvancing()).isEqualTo(2L);
        assertThat(day2.getAdvanceRatio()).isEqualByComparingTo(new BigDecimal("0.6666666667"));
        assertThat(day2.getAdLine()).isEqualTo(2L);
        DailyBreadth day3 = daily.get(2);  // A 跌、B 跌、C 平 → 2+0−2
        assertThat(day3.getDeclining()).isEqualTo(2L);
        assertThat(day3.getAdvanceRatio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(day3.getAdLine()).isEqualTo(0L);
        DailyBreadth day4 = daily.get(3);  // 基准有 bar 而样本无 bar → 空有效池
        assertThat(day4.getValidStocks()).isZero();
        assertThat(day4.getStatus()).isEqualTo("EMPTY_VALID_UNIVERSE");
        assertThat(day4.getAdvanceRatio()).isNull();
        assertThat(day4.getAdLine()).isNull();
    }

    // ==================== M2 行业成交占比（字典 M-11/M-12，覆盖域 AMD-3） ====================

    @Test
    void industryTurnoverShareSumsWithinCoverage() {
        seedBenchmarks("2026-07-01", "2026-07-02");
        seedStockWithAmount("SH.600519", "2026-07-01", "10", "100");
        seedStockWithAmount("SH.600519", "2026-07-02", "10", "150");
        seedStockWithAmount("SZ.000001", "2026-07-01", "20", "200");
        seedStockWithAmount("SZ.000001", "2026-07-02", "20", "250");
        seedStockWithAmount("SH.601318", "2026-07-01", "30", "700");
        seedStockWithAmount("SH.601318", "2026-07-02", "30", "600");
        seedStockWithAmount("SH.601398", "2026-07-01", "8", "1000");  // 无成分：coverageGap，不入分母
        seedStockWithAmount("SH.601398", "2026-07-02", "8", "500");
        seedUniverse("2026-07-01", "SH.600519", "SZ.000001", "SH.601318", "SH.601398");
        seedMembership("2026-06-01", "new_blhy", "玻璃行业", "SH.600519", "SZ.000001");
        seedMembership("2026-06-01", "new_yysw", "医药生物", "SH.601318");

        IndustryTurnoverBlock turnover = service.analyze(command()).getIndustryTurnover();

        assertThat(turnover.getCoverageGap().getCount()).isEqualTo(1L);
        assertThat(turnover.getCoverageGap().getSymbols()).containsExactly("SH.601398");
        assertThat(turnover.getDailyMarket()).hasSize(2);
        assertThat(turnover.getDailyMarket().get(0).getMarketTurnover()).isEqualByComparingTo("1000");
        assertThat(turnover.getDailyMarket().get(1).getMarketTurnover()).isEqualByComparingTo("1000");
        // 逐日 Σshare=1±1e-6 于覆盖域
        for (var day : turnover.getDailyMarket()) {
            assertThat(day.getSumShare().subtract(BigDecimal.ONE).abs())
                    .isLessThanOrEqualTo(new BigDecimal("0.000001"));
        }
        assertThat(turnover.getByIndustry()).hasSize(2);
        var glass = turnover.getByIndustry().get(0);
        assertThat(glass.getIndustryName()).isEqualTo("玻璃行业");
        assertThat(glass.getDays().get(0).getSectorTurnover()).isEqualByComparingTo("300");
        assertThat(glass.getDays().get(0).getShare()).isEqualByComparingTo(new BigDecimal("0.3"));
        assertThat(glass.getDays().get(0).isLookaheadAffected()).isFalse();
        assertThat(turnover.getByIndustry().get(1).getDays().get(1).getShare())
                .isEqualByComparingTo(new BigDecimal("0.6"));
    }

    // ==================== M3 资金事实与 cate_na 偏差（字典 M-15，D9 只报偏差） ====================

    @Test
    void moneyFlowIndustryDeviationIsReported() {
        seedBenchmarks("2026-07-01", "2026-07-02");
        seedStockWithAmount("SH.600519", "2026-07-01", "10", "1000000");
        seedStockWithAmount("SH.600519", "2026-07-02", "10.5", "1000000");
        seedStockWithAmount("SZ.000001", "2026-07-01", "20", "1000000");
        seedStockWithAmount("SZ.000001", "2026-07-02", "20", "1000000");
        seedUniverse("2026-07-01", "SH.600519", "SZ.000001");
        seedMembership("2026-06-01", "new_blhy", "玻璃行业", "SH.600519", "SZ.000001");
        seedFlow("SH.600519", "2026-07-01", "100", "350");
        seedFlow("SZ.000001", "2026-07-01", "200", "350");
        seedFlow("SH.600519", "2026-07-02", "100", "350");
        seedFlow("SZ.000001", "2026-07-02", "100", "400");  // 成员 cate_na 不一致日

        MoneyFactsBlock money = service.analyze(command()).getMoneyFacts();

        assertThat(money.getByIndustry()).hasSize(1);
        var days = money.getByIndustry().get(0).getDays();
        assertThat(days).hasSize(2);
        assertThat(days.get(0).getSumMainNetInflow()).isEqualByComparingTo("300");
        assertThat(days.get(0).getCateNaValue()).isEqualByComparingTo("350");
        // deviation=Σ成员main_net_inflow−cate_na=300−350=−50（M-15 冻结绝对差，元；CR-4）
        assertThat(days.get(0).getDeviation()).isEqualByComparingTo(new BigDecimal("-50"));
        assertThat(days.get(0).isInconsistentCateNa()).isFalse();
        assertThat(days.get(1).isInconsistentCateNa()).isTrue();
        assertThat(days.get(1).getCateNaValue()).isEqualByComparingTo("350");  // 众数（并列取较小）
        assertThat(days.get(1).getDeviation()).isEqualByComparingTo(new BigDecimal("-150"));  // 200−350（绝对差）
        assertThat(money.getInconsistentCateNaDays()).isEqualTo(1L);
        // flowIntensity 混源：Σ净流入/Σ成交额=500/4000000
        assertThat(money.getFlowIntensity().getProviders())
                .containsExactly("SINA_PUBLIC", "TENCENT_PUBLIC");
        assertThat(money.getFlowIntensity().getValue())
                .isEqualByComparingTo(new BigDecimal("500").divide(new BigDecimal("4000000"), 12, RoundingMode.HALF_UP));
    }

    // ==================== M4 预热不足边界两侧（字典 M-19，TD-05-M4a/M4b） ====================

    @Test
    void analysisBlocksWhenWarmupInsufficient() {
        List<String> dates = new ArrayList<>();
        for (LocalDate date = LocalDate.of(2026, 6, 11); !date.isAfter(LocalDate.of(2026, 7, 1)); date = date.plusDays(1)) {
            dates.add(date.toString());
        }
        seedBenchmarks(dates.toArray(new String[0]));
        seedUniverse("2026-06-11", "SH.600519");
        String[] closes = new String[dates.size() - 1];  // 先少一根（20 根）
        for (int i = 1; i < dates.size(); i++) {
            closes[i - 1] = String.valueOf(100 + i);
            seedStockWithAmount("SH.600519", dates.get(i), closes[i - 1], "1000000");
        }

        VolatilityBlock blocked = service.analyze(command()).getVolatility();

        assertThat(blocked.getStatus()).isEqualTo("INSUFFICIENT_WARMUP");
        assertThat(blocked.getQualifiedStocks()).isZero();
        assertThat(blocked.getExcludedForWarmup()).isEqualTo(1L);
        assertThat(blocked.getMarketMedian()).isNull();
        assertThat(blocked.getMarketP90()).isNull();
        assertThat(blocked.getAsOfDate()).isNull();

        // 补齐第 21 根收盘（恰好最小观测）→ 成功有值。经 SLICE-02 mapper 写入
        // （jdbcTemplate 直插不经过 MyBatis，会留下一级缓存陈旧行集，违背重读语义）。
        mr0PocMapper.upsertStockDailyBarBatch(List.of(bar("SH.600519", dates.get(0), "100")));
        VolatilityBlock ready = service.analyze(command()).getVolatility();
        assertThat(ready.getStatus()).isEqualTo("OK");
        assertThat(ready.getQualifiedStocks()).isEqualTo(1L);
        assertThat(ready.getExcludedForWarmup()).isZero();
        assertThat(ready.getMarketMedian()).isNotNull();
        assertThat(ready.isAnnualized()).isFalse();

        // CR-6：21 根收盘但 asOf（末交易日 2026-07-01）当日无 bar 的陈旧窗口 → 不合格计入 excluded。
        // 与上方同理经 MyBatis 写入（jdbcTemplate 直插不刷新一级缓存，第二次分析看不到新行）。
        mr0PocMapper.upsertUniverseSnapshotBatch(List.of(
                com.quant.trade.marketdata.poc.Mr0PocIngestService.UniverseSnapshotRow.builder()
                        .providerCode("SINA_PUBLIC").canonicalSymbol("SH.600520").symbol("600520")
                        .name("股票600520").market("SH").circulatingMarketCap(new BigDecimal("1000000"))
                        .turnoverRate(new BigDecimal("0.01")).asOfDate(LocalDate.of(2026, 6, 11))
                        .fetchedAt(FETCHED).build()));
        List<StockDailyBarDO> staleBars = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            LocalDate day = LocalDate.of(2026, 6, 10).plusDays(i);  // 06-10..06-30，均无 07-01 bar
            staleBars.add(bar("SH.600520", day.toString(), String.valueOf(200 + i)));
        }
        mr0PocMapper.upsertStockDailyBarBatch(staleBars);
        VolatilityBlock stale = service.analyze(command()).getVolatility();
        assertThat(stale.getStatus()).isEqualTo("OK");              // SH.600519 仍合格
        assertThat(stale.getQualifiedStocks()).isEqualTo(1L);
        assertThat(stale.getExcludedForWarmup()).isEqualTo(1L);     // SH.600520：asOf 当日无 bar
    }

    // ==================== M5 波动率/流动性代理公式（字典 M-19/M-20，手算） ====================

    @Test
    void volatilityAndLiquidityProxyMatchDictionaryFormulas() {
        List<String> dates = new ArrayList<>();
        for (LocalDate date = LocalDate.of(2026, 6, 11); !date.isAfter(LocalDate.of(2026, 7, 1)); date = date.plusDays(1)) {
            dates.add(date.toString());
        }
        seedBenchmarks(dates.toArray(new String[0]));
        seedUniverse("2026-06-11", "SH.600519");
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            closes.add(BigDecimal.valueOf(100 + i));
            seedStockWithAmount("SH.600519", dates.get(i), String.valueOf(100 + i), "1000000");
        }

        AnalysisResult result = service.analyze(AnalysisCommand.builder()
                .analysisStart(LocalDate.of(2026, 6, 11)).analysisEnd(LocalDate.of(2026, 7, 1)).build());

        // 手算：20 个简单收益率的样本标准差（ddof=1），BigDecimal 输入
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            returns.add(closes.get(i).divide(closes.get(i - 1), 20, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE));
        }
        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 20, RoundingMode.HALF_UP);
        BigDecimal squares = BigDecimal.ZERO;
        for (BigDecimal value : returns) {
            squares = squares.add(value.subtract(mean).pow(2));
        }
        BigDecimal variance = squares.divide(BigDecimal.valueOf(returns.size() - 1L), 20, RoundingMode.HALF_UP);
        BigDecimal expectedVol = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
                .setScale(12, RoundingMode.HALF_UP);
        assertThat(result.getVolatility().getMarketMedian()).isEqualByComparingTo(expectedVol);
        assertThat(result.getVolatility().getMarketP90()).isEqualByComparingTo(expectedVol);
        assertThat(result.getVolatility().isAnnualized()).isFalse();

        // 手算：illiquidity=|r(t)|/amount(t)，逐日均值（1/元）
        BigDecimal illiquiditySum = BigDecimal.ZERO;
        for (int i = 1; i < closes.size(); i++) {
            BigDecimal ratio = closes.get(i).divide(closes.get(i - 1), 20, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE);
            illiquiditySum = illiquiditySum.add(ratio.abs().divide(new BigDecimal("1000000"), 20, RoundingMode.HALF_UP));
        }
        BigDecimal expectedIlliquidity = illiquiditySum
                .divide(BigDecimal.valueOf(closes.size() - 1L), 12, RoundingMode.HALF_UP);
        assertThat(result.getLiquidityProxy().getUnit()).isEqualTo("1/元");
        assertThat(result.getLiquidityProxy().getQualifiedStocks()).isEqualTo(1L);
        assertThat(result.getLiquidityProxy().getMarketMedian()).isEqualByComparingTo(expectedIlliquidity);
        assertThat(result.getLiquidityProxy().getZeroAmountRows()).isZero();
    }

    // ==================== M6 单一 provider 标注与混源标记（AC-05） ====================

    @Test
    void everyAnalysisMetricCarriesSingleProviderAttribution() {
        seedBenchmarks("2026-06-30", "2026-07-01", "2026-07-02");
        seedStock("SH.600519", new String[] {"2026-06-30", "2026-07-01", "2026-07-02"},
                new String[] {"10", "11", "12"});
        // CR-3 附加快照行在首次 analyze 前种好（jdbcTemplate 直插不刷新 MyBatis 一级缓存）：
        // 基准在列且市值最高也不算样本；SZ.000002 为流通市值 Top-1；SH.601398 出于 sampleSize=1 之外；
        // SZ.301999 无市值 → 排除。首次 analyze（sampleSize 默认 150）不受影响。
        seedUniverse("2026-07-01", "SH.600519");
        seedUniverseCap("2026-07-01", "SH.000001", "9999999");
        seedUniverseCap("2026-07-01", "SZ.000002", "2000000");
        seedUniverseCap("2026-07-01", "SH.601398", "500000");
        seedUniverseCap("2026-07-01", "SZ.301999", null);
        seedMembership("2026-06-01", "new_blhy", "玻璃行业", "SH.600519");
        seedFlow("SH.600519", "2026-07-01", "100", "350");

        AnalysisResult result = service.analyze(command());

        assertThat(result.getUniverse().getProviders()).containsExactly("SINA_PUBLIC");
        assertThat(result.getTradingDays().getProviders()).containsExactly("TENCENT_PUBLIC");
        assertThat(result.getBreadth().getProviders()).containsExactly("TENCENT_PUBLIC");
        assertThat(result.getIndustryTurnover().getProviders()).containsExactly("TENCENT_PUBLIC");
        assertThat(result.getVolatility().getProviders()).containsExactly("TENCENT_PUBLIC");
        assertThat(result.getLiquidityProxy().getProviders()).containsExactly("TENCENT_PUBLIC");
        assertThat(result.getMoneyFacts().getProviders()).containsExactly("SINA_PUBLIC");
        assertThat(result.getMoneyFacts().getFlowIntensity().getProviders())
                .containsExactly("SINA_PUBLIC", "TENCENT_PUBLIC");
        assertThat(result.getMixedMetrics()).contains("flowIntensity");
        assertThat(result.getMetricAttributions()).hasSize(8);
        assertThat(result.getMetricAttributions()).allSatisfy(attribution ->
                assertThat(attribution.getProviders()).isNotEmpty());
        assertThat(result.getAnalysisContentHash()).hasSize(64).matches("[0-9a-f]{64}");

        // CR-3：样本=最新档快照流通市值 Top-N（排除基准与 null 市值）；universeSize=Top-N+1（含基准）；
        // universeSymbolsSha256=(Top-N ∪ 基准) 排序逗号拼接哈希
        UniverseBlock universe = service.analyze(AnalysisCommand.builder()
                .analysisStart(START).analysisEnd(END).sampleSize(1).build()).getUniverse();
        assertThat(universe.getSampleSymbols()).isEqualTo(1L);
        assertThat(universe.getSampleSymbolList()).containsExactly("SZ.000002");
        assertThat(universe.getUniverseSize()).isEqualTo(2L);    // Top-1 + 基准
        List<String> hashInput = new ArrayList<>(List.of("SH.000001", "SZ.000002"));
        java.util.Collections.sort(hashInput);
        assertThat(universe.getUniverseSymbolsSha256()).isEqualTo(sha256Hex(String.join(",", hashInput)));
        assertThat(universe.getStatus()).isEqualTo("OK");
        assertThat(universe.getCaliber()).contains("as_of 无上界");
    }

    // ==================== M7 每次调用重读存储（无缓存，关闭恒等假阳） ====================

    @Test
    void analysisRereadsStorageEachCall() {
        seedBenchmarks("2026-06-30", "2026-07-01", "2026-07-02");
        seedStock("SH.600519", new String[] {"2026-06-30", "2026-07-01", "2026-07-02"},
                new String[] {"10", "11", "12"});
        seedUniverse("2026-07-01", "SH.600519", "SH.601398");  // SH.601398 暂无 bar

        DailyBreadth before = service.analyze(command()).getBreadth().getDaily().get(1);
        assertThat(before.getValidStocks()).isEqualTo(1L);

        mr0PocMapper.upsertStockDailyBarBatch(List.of(
                bar("SH.601398", "2026-07-01", "50"),
                bar("SH.601398", "2026-07-02", "55")));

        DailyBreadth after = service.analyze(command()).getBreadth().getDaily().get(1);
        assertThat(after.getValidStocks()).isEqualTo(2L);  // 新 bar 必须被第二次分析看到
        assertThat(after.getAdvancing()).isEqualTo(2L);
        assertThat(after.getAdvanceRatio()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(after.getAdLine()).isEqualTo(before.getAdLine() + 1);
    }

    // ==================== helpers（直接 SQL 构造，事务回滚） ====================

    private void seedBenchmarks(String... dates) {
        for (String date : dates) {
            jdbcTemplate.update("INSERT INTO stock_daily_bar(canonical_symbol, trade_date, adjust_type,"
                    + " data_source, open_price, high_price, low_price, close_price, volume, amount, fetched_at)"
                    + " VALUES ('SH.000001', ?, 'NONE', 'TENCENT_PUBLIC', 3000, 3100, 2950, 3050, 1000, 3000000, ?)",
                    LocalDate.parse(date), FETCHED);
        }
    }

    private void seedStock(String symbol, String[] dates, String[] closes) {
        for (int i = 0; i < dates.length; i++) {
            seedStockWithAmount(symbol, dates[i], closes[i], "1000000");
        }
    }

    private void seedStockWithAmount(String symbol, String date, String close, String amount) {
        BigDecimal closePrice = new BigDecimal(close);
        jdbcTemplate.update("INSERT INTO stock_daily_bar(canonical_symbol, trade_date, adjust_type,"
                + " data_source, open_price, high_price, low_price, close_price, volume, amount, fetched_at)"
                + " VALUES (?, ?, 'NONE', 'TENCENT_PUBLIC', ?, ?, ?, ?, 100000, ?, ?)", symbol,
                LocalDate.parse(date), closePrice.subtract(BigDecimal.ONE), closePrice.add(BigDecimal.ONE),
                closePrice.subtract(BigDecimal.ONE), closePrice, new BigDecimal(amount), FETCHED);
    }

    private void seedUniverse(String asOf, String... symbols) {
        for (String symbol : symbols) {
            seedUniverseCap(asOf, symbol, "1000000");
        }
    }

    /** CR-3：快照行带流通市值（元）；cap=null 表示无市值行（不得入样本）。 */
    private void seedUniverseCap(String asOf, String symbol, String cap) {
        jdbcTemplate.update("INSERT INTO mr0_universe_snapshot(provider_code, canonical_symbol, symbol,"
                + " name, market, turnover_rate, circulating_market_cap, as_of_date, fetched_at)"
                + " VALUES ('SINA_PUBLIC', ?, ?, ?, 'SH', 0.01, ?, ?, ?)", symbol, symbol.substring(3),
                "股票" + symbol.substring(3), cap == null ? null : new BigDecimal(cap),
                LocalDate.parse(asOf), FETCHED);
    }

    private void seedMembership(String asOf, String industryCode, String industryName, String... symbols) {
        for (String symbol : symbols) {
            jdbcTemplate.update("INSERT INTO mr0_industry_membership(taxonomy_code, provider_code,"
                    + " industry_code, industry_name, canonical_symbol, as_of_date, fetched_at)"
                    + " VALUES ('SINA_INDUSTRY', 'SINA_PUBLIC', ?, ?, ?, ?, ?)", industryCode, industryName,
                    symbol, LocalDate.parse(asOf), FETCHED);
        }
    }

    private void seedFlow(String symbol, String date, String netInflow, String cateNa) {
        jdbcTemplate.update("INSERT INTO mr0_stock_money_flow_daily(canonical_symbol, trade_date,"
                + " provider_code, main_net_inflow, industry_net_inflow, fetched_at)"
                + " VALUES (?, ?, 'SINA_PUBLIC', ?, ?, ?)", symbol, LocalDate.parse(date),
                new BigDecimal(netInflow), new BigDecimal(cateNa), FETCHED);
    }

    private StockDailyBarDO bar(String symbol, String date, String close) {
        return StockDailyBarDO.builder().canonicalSymbol(symbol).tradeDate(LocalDate.parse(date))
                .adjustType("NONE").dataSource("TENCENT_PUBLIC")
                .openPrice(new BigDecimal(close)).highPrice(new BigDecimal(close))
                .lowPrice(new BigDecimal(close)).closePrice(new BigDecimal(close))
                .volume(100000L).amount(new BigDecimal("1000000")).fetchedAt(FETCHED).build();
    }

    private static String sha256Hex(String input) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
