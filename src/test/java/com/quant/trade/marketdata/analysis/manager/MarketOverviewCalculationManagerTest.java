package com.quant.trade.marketdata.analysis.manager;

import com.quant.trade.marketdata.analysis.derived.MarketDerivedCalculators;
import com.quant.trade.marketdata.analysis.manager.MarketOverviewCalculationManager.CalculationInput;
import com.quant.trade.marketdata.analysis.manager.MarketOverviewCalculationManager.CalculationOutput;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.ActivityPoint;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.BenchmarkPoint;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.BreadthPoint;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.IndustryMigrationRow;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO.LiquidityProxyPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MR-1A 市场全景计算 Manager 聚焦测试：纯计算（不起 Spring、零数据库、零联网）。
 * 期望值全部按冻结公式（docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md M-01..M-13/M-20/M-21）
 * 手工推导：均线=算术平均、中位/分位=升序取位、占比=part/total、illiquidity=|r|/amount；
 * 测试内的期望计算独立书写（排序取中位、显式求和），不复用生产实现的方法。
 *
 * <p>共享夹具时间轴：自 2026-06-01（周一）起跳过周末的 30 个基准交易日（索引 i=0..29，即
 * 2026-06-01..2026-07-10；测试日历不含节假日），展示窗口为第 20..29 个交易日，前 20 日为预热。
 * 基准收盘 i&le;24 为 100+i、i&ge;25 跌至 90；基准成交额 1000+10i。</p>
 */
class MarketOverviewCalculationManagerTest {

    private static final String BENCHMARK = "SH.000001";
    /** 2026-06-01 为周一；测试交易日历 = 自该周一起跳过周末。 */
    private static final LocalDate DAY0 = LocalDate.of(2026, 6, 1);
    /** 展示窗口起点 = 第 20 个交易日（2026-06-29）。 */
    private static final LocalDate START = day(20);
    /** 展示窗口终点 = 第 29 个交易日（2026-07-10）。 */
    private static final LocalDate END = day(29);

    private final MarketOverviewCalculationManager manager = new MarketOverviewCalculationManager();

    /** 第 index 个交易日（自 2026-06-01 周一起跳过周末；测试日历不含节假日）。 */
    private static LocalDate day(int index) {
        return DAY0.plusWeeks(index / 5).plusDays(index % 5);
    }

    private static TreeMap<LocalDate, BigDecimal> benchmarkCloses() {
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (int i = 0; i < 30; i++) {
            closes.put(day(i), i <= 24 ? BigDecimal.valueOf(100 + i) : BigDecimal.valueOf(90));
        }
        return closes;
    }

    private static TreeMap<LocalDate, BigDecimal> benchmarkAmounts() {
        TreeMap<LocalDate, BigDecimal> amounts = new TreeMap<>();
        for (int i = 0; i < 30; i++) {
            amounts.put(day(i), BigDecimal.valueOf(1000 + 10 * i));
        }
        return amounts;
    }

    /** 构造单证券收盘/成交额序列（fromIndex..toIndex 闭区间）。 */
    private static void putStock(Map<String, TreeMap<LocalDate, BigDecimal>> closes,
                                 Map<String, TreeMap<LocalDate, BigDecimal>> amounts, String symbol,
                                 double startClose, double closeStep, long startAmount, long amountStep,
                                 int fromIndex, int toIndex) {
        TreeMap<LocalDate, BigDecimal> closeSeries = new TreeMap<>();
        TreeMap<LocalDate, BigDecimal> amountSeries = new TreeMap<>();
        for (int i = fromIndex; i <= toIndex; i++) {
            closeSeries.put(day(i), BigDecimal.valueOf(startClose + closeStep * i));
            amountSeries.put(day(i), BigDecimal.valueOf(startAmount + amountStep * i));
        }
        closes.put(symbol, closeSeries);
        amounts.put(symbol, amountSeries);
    }

    // ==================== 1/2/3：基准 MA 预热、dailyReturn、drawdown ====================

    @Test
    void benchmarkSeriesUsesWarmupForMaAndComputesReturnAndDrawdown() {
        CalculationOutput output = manager.calculate(baseInput(emptySeries(), emptySeries(),
                List.of(), Map.of(), Map.of()));

        List<BenchmarkPoint> series = output.benchmarkSeries();
        assertThat(series).hasSize(10);
        BenchmarkPoint first = series.get(0);  // i=20（窗口首日，均线吃到预热 19 根）
        assertThat(first.tradeDate()).isEqualTo(START);
        assertThat(first.ma20()).isEqualByComparingTo(
                BigDecimal.valueOf(110.5));  // mean(closes[1..20])=mean(101..120)
        assertThat(first.ma60()).isNull();   // 仅 30 个观测，MA60 不可计算
        // dailyReturn = 120/119 − 1（前收来自预热最后一日）
        assertThat(first.dailyReturn()).isEqualByComparingTo(new BigDecimal("120")
                .divide(new BigDecimal("119"), 20, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                .setScale(10, RoundingMode.HALF_UP));
        assertThat(first.drawdown()).isEqualByComparingTo(BigDecimal.ZERO);  // 窗口内新高，峰值为当日
        assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("1200"));  // 1000+10×20

        BenchmarkPoint peak = series.get(4);  // i=24：close=124 为窗口峰值
        assertThat(peak.drawdown()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(peak.ma20()).isEqualByComparingTo(BigDecimal.valueOf(114.5));  // mean(105..124)
        BenchmarkPoint dropped = series.get(5);  // i=25：close=90
        assertThat(dropped.dailyReturn()).isEqualByComparingTo(new BigDecimal("90")
                .divide(new BigDecimal("124"), 20, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                .setScale(10, RoundingMode.HALF_UP));
        assertThat(dropped.drawdown()).isEqualByComparingTo(new BigDecimal("90")
                .divide(new BigDecimal("124"), 20, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                .setScale(10, RoundingMode.HALF_UP));  // 相对窗口内峰值 124 的回撤
        assertThat(dropped.ma20()).isEqualByComparingTo(new BigDecimal("113.75"));  // mean(106..124)+90 的均值=(2185+90)/20
        assertThat(output.dataAsOf()).isEqualTo(END);
    }

    @Test
    void benchmarkMaIsNullWhenWarmupInsufficient() {
        // 仅加载 i=10..29（20 根），窗口从 i=20 开始：首日恰好 11 根观测 → ma20 null
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        TreeMap<LocalDate, BigDecimal> amounts = new TreeMap<>();
        for (int i = 10; i < 30; i++) {
            closes.put(day(i), BigDecimal.valueOf(100 + i));
            amounts.put(day(i), BigDecimal.valueOf(1000));
        }
        CalculationOutput output = manager.calculate(new CalculationInput(START, END, BENCHMARK,
                closes, amounts, emptySeries(), emptySeries(), List.of(), Map.of(), Map.of(), 150));
        assertThat(output.benchmarkSeries().get(0).ma20()).isNull();
        assertThat(output.benchmarkSeries().get(8).ma20()).isNull();   // i=28：19 根观测仍不足
        assertThat(output.benchmarkSeries().get(9).ma20()).isNotNull(); // i=29：恰好 20 根
    }

    // ==================== 4/5：成交额 20/60 日中位数与活跃度比值 ====================

    @Test
    void activitySeriesComputesTurnoverMediansAndActiveStockRatio() {
        Map<String, TreeMap<LocalDate, BigDecimal>> closes = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> amounts = new TreeMap<>();
        putStock(closes, amounts, "SH.A", 10, 1, 1000, 1, 0, 29);   // 成交额递增 1000+i
        putStock(closes, amounts, "SH.B", 200, -1, 2000, 0, 0, 29); // 常量 2000
        putStock(closes, amounts, "SH.C", 30, 0, 3000, 0, 0, 29);   // 常量 3000
        CalculationOutput output = manager.calculate(baseInput(closes, amounts,
                List.of(cap("SH.A", 900), cap("SH.B", 800), cap("SH.C", 700)), Map.of(), Map.of()));

        ActivityPoint first = output.activitySeries().get(0);  // i=20
        assertThat(first.validStocks()).isEqualTo(3);
        assertThat(first.marketTurnover()).isEqualByComparingTo(new BigDecimal("6020"));  // 1020+2000+3000
        // 中位数（M-04 含 t）：{6001..6020} 共 20 个整数 → (6010+6011)/2 = 6010.5
        assertThat(first.turnoverMedian20()).isEqualByComparingTo(new BigDecimal("6010.5"));
        assertThat(first.turnoverMedian60()).isNull();  // 仅 30 个交易日观测
        assertThat(first.activityRatio()).isEqualByComparingTo(new BigDecimal("6020")
                .divide(new BigDecimal("6010.5"), 10, RoundingMode.HALF_UP));
        // 成交扩散（M-13）：A 当日 1020 > 前 20 日中位 (1009+1010)/2=1009.5；B/C 常量不严格大于 → 1/3
        assertThat(first.activeStockRatio()).isEqualByComparingTo(new BigDecimal("1")
                .divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP));
    }

    @Test
    void activityMedianIsNullWhenObservationsFewerThanWindow() {
        // 仅 15 个交易日：中位数基线不足 → null，activityRatio null
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        TreeMap<LocalDate, BigDecimal> amounts = new TreeMap<>();
        for (int i = 0; i < 15; i++) {
            closes.put(day(i), BigDecimal.valueOf(100 + i));
            amounts.put(day(i), BigDecimal.valueOf(1000));
        }
        Map<String, TreeMap<LocalDate, BigDecimal>> stockCloses = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> stockAmounts = new TreeMap<>();
        putStock(stockCloses, stockAmounts, "SH.A", 10, 0, 1000, 0, 0, 14);
        CalculationOutput output = manager.calculate(new CalculationInput(DAY0, day(14), BENCHMARK,
                closes, amounts, stockCloses, stockAmounts, List.of(cap("SH.A", 900)), Map.of(), Map.of(), 150));
        ActivityPoint last = output.activitySeries().get(14);
        assertThat(last.turnoverMedian20()).isNull();
        assertThat(last.turnoverMedian60()).isNull();
        assertThat(last.activityRatio()).isNull();
        assertThat(last.activeStockRatio()).isNull();  // 基线交易日不足 20
    }

    // ==================== 6/7/8：广度计数、advanceRatio、A/D 线、aboveMa20 分母 ====================

    @Test
    void breadthSeriesCountsAdvanceDeclineAdLineAndAboveMa20Denominator() {
        Map<String, TreeMap<LocalDate, BigDecimal>> closes = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> amounts = new TreeMap<>();
        putStock(closes, amounts, "SH.A", 10, 1, 1000, 0, 0, 29);    // 每日上涨
        putStock(closes, amounts, "SH.B", 200, -1, 1000, 0, 0, 29);  // 每日下跌
        putStock(closes, amounts, "SH.C", 30, 0, 1000, 0, 0, 29);    // 每日平盘
        putStock(closes, amounts, "SH.D", 50, -1, 5000, 0, 0, 14);   // 仅 15 根收盘：历史不足
        CalculationOutput output = manager.calculate(baseInput(closes, amounts,
                List.of(cap("SH.A", 900), cap("SH.B", 800), cap("SH.C", 700), cap("SH.D", 600)),
                Map.of(), Map.of()));

        List<BreadthPoint> series = output.breadthSeries();
        assertThat(series).hasSize(10);
        BreadthPoint first = series.get(0);  // 窗口首日 i=20（t-1 来自预热）
        assertThat(first.advancingStocks()).isEqualTo(1);   // A
        assertThat(first.decliningStocks()).isEqualTo(1);   // B
        assertThat(first.flatStocks()).isEqualTo(1);        // C
        assertThat(first.validStocks()).isEqualTo(3);       // D 当日无收盘
        assertThat(first.advanceRatio()).isEqualByComparingTo(new BigDecimal("1")
                .divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP));
        assertThat(first.adLine()).isEqualTo(0L);           // 首日种子 adv−dec = 0
        BreadthPoint second = series.get(1);
        assertThat(second.adLine()).isEqualTo(0L);          // 每日 +0 累计
        // aboveMa20：A close>MA20（递增）；B close<MA20（递减）；C close==MA20 不计入分子（严格大于）；
        // D 历史不足 20 根 → 不入分母（M-09）→ 分母=3 而非 4
        assertThat(first.aboveMa20Stocks()).isEqualTo(1);
        assertThat(first.aboveMa20Ratio()).isEqualByComparingTo(new BigDecimal("1")
                .divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP));
    }

    // ==================== 9：流动性代理 median/P90（11 只合格样本，分位恰好落在整数序位） ====================

    @Test
    void liquidityProxyComputesDailyMedianAndP90WithZeroAmountGuard() {
        Map<String, TreeMap<LocalDate, BigDecimal>> closes = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> amounts = new TreeMap<>();
        for (int stock = 1; stock <= 11; stock++) {
            // 11 只全部 100→101（r=0.01 精确），成交额 1000×stock → illiq=0.01/amount 互异
            putStock(closes, amounts, "SH.S" + stock, 100, 0, 1000L * stock, 0, 0, 29);
        }
        // 手工把每只收盘改为隔日 100/101 交替等价做法太繁琐：直接改为 101（每日常数收盘，r=0）不行，
        // 因此这里改写收盘序列：i 偶数 100、奇数 101 → 相邻日 r=±0.01，|r| 恒为 0.01。
        for (int stock = 1; stock <= 11; stock++) {
            TreeMap<LocalDate, BigDecimal> series = closes.get("SH.S" + stock);
            for (int i = 0; i < 30; i++) {
                series.put(day(i), BigDecimal.valueOf(i % 2 == 0 ? 100 : 101));
            }
        }
        putStock(closes, amounts, "SH.E", 100, 0, 0, 0, 0, 29);  // 成交额 0：除零守卫
        List<MarketDerivedCalculators.SymbolMarketCap> snapshot = new ArrayList<>();
        for (int stock = 1; stock <= 11; stock++) {
            snapshot.add(cap("SH.S" + stock, 1000 - stock));
        }
        snapshot.add(cap("SH.E", 1));
        CalculationOutput output = manager.calculate(baseInput(closes, amounts, snapshot, Map.of(), Map.of()));

        LiquidityProxyPoint first = output.liquidityProxySeries().days().get(0);  // i=20（偶数→close=100）
        // i=20 close=100、i=19 close=101 → |r| = |100/101 − 1| = 1/101；illiq_s = (1/101)/amount_s
        BigDecimal oneOver101 = BigDecimal.ONE.divide(new BigDecimal("101"), 20, RoundingMode.HALF_UP);
        List<BigDecimal> sorted = new ArrayList<>();
        for (int stock = 1; stock <= 11; stock++) {
            // 分位输出按冻结口径（M-21/PoC percentile）为 12 位小数
            sorted.add(oneOver101.divide(BigDecimal.valueOf(1000L * stock), 20, RoundingMode.HALF_UP)
                    .setScale(12, RoundingMode.HALF_UP));
        }
        sorted.sort(BigDecimal::compareTo);  // amount 递增 → illiq 递减，升序=amount 从大到小
        assertThat(first.qualifiedStocks()).isEqualTo(11);
        assertThat(first.zeroAmountRows()).isEqualTo(1);  // SH.E 成交额 0
        // n=11：median 序位 5；P90 序位 floor(0.9×10)=9（整数序位，无插值）
        assertThat(first.medianIlliquidity()).isEqualByComparingTo(sorted.get(5));
        assertThat(first.p90Illiquidity()).isEqualByComparingTo(sorted.get(9));
        assertThat(output.liquidityProxySeries().unit()).isEqualTo("1/元");
        assertThat(output.liquidityProxySeries().caliber()).contains("不冒充买卖价差");
    }

    // ==================== 10/11/12/13：行业 Top8+OTHER、前值/中位变化、coverageGap 排除 ====================

    @Test
    void industryMigrationReturnsTop8PlusOtherAndExcludesCoverageGapFromDenominator() {
        // 两天窗口；10 个行业各 1 只样本股 + 1 只无映射样本股 Z（不得入占比分母）
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        TreeMap<LocalDate, BigDecimal> amounts = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> stockCloses = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> stockAmounts = new TreeMap<>();
        for (int i = 0; i < 2; i++) {
            closes.put(day(i), BigDecimal.valueOf(3000));
            amounts.put(day(i), BigDecimal.valueOf(3000));
        }
        List<MarketDerivedCalculators.SymbolMarketCap> snapshot = new ArrayList<>();
        Map<String, MarketDerivedCalculators.IndustryRef> membership = new TreeMap<>();
        Map<String, String> names = new TreeMap<>();
        long[] day1Amounts = {100, 90, 80, 70, 60, 50, 40, 30, 20, 10};
        for (int industry = 1; industry <= 10; industry++) {
            String symbol = "SH.I" + industry;
            String code = "IND" + industry;
            TreeMap<LocalDate, BigDecimal> closeSeries = new TreeMap<>();
            TreeMap<LocalDate, BigDecimal> amountSeries = new TreeMap<>();
            closeSeries.put(day(0), BigDecimal.TEN);
            closeSeries.put(day(1), BigDecimal.TEN);
            amountSeries.put(day(0), BigDecimal.valueOf(day1Amounts[industry - 1]));
            // 第二日 IND9 放量到 200，其余不变 → Top8 集合变化
            amountSeries.put(day(1), BigDecimal.valueOf(industry == 9 ? 200 : day1Amounts[industry - 1]));
            stockCloses.put(symbol, closeSeries);
            stockAmounts.put(symbol, amountSeries);
            snapshot.add(cap(symbol, 1000 - industry));
            membership.put(symbol, new MarketDerivedCalculators.IndustryRef(code, LocalDate.of(2026, 5, 1)));
            names.put(code, "行业" + industry);
        }
        // Z：无行业映射，两日各 1000 成交额 → 只进 coverageGap
        TreeMap<LocalDate, BigDecimal> zCloses = new TreeMap<>();
        TreeMap<LocalDate, BigDecimal> zAmounts = new TreeMap<>();
        zCloses.put(day(0), BigDecimal.TEN);
        zCloses.put(day(1), BigDecimal.TEN);
        zAmounts.put(day(0), BigDecimal.valueOf(1000));
        zAmounts.put(day(1), BigDecimal.valueOf(1000));
        stockCloses.put("SH.Z", zCloses);
        stockAmounts.put("SH.Z", zAmounts);
        snapshot.add(cap("SH.Z", 1));

        CalculationOutput output = manager.calculate(new CalculationInput(day(0), day(1), BENCHMARK,
                closes, amounts, stockCloses, stockAmounts, snapshot, membership, names, 150));

        List<IndustryMigrationRow> rows = output.industryTurnoverMigration();
        // 每日 9 行（Top8 + OTHER）
        assertThat(rows).hasSize(18);
        List<IndustryMigrationRow> day1 = rows.stream().filter(row -> row.tradeDate().equals(day(0))).toList();
        List<IndustryMigrationRow> day2 = rows.stream().filter(row -> row.tradeDate().equals(day(1))).toList();
        assertThat(day1).hasSize(9);
        assertThat(day2).hasSize(9);

        // 第一日：市场总额=550（Z 的 1000 不入分母）；IND1..IND8 为 Top8，OTHER=IND9+IND10=30
        IndustryMigrationRow ind1Day1 = rowOf(day1, "IND1");
        assertThat(ind1Day1.rank()).isEqualTo(1);
        assertThat(ind1Day1.turnover()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(ind1Day1.turnoverShare()).isEqualByComparingTo(share("100", "550"));
        assertThat(ind1Day1.coveredStocks()).isEqualTo(1);
        IndustryMigrationRow otherDay1 = rowOf(day1, "OTHER");
        assertThat(otherDay1.rank()).isNull();
        assertThat(otherDay1.turnover()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(otherDay1.turnoverShare()).isEqualByComparingTo(share("30", "550"));
        assertThat(otherDay1.coveredStocks()).isEqualTo(2);
        // 每日 Top8 + OTHER 占比之和 = 1±1e-6（coverageGap 不得挤占分母）
        BigDecimal day1Sum = day1.stream().map(IndustryMigrationRow::turnoverShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(day1Sum.subtract(BigDecimal.ONE).abs()).isLessThanOrEqualTo(new BigDecimal("0.000001"));
        BigDecimal day2Sum = day2.stream().map(IndustryMigrationRow::turnoverShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(day2Sum.subtract(BigDecimal.ONE).abs()).isLessThanOrEqualTo(new BigDecimal("0.000001"));

        // 第二日：IND9(200) 进入 Top8 第 1 名；前值变化取行业自身口径 share(t)−share(t−1)
        IndustryMigrationRow ind9Day2 = rowOf(day2, "IND9");
        assertThat(ind9Day2.rank()).isEqualTo(1);
        assertThat(ind9Day2.turnoverShare()).isEqualByComparingTo(share("200", "730"));  // 550−20+200=730
        assertThat(ind9Day2.previousDayShareChange()).isEqualByComparingTo(share("200", "730")
                .subtract(share("20", "550")).setScale(10, RoundingMode.HALF_UP));
        IndustryMigrationRow ind1Day2 = rowOf(day2, "IND1");
        assertThat(ind1Day2.rank()).isEqualTo(2);  // 100 次于 200
        assertThat(ind1Day2.previousDayShareChange()).isEqualByComparingTo(share("100", "730")
                .subtract(share("100", "550")).setScale(10, RoundingMode.HALF_UP));
        // IND8 跌出 Top8 → 第二日无 IND8 行（进入 OTHER）；OTHER 第二日 = IND8(30)+IND10(10)=40
        assertThat(day2.stream().anyMatch(row -> "IND8".equals(row.industryCode()))).isFalse();
        IndustryMigrationRow otherDay2 = rowOf(day2, "OTHER");
        assertThat(otherDay2.turnover()).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(otherDay2.turnoverShare()).isEqualByComparingTo(share("40", "730"));
        assertThat(otherDay2.coveredStocks()).isEqualTo(2);
        // OTHER 前值变化 = OTHER(第2日口径) − OTHER(第1日口径) = 40/730 − 30/550
        assertThat(otherDay2.previousDayShareChange()).isEqualByComparingTo(share("40", "730")
                .subtract(share("30", "550")).setScale(10, RoundingMode.HALF_UP));
        // median20Share：两日观测的线性中位 = 两值均值（percentile idx=0.5 精确）
        assertThat(ind1Day2.median20Share()).isEqualByComparingTo(medianOfTwo(
                share("100", "550"), share("100", "730")));
        assertThat(ind1Day2.median20ShareChange()).isEqualByComparingTo(share("100", "730")
                .subtract(medianOfTwo(share("100", "550"), share("100", "730"))).setScale(10, RoundingMode.HALF_UP));

        // coverageGap：Z 未映射 → 数量 1、窗口成交额合计 2000，且不入行业分母（上式分母已验证）
        assertThat(output.coverageGap().uncoveredSampleStocks()).isEqualTo(1);
        assertThat(output.coverageGap().symbols()).containsExactly("SH.Z");
        assertThat(output.coverageGap().uncoveredTurnoverAmount()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(output.findings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("PARTIAL_INDUSTRY_MAPPING");
            assertThat(finding.affectedCount()).isEqualTo(1);
        });
    }

    // ==================== M-22 覆盖门禁：全覆盖 / 阈值边界 / 低覆盖 / 严重不足 / 完全缺失 ====================

    /** 10 样本夹具：mappedCount 只映射前 N 只（全部有 30 日 bar），窗口内每日覆盖域由映射数决定。 */
    private CalculationOutput calculateWithMembership(int mappedCount) {
        Map<String, TreeMap<LocalDate, BigDecimal>> closes = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> amounts = new TreeMap<>();
        List<MarketDerivedCalculators.SymbolMarketCap> snapshot = new ArrayList<>();
        Map<String, MarketDerivedCalculators.IndustryRef> membership = new TreeMap<>();
        Map<String, String> names = new TreeMap<>();
        for (int stock = 0; stock < 10; stock++) {
            String symbol = "SH.M" + stock;
            putStock(closes, amounts, symbol, 10 + stock, 0, 1000 + stock, 0, 0, 29);
            snapshot.add(cap(symbol, 1000 - stock));
            if (stock < mappedCount) {
                membership.put(symbol, new MarketDerivedCalculators.IndustryRef("IND", LocalDate.of(2026, 5, 1)));
            }
        }
        names.put("IND", "行业A");
        return manager.calculate(baseInput(closes, amounts, snapshot, membership, names));
    }

    @Test
    void coverageGatesEnforceFrozenThresholdsAcrossAllTiers() {
        // 全覆盖 10/10=1.0：无任何行业映射 WARN，迁移正常输出
        CalculationOutput full = calculateWithMembership(10);
        assertThat(full.membershipCoverage()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(full.findings()).noneMatch(finding -> finding.code().startsWith("INDUSTRY")
                || "LOW_MEMBERSHIP_COVERAGE".equals(finding.code()));
        assertThat(full.industryTurnoverMigration()).isNotEmpty();

        // 阈值边界 9/10=0.90：恰好等于告警阈值 → 不告警，仅 PARTIAL INFO，迁移正常
        CalculationOutput boundary = calculateWithMembership(9);
        assertThat(boundary.membershipCoverage())
                .isEqualByComparingTo(MarketOverviewCalculationManager.MEMBERSHIP_COVERAGE_WARN_THRESHOLD);
        assertThat(boundary.findings()).noneMatch(finding -> "WARN".equals(finding.severity())
                && finding.code().contains("MEMBERSHIP"));
        assertThat(boundary.findings()).anyMatch(finding -> "PARTIAL_INDUSTRY_MAPPING".equals(finding.code()));
        assertThat(boundary.industryTurnoverMigration()).isNotEmpty();

        // 低覆盖 8/10=0.80：LOW_MEMBERSHIP_COVERAGE WARN（整体 DEGRADED 由 Service 判定），迁移仍输出
        CalculationOutput low = calculateWithMembership(8);
        assertThat(low.membershipCoverage()).isEqualByComparingTo(new BigDecimal("0.8"));
        assertThat(low.findings()).anyMatch(finding -> "LOW_MEMBERSHIP_COVERAGE".equals(finding.code())
                && "WARN".equals(finding.severity()));
        assertThat(low.industryTurnoverMigration()).isNotEmpty();

        // 严重不足 4/10=0.40：WARN + INDUSTRY_MIGRATION_BLOCKED，迁移必须阻断为空
        CalculationOutput blocked = calculateWithMembership(4);
        assertThat(blocked.membershipCoverage()).isEqualByComparingTo(new BigDecimal("0.4"));
        assertThat(blocked.findings()).anyMatch(finding -> "INDUSTRY_MIGRATION_BLOCKED".equals(finding.code())
                && "WARN".equals(finding.severity()));
        assertThat(blocked.industryTurnoverMigration()).isEmpty();  // 不得按极少数映射股票输出误导性行业图
        assertThat(blocked.coverageGap().uncoveredSampleStocks()).isEqualTo(6);

        // 完全缺失 0/10：INDUSTRY_MAPPING_MISSING + 阻断，迁移为空
        CalculationOutput missing = calculateWithMembership(0);
        assertThat(missing.membershipCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(missing.findings()).anyMatch(finding -> "INDUSTRY_MAPPING_MISSING".equals(finding.code()));
        assertThat(missing.findings()).anyMatch(finding -> "INDUSTRY_MIGRATION_BLOCKED".equals(finding.code()));
        assertThat(missing.industryTurnoverMigration()).isEmpty();
    }

    @Test
    void realPoCMembershipCoverageLevelNeverReturnsOk() {
        // 真实 PoC 覆盖水平 101/150=0.673333：必须产生 LOW_MEMBERSHIP_COVERAGE WARN（整体不可为 OK）
        Map<String, TreeMap<LocalDate, BigDecimal>> closes = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> amounts = new TreeMap<>();
        List<MarketDerivedCalculators.SymbolMarketCap> snapshot = new ArrayList<>();
        Map<String, MarketDerivedCalculators.IndustryRef> membership = new TreeMap<>();
        Map<String, String> names = new TreeMap<>();
        for (int stock = 0; stock < 150; stock++) {
            String symbol = "SH.N" + stock;
            putStock(closes, amounts, symbol, 10, 0, 1000, 0, 0, 29);
            snapshot.add(cap(symbol, 2000 - stock));
            if (stock < 101) {
                membership.put(symbol, new MarketDerivedCalculators.IndustryRef("IND", LocalDate.of(2026, 5, 1)));
            }
        }
        names.put("IND", "行业A");
        CalculationOutput output = manager.calculate(baseInput(closes, amounts, snapshot, membership, names));
        assertThat(output.membershipCoverage()).isEqualByComparingTo(new BigDecimal("0.673333"));
        assertThat(output.findings()).anyMatch(finding -> "LOW_MEMBERSHIP_COVERAGE".equals(finding.code())
                && "WARN".equals(finding.severity()));  // WARN → Service 判定整体 DEGRADED，绝不返回 OK
        assertThat(output.industryTurnoverMigration()).isNotEmpty();  // 0.673 ≥ 0.50：告警但不阻断
    }

    // ==================== 预热门禁：<20 / 20~59 / 60~119 / ≥120 真实合格交易日 ====================

    @Test
    void warmupGateAppliesAcrossQualifiedTradingDayBands() {
        // 19 个合格交易日：INSUFFICIENT_WARMUP；MA20/MA60 均不可计算
        CalculationOutput short19 = calculateWithQualifiedDays(19);
        assertThat(short19.qualifiedTradingDays()).isEqualTo(19);
        assertThat(short19.findings()).anyMatch(finding -> "INSUFFICIENT_WARMUP".equals(finding.code()));
        assertThat(short19.benchmarkSeries().get(short19.benchmarkSeries().size() - 1).ma20()).isNull();
        assertThat(short19.benchmarkSeries().get(short19.benchmarkSeries().size() - 1).ma60()).isNull();
        // 短期序列保留：基准序列仍按 19 个交易日完整输出，null 不填 0
        assertThat(short19.benchmarkSeries()).hasSize(19);
        assertThat(short19.benchmarkSeries().get(18).dailyReturn()).isNotNull();

        // 30 日（20~59 档）：MA20 可算、MA60 仍 null，INSUFFICIENT_WARMUP 在
        CalculationOutput days30 = calculateWithQualifiedDays(30);
        assertThat(days30.findings()).anyMatch(finding -> "INSUFFICIENT_WARMUP".equals(finding.code()));
        assertThat(days30.benchmarkSeries().get(29).ma20()).isNotNull();
        assertThat(days30.benchmarkSeries().get(29).ma60()).isNull();

        // 90 日（60~119 档）：MA60 已可算，INSUFFICIENT_WARMUP 仍在
        CalculationOutput days90 = calculateWithQualifiedDays(90);
        assertThat(days90.findings()).anyMatch(finding -> "INSUFFICIENT_WARMUP".equals(finding.code()));
        assertThat(days90.benchmarkSeries().get(89).ma60()).isNotNull();

        // 120 个真实合格交易日（样本覆盖达标）：门禁通过，INSUFFICIENT_WARMUP 消失
        CalculationOutput days120 = calculateWithQualifiedDays(120);
        assertThat(days120.qualifiedTradingDays()).isEqualTo(120);
        assertThat(days120.findings())
                .noneMatch(finding -> "INSUFFICIENT_WARMUP".equals(finding.code()));
        assertThat(days120.benchmarkSeries().get(119).ma60()).isNotNull();
    }

    @Test
    void qualifiedTradingDaysRequireSampleCoverageNotBenchmarkAlone() {
        // 基准有 120 个交易日但空样本：合格日恒为 0，必须触发 INSUFFICIENT_WARMUP（不得以基准数量冒充）
        CalculationOutput benchmarkOnly = calculateWithQualifiedDays(120, 0);
        assertThat(benchmarkOnly.qualifiedTradingDays()).isZero();
        assertThat(benchmarkOnly.findings()).anyMatch(finding -> "INSUFFICIENT_WARMUP".equals(finding.code()));
        assertThat(benchmarkOnly.findings()).anyMatch(finding -> "EMPTY_SAMPLE".equals(finding.code()));

        // 基准 120 个交易日、样本仅最近 40 日有 bar：合格日 = 40 < 120 → INSUFFICIENT_WARMUP
        CalculationOutput shortHistory = calculateWithQualifiedDays(120, 40);
        assertThat(shortHistory.qualifiedTradingDays()).isEqualTo(40);
        assertThat(shortHistory.findings()).anyMatch(finding -> "INSUFFICIENT_WARMUP".equals(finding.code()));
        // 60 日均线在末段可算（样本近 40 日有观测）但门禁按真实合格日判失败
        assertThat(shortHistory.benchmarkSeries().get(119).ma60()).isNotNull();

        // 边界：10 只样本、9 只有 bar → 当日覆盖 0.90 恰达阈值，该日记为合格
        CalculationOutput boundary = calculateWithBoundaryCoverageDays(120);
        assertThat(boundary.qualifiedTradingDays()).isEqualTo(120);
        assertThat(boundary.findings())
                .noneMatch(finding -> "INSUFFICIENT_WARMUP".equals(finding.code()));
    }

    /** n 个真实合格交易日：基准全 n 日 + 1 只样本股全 n 日有 bar（覆盖 1.0）。 */
    private CalculationOutput calculateWithQualifiedDays(int dayCount) {
        return calculateWithQualifiedDays(dayCount, dayCount);
    }

    /** 基准全 n 日 + 1 只样本股仅最后 sampleDays 日有 bar（其余日覆盖 0）；sampleDays=0 即空样本。 */
    private CalculationOutput calculateWithQualifiedDays(int dayCount, int sampleDays) {
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        TreeMap<LocalDate, BigDecimal> amounts = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> stockCloses = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> stockAmounts = new TreeMap<>();
        for (int i = 0; i < dayCount; i++) {
            closes.put(day(i), BigDecimal.valueOf(100 + i));
            amounts.put(day(i), BigDecimal.valueOf(1000));
        }
        if (sampleDays > 0) {
            putStock(stockCloses, stockAmounts, "SH.Q", 10, 0, 1000, 0, dayCount - sampleDays, dayCount - 1);
        }
        return manager.calculate(new CalculationInput(day(0), day(dayCount - 1), BENCHMARK,
                closes, amounts, stockCloses, stockAmounts,
                sampleDays > 0 ? List.of(cap("SH.Q", 900)) : List.of(), Map.of(), Map.of(), 150));
    }

    /** 边界夹具：10 只样本每日 9 只有收盘（当日覆盖 9/10=0.90 恰达合格阈值）。 */
    private CalculationOutput calculateWithBoundaryCoverageDays(int dayCount) {
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        TreeMap<LocalDate, BigDecimal> amounts = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> stockCloses = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> stockAmounts = new TreeMap<>();
        List<MarketDerivedCalculators.SymbolMarketCap> snapshot = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            closes.put(day(i), BigDecimal.valueOf(100 + i));
            amounts.put(day(i), BigDecimal.valueOf(1000));
        }
        for (int stock = 0; stock < 10; stock++) {
            String symbol = "SH.B" + stock;
            if (stock < 9) {  // 第 10 只整段无 bar：每日覆盖恰为 9/10
                putStock(stockCloses, stockAmounts, symbol, 10 + stock, 0, 1000, 0, 0, dayCount - 1);
            }
            snapshot.add(cap(symbol, 1000 - stock));
        }
        return manager.calculate(new CalculationInput(day(0), day(dayCount - 1), BENCHMARK,
                closes, amounts, stockCloses, stockAmounts, snapshot, Map.of(), Map.of(), 150));
    }

    // ==================== 16：无数据 / 空样本业务状态 ====================

    @Test
    void returnsNoDataFindingsWhenBenchmarkMissingInWindow() {
        // 预热日有基准，窗口内无基准 → BENCHMARK_DATA_MISSING + 空序列
        TreeMap<LocalDate, BigDecimal> benchmarkCloses = new TreeMap<>();
        TreeMap<LocalDate, BigDecimal> benchmarkAmounts = new TreeMap<>();
        benchmarkCloses.put(DAY0, BigDecimal.valueOf(3000));
        benchmarkAmounts.put(DAY0, BigDecimal.valueOf(3000));
        CalculationOutput output = manager.calculate(new CalculationInput(START, END, BENCHMARK,
                benchmarkCloses, benchmarkAmounts, emptySeries(), emptySeries(), List.of(), Map.of(), Map.of(), 150));
        assertThat(output.dataAsOf()).isNull();
        assertThat(output.benchmarkSeries()).isEmpty();
        assertThat(output.activitySeries()).isEmpty();
        assertThat(output.breadthSeries()).isEmpty();
        assertThat(output.liquidityProxySeries().days()).isEmpty();
        assertThat(output.industryTurnoverMigration()).isEmpty();
        assertThat(output.findings()).anySatisfy(finding ->
                assertThat(finding.code()).isEqualTo("BENCHMARK_DATA_MISSING"));
    }

    @Test
    void reportsEmptySampleWhenUniverseSnapshotMissing() {
        CalculationOutput output = manager.calculate(baseInput(emptySeries(), emptySeries(),
                List.of(), Map.of(), Map.of()));
        assertThat(output.findings()).anySatisfy(finding ->
                assertThat(finding.code()).isEqualTo("EMPTY_SAMPLE"));
        assertThat(output.benchmarkSeries()).hasSize(10);  // 基准序列不受空样本影响
        assertThat(output.activitySeries().get(0).validStocks()).isZero();
        assertThat(output.breadthSeries().get(0).validStocks()).isZero();
        assertThat(output.barCoverage()).isNull();        // 样本为空 → 覆盖率不可计算
        assertThat(output.membershipCoverage()).isNull();
    }

    // ==================== helpers ====================

    /** 空的样本股序列（仅基准夹具使用）。 */
    private static Map<String, TreeMap<LocalDate, BigDecimal>> emptySeries() {
        return new TreeMap<>();
    }

    private CalculationInput baseInput(Map<String, TreeMap<LocalDate, BigDecimal>> closes,
                                       Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
                                       List<MarketDerivedCalculators.SymbolMarketCap> snapshot,
                                       Map<String, MarketDerivedCalculators.IndustryRef> membership,
                                       Map<String, String> names) {
        return new CalculationInput(START, END, BENCHMARK, benchmarkCloses(), benchmarkAmounts(),
                closes, amounts, snapshot, membership, names, 150);
    }

    private static MarketDerivedCalculators.SymbolMarketCap cap(String symbol, long cap) {
        return new MarketDerivedCalculators.SymbolMarketCap(symbol, BigDecimal.valueOf(cap));
    }

    private static IndustryMigrationRow rowOf(List<IndustryMigrationRow> rows, String code) {
        return rows.stream().filter(row -> code.equals(row.industryCode())).findFirst().orElseThrow();
    }

    /** 独立计算占比：part/total 10 位小数（测试侧书写，不复用生产 divide）。 */
    private static BigDecimal share(String part, String total) {
        return new BigDecimal(part).divide(new BigDecimal(total), 10, RoundingMode.HALF_UP);
    }

    /** 两观测中位 = 均值（独立书写：升序后相邻序位各 0.5 权重）。 */
    private static BigDecimal medianOfTwo(BigDecimal a, BigDecimal b) {
        return a.add(b).divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP);
    }
}
