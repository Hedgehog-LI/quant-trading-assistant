package com.quant.trade.marketdata.poc;

import com.quant.trade.marketdata.manager.StockBasicRegistrationManager;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import com.quant.trade.marketdata.poc.PublicMarketDataClient.DailyBarEntry;
import com.quant.trade.marketdata.poc.PublicMarketDataClient.MoneyFlowEntry;
import com.quant.trade.marketdata.poc.PublicMarketDataClient.UniverseEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MR-0 PoC 幂等导入服务（AC-04）。流程：证券池分页全量 → 流通市值降序前 sampleSize → 行业目录+全行业
 * 分页成分（样本外丢弃不落库）→ 逐样本腾讯日 K（换算后落既有 stock_daily_bar，
 * data_source=TENCENT_PUBLIC、adjust_type=NONE）→ 逐样本新浪资金流（窗口过滤后落 mr0 表）→ 恒抓基准
 * SH.000001 日 K（CR-1：样本循环后追加，指数行免字典 §3 个股 VWAP 自检；失败仅记 failure 不中断；
 * 不参与资金流/成分/sampleSize/ensureRegistered）→ 全样本幂等补齐 stock_basic 最小身份
 * （不覆盖已有 name/list_date；基准不回填身份）。
 * 冻结口径：amount=万元×10000、volume=手×100、换手率 %/100 仅作用于 universe 快照（快照值不拼日频
 * 序列，字典 M-05）；资金净额原值元；腾讯换手率不写任何表（无归属列）。幂等靠 uk + ON DUPLICATE
 * KEY UPDATE（重跑 inserted=0）；单 symbol 失败记录后继续。失效场景：公共源结构变化按 symbol 抛错
 * 记录；NONE 复权除权日 VWAP 自检可能误报（字典 §3、D7）。
 */
@Service
@RequiredArgsConstructor
public class Mr0PocIngestService {

    public static final String PROVIDER_SINA_PUBLIC = "SINA_PUBLIC";
    public static final String PROVIDER_TENCENT_PUBLIC = "TENCENT_PUBLIC";
    public static final String TAXONOMY_SINA_INDUSTRY = "SINA_INDUSTRY";
    public static final String ADJUST_TYPE_NONE = "NONE";
    private static final String BENCHMARK_CANONICAL = "SH.000001";  // D5 基准恒入 universe 不算样本
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private static final BigDecimal WAN = new BigDecimal("10000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PublicMarketDataClient client;
    private final Mr0PocMapper mr0PocMapper;
    private final StockBasicRegistrationManager stockBasicRegistrationManager;

    /** mr0_universe_snapshot 写入行（换算后单位：市值元、换手率小数）。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UniverseSnapshotRow {
        private String providerCode, canonicalSymbol, symbol, name, market;
        private BigDecimal totalMarketCap, circulatingMarketCap, turnoverRate;
        private LocalDate asOfDate;
        private LocalDateTime fetchedAt;
    }

    /** mr0_industry_membership 写入行。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IndustryMembershipRow {
        private String taxonomyCode, providerCode, industryCode, industryName, canonicalSymbol;
        private LocalDate asOfDate;
        private LocalDateTime fetchedAt;
    }

    /** mr0_stock_money_flow_daily 写入行（净额元）。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MoneyFlowRow {
        private String canonicalSymbol, providerCode, industryCode;
        private LocalDate tradeDate;
        private BigDecimal mainNetInflow, mainNetInflowRatio, superNet, industryNetInflow;
        private LocalDateTime fetchedAt;
    }

    /** 导入命令；默认值即冻结 PoC 口径（D5）。asOfDate/fetchedAt 为 null=抓取当日/now（REC-10）；dryRun 供测试不落库。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IngestCommand {
        @Builder.Default private LocalDate analysisStart = LocalDate.of(2026, 7, 1);
        @Builder.Default private LocalDate analysisEnd = LocalDate.of(2026, 7, 31);
        @Builder.Default private LocalDate warmupStart = LocalDate.of(2026, 4, 1);
        @Builder.Default private int sampleSize = 150;
        private LocalDate asOfDate;
        private LocalDateTime fetchedAt;
        private boolean dryRun;
    }

    /** 导入汇总：各表 inserted/updated/skipped 计数、失败明细（symbol+阶段+原因）与样本清单。 */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IngestResult {
        @Builder.Default private TableSummary universe = new TableSummary();
        @Builder.Default private TableSummary membership = new TableSummary();
        @Builder.Default private TableSummary dailyBar = new TableSummary();
        @Builder.Default private TableSummary moneyFlow = new TableSummary();
        @Builder.Default private List<SymbolFailure> failures = new ArrayList<>();
        @Builder.Default private List<String> sampleSymbols = new ArrayList<>();
    }

    /** 单表写入计数（H2/MySQL ODKU 返回行数方言不一致，inserted/updated 由窗口预查计数推出）。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TableSummary {
        private long inserted, updated, skipped;

        void accumulate(long written, long existing) {
            if (existing >= written) {
                updated += written;
            } else {
                inserted += written - existing;
                updated += existing;
            }
        }

        void addSkipped(long delta) {
            skipped += delta;
        }
    }

    /** 单 symbol/阶段失败记录（不中断整批）。 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SymbolFailure {
        private String canonicalSymbol, stage, reason;
    }

    /** 执行一次完整导入并返回汇总（单 symbol 失败记录后继续）。 */
    public IngestResult ingest(IngestCommand command) {
        LocalDate asOfDate = command.getAsOfDate() != null ? command.getAsOfDate() : LocalDate.now();
        LocalDateTime fetchedAt = command.getFetchedAt() != null ? command.getFetchedAt() : LocalDateTime.now();
        IngestResult result = IngestResult.builder().build();
        List<UniverseEntry> universe = fetchUniverseAll();
        List<UniverseEntry> sample = universe.stream()
                .filter(entry -> entry.getCirculatingMarketCapTenThousand() != null)
                .sorted(Comparator.comparing(UniverseEntry::getCirculatingMarketCapTenThousand).reversed())
                .limit(Math.max(command.getSampleSize(), 0)).toList();
        result.setSampleSymbols(sample.stream().map(UniverseEntry::getCanonicalSymbol).toList());
        if (!command.isDryRun()) {
            upsertUniverse(universe, asOfDate, fetchedAt, result);
            List<IndustryMembershipRow> memberships = fetchMemberships(sample, asOfDate, fetchedAt, result);
            if (!memberships.isEmpty()) {
                long existing = mr0PocMapper.countIndustryMembership(TAXONOMY_SINA_INDUSTRY, asOfDate,
                        memberships.stream().map(IndustryMembershipRow::getCanonicalSymbol).toList());
                mr0PocMapper.upsertIndustryMembershipBatch(memberships);
                result.getMembership().accumulate(memberships.size(), existing);
            }
        }
        for (UniverseEntry entry : sample) {
            try {
                ingestDailyBars(entry, command, fetchedAt, result);
            } catch (RuntimeException exception) {
                result.getFailures().add(new SymbolFailure(entry.getCanonicalSymbol(), "DAILY_BAR", reason(exception)));
            }
            try {
                ingestMoneyFlow(entry, command, fetchedAt, result);
            } catch (RuntimeException exception) {
                result.getFailures().add(new SymbolFailure(entry.getCanonicalSymbol(), "MONEY_FLOW", reason(exception)));
            }
        }
        // CR-1：恒抓基准 SH.000001 日 K（tradingDays 由基准推导，D8）；失败仅记 failure 不中断。
        // 基准不参与资金流/成分/sampleSize/ensureRegistered，sampleSymbols 不含基准。
        try {
            ingestDailyBars(UniverseEntry.builder()
                    .sinaSymbol("sh000001").canonicalSymbol(BENCHMARK_CANONICAL).build(), command, fetchedAt, result, false);
        } catch (RuntimeException exception) {
            result.getFailures().add(new SymbolFailure(BENCHMARK_CANONICAL, "BENCHMARK_DAILY_BAR", reason(exception)));
        }
        if (!command.isDryRun() && !sample.isEmpty()) {
            stockBasicRegistrationManager.ensureRegistered(result.getSampleSymbols());
        }
        return result;
    }

    private List<UniverseEntry> fetchUniverseAll() {
        List<UniverseEntry> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<UniverseEntry> chunk = client.fetchUniversePage(page, PAGE_SIZE);
            all.addAll(chunk);
            if (chunk.size() < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    /** universe 快照 + 基准行（as_of_date=抓取日，幂等 upsert）。 */
    private void upsertUniverse(List<UniverseEntry> universe, LocalDate asOfDate,
                                LocalDateTime fetchedAt, IngestResult result) {
        List<UniverseSnapshotRow> rows = new ArrayList<>(universe.size() + 1);
        for (UniverseEntry entry : universe) {
            rows.add(UniverseSnapshotRow.builder().providerCode(PROVIDER_SINA_PUBLIC)
                    .canonicalSymbol(entry.getCanonicalSymbol()).symbol(entry.getCode()).name(entry.getName())
                    .market(entry.getMarket()).totalMarketCap(multiplyWan(entry.getTotalMarketCapTenThousand()))
                    .circulatingMarketCap(multiplyWan(entry.getCirculatingMarketCapTenThousand()))
                    .turnoverRate(percentToDecimal(entry.getTurnoverRatioPercent()))
                    .asOfDate(asOfDate).fetchedAt(fetchedAt).build());
        }
        rows.add(UniverseSnapshotRow.builder().providerCode(PROVIDER_SINA_PUBLIC)
                .canonicalSymbol(BENCHMARK_CANONICAL).symbol("000001").name("上证指数").market("SH")
                .asOfDate(asOfDate).fetchedAt(fetchedAt).build());
        long existing = mr0PocMapper.countUniverse(PROVIDER_SINA_PUBLIC, asOfDate,
                rows.stream().map(UniverseSnapshotRow::getCanonicalSymbol).toList());
        mr0PocMapper.upsertUniverseSnapshotBatch(rows);
        result.getUniverse().accumulate(rows.size(), existing);
    }

    /** 行业目录 + 全行业分页成分；样本外丢弃不落库；目录/行业失败记录为整项失败。 */
    private List<IndustryMembershipRow> fetchMemberships(List<UniverseEntry> sample, LocalDate asOfDate,
                                                         LocalDateTime fetchedAt, IngestResult result) {
        Set<String> sampleSymbols = new LinkedHashSet<>(
                sample.stream().map(UniverseEntry::getCanonicalSymbol).toList());
        Map<String, String> catalog;
        try {
            catalog = client.fetchIndustryCatalog();
        } catch (RuntimeException exception) {
            result.getFailures().add(new SymbolFailure("*", "INDUSTRY_CATALOG", reason(exception)));
            return List.of();
        }
        List<IndustryMembershipRow> memberships = new ArrayList<>();
        for (Map.Entry<String, String> industry : catalog.entrySet()) {
            try {
                for (int page = 1; page <= MAX_PAGES; page++) {
                    List<String> chunk = client.fetchIndustryMembers(industry.getKey(), page, PAGE_SIZE);
                    for (String sinaSymbol : chunk) {
                        String canonical;
                        try {
                            canonical = PublicMarketDataClient.toCanonical(sinaSymbol);
                        } catch (RuntimeException malformed) {
                            continue;
                        }
                        if (sampleSymbols.contains(canonical)) {
                            memberships.add(IndustryMembershipRow.builder()
                                    .taxonomyCode(TAXONOMY_SINA_INDUSTRY).providerCode(PROVIDER_SINA_PUBLIC)
                                    .industryCode(industry.getKey()).industryName(industry.getValue())
                                    .canonicalSymbol(canonical).asOfDate(asOfDate).fetchedAt(fetchedAt).build());
                        }
                    }
                    if (chunk.size() < PAGE_SIZE) {
                        break;
                    }
                }
            } catch (RuntimeException exception) {
                result.getFailures().add(new SymbolFailure(industry.getKey(), "INDUSTRY_MEMBERS", reason(exception)));
            }
        }
        return memberships;
    }

    /**
     * 日 K：amount 万元×10000、volume 手×100 + VWAP∈[low,high] 自检（字典 §3）后落 stock_daily_bar。
     * vwapSelfCheck=false 仅供基准指数行（CR-1）：指数 low/high 为点位非成交价，字典 §3 检查对象
     * 为个股价格域，对指数行不适用（否则基准 bar 恒被丢弃，tradingDays=0，即审查 F-001/CR-1）。
     */
    private void ingestDailyBars(UniverseEntry entry, IngestCommand command,
                                 LocalDateTime fetchedAt, IngestResult result) {
        ingestDailyBars(entry, command, fetchedAt, result, true);
    }

    private void ingestDailyBars(UniverseEntry entry, IngestCommand command,
                                 LocalDateTime fetchedAt, IngestResult result, boolean vwapSelfCheck) {
        List<DailyBarEntry> bars = client.fetchDailyBars(
                entry.getSinaSymbol(), command.getWarmupStart(), command.getAnalysisEnd());
        List<StockDailyBarDO> rows = new ArrayList<>(bars.size());
        for (DailyBarEntry bar : bars) {
            if (bar.getTradeDate() == null || bar.getTradeDate().isBefore(command.getWarmupStart())
                    || bar.getTradeDate().isAfter(command.getAnalysisEnd())) {
                result.getDailyBar().addSkipped(1);
                continue;
            }
            StockDailyBarDO row = StockDailyBarDO.builder()
                    .canonicalSymbol(entry.getCanonicalSymbol()).tradeDate(bar.getTradeDate())
                    .adjustType(ADJUST_TYPE_NONE).dataSource(PROVIDER_TENCENT_PUBLIC)
                    .openPrice(bar.getOpen()).highPrice(bar.getHigh()).lowPrice(bar.getLow())
                    .closePrice(bar.getClose()).volume(bar.getVolumeHands() == null ? 0L
                            : bar.getVolumeHands().multiply(HUNDRED).longValueExact())
                    .amount(multiplyWan(bar.getAmountTenThousand())).fetchedAt(fetchedAt).build();
            if (!vwapSelfCheck || vwapWithinRange(row)) {
                rows.add(row);
            } else {
                result.getDailyBar().addSkipped(1);
            }
        }
        if (command.isDryRun() || rows.isEmpty()) {
            return;
        }
        long existing = mr0PocMapper.countStockDailyBar(entry.getCanonicalSymbol(), PROVIDER_TENCENT_PUBLIC,
                ADJUST_TYPE_NONE, command.getWarmupStart(), command.getAnalysisEnd());
        mr0PocMapper.upsertStockDailyBarBatch(rows);
        result.getDailyBar().accumulate(rows.size(), existing);
    }

    /** 资金流：全部历史一次返回，按分析窗口过滤后落 mr0 表（netamount/r0_net/cate_na 原值元）。 */
    private void ingestMoneyFlow(UniverseEntry entry, IngestCommand command,
                                 LocalDateTime fetchedAt, IngestResult result) {
        List<MoneyFlowRow> rows = new ArrayList<>();
        for (MoneyFlowEntry flow : client.fetchMoneyFlow(entry.getSinaSymbol())) {
            if (flow.getTradeDate() == null || flow.getTradeDate().isBefore(command.getAnalysisStart())
                    || flow.getTradeDate().isAfter(command.getAnalysisEnd())) {
                continue;
            }
            rows.add(MoneyFlowRow.builder().canonicalSymbol(entry.getCanonicalSymbol())
                    .tradeDate(flow.getTradeDate()).providerCode(PROVIDER_SINA_PUBLIC)
                    .mainNetInflow(flow.getNetAmount()).mainNetInflowRatio(flow.getRatioAmount())
                    .superNet(flow.getR0Net()).industryNetInflow(flow.getCateNa())
                    .fetchedAt(fetchedAt).build());
        }
        if (command.isDryRun() || rows.isEmpty()) {
            return;
        }
        long existing = mr0PocMapper.countMoneyFlow(PROVIDER_SINA_PUBLIC, List.of(entry.getCanonicalSymbol()),
                command.getAnalysisStart(), command.getAnalysisEnd());
        mr0PocMapper.upsertMoneyFlowBatch(rows);
        result.getMoneyFlow().accumulate(rows.size(), existing);
    }

    /** 字典 §3 单位自检：vwap = amount(元)/volume(股) 必须落在同行 [low,high]（NONE 口径）。 */
    private boolean vwapWithinRange(StockDailyBarDO row) {
        if (row.getVolume() == null || row.getVolume() <= 0 || row.getAmount() == null
                || row.getLowPrice() == null || row.getHighPrice() == null) {
            return false;
        }
        BigDecimal vwap = row.getAmount().divide(new BigDecimal(row.getVolume()), 6, RoundingMode.HALF_UP);
        return vwap.compareTo(row.getLowPrice()) >= 0 && vwap.compareTo(row.getHighPrice()) <= 0;
    }

    private static BigDecimal multiplyWan(BigDecimal tenThousand) {
        return tenThousand == null ? null : tenThousand.multiply(WAN);
    }

    private static BigDecimal percentToDecimal(BigDecimal percent) {
        return percent == null ? null : percent.divide(HUNDRED);
    }

    private static String reason(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}
