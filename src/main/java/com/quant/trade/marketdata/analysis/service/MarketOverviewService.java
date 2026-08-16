package com.quant.trade.marketdata.analysis.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.analysis.dao.MarketOverviewMapper;
import com.quant.trade.marketdata.analysis.derived.MarketDerivedCalculators;
import com.quant.trade.marketdata.analysis.manager.MarketOverviewCalculationManager;
import com.quant.trade.marketdata.analysis.manager.MarketOverviewCalculationManager.CalculationInput;
import com.quant.trade.marketdata.analysis.manager.MarketOverviewCalculationManager.CalculationOutput;
import com.quant.trade.marketdata.analysis.vo.MarketOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * MR-1A 市场全景只读 Service：装载已落库事实（TENCENT_PUBLIC 日 K + SINA_PUBLIC 证券池/行业成分），
 * 委托 {@link MarketOverviewCalculationManager} 计算五类核心证据，并组装带完整数据边界声明的响应。
 * 零外联（不调用任何 provider）、零写库；数据不足以 qualityStatus（NO_DATA/DEGRADED）+ 质量发现
 * 表达，参数非法抛 400，禁止 500 冒充业务状态。
 *
 * <p>样本口径与 MR-0 PoC 冻结口径一致（CR-3：最新档快照流通市值 Top-150，as_of 无上界），
 * 因此响应 dataScope 固定 SAMPLE，limitations 固定声明"不是全市场 / 非 PIT 申万 / 时点穿越假设 /
 * 不提供官方口径资金流"。</p>
 */
@Service
@RequiredArgsConstructor
public class MarketOverviewService {

    /** 首期仅支持的市场。 */
    public static final String MARKET_CN = "CN";
    /** 日 K 事实 Provider（与 MR-0 PoC 入库一致，单一来源红线）。 */
    public static final String PROVIDER_TENCENT_PUBLIC = "TENCENT_PUBLIC";
    /** 证券池与行业成分事实 Provider。 */
    public static final String PROVIDER_SINA_PUBLIC = "SINA_PUBLIC";
    /** 行业分类体系（新浪互斥行业，非申万，禁混称混算）。 */
    public static final String TAXONOMY_SINA_INDUSTRY = "SINA_INDUSTRY";
    /** 基准指数（上证指数；交易日集合由其日 K 推导）。 */
    public static final String BENCHMARK_SYMBOL = "SH.000001";
    /** 数据范围：当前必须是 SAMPLE（Top-N 样本，不代表全市场）。 */
    public static final String DATA_SCOPE_SAMPLE = "SAMPLE";
    /** 不可用指标：官方口径资金流（禁止由价量推算填充）。 */
    public static final String UNAVAILABLE_METRIC_MONEY_FLOW = "OFFICIAL_MONEY_FLOW";
    /** 样本规模（MR-0 D5 冻结 Top-150；与 PoC 保持同一口径便于交叉核对）。 */
    static final int SAMPLE_SIZE = 150;
    /** 窗口跨度上限（与 MR-0 AMD-001 一致）。 */
    private static final long MAX_SPAN_DAYS = 365L;
    /**
     * 预热回看自然日：预热门禁需要 120 个真实合格交易日，A 股最坏日历比约 1.5 自然日/交易日
     * （周末+春节/国庆长假，120 交易日 ≤ 约 185 自然日）；取 300 自然日提供明确冗余（最坏仍可
     * 覆盖 ≥190 个交易日），不依赖"180 自然日恰够"的脆弱假设。不足时对应指标输出 null、门禁按
     * 真实合格日数判定（INSUFFICIENT_WARMUP 语义）。
     */
    private static final long WARMUP_LOOKBACK_DAYS = 300L;

    /** 数据边界声明（固定四条，禁止删减；前端必须展示）。 */
    private static final List<String> LIMITATIONS = List.of(
            "当前为 Top-N 样本（最新快照流通市值前 150 只），不是全市场",
            "行业分类为 SINA_INDUSTRY 快照，不是 PIT 申万行业",
            "行业成分按抓取日快照聚合历史，存在时点穿越假设",
            "不提供官方口径全市场资金流（OFFICIAL_MONEY_FLOW=UNAVAILABLE）");

    /** 口径假设声明（与 MR-0 指标字典一致）。 */
    private static final List<String> ASSUMPTIONS = List.of(
            "交易日集合由基准指数日 K 推导（INDEX_KLINE_DERIVED）；market_calendar CN 为空表未回填",
            "证券池与行业成分取分析时点可见最新档快照（as_of 无上界），当前成分聚合历史为显式时点穿越假设",
            "全部价格事实为 NONE 复权（adjust_type=NONE），除权日收益率失真是已知失效条件（字典 D7）",
            "样本域成交额为 Top-N 样本股合计（字典 M-03），不可宣称为全市场成交额");

    private final MarketOverviewMapper mapper;
    private final MarketOverviewCalculationManager calculationManager;

    /** 执行一次市场全景只读查询（首期仅 CN；参数非法 400，数据不足返回明确业务状态）。 */
    public MarketOverviewVO.Overview overview(String market, LocalDate start, LocalDate end) {
        validate(market, start, end);
        LocalDate warmupStart = start.minusDays(WARMUP_LOOKBACK_DAYS);

        Map<String, TreeMap<LocalDate, BigDecimal>> closes = new TreeMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> amounts = new TreeMap<>();
        for (MarketOverviewMapper.DailyBarRow bar : mapper.selectDailyBars(warmupStart, end, PROVIDER_TENCENT_PUBLIC)) {
            closes.computeIfAbsent(bar.getCanonicalSymbol(), ignored -> new TreeMap<>())
                    .put(bar.getTradeDate(), bar.getClosePrice());
            amounts.computeIfAbsent(bar.getCanonicalSymbol(), ignored -> new TreeMap<>())
                    .put(bar.getTradeDate(), bar.getAmount());
        }
        TreeMap<LocalDate, BigDecimal> benchmarkCloses =
                new TreeMap<>(closes.getOrDefault(BENCHMARK_SYMBOL, new TreeMap<>()));
        TreeMap<LocalDate, BigDecimal> benchmarkAmounts =
                new TreeMap<>(amounts.getOrDefault(BENCHMARK_SYMBOL, new TreeMap<>()));
        closes.remove(BENCHMARK_SYMBOL);
        amounts.remove(BENCHMARK_SYMBOL);

        List<MarketDerivedCalculators.SymbolMarketCap> latestSnapshot = latestSnapshotRows(mapper
                .selectUniverseSnapshots(PROVIDER_SINA_PUBLIC));
        Map<String, MarketDerivedCalculators.IndustryRef> membership = new LinkedHashMap<>();
        Map<String, String> industryNames = new TreeMap<>();
        for (MarketOverviewMapper.MembershipRow row : mapper.selectIndustryMemberships(TAXONOMY_SINA_INDUSTRY)) {
            membership.putIfAbsent(row.getCanonicalSymbol(),
                    new MarketDerivedCalculators.IndustryRef(row.getIndustryCode(), row.getAsOfDate()));
            industryNames.putIfAbsent(row.getIndustryCode(), row.getIndustryName());
        }

        CalculationOutput output = calculationManager.calculate(new CalculationInput(start, end, BENCHMARK_SYMBOL,
                benchmarkCloses, benchmarkAmounts, closes, amounts, latestSnapshot, membership, industryNames,
                SAMPLE_SIZE));

        String qualityStatus = resolveQualityStatus(output);
        MarketOverviewVO.Metadata metadata = new MarketOverviewVO.Metadata(market, start, end, output.dataAsOf(),
                DATA_SCOPE_SAMPLE, output.sampleSymbols().size(), BENCHMARK_SYMBOL,
                List.of(PROVIDER_SINA_PUBLIC, PROVIDER_TENCENT_PUBLIC), TAXONOMY_SINA_INDUSTRY,
                output.barCoverage(), output.membershipCoverage(), output.qualifiedTradingDays(),
                qualityStatus, LIMITATIONS, List.of(UNAVAILABLE_METRIC_MONEY_FLOW));
        MarketOverviewVO.QualityBlock quality = new MarketOverviewVO.QualityBlock(output.coverageGap(),
                providerAttribution(), output.findings(), ASSUMPTIONS, List.of(UNAVAILABLE_METRIC_MONEY_FLOW));
        return new MarketOverviewVO.Overview(metadata, output.benchmarkSeries(), output.activitySeries(),
                output.breadthSeries(), output.liquidityProxySeries(), output.industryTurnoverMigration(), quality);
    }

    /** 参数边界（AMD-001 同纪律）：market 必填且首期仅 CN；start/end 必填、顺序合法、跨度 ≤365 天。 */
    private void validate(String market, LocalDate start, LocalDate end) {
        if (market == null || market.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "market 不能为空");
        }
        if (!MARKET_CN.equals(market.trim().toUpperCase())) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "MR-1A 市场全景首期仅支持 CN 市场（收到 " + market + "）");
        }
        if (start == null) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "start 不能为空");
        }
        if (end == null) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "end 不能为空");
        }
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "start 不能晚于 end");
        }
        long spanDays = ChronoUnit.DAYS.between(start, end);
        if (spanDays > MAX_SPAN_DAYS) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "窗口跨度不能超过 " + MAX_SPAN_DAYS + " 天（当前 " + spanDays + " 天）");
        }
    }

    /** 最新一档证券池快照（as_of 倒序首行日期；与 MR-0 PoC 同口径：as_of 无上界）。 */
    private List<MarketDerivedCalculators.SymbolMarketCap> latestSnapshotRows(
            List<MarketOverviewMapper.UniverseSnapshotRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        LocalDate latestAsOf = rows.get(0).getAsOfDate();
        return rows.stream()
                .filter(row -> latestAsOf.equals(row.getAsOfDate()))
                .map(row -> new MarketDerivedCalculators.SymbolMarketCap(
                        row.getCanonicalSymbol(), row.getCirculatingMarketCap()))
                .toList();
    }

    /** 整体质量状态：无基准交易日=NO_DATA；存在 WARN 发现（含 M-22 覆盖门禁与预热门禁）=DEGRADED；否则 OK。 */
    private String resolveQualityStatus(CalculationOutput output) {
        boolean noData = output.findings().stream()
                .anyMatch(finding -> "BENCHMARK_DATA_MISSING".equals(finding.code()));
        if (noData) {
            return "NO_DATA";
        }
        return output.findings().stream().anyMatch(finding -> "WARN".equals(finding.severity()))
                ? "DEGRADED" : "OK";
    }

    /** 数据集级 Provider 归属（单一来源红线：每个数据集只列事实 Provider）。 */
    private List<MarketOverviewVO.ProviderAttribution> providerAttribution() {
        return List.of(
                new MarketOverviewVO.ProviderAttribution("benchmarkDailyBar", List.of(PROVIDER_TENCENT_PUBLIC)),
                new MarketOverviewVO.ProviderAttribution("sampleDailyBar", List.of(PROVIDER_TENCENT_PUBLIC)),
                new MarketOverviewVO.ProviderAttribution("sampleUniverseSnapshot", List.of(PROVIDER_SINA_PUBLIC)),
                new MarketOverviewVO.ProviderAttribution("industryMembership", List.of(PROVIDER_SINA_PUBLIC)));
    }
}
