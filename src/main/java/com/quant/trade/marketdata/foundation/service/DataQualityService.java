package com.quant.trade.marketdata.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskMapper;
import com.quant.trade.marketdata.foundation.dao.MdfBackfillTaskSymbolMapper;
import com.quant.trade.marketdata.foundation.dao.MdfCoverageWatermarkMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper;
import com.quant.trade.marketdata.foundation.dao.MdfIndustryMembershipMapper;
import com.quant.trade.marketdata.foundation.dao.MdfQualityResultMapper;
import com.quant.trade.marketdata.foundation.dao.MdfQualitySourceMapper;
import com.quant.trade.marketdata.foundation.dao.MdfUniverseSnapshotMapper;
import com.quant.trade.marketdata.foundation.dao.MdfVersionManifestMapper;
import com.quant.trade.marketdata.foundation.model.MdfCoverageWatermarkDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.model.MdfQualityResultDO;
import com.quant.trade.marketdata.foundation.model.MdfSymbolBarStatDO;
import com.quant.trade.marketdata.foundation.model.MdfSymbolExpectationDO;
import com.quant.trade.marketdata.foundation.vo.CoverageWatermarkVO;
import com.quant.trade.marketdata.foundation.vo.QualityResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据质量门禁（Repair R1 §四/§五：manifest 域 + 严格发布门槛）。
 *
 * - 全部数据检查基于版本 manifest（版本归属行），同窗口其他 Provider 的合法行情不参与判定（§五）。
 * - 发布门槛（可配 qta.data-foundation.publish-coverage-threshold，默认 0.90 沿用 MR-1）：
 *   空数据 FAIL；日期覆盖/总体覆盖/首末边界覆盖 低于阈值 FAIL；版本内 source/adjust 混入 FAIL。
 * - 期望行基于日历交易日 + stock_basic 上市日（list_date 缺失=假设窗口起点；DELISTED 剔除——显式假设）。
 * - 已冻结版本执行漂移检测（LINEAGE_DRIFT），漂移 FAIL 并标记 DRIFTED（阻断发布/复现声明）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQualityService {

    /** 陈旧度阈值：事实最大 fetched_at 距今超过该天数记 WARN。 */
    private static final long STALENESS_WARN_DAYS = 7;

    private final MdfDatasetVersionMapper versionMapper;
    private final MdfDatasetMapper datasetMapper;
    private final MdfQualityResultMapper qualityResultMapper;
    private final MdfQualitySourceMapper qualitySourceMapper;
    private final MdfVersionManifestMapper manifestMapper;
    private final MdfCoverageWatermarkMapper coverageMapper;
    private final MdfUniverseSnapshotMapper universeMapper;
    private final MdfIndustryMembershipMapper membershipMapper;
    private final MdfBackfillTaskMapper taskMapper;
    private final MdfBackfillTaskSymbolMapper taskSymbolMapper;
    private final VersionLineageService lineageService;
    private final TransactionTemplate txRequiresNew;
    private final ObjectMapper objectMapper;
    private final Clock marketDataClock;

    @Value("${qta.data-foundation.publish-coverage-threshold:0.90}")
    private double coverageThreshold;

    /** 运行检查族 → 覆盖水位 → 版本状态（QUALIFIED/REJECTED）。 */
    public List<MdfQualityResultDO> runChecks(long versionId) {
        MdfDatasetVersionDO version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_VERSION_NOT_FOUND, "数据集版本不存在");
        }
        QualityContext ctx = buildContext(version);
        List<MdfQualityResultDO> results = new ArrayList<>();
        results.add(checkEmptyDataset(ctx));
        results.add(checkDateRangeCoverage(ctx));
        results.add(checkUniverseCoverage(ctx));
        results.add(checkDailyBarGap(ctx));
        results.add(checkDuplicates(ctx));
        results.add(checkOhlcValidity(ctx));
        results.add(checkUnitAnomaly(ctx));
        results.add(checkNonTradingDay(ctx));
        results.add(checkMembershipOverlap(ctx));
        results.add(checkMembershipInvalidPeriod(ctx));
        results.add(checkUnmappedIndustry(ctx));
        results.add(checkProviderAdjustMixing(ctx));
        results.add(checkStaleness(ctx));
        results.add(checkOverallCoverageGate(ctx));
        results.add(checkBoundaryCoverage(ctx));
        results.add(checkLineageDrift(ctx));
        persist(ctx, results);
        log.info("质量检查完成: versionId={}, manifestRows={}, 非 OK 项={}", versionId, ctx.manifestRows(),
                results.stream().filter(r -> !"OK".equals(r.getStatus())).count());
        return qualityResultMapper.selectByVersion(versionId);
    }

    public List<MdfQualityResultDO> listResults(long versionId) {
        return qualityResultMapper.selectByVersion(versionId);
    }

    public long countFail(long versionId) {
        return qualityResultMapper.countFailByVersion(versionId);
    }

    public List<MdfCoverageWatermarkDO> listCoverage(long versionId) {
        return coverageMapper.selectByVersion(versionId);
    }

    // ---------------------------------------------------------------- VO 装配

    public QualityResultVO toQualityVO(MdfQualityResultDO result) {
        return QualityResultVO.builder()
                .datasetVersionId(result.getDatasetVersionId()).checkCode(result.getCheckCode())
                .status(result.getStatus()).affectedCount(result.getAffectedCount())
                .detailJson(result.getDetailJson()).checkedAt(result.getCheckedAt())
                .build();
    }

    public CoverageWatermarkVO toCoverageVO(MdfCoverageWatermarkDO row) {
        return CoverageWatermarkVO.builder()
                .datasetVersionId(row.getDatasetVersionId()).canonicalSymbol(row.getCanonicalSymbol())
                .firstDate(row.getFirstDate()).lastDate(row.getLastDate()).rowCount(row.getRowCount())
                .expectedDays(row.getExpectedDays()).coveredDays(row.getCoveredDays())
                .coverageRatio(row.getCoverageRatio()).calculatedAt(row.getCalculatedAt())
                .build();
    }

    // ---------------------------------------------------------------- 上下文

    private record QualityContext(MdfDatasetVersionDO version, MdfDatasetDO dataset,
                                  String expectedSource, String expectedAdjust,
                                  LocalDateTime now, long manifestRows, long calendarDays,
                                  List<LocalDate> tradingDates,
                                  Map<String, MdfSymbolBarStatDO> manifestStats,
                                  List<String> universeSymbols,
                                  List<String> scopeSymbols,
                                  long expectedRows,
                                  Map<String, LocalDate> symbolListDates,
                                  List<MdfCoverageWatermarkDO> coverageRows) {
    }

    private QualityContext buildContext(MdfDatasetVersionDO version) {
        MdfDatasetDO dataset = datasetMapper.selectById(version.getDatasetId());
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        long manifestRows = manifestMapper.countByVersion(version.getId());
        List<MdfSymbolBarStatDO> stats = manifestMapper.selectSymbolStats(version.getId());
        Map<String, MdfSymbolBarStatDO> manifestStats = new LinkedHashMap<>();
        stats.forEach(stat -> manifestStats.put(stat.getCanonicalSymbol(), stat));
        long calendarDays = qualitySourceMapper.countCalendarDays(dataset.getMarketCode(),
                version.getStartDate(), version.getEndDate());
        List<LocalDate> tradingDates = calendarDays == 0 ? List.of()
                : qualitySourceMapper.selectCalendarDates(dataset.getMarketCode(),
                        version.getStartDate(), version.getEndDate());
        LocalDate latestAsOf = universeMapper.selectLatestAsOfDate();
        List<String> universeSymbols = latestAsOf == null ? List.of()
                : universeMapper.selectSymbolsByAsOf(latestAsOf);

        // 版本 scope：回补版本=任务证券范围；导入版本=manifest 证券本身
        List<String> scopeSymbols = resolveScopeSymbols(version.getId(), stats);
        Map<String, LocalDate> listDates = loadListDates(dataset.getMarketCode(), scopeSymbols);
        long expectedRows = computeExpectedRows(scopeSymbols, listDates, tradingDates);

        return new QualityContext(version, dataset,
                dataset.getProviderCode(), dataset.getAdjustType(),
                now, manifestRows, calendarDays, tradingDates, manifestStats, universeSymbols,
                scopeSymbols, expectedRows, listDates,
                buildCoverageRows(version.getId(), stats, calendarDays, now));
    }

    private List<String> resolveScopeSymbols(long versionId, List<MdfSymbolBarStatDO> manifestStats) {
        var task = taskMapper.selectByDatasetVersionId(versionId);
        if (task != null) {
            return taskSymbolMapper.selectByTask(task.getId());
        }
        return manifestStats.stream().map(MdfSymbolBarStatDO::getCanonicalSymbol).toList();
    }

    private Map<String, LocalDate> loadListDates(String marketCode, List<String> scopeSymbols) {
        Map<String, LocalDate> listDates = new HashMap<>();
        for (int from = 0; from < scopeSymbols.size(); from += 500) {
            qualitySourceMapper.selectListedSymbols(marketCode,
                    scopeSymbols.subList(from, Math.min(from + 500, scopeSymbols.size())))
                    .forEach(row -> listDates.put(row.getCanonicalSymbol(), row.getListDate()));
        }
        return listDates;
    }

    /** 期望行 = Σ 证券 × [max(窗口起点, 上市日), 窗口终点] 内交易日；上市日缺失=窗口起点（显式假设）。 */
    private long computeExpectedRows(List<String> scopeSymbols, Map<String, LocalDate> listDates,
                                     List<LocalDate> tradingDates) {
        if (tradingDates.isEmpty() || scopeSymbols.isEmpty()) {
            return 0;
        }
        LocalDate windowStart = tradingDates.get(0);
        long expected = 0;
        for (String symbol : scopeSymbols) {
            LocalDate effectiveStart = listDates.getOrDefault(symbol, windowStart);
            if (effectiveStart == null || effectiveStart.isBefore(windowStart)) {
                effectiveStart = windowStart;
            }
            for (LocalDate date : tradingDates) {
                if (!date.isBefore(effectiveStart)) {
                    expected++;
                }
            }
        }
        return expected;
    }

    private List<MdfCoverageWatermarkDO> buildCoverageRows(long versionId, List<MdfSymbolBarStatDO> stats,
                                                           long calendarDays, LocalDateTime now) {
        List<MdfCoverageWatermarkDO> rows = new ArrayList<>();
        for (MdfSymbolBarStatDO stat : stats) {
            long covered = stat.getRowCount() == null ? 0 : stat.getRowCount();
            rows.add(MdfCoverageWatermarkDO.builder()
                    .datasetVersionId(versionId).canonicalSymbol(stat.getCanonicalSymbol())
                    .firstDate(stat.getFirstDate()).lastDate(stat.getLastDate())
                    .rowCount(covered).expectedDays(calendarDays).coveredDays(covered)
                    .coverageRatio(calendarDays <= 0 ? null
                            : BigDecimal.valueOf(covered).divide(BigDecimal.valueOf(calendarDays), 8, RoundingMode.HALF_UP))
                    .calculatedAt(now).build());
        }
        return rows;
    }

    private void persist(QualityContext ctx, List<MdfQualityResultDO> results) {
        long versionId = ctx.version().getId();
        txRequiresNew.executeWithoutResult(status -> {
            qualityResultMapper.deleteByVersion(versionId);
            if (!results.isEmpty()) {
                qualityResultMapper.insertBatch(results);
            }
            if (!ctx.coverageRows().isEmpty()) {
                coverageMapper.upsertBatch(ctx.coverageRows());
            }
            versionMapper.updateRowCount(versionId, ctx.manifestRows());
            boolean fail = results.stream().anyMatch(r -> FoundationConstants.QUALITY_FAIL.equals(r.getStatus()));
            String nextStatus = fail || ctx.manifestRows() == 0
                    ? FoundationConstants.VERSION_REJECTED : FoundationConstants.VERSION_QUALIFIED;
            versionMapper.updateStatus(versionId, nextStatus, ctx.now(), null, null);
        });
    }

    // ---------------------------------------------------------------- 检查族（数据族全部 manifest 域）

    /** 1) 空数据（FAIL）。 */
    private MdfQualityResultDO checkEmptyDataset(QualityContext ctx) {
        boolean empty = ctx.manifestRows() == 0;
        return build(ctx, FoundationConstants.CHECK_EMPTY_DATASET,
                empty ? FAIL() : OK(), empty ? 1L : 0L, detail("manifestRows", ctx.manifestRows()));
    }

    /** 2) 日期范围覆盖（无日历基准=无法证明→FAIL；有日历时低于阈值 FAIL）。 */
    private MdfQualityResultDO checkDateRangeCoverage(QualityContext ctx) {
        if (ctx.calendarDays() <= 0) {
            return build(ctx, FoundationConstants.CHECK_DATE_RANGE_COVERAGE, FAIL(), 0L,
                    detail("reason", "CALENDAR_MISSING", "calendarTradingDays", 0));
        }
        long coveredDates = manifestMapper.countDistinctDates(ctx.version().getId());
        double ratio = coveredDates / (double) ctx.calendarDays();
        return build(ctx, FoundationConstants.CHECK_DATE_RANGE_COVERAGE,
                ratio < coverageThreshold ? FAIL() : OK(), coveredDates,
                detail("coveredDates", coveredDates, "calendarTradingDays", ctx.calendarDays(),
                        "ratio", round(ratio)));
    }

    /** 3) 股票池覆盖（信息项 WARN；发布阻断由总体覆盖门禁承担）。 */
    private MdfQualityResultDO checkUniverseCoverage(QualityContext ctx) {
        long affected = 0;
        String status = WARN();
        if (!ctx.universeSymbols().isEmpty()) {
            affected = ctx.universeSymbols().stream().filter(s -> !ctx.manifestStats().containsKey(s)).count();
        }
        return build(ctx, FoundationConstants.CHECK_UNIVERSE_COVERAGE, affected > 0 || ctx.universeSymbols().isEmpty() ? WARN() : OK(),
                affected, detail("universeSize", ctx.universeSymbols().size()));
    }

    /** 4) 日 K 缺口（信息项 WARN：正常停牌/上市差异不断言 FAIL，期望假设见总体门禁）。 */
    private MdfQualityResultDO checkDailyBarGap(QualityContext ctx) {
        long gapSymbols = ctx.coverageRows().stream()
                .filter(row -> row.getExpectedDays() != null && row.getExpectedDays() > 0
                        && row.getCoveredDays() < row.getExpectedDays())
                .count();
        return build(ctx, FoundationConstants.CHECK_DAILY_BAR_GAP,
                gapSymbols > 0 ? WARN() : OK(), gapSymbols, detail("gapSymbols", gapSymbols));
    }

    /** 5) 重复数据（manifest 业务键重复=防线破坏；FAIL）。 */
    private MdfQualityResultDO checkDuplicates(QualityContext ctx) {
        long duplicates = manifestMapper.countDuplicatedKeys(ctx.version().getId());
        return build(ctx, FoundationConstants.CHECK_DUPLICATE_ROWS,
                duplicates > 0 ? FAIL() : OK(), duplicates, detail("duplicateKeys", duplicates));
    }

    /** 6) OHLC 合法性（manifest 域；FAIL）。 */
    private MdfQualityResultDO checkOhlcValidity(QualityContext ctx) {
        long violations = manifestMapper.countOhlcViolations(ctx.version().getId());
        return build(ctx, FoundationConstants.CHECK_OHLC_VALIDITY,
                violations > 0 ? FAIL() : OK(), violations, detail("violationRows", violations));
    }

    /** 7) 单位异常（manifest 域 VWAP/负值；FAIL）。 */
    private MdfQualityResultDO checkUnitAnomaly(QualityContext ctx) {
        long anomalies = manifestMapper.countUnitAnomalies(ctx.version().getId());
        return build(ctx, FoundationConstants.CHECK_UNIT_ANOMALY,
                anomalies > 0 ? FAIL() : OK(), anomalies, detail("anomalyRows", anomalies));
    }

    /** 8) 周末/非交易日（manifest 域；FAIL）。 */
    private MdfQualityResultDO checkNonTradingDay(QualityContext ctx) {
        long rows = manifestMapper.countNonTradingDayRows(ctx.version().getId(),
                ctx.dataset().getMarketCode(), ctx.calendarDays());
        return build(ctx, FoundationConstants.CHECK_NON_TRADING_DAY,
                rows > 0 ? FAIL() : OK(), rows, detail("nonTradingRows", rows));
    }

    /** 9) 行业成员重叠（FAIL）。 */
    private MdfQualityResultDO checkMembershipOverlap(QualityContext ctx) {
        long overlaps = membershipMapper.countOverlapPairs("SINA_INDUSTRY");
        return build(ctx, FoundationConstants.CHECK_MEMBERSHIP_OVERLAP,
                overlaps > 0 ? FAIL() : OK(), overlaps, detail("overlapPairs", overlaps));
    }

    /** 10) 行业成员无效有效期（FAIL）。 */
    private MdfQualityResultDO checkMembershipInvalidPeriod(QualityContext ctx) {
        long invalid = membershipMapper.countInvalidPeriods("SINA_INDUSTRY");
        return build(ctx, FoundationConstants.CHECK_MEMBERSHIP_INVALID_PERIOD,
                invalid > 0 ? FAIL() : OK(), invalid, detail("invalidPeriodRows", invalid));
    }

    /** 11) 证券未映射行业（信息项 WARN）。 */
    private MdfQualityResultDO checkUnmappedIndustry(QualityContext ctx) {
        long unmapped = 0;
        String status = OK();
        if (!ctx.universeSymbols().isEmpty()) {
            long mapped = membershipMapper.countDistinctSymbols("SINA_INDUSTRY");
            unmapped = Math.max(0, ctx.universeSymbols().size() - mapped);
            if (unmapped > 0) {
                status = WARN();
            }
        }
        return build(ctx, FoundationConstants.CHECK_UNMAPPED_INDUSTRY, status,
                unmapped, detail("unmappedSymbols", unmapped));
    }

    /** 12) Provider/复权混用（仅版本 manifest 域；同窗其他 Provider 合法共存不参与，R1 §五；FAIL）。 */
    private MdfQualityResultDO checkProviderAdjustMixing(QualityContext ctx) {
        long mixing = manifestMapper.countForeignRows(ctx.version().getId(),
                ctx.expectedSource(), ctx.expectedAdjust());
        return build(ctx, FoundationConstants.CHECK_PROVIDER_ADJUST_MIXING,
                mixing > 0 ? FAIL() : OK(), mixing, detail("foreignRows", mixing,
                        "expectedSource", ctx.expectedSource(), "expectedAdjust", ctx.expectedAdjust()));
    }

    /** 13) 数据陈旧度（manifest 域；WARN）。 */
    private MdfQualityResultDO checkStaleness(QualityContext ctx) {
        LocalDateTime maxFetchedAt = manifestMapper.selectMaxFetchedAt(ctx.version().getId());
        long staleDays = maxFetchedAt == null ? Long.MAX_VALUE
                : Duration.between(maxFetchedAt, ctx.now()).toDays();
        return build(ctx, FoundationConstants.CHECK_DATA_STALENESS,
                staleDays > STALENESS_WARN_DAYS ? WARN() : OK(),
                maxFetchedAt == null ? 1L : 0L,
                detail("maxFetchedAt", maxFetchedAt == null ? null : maxFetchedAt.toString()));
    }

    /** 14) 总体覆盖门禁（manifestRows/expectedRows < 阈值 FAIL；R1 §四.4）。 */
    private MdfQualityResultDO checkOverallCoverageGate(QualityContext ctx) {
        if (ctx.expectedRows() <= 0) {
            return build(ctx, FoundationConstants.COVERAGE_GATE_CHECK, FAIL(), 0L,
                    detail("reason", "EXPECTED_ROWS_UNAVAILABLE", "threshold", coverageThreshold,
                            "assumption", "日历缺失或范围证券为空，无法证明覆盖"));
        }
        double ratio = ctx.manifestRows() / (double) ctx.expectedRows();
        String detail = detail("manifestRows", ctx.manifestRows(), "expectedRows", ctx.expectedRows(),
                "ratio", round(ratio), "threshold", coverageThreshold,
                "assumption", "期望=日历交易日×范围证券（上市日缺失假设窗口起点；DELISTED 剔除）");
        return build(ctx, FoundationConstants.COVERAGE_GATE_CHECK,
                ratio + 1e-9 < coverageThreshold ? FAIL() : OK(),
                ctx.manifestRows(), detail);
    }

    /** 15) 首末边界覆盖（首/最后交易日在市证券覆盖比 < 阈值 FAIL：截断/严重边界缺失必拒，R1 §四.5/6）。 */
    private MdfQualityResultDO checkBoundaryCoverage(QualityContext ctx) {
        if (ctx.tradingDates().isEmpty()) {
            return build(ctx, FoundationConstants.BOUNDARY_COVERAGE_CHECK, FAIL(), 0L,
                    detail("reason", "CALENDAR_MISSING"));
        }
        LocalDate first = ctx.tradingDates().get(0);
        LocalDate last = ctx.tradingDates().get(ctx.tradingDates().size() - 1);
        long firstCovered = manifestMapper.countSymbolsOnDate(ctx.version().getId(), first);
        long lastCovered = manifestMapper.countSymbolsOnDate(ctx.version().getId(), last);
        long firstExpected = activeSymbolsAt(ctx, first);
        long lastExpected = activeSymbolsAt(ctx, last);
        double firstRatio = firstExpected <= 0 ? 1.0 : firstCovered / (double) firstExpected;
        double lastRatio = lastExpected <= 0 ? 1.0 : lastCovered / (double) lastExpected;
        boolean fail = firstRatio + 1e-9 < coverageThreshold || lastRatio + 1e-9 < coverageThreshold;
        return build(ctx, FoundationConstants.BOUNDARY_COVERAGE_CHECK, fail ? FAIL() : OK(),
                fail ? Math.max(firstExpected - firstCovered, lastExpected - lastCovered) : 0L,
                detail("firstDate", first.toString(), "firstRatio", round(firstRatio),
                        "lastDate", last.toString(), "lastRatio", round(lastRatio),
                        "threshold", coverageThreshold));
    }

    /** 16) 血缘漂移（已冻结版本重算比对；漂移 FAIL 并标记 DRIFTED，R1 §六）。 */
    private MdfQualityResultDO checkLineageDrift(QualityContext ctx) {
        MdfDatasetVersionDO version = ctx.version();
        if (version.getContentHash() == null) {
            return build(ctx, FoundationConstants.LINEAGE_DRIFT_CHECK, OK(), 0L,
                    detail("reason", "NOT_FROZEN", "note", "发布前冻结内容哈希"));
        }
        long drifted = lineageService.countDrifted(version.getId());
        if (drifted > 0) {
            lineageService.markDrifted(version.getId());
        }
        return build(ctx, FoundationConstants.LINEAGE_DRIFT_CHECK,
                drifted > 0 ? FAIL() : OK(), drifted, detail("driftedRows", drifted,
                        "contentHash", version.getContentHash()));
    }

    /**
     * 首末日在市证券数（分母=完整版本 scope；stock_basic 缺行或 list_date 缺失按冻结规则
     * 视为窗口起点计入分母，不从分母中消失——R2 §四）。
     */
    private long activeSymbolsAt(QualityContext ctx, LocalDate date) {
        return ctx.scopeSymbols().stream()
                .filter(symbol -> {
                    LocalDate listDate = ctx.symbolListDates().get(symbol);
                    return listDate == null || !listDate.isAfter(date);
                })
                .count();
    }

    // ---------------------------------------------------------------- 工具

    private MdfQualityResultDO build(QualityContext ctx, String checkCode, String status,
                                     long affectedCount, String detailJson) {
        return MdfQualityResultDO.builder()
                .datasetVersionId(ctx.version().getId()).checkCode(checkCode).status(status)
                .affectedCount(affectedCount).detailJson(detailJson).checkedAt(ctx.now())
                .build();
    }

    private String detail(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private static String OK() {
        return FoundationConstants.QUALITY_OK;
    }

    private static String WARN() {
        return FoundationConstants.QUALITY_WARN;
    }

    private static String FAIL() {
        return FoundationConstants.QUALITY_FAIL;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
