package com.quant.trade.marketdata.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.AnalysisResult;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.BreadthBlock;
import com.quant.trade.marketdata.poc.Mr0PocAnalysisService.DailyBreadth;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MR-0 PoC 质量报告引擎（AC-06，report 引擎并入，REC-7）。八检查族全部为结构化对象
 * （family/status/reasonCode/affectedCount/details，REC-8），status∈OK/WARN/FAIL/BLOCKED。
 * 只读库+分析结果，零外联。toMarkdown() 确定性：固定族顺序、无时间戳。跨进程重算一致性由
 * TEST-07 两次运行与 analysisRereadsStorageEachCall 用例证明，本引擎只做进程内中位日重算比对。
 */
@Service
@RequiredArgsConstructor
public class Mr0PocQualityService {

    public static final String COVERAGE = "COVERAGE";
    public static final String GAPS = "GAPS";
    public static final String DUPLICATES = "DUPLICATES";
    public static final String STALENESS = "STALENESS";
    public static final String TIME_POINT_LOOKAHEAD = "TIME_POINT_LOOKAHEAD";
    public static final String PROVIDER_MIXING = "PROVIDER_MIXING";
    public static final String UNIT_ANOMALY = "UNIT_ANOMALY";
    public static final String RECOMPUTE_CONSISTENCY = "RECOMPUTE_CONSISTENCY";
    static final String LOOKAHEAD_NOTE = "当前成分聚合历史=时点穿越风险，PoC 显式假设";
    private static final BigDecimal VWAP_EPS = new BigDecimal("0.000001");
    private static final long STALE_LAG_HOURS = 48;
    private static final double LOW_COVERAGE = 0.9;

    private final Mr0PocAnalysisMapper mapper;

    /** 单检查族（结构化对象，REC-8）。 */
    @Data @Builder public static class CheckFamily { private String family, status, reasonCode; private long affectedCount; private List<String> details; }

    /** 质量报告（JSON 序列化稳定；markdown 经 toMarkdown()）。 */
    @Data @Builder public static class QualityReport {
        private List<CheckFamily> families;

        /** 确定性 Markdown：固定族顺序、无时间戳。 */
        public String toMarkdown() {
            StringBuilder markdown = new StringBuilder("# MR-0 PoC 质量报告\n\n");
            for (CheckFamily family : families) {
                markdown.append("## ").append(family.getFamily()).append('\n')
                        .append("- status: ").append(family.getStatus()).append('\n')
                        .append("- reasonCode: ").append(family.getReasonCode()).append('\n')
                        .append("- affectedCount: ").append(family.getAffectedCount()).append('\n');
                for (String detail : family.getDetails()) { markdown.append("- ").append(detail).append('\n'); }
            }
            return markdown.toString();
        }
    }

    /** 基于分析结果生成八族质量报告（每次调用重新读库核对）。 */
    public QualityReport generateReport(AnalysisResult analysis) {
        return QualityReport.builder().families(List.of(coverage(analysis), gaps(analysis), duplicates(analysis),
                staleness(analysis), lookahead(analysis), providerMixing(analysis), unitAnomaly(analysis),
                recomputeConsistency(analysis))).build();
    }

    private CheckFamily coverage(AnalysisResult analysis) {
        List<String> dates = analysis.getTradingDays().getDates();
        List<String> details = new ArrayList<>();
        if (dates.isEmpty()) { return family(COVERAGE, "BLOCKED", "NO_TRADING_DAYS", 0, details); }
        long sampleSize = analysis.getUniverse().getSampleSymbols();
        Map<String, Long> barsPerDay = tencentBars(analysis).stream().filter(bar -> !"SH.000001".equals(bar.getCanonicalSymbol()))
                .collect(Collectors.groupingBy(bar -> bar.getTradeDate().toString(), Collectors.counting()));
        BigDecimal worst = BigDecimal.ONE;
        for (String date : dates) {
            long bars = barsPerDay.getOrDefault(date, 0L);
            BigDecimal ratio = sampleSize == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(bars).divide(BigDecimal.valueOf(sampleSize), 6, RoundingMode.HALF_UP);
            worst = ratio.compareTo(worst) < 0 ? ratio : worst;
            details.add(date + " tencentBars=" + bars + " sampleSymbols=" + sampleSize + " coverage=" + ratio.toPlainString());
        }
        long gap = analysis.getIndustryTurnover().getCoverageGap().getCount();
        details.add("membershipCoverage=" + (sampleSize == 0 ? "0" : BigDecimal.valueOf(sampleSize - gap)
                .divide(BigDecimal.valueOf(sampleSize), 6, RoundingMode.HALF_UP).toPlainString()) + " coverageGapSymbols=" + gap);
        boolean low = sampleSize == 0 || worst.compareTo(BigDecimal.valueOf(LOW_COVERAGE)) < 0;
        return family(COVERAGE, low ? "WARN" : "OK", low ? "LOW_DAY_COVERAGE" : "NONE", 0, details);
    }

    private CheckFamily gaps(AnalysisResult analysis) {
        List<String> dates = analysis.getTradingDays().getDates();
        List<String> details = new ArrayList<>();
        Map<String, Set<String>> present = tencentBars(analysis).stream().collect(Collectors.groupingBy(
                Mr0PocAnalysisMapper.BarRow::getCanonicalSymbol,
                Collectors.mapping(bar -> bar.getTradeDate().toString(), Collectors.toSet())));
        long missing = dates.size() * analysis.getUniverse().getSampleSymbols();
        missing -= present.entrySet().stream().filter(e -> !"SH.000001".equals(e.getKey()))
                .mapToLong(e -> dates.stream().filter(e.getValue()::contains).count()).sum();
        List<String> zeroBarSymbols = present.entrySet().stream()
                .filter(e -> !"SH.000001".equals(e.getKey()) && dates.stream().noneMatch(e.getValue()::contains))
                .map(Map.Entry::getKey).sorted().toList();
        long coverageGapDays = analysis.getIndustryTurnover().getCoverageGap().getCount() * dates.size();
        details.add("missingStockDays=" + missing);
        details.add("zeroBarSymbols=" + zeroBarSymbols);
        details.add("coverageGapDays=" + coverageGapDays);
        boolean has = missing > 0 || !zeroBarSymbols.isEmpty() || coverageGapDays > 0;
        return family(GAPS, has ? "WARN" : "OK", has ? "MISSING_TRADING_DATA" : "NONE", missing, details);
    }

    private CheckFamily duplicates(AnalysisResult analysis) {
        Map<String, List<String>> keySources = new TreeMap<>();
        for (Mr0PocAnalysisMapper.BarRow row : mapper.selectDailyBars(analysis.getAnalysisStart(), analysis.getAnalysisEnd(), null)) {
            keySources.computeIfAbsent(row.getCanonicalSymbol() + "|" + row.getTradeDate(), k -> new ArrayList<>()).add(row.getDataSource());
        }
        List<String> details = new ArrayList<>();
        long affected = 0;
        for (Map.Entry<String, List<String>> entry : keySources.entrySet()) {
            if (entry.getValue().stream().distinct().count() > 1) {  // 跨 data_source 并存（uk 保证同源无重复）
                affected += entry.getValue().size();
                details.add(entry.getKey() + " dataSources=" + entry.getValue().stream().distinct().sorted().toList());
            }
        }
        return family(DUPLICATES, "OK", affected > 0 ? "CROSS_SOURCE_COEXISTENCE" : "NONE", affected, details);
    }

    private CheckFamily staleness(AnalysisResult analysis) {
        List<Long> lags = tencentBars(analysis).stream().filter(bar -> bar.getFetchedAt() != null)
                .map(bar -> Duration.between(bar.getTradeDate().atStartOfDay(), bar.getFetchedAt()).toHours()).sorted().toList();
        List<String> details = new ArrayList<>();
        details.add("fetchedLagHours max=" + (lags.isEmpty() ? 0 : lags.get(lags.size() - 1))
                + " median=" + (lags.isEmpty() ? 0 : lags.get(lags.size() / 2)));
        LocalDate asOf = analysis.getUniverse().getAsOfDate();
        details.add("universeAsOfLagDays=" + (analysis.getAnalysisEnd().toEpochDay()
                - (asOf == null ? analysis.getAnalysisEnd() : asOf).toEpochDay()));
        long calendarRows = mapper.countMarketCalendar("CN");
        boolean calendarEmpty = calendarRows == 0;  // 本地事实发现，不外联回填（D8）
        details.add("marketCalendarCnRows=" + calendarRows + (calendarEmpty ? "（空表，INDEX_KLINE_DERIVED 兜底）" : ""));
        long stale = lags.stream().filter(lag -> lag > STALE_LAG_HOURS).count();
        boolean warn = calendarEmpty || stale > 0;
        return family(STALENESS, warn ? "WARN" : "OK",
                calendarEmpty ? "MARKET_CALENDAR_CN_EMPTY" : stale > 0 ? "STALE_FETCH_LAG" : "NONE", stale, details);
    }

    private CheckFamily lookahead(AnalysisResult analysis) {
        List<String> details = new ArrayList<>(List.of(LOOKAHEAD_NOTE));
        long industryDays = analysis.getIndustryTurnover().getByIndustry().stream()
                .flatMap(industry -> industry.getDays().stream())
                .filter(Mr0PocAnalysisService.IndustryDay::isLookaheadAffected).count();
        details.add("lookaheadAffectedIndustryDays=" + industryDays);
        Map<String, LocalDate> membershipAsOf = new LinkedHashMap<>();
        mapper.selectIndustryMemberships(null, Mr0PocAnalysisService.TAXONOMY_SINA_INDUSTRY)
                .forEach(row -> membershipAsOf.putIfAbsent(row.getCanonicalSymbol(), row.getAsOfDate()));
        long stockDays = 0;
        for (Map.Entry<String, LocalDate> entry : membershipAsOf.entrySet()) {
            long affected = analysis.getTradingDays().getDates().stream()
                    .filter(date -> entry.getValue().isAfter(LocalDate.parse(date))).count();
            if (affected > 0) { stockDays += affected; details.add(entry.getKey() + " asOf=" + entry.getValue() + " affectedDays=" + affected); }
        }
        boolean affected = stockDays + industryDays > 0;
        return family(TIME_POINT_LOOKAHEAD, affected ? "WARN" : "OK", "CURRENT_MEMBERSHIP_FOR_HISTORY",
                stockDays + industryDays, details);
    }

    private CheckFamily providerMixing(AnalysisResult analysis) {
        Map<String, List<String>> blockProviders = new LinkedHashMap<>();
        blockProviders.put("universe", analysis.getUniverse().getProviders());
        blockProviders.put("tradingDays", analysis.getTradingDays().getProviders());
        blockProviders.put("breadth", analysis.getBreadth().getProviders());
        blockProviders.put("industryTurnover", analysis.getIndustryTurnover().getProviders());
        blockProviders.put("volatility", analysis.getVolatility().getProviders());
        blockProviders.put("liquidityProxy", analysis.getLiquidityProxy().getProviders());
        blockProviders.put("moneyFacts", analysis.getMoneyFacts().getProviders());
        blockProviders.put("flowIntensity", analysis.getMoneyFacts().getFlowIntensity().getProviders());
        List<String> details = new ArrayList<>();
        blockProviders.forEach((block, providers) -> { if (emptyProviders(providers)) { details.add(block + " 缺 provider 标注"); } });
        if (analysis.getMixedMetrics() == null || !analysis.getMixedMetrics().contains("flowIntensity")) {
            details.add("mixedMetrics 未列出 flowIntensity");
        }
        boolean fail = !details.isEmpty();
        return family(PROVIDER_MIXING, fail ? "FAIL" : "OK", fail ? "MISSING_PROVIDER_ATTRIBUTION" : "NONE", details.size(), details);
    }

    private boolean emptyProviders(List<String> providers) {
        return providers == null || providers.isEmpty() || providers.stream().anyMatch(String::isBlank);
    }

    private CheckFamily unitAnomaly(AnalysisResult analysis) {
        List<String> details = new ArrayList<>();
        long vwapViolations = 0;
        long nonPositiveAmount = 0;
        for (Mr0PocAnalysisMapper.BarRow row : mapper.selectDailyBars(analysis.getAnalysisStart(), analysis.getAnalysisEnd(), null)) {
            if (row.getVolume() != null && row.getVolume() > 0 && row.getAmount() != null
                    && row.getLowPrice() != null && row.getHighPrice() != null) {
                BigDecimal vwap = row.getAmount().divide(BigDecimal.valueOf(row.getVolume()), 6, RoundingMode.HALF_UP);
                BigDecimal lowFloor = row.getLowPrice().subtract(row.getLowPrice().multiply(VWAP_EPS));
                BigDecimal highCeiling = row.getHighPrice().add(row.getHighPrice().multiply(VWAP_EPS));
                if (vwap.compareTo(lowFloor) < 0 || vwap.compareTo(highCeiling) > 0) {  // 字典 §3 单位自检
                    vwapViolations++;
                    details.add(row.getCanonicalSymbol() + "|" + row.getTradeDate() + " vwap=" + vwap.toPlainString()
                            + " outside [" + row.getLowPrice().toPlainString() + "," + row.getHighPrice().toPlainString() + "]");
                }
            }
            if (row.getAmount() != null && row.getAmount().signum() <= 0 && row.getVolume() != null && row.getVolume() > 0) {
                nonPositiveAmount++;
                details.add(row.getCanonicalSymbol() + "|" + row.getTradeDate() + " amount<=0 with volume>0");
            }
        }
        long turnoverViolations = mapper.selectUniverseSnapshots(null, Mr0PocAnalysisService.PROVIDER_SINA_PUBLIC).stream()
                .filter(row -> row.getTurnoverRate() != null && row.getTurnoverRate().compareTo(BigDecimal.ONE) > 0).count();
        if (turnoverViolations > 0) { details.add("universeTurnoverRateAboveOne=" + turnoverViolations); }
        long nanHits = scanForbiddenTokens(analysis);
        if (nanHits > 0) { details.add("forbiddenTokenHits=" + nanHits); }
        long affectedCount = vwapViolations + turnoverViolations + nonPositiveAmount + nanHits;
        String status = vwapViolations > 0 ? "FAIL" : affectedCount > 0 ? "WARN" : "OK";
        return family(UNIT_ANOMALY, status, vwapViolations > 0 ? "UNIT_MISMATCH_VWAP" : "NONE", affectedCount, details);
    }

    private long scanForbiddenTokens(AnalysisResult analysis) {
        try {
            String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(analysis);
            return (json.contains("NaN") ? 1 : 0) + (json.contains("Infinity") ? 1 : 0);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("分析结果序列化失败", exception);
        }
    }

    private CheckFamily recomputeConsistency(AnalysisResult analysis) {
        List<String> details = new ArrayList<>();
        String hash = analysis.getAnalysisContentHash();
        if (hash == null || hash.isBlank()) { return family(RECOMPUTE_CONSISTENCY, "FAIL", "MISSING_CONTENT_HASH", 1, details); }
        details.add("analysisContentHash present: " + hash);
        BreadthBlock breadth = analysis.getBreadth();
        if (breadth.getDaily().isEmpty()) { return family(RECOMPUTE_CONSISTENCY, "BLOCKED", "NO_TRADING_DAYS", 0, details); }
        DailyBreadth medianDay = breadth.getDaily().get(breadth.getDaily().size() / 2);
        Map<String, TreeMap<LocalDate, BigDecimal>> closes = new TreeMap<>();
        for (Mr0PocAnalysisMapper.BarRow bar : mapper.selectDailyBars(analysis.getAnalysisStart().minusDays(120),
                analysis.getAnalysisEnd(), Mr0PocAnalysisService.PROVIDER_TENCENT_PUBLIC)) {
            closes.computeIfAbsent(bar.getCanonicalSymbol(), k -> new TreeMap<>()).put(bar.getTradeDate(), bar.getClosePrice());
        }
        LocalDate day = LocalDate.parse(medianDay.getDate());
        LocalDate prev = closes.getOrDefault("SH.000001", new TreeMap<>()).floorKey(day.minusDays(1));
        long recomputed = 0;
        if (prev != null) {
            for (TreeMap<LocalDate, BigDecimal> series : closes.values()) {
                BigDecimal close = series.get(day), previous = series.get(prev);
                if (close != null && previous != null && close.compareTo(previous) > 0) { recomputed++; }
            }
        }
        boolean match = recomputed == medianDay.getAdvancing();
        details.add("medianDay=" + medianDay.getDate() + " breadthAdvancing=" + medianDay.getAdvancing()
                + " recomputedAdvancing=" + recomputed);
        details.add("跨进程重算一致性由 TEST-07 两次运行与 analysisRereadsStorageEachCall 证明");
        return family(RECOMPUTE_CONSISTENCY, match ? "OK" : "FAIL", match ? "RECOMPUTED_MATCH" : "RECOMPUTE_MISMATCH",
                match ? 0 : 1, details);
    }

    private List<Mr0PocAnalysisMapper.BarRow> tencentBars(AnalysisResult analysis) {
        return mapper.selectDailyBars(analysis.getAnalysisStart(), analysis.getAnalysisEnd(), Mr0PocAnalysisService.PROVIDER_TENCENT_PUBLIC);
    }

    private CheckFamily family(String family, String status, String reasonCode, long affectedCount, List<String> details) {
        return CheckFamily.builder().family(family).status(status).reasonCode(reasonCode)
                .affectedCount(affectedCount).details(details).build();
    }
}
