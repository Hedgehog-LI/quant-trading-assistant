package com.quant.trade.marketdata.poc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * MR-0 PoC 分析引擎（AC-05）。只读库（Mr0PocAnalysisMapper）、零外联、无进程内结果缓存。
 * 样本派生（CR-3）：最新档快照流通市值降序 Top-N（默认 150；排除基准与 null 市值），
 * universeSize=Top-N+1（含基准）；全池快照行仅作事实保留，不进任何分母。
 * 公式冻结于 docs/features/MARKET_RESEARCH_MR0_METRIC_DICTIONARY.md：adLine 首日种子=adv(t0)−dec(t0)；
 * advanceRatio=adv/validStocks，validStocks=0 → EMPTY_VALID_UNIVERSE 禁止 NaN；占比覆盖域=有成分的
 * 样本股，无成分股票计入 coverageGap 不入分母，Σshare=1±1e-6；rv20=简单收益 ddof=1 不年化且要求
 * asOf 当日有 bar（CR-6）；illiquidity=|r|/amount(元)；行业净流入与 cate_na 偏差=绝对差元（M-15，
 * CR-4）只报告不判等；flowIntensity 混源必须标注；share/sumShare 输出 10 位小数（CR-10）。
 * 失效场景：NONE 复权除权日收益失真（D7）；当前成分聚合历史=时点穿越（质量族标记；快照/成分读取
 * 不设 as-of 上界，取分析时点可见最新档——冻结 TEST-06 M4 场景）。
 */
@Service
@RequiredArgsConstructor
public class Mr0PocAnalysisService {

    public static final String PROVIDER_SINA_PUBLIC = "SINA_PUBLIC";
    public static final String PROVIDER_TENCENT_PUBLIC = "TENCENT_PUBLIC";
    public static final String TAXONOMY_SINA_INDUSTRY = "SINA_INDUSTRY";
    public static final String STATUS_EMPTY = "EMPTY_VALID_UNIVERSE";
    public static final String STATUS_WARMUP = "INSUFFICIENT_WARMUP";
    private static final String BENCHMARK = "SH.000001";
    private static final String CALENDAR = "INDEX_KLINE_DERIVED";
    private static final long WARMUP_LOOKBACK_DAYS = 120;

    /** analysisContentHash 字段白名单（AMD-1 冻结）：七个指标块全字段+分析窗口；排除项见 HASH_EXCLUDED_FIELDS。 */
    static final String[] HASH_FIELD_WHITELIST = {"analysisStart", "analysisEnd", "universe", "tradingDays",
            "breadth", "industryTurnover", "volatility", "liquidityProxy", "moneyFacts"};
    /** 哈希排除字段：时间戳类运行元数据与文案标签（generatedAt/runId/durationMs/fetchedAt 等）。 */
    static final String[] HASH_EXCLUDED_FIELDS = {"runId", "generatedAt", "durationMs", "caliber",
            "metricAttributions", "mixedMetrics", "fetchedAt"};

    private final Mr0PocAnalysisMapper mapper;

    /** 分析命令；默认即冻结 PoC 窗口与样本规模（D5；CR-3 Top-N=150）。 */
    @Data @Builder public static class AnalysisCommand {
        @Builder.Default private LocalDate analysisStart = LocalDate.of(2026, 7, 1);
        @Builder.Default private LocalDate analysisEnd = LocalDate.of(2026, 7, 31);
        @Builder.Default private int sampleSize = 150;
    }

    /** 分析结果（runId/generatedAt/durationMs 为运行元数据，不参与 analysisContentHash）。 */
    @Data @Builder public static class AnalysisResult {
        private String runId, analysisContentHash; private LocalDateTime generatedAt; private long durationMs;
        private LocalDate analysisStart, analysisEnd;
        private UniverseBlock universe; private TradingDaysBlock tradingDays; private BreadthBlock breadth;
        private IndustryTurnoverBlock industryTurnover; private VolatilityBlock volatility;
        private LiquidityProxyBlock liquidityProxy; private MoneyFactsBlock moneyFacts;
        private List<MetricAttribution> metricAttributions; private List<String> mixedMetrics;
    }
    @Data @Builder public static class UniverseBlock { private LocalDate asOfDate; private long universeSize, sampleSymbols; private List<String> sampleSymbolList; private String benchmarkSymbol, universeSymbolsSha256, status, caliber; private List<String> providers; }
    @Data @Builder public static class TradingDaysBlock { private String calendar; private List<String> dates; private int count; private List<String> providers; }
    @Data @Builder public static class BreadthBlock { private String caliber; private List<DailyBreadth> daily; private List<String> providers; }
    @Data @Builder public static class DailyBreadth { private String date, status; private long advancing, declining, flat, validStocks; private BigDecimal advanceRatio; private Long adLine; }
    @Data @Builder public static class IndustryTurnoverBlock { private String caliber; private List<IndustryTurnover> byIndustry; private List<DailyMarketTurnover> dailyMarket; private CoverageGap coverageGap; private List<String> providers; }
    @Data @Builder public static class IndustryTurnover { private String industryCode, industryName; private List<IndustryDay> days; }
    @Data @Builder public static class IndustryDay { private String date; private BigDecimal sectorTurnover, share; private boolean lookaheadAffected; }
    @Data @Builder public static class DailyMarketTurnover { private String date; private BigDecimal marketTurnover, sumShare; }
    @Data @Builder public static class CoverageGap { private long count; private List<String> symbols; }
    @Data @Builder public static class VolatilityBlock { private String asOfDate, status, caliber; private boolean annualized; private long qualifiedStocks, excludedForWarmup; private BigDecimal marketMedian, marketP90; private List<String> providers; }
    @Data @Builder public static class LiquidityProxyBlock { private String unit, status, caliber; private long qualifiedStocks, zeroAmountRows; private BigDecimal marketMedian, marketP90; private List<String> providers; }
    @Data @Builder public static class MoneyFactsBlock { private String caliber; private List<IndustryMoney> byIndustry; private long inconsistentCateNaDays; private FlowIntensity flowIntensity; private List<String> providers; }
    @Data @Builder public static class IndustryMoney { private String industryCode, industryName; private List<MoneyDay> days; }
    @Data @Builder public static class MoneyDay { private String date; private BigDecimal sumMainNetInflow, cateNaValue, deviation; private boolean inconsistentCateNa; }
    @Data @Builder public static class FlowIntensity { private List<String> providers; private String window, status; private BigDecimal windowNetInflow, windowTurnover, value; }
    @Data @Builder public static class MetricAttribution { private String metric, caliber; private List<String> providers; }

    /** 执行一次只读分析（每次调用重读存储，无缓存）。 */
    public AnalysisResult analyze(AnalysisCommand command) {
        long started = System.currentTimeMillis();
        LocalDate start = command.getAnalysisStart(), end = command.getAnalysisEnd();
        Map<String, TreeMap<LocalDate, BigDecimal>> closes = new TreeMap<>(), amounts = new TreeMap<>();
        for (Mr0PocAnalysisMapper.BarRow bar : mapper.selectDailyBars(start.minusDays(WARMUP_LOOKBACK_DAYS), end, PROVIDER_TENCENT_PUBLIC)) {
            closes.computeIfAbsent(bar.getCanonicalSymbol(), k -> new TreeMap<>()).put(bar.getTradeDate(), bar.getClosePrice());
            amounts.computeIfAbsent(bar.getCanonicalSymbol(), k -> new TreeMap<>()).put(bar.getTradeDate(), bar.getAmount());
        }
        // as-of 上界传 null：取分析时点可见最新快照档（时点穿越由质量族显式标记）
        List<Mr0PocAnalysisMapper.UniverseRow> universeRows = mapper.selectUniverseSnapshots(null, PROVIDER_SINA_PUBLIC);
        LocalDate universeAsOf = universeRows.isEmpty() ? null : universeRows.get(0).getAsOfDate();
        List<Mr0PocAnalysisMapper.UniverseRow> latestSnapshot = universeAsOf == null ? List.of()
                : universeRows.stream().filter(row -> universeAsOf.equals(row.getAsOfDate())).toList();
        // CR-3：样本=最新档快照流通市值降序 Top-N（排除基准与 null 市值；全池快照行仅作事实保留），
        // coverageGap/excludedForWarmup/COVERAGE/GAPS 全部以该 Top-N 样本为分母口径。
        List<String> sampleSymbols = latestSnapshot.stream()
                .filter(row -> !BENCHMARK.equals(row.getCanonicalSymbol()))
                .filter(row -> row.getCirculatingMarketCap() != null)
                .sorted(Comparator.comparing(Mr0PocAnalysisMapper.UniverseRow::getCirculatingMarketCap).reversed()
                        .thenComparing(Mr0PocAnalysisMapper.UniverseRow::getCanonicalSymbol))
                .limit(Math.max(command.getSampleSize(), 0))
                .map(Mr0PocAnalysisMapper.UniverseRow::getCanonicalSymbol).distinct().sorted().toList();
        List<String> hashSymbols = new ArrayList<>(sampleSymbols);
        hashSymbols.add(BENCHMARK);  // 哈希=Top-N ∪ 基准（排序后），检测样本漂移
        Collections.sort(hashSymbols);
        Map<String, Mr0PocAnalysisMapper.MembershipRow> membership = new LinkedHashMap<>();
        for (Mr0PocAnalysisMapper.MembershipRow row : mapper.selectIndustryMemberships(null, TAXONOMY_SINA_INDUSTRY)) { membership.putIfAbsent(row.getCanonicalSymbol(), row); }
        List<LocalDate> benchmarkDays = new ArrayList<>(closes.getOrDefault(BENCHMARK, new TreeMap<>()).keySet());
        Map<LocalDate, LocalDate> prevDay = new HashMap<>();
        for (int i = 1; i < benchmarkDays.size(); i++) { prevDay.put(benchmarkDays.get(i), benchmarkDays.get(i - 1)); }
        List<LocalDate> tradingDays = benchmarkDays.stream().filter(day -> !day.isBefore(start) && !day.isAfter(end)).toList();
        Map<String, Map<LocalDate, Mr0PocAnalysisMapper.MoneyFlowRow>> flows = new TreeMap<>();
        mapper.selectMoneyFlows(start, end, PROVIDER_SINA_PUBLIC).forEach(row ->
                flows.computeIfAbsent(row.getCanonicalSymbol(), k -> new TreeMap<>()).put(row.getTradeDate(), row));
        AnalysisResult result = AnalysisResult.builder().runId(UUID.randomUUID().toString()).generatedAt(LocalDateTime.now())
                .analysisStart(start).analysisEnd(end)
                .universe(UniverseBlock.builder().asOfDate(universeAsOf)
                        .universeSize(sampleSymbols.size() + 1L).sampleSymbols(sampleSymbols.size())
                        .sampleSymbolList(sampleSymbols).benchmarkSymbol(BENCHMARK)
                        .universeSymbolsSha256(sha256(String.join(",", hashSymbols)))
                        .status(latestSnapshot.isEmpty() || sampleSymbols.isEmpty() ? STATUS_EMPTY : "OK")
                        .caliber("分析时点可见最新档快照（as_of 无上界；时点穿越由 TIME_POINT_LOOKAHEAD 族显式标记）；基准恒入快照不算样本")
                        .providers(List.of(PROVIDER_SINA_PUBLIC)).build())
                .tradingDays(TradingDaysBlock.builder().calendar(CALENDAR).dates(tradingDays.stream().map(LocalDate::toString).toList())
                        .count(tradingDays.size()).providers(List.of(PROVIDER_TENCENT_PUBLIC)).build())
                .breadth(breadth(tradingDays, prevDay, closes, sampleSymbols))
                .industryTurnover(industryTurnover(tradingDays, amounts, sampleSymbols, membership))
                .volatility(volatility(tradingDays.isEmpty() ? null : tradingDays.get(tradingDays.size() - 1), closes, sampleSymbols))
                .liquidityProxy(liquidity(tradingDays, prevDay, closes, amounts, sampleSymbols))
                .moneyFacts(moneyFacts(tradingDays, flows, membership, sampleSymbols, amounts))
                .metricAttributions(attributions()).mixedMetrics(List.of("flowIntensity")).build();
        result.setAnalysisContentHash(computeContentHash(result));
        result.setDurationMs(System.currentTimeMillis() - started);
        return result;
    }

    private BreadthBlock breadth(List<LocalDate> tradingDays, Map<LocalDate, LocalDate> prevDay,
                                 Map<String, TreeMap<LocalDate, BigDecimal>> closes, List<String> sampleSymbols) {
        List<DailyBreadth> daily = new ArrayList<>();
        Long adLine = null;
        boolean broken = false;
        for (LocalDate day : tradingDays) {
            long adv = 0, dec = 0, flat = 0, valid = 0;
            LocalDate prev = prevDay.get(day);
            if (prev != null) {
                for (String symbol : sampleSymbols) {
                    TreeMap<LocalDate, BigDecimal> series = closes.getOrDefault(symbol, new TreeMap<>());
                    BigDecimal close = series.get(day), previous = series.get(prev);
                    if (close == null || previous == null) continue;
                    valid++;
                    int cmp = close.compareTo(previous);
                    if (cmp > 0) adv++; else if (cmp < 0) dec++; else flat++;
                }
            }
            DailyBreadth row = DailyBreadth.builder().date(day.toString()).advancing(adv).declining(dec).flat(flat)
                    .validStocks(valid).status(valid == 0 ? STATUS_EMPTY : "OK").build();
            if (valid == 0) {
                broken = true;  // M-08：该日 A/D 不产出，且不得跳日外推
            } else {
                row.setAdvanceRatio(BigDecimal.valueOf(adv).divide(BigDecimal.valueOf(valid), 10, RoundingMode.HALF_UP));
                if (!broken) adLine = adLine == null ? adv - dec : adLine + adv - dec;  // 首日种子 adv(t0)−dec(t0)
            }
            row.setAdLine(broken ? null : adLine);
            daily.add(row);
        }
        return BreadthBlock.builder().daily(daily)
                .caliber("adv/dec/flat 需 t 与 t-1 两根 bar，首日 t-1 取预热窗；adLine 首日种子=adv−dec")
                .providers(List.of(PROVIDER_TENCENT_PUBLIC)).build();
    }

    private IndustryTurnoverBlock industryTurnover(List<LocalDate> tradingDays,
                                                   Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
                                                   List<String> sampleSymbols,
                                                   Map<String, Mr0PocAnalysisMapper.MembershipRow> membership) {
        Map<String, String> industryNames = new TreeMap<>();
        sampleSymbols.stream().map(membership::get).filter(Objects::nonNull)
                .forEach(m -> industryNames.putIfAbsent(m.getIndustryCode(), m.getIndustryName()));
        Map<String, IndustryTurnover> byIndustryMap = new LinkedHashMap<>();
        List<DailyMarketTurnover> dailyMarket = new ArrayList<>();
        for (LocalDate day : tradingDays) {
            Map<String, BigDecimal> sectorSum = new TreeMap<>();
            BigDecimal market = BigDecimal.ZERO;
            boolean lookahead = false;
            for (String symbol : sampleSymbols) {
                Mr0PocAnalysisMapper.MembershipRow m = membership.get(symbol);
                if (m == null) continue;  // 无成分股票不入分母，计入 coverageGap
                BigDecimal amount = amounts.getOrDefault(symbol, new TreeMap<>()).get(day);
                if (amount == null) continue;
                sectorSum.merge(m.getIndustryCode(), amount, BigDecimal::add);
                market = market.add(amount);
                lookahead = lookahead || m.getAsOfDate().isAfter(day);
            }
            BigDecimal sumShare = BigDecimal.ZERO;
            for (BigDecimal sector : sectorSum.values()) { sumShare = sumShare.add(sector.divide(market, 10, RoundingMode.HALF_UP)); }
            if (!sectorSum.isEmpty()) {
                dailyMarket.add(DailyMarketTurnover.builder().date(day.toString()).marketTurnover(setScale(market))
                        .sumShare(setScale10(sumShare)).build());  // CR-10：占比输出 10 位小数
            }
            for (Map.Entry<String, BigDecimal> sector : sectorSum.entrySet()) {
                byIndustryMap.computeIfAbsent(sector.getKey(), code -> IndustryTurnover.builder().industryCode(code)
                        .industryName(industryNames.getOrDefault(code, code)).days(new ArrayList<>()).build())
                        .getDays().add(IndustryDay.builder().date(day.toString()).sectorTurnover(setScale(sector.getValue()))
                                .share(setScale10(sector.getValue().divide(market, 10, RoundingMode.HALF_UP)))  // CR-10
                                .lookaheadAffected(lookahead).build());
            }
        }
        List<String> gap = sampleSymbols.stream().filter(symbol -> membership.get(symbol) == null).sorted().toList();
        return IndustryTurnoverBlock.builder().byIndustry(new ArrayList<>(byIndustryMap.values())).dailyMarket(dailyMarket)
                .coverageGap(CoverageGap.builder().count(gap.size()).symbols(gap).build())
                .caliber("sectorTurnover=成分样本股 amount 合计(元)；share 分母=覆盖域(有成分样本股)，Σshare=1±1e-6")
                .providers(List.of(PROVIDER_TENCENT_PUBLIC)).build();
    }

    private VolatilityBlock volatility(LocalDate asOf, Map<String, TreeMap<LocalDate, BigDecimal>> closes,
                                       List<String> sampleSymbols) {
        List<BigDecimal> vols = new ArrayList<>();
        long excluded = 0;
        for (String symbol : sampleSymbols) {
            TreeMap<LocalDate, BigDecimal> series = closes.getOrDefault(symbol, new TreeMap<>());
            // CR-6：asOf 当日必须有 bar（headMap 会接受无 asOf bar 的陈旧窗口，视为不合格）
            if (asOf == null || !series.containsKey(asOf)) { excluded++; continue; }
            List<BigDecimal> history = List.copyOf(series.headMap(asOf, true).values());
            if (history.size() < 21) { excluded++; continue; }  // 边界两侧：恰好 21=成功，20=阻断
            vols.add(realizedVol20(history.subList(history.size() - 21, history.size())));
        }
        vols.sort(BigDecimal::compareTo);
        boolean blocked = vols.isEmpty();  // 合格股票数=0 → 整块阻断，无任何部分数值
        return VolatilityBlock.builder().asOfDate(blocked ? null : asOf.toString()).annualized(false)
                .qualifiedStocks(vols.size()).excludedForWarmup(excluded)
                .marketMedian(blocked ? null : percentile(vols, 0.5)).marketP90(blocked ? null : percentile(vols, 0.9))
                .status(blocked ? STATUS_WARMUP : "OK")
                .caliber("20 日实现波动率=最近 21 根收盘的 20 个简单收益率样本标准差(ddof=1)，未年化")
                .providers(List.of(PROVIDER_TENCENT_PUBLIC)).build();
    }

    private LiquidityProxyBlock liquidity(List<LocalDate> tradingDays, Map<LocalDate, LocalDate> prevDay,
                                          Map<String, TreeMap<LocalDate, BigDecimal>> closes,
                                          Map<String, TreeMap<LocalDate, BigDecimal>> amounts,
                                          List<String> sampleSymbols) {
        List<BigDecimal> means = new ArrayList<>();
        long zeroRows = 0;
        for (String symbol : sampleSymbols) {
            BigDecimal sum = BigDecimal.ZERO;
            long observations = 0;
            for (LocalDate day : tradingDays) {
                LocalDate prev = prevDay.get(day);
                if (prev == null) continue;
                TreeMap<LocalDate, BigDecimal> series = closes.getOrDefault(symbol, new TreeMap<>());
                BigDecimal close = series.get(day), previous = series.get(prev);
                BigDecimal amount = amounts.getOrDefault(symbol, new TreeMap<>()).get(day);
                if (close == null || previous == null) continue;
                if (amount == null || amount.signum() <= 0) { zeroRows++; continue; }  // amount=0 跳过并计数（除零守卫）
                sum = sum.add(ratio(close, previous).abs().divide(amount, 20, RoundingMode.HALF_UP));
                observations++;
            }
            if (observations > 0) { means.add(sum.divide(BigDecimal.valueOf(observations), 12, RoundingMode.HALF_UP)); }
        }
        means.sort(BigDecimal::compareTo);
        boolean empty = means.isEmpty();
        return LiquidityProxyBlock.builder().unit("1/元").zeroAmountRows(zeroRows).qualifiedStocks(means.size())
                .marketMedian(empty ? null : percentile(means, 0.5)).marketP90(empty ? null : percentile(means, 0.9))
                .status(empty ? STATUS_EMPTY : "OK")
                .caliber("illiquidity(i,t)=|close(t)/close(t-1)−1|/amount(t)，逐股窗口均值，市场中位数+P90")
                .providers(List.of(PROVIDER_TENCENT_PUBLIC)).build();
    }

    private MoneyFactsBlock moneyFacts(List<LocalDate> tradingDays,
                                       Map<String, Map<LocalDate, Mr0PocAnalysisMapper.MoneyFlowRow>> flows,
                                       Map<String, Mr0PocAnalysisMapper.MembershipRow> membership,
                                       List<String> sampleSymbols,
                                       Map<String, TreeMap<LocalDate, BigDecimal>> amounts) {
        Map<String, String> industryNames = new TreeMap<>();
        sampleSymbols.stream().map(membership::get).filter(Objects::nonNull)
                .forEach(m -> industryNames.putIfAbsent(m.getIndustryCode(), m.getIndustryName()));
        Map<String, List<MoneyDay>> industryDays = new TreeMap<>();
        long inconsistentDays = 0;
        for (LocalDate day : tradingDays) {
            Map<String, List<Mr0PocAnalysisMapper.MoneyFlowRow>> byIndustry = new TreeMap<>();
            for (String symbol : sampleSymbols) {
                Mr0PocAnalysisMapper.MembershipRow m = membership.get(symbol);
                Mr0PocAnalysisMapper.MoneyFlowRow flow = flows.getOrDefault(symbol, Map.of()).get(day);
                if (m != null && flow != null) byIndustry.computeIfAbsent(m.getIndustryCode(), k -> new ArrayList<>()).add(flow);
            }
            for (Map.Entry<String, List<Mr0PocAnalysisMapper.MoneyFlowRow>> entry : byIndustry.entrySet()) {
                BigDecimal sum = BigDecimal.ZERO;
                Collection<BigDecimal> cateNas = new ArrayList<>();
                for (Mr0PocAnalysisMapper.MoneyFlowRow flow : entry.getValue()) {
                    if (flow.getMainNetInflow() != null) { sum = sum.add(flow.getMainNetInflow()); }
                    if (flow.getIndustryNetInflow() != null) { cateNas.add(flow.getIndustryNetInflow()); }
                }
                boolean inconsistent = cateNas.stream().distinct().count() > 1;
                if (inconsistent) { inconsistentDays++; }
                BigDecimal cateNa = modeOf(cateNas);  // 同行业成员同值；不一致取众数（并列取较小）
                industryDays.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(MoneyDay.builder().date(day.toString()).sumMainNetInflow(setScale(sum)).cateNaValue(cateNa)
                                // CR-4：字典 M-15 冻结绝对差（元）deviation=Σ成员净流入−cate_na；cate_na null→null
                                .deviation(cateNa == null ? null : sum.subtract(cateNa))
                                .inconsistentCateNa(inconsistent).build());
            }
        }
        BigDecimal netFlow = BigDecimal.ZERO, turnover = BigDecimal.ZERO;  // flowIntensity 混源：同窗口同覆盖域
        long pairs = 0;
        for (String symbol : sampleSymbols) {
            for (LocalDate day : tradingDays) {
                Mr0PocAnalysisMapper.MoneyFlowRow flow = flows.getOrDefault(symbol, Map.of()).get(day);
                BigDecimal amount = amounts.getOrDefault(symbol, new TreeMap<>()).get(day);
                if (flow != null && flow.getMainNetInflow() != null && amount != null && amount.signum() > 0) {
                    netFlow = netFlow.add(flow.getMainNetInflow());
                    turnover = turnover.add(amount);
                    pairs++;
                }
            }
        }
        FlowIntensity intensity = FlowIntensity.builder().providers(List.of(PROVIDER_SINA_PUBLIC, PROVIDER_TENCENT_PUBLIC))
                .window("analysisWindow").windowNetInflow(setScale(netFlow)).windowTurnover(setScale(turnover))
                .value(pairs == 0 || turnover.signum() == 0 ? null : netFlow.divide(turnover, 12, RoundingMode.HALF_UP))
                .status(pairs == 0 ? STATUS_EMPTY : "OK").build();
        List<IndustryMoney> byIndustry = industryDays.entrySet().stream()
                .map(entry -> IndustryMoney.builder().industryCode(entry.getKey())
                        .industryName(industryNames.getOrDefault(entry.getKey(), entry.getKey())).days(entry.getValue()).build()).toList();
        return MoneyFactsBlock.builder().byIndustry(byIndustry).inconsistentCateNaDays(inconsistentDays).flowIntensity(intensity)
                .caliber("sumMainNetInflow=成员 netamount 合计(元)；cate_na 偏差仅报告不判等；flowIntensity 混源已标注")
                .providers(List.of(PROVIDER_SINA_PUBLIC)).build();
    }

    /** 对白名单字段的规范化 JSON（键排序、BigDecimal.toPlainString、无空格）计算 sha256。 */
    private String computeContentHash(AnalysisResult result) {
        try {
            ObjectNode tree = (ObjectNode) new ObjectMapper().findAndRegisterModules().valueToTree(result);
            tree.retain(List.of(HASH_FIELD_WHITELIST));  // 只保留白名单块
            tree.put("analysisStart", String.valueOf(result.getAnalysisStart()));
            tree.put("analysisEnd", String.valueOf(result.getAnalysisEnd()));
            return sha256(new ObjectMapper().writeValueAsString(canonicalize(tree, List.of(HASH_EXCLUDED_FIELDS))));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("规范化 JSON 序列化失败", exception);
        }
    }

    /** 递归转 TreeMap/ArrayList/字符串叶子（键排序；DecimalNode.asText 即 toPlainString；跳过排除字段）。 */
    private Object canonicalize(JsonNode node, List<String> excluded) {
        if (node.isObject()) {
            TreeMap<String, Object> map = new TreeMap<>();
            node.fields().forEachRemaining(field -> {
                if (!excluded.contains(field.getKey())) { map.put(field.getKey(), canonicalize(field.getValue(), excluded)); }
            });
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(canonicalize(item, excluded)));
            return list;
        }
        return node.isBoolean() ? Boolean.toString(node.asBoolean()) : node.asText();
    }

    private List<MetricAttribution> attributions() {
        return List.of(new MetricAttribution("universe", "证券池快照 as-of 入库", List.of(PROVIDER_SINA_PUBLIC)),
                new MetricAttribution("tradingDays", CALENDAR, List.of(PROVIDER_TENCENT_PUBLIC)),
                new MetricAttribution("breadth", "adLine 首日种子=adv−dec", List.of(PROVIDER_TENCENT_PUBLIC)),
                new MetricAttribution("industryTurnover", "成交额元；分组标签 SINA_INDUSTRY（taxonomy 非价量源）", List.of(PROVIDER_TENCENT_PUBLIC)),
                new MetricAttribution("volatility", "简单收益 ddof=1 未年化", List.of(PROVIDER_TENCENT_PUBLIC)),
                new MetricAttribution("liquidityProxy", "|r|/amount(元)", List.of(PROVIDER_TENCENT_PUBLIC)),
                new MetricAttribution("moneyFacts", "netamount/cate_na 原值元", List.of(PROVIDER_SINA_PUBLIC)),
                new MetricAttribution("flowIntensity", "Σ净流入/Σ成交额 同窗口同覆盖域（混源）", List.of(PROVIDER_SINA_PUBLIC, PROVIDER_TENCENT_PUBLIC)));
    }

    private BigDecimal realizedVol20(List<BigDecimal> closes21) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < closes21.size(); i++) { returns.add(ratio(closes21.get(i), closes21.get(i - 1))); }
        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(returns.size()), 20, RoundingMode.HALF_UP);
        BigDecimal variance = returns.stream().map(value -> value.subtract(mean).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size() - 1L), 20, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(12, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal close, BigDecimal previous) { return close.divide(previous, 20, RoundingMode.HALF_UP).subtract(BigDecimal.ONE); }

    /** 线性插值分位（字典 M-21 冻结方法）。 */
    private BigDecimal percentile(List<BigDecimal> sorted, double quantile) {
        double index = quantile * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = Math.min(lower + 1, sorted.size() - 1);
        return sorted.get(lower).add(sorted.get(upper).subtract(sorted.get(lower))
                .multiply(BigDecimal.valueOf(index - lower))).setScale(12, RoundingMode.HALF_UP);
    }

    /** 众数（并列取较小值，TreeMap+max 首遇确定性）。 */
    private BigDecimal modeOf(Collection<BigDecimal> values) {
        TreeMap<BigDecimal, Long> counts = new TreeMap<>();
        values.forEach(value -> counts.merge(value, 1L, Long::sum));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static BigDecimal setScale(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }

    /** CR-10：share/sumShare 输出 10 位小数（与 ε=1e-6 求和容差同数量级，去除 2 位粗粒度）。 */
    private static BigDecimal setScale10(BigDecimal value) { return value == null ? null : value.setScale(10, RoundingMode.HALF_UP); }

    private static String sha256(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 不可用", exception); }
    }
}
