package com.quant.trade.marketdata.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.foundation.dao.MdfCoverageWatermarkMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper;
import com.quant.trade.marketdata.foundation.dao.MdfIndustryMembershipMapper;
import com.quant.trade.marketdata.foundation.dao.MdfQualityResultMapper;
import com.quant.trade.marketdata.foundation.dao.MdfQualitySourceMapper;
import com.quant.trade.marketdata.foundation.dao.MdfUniverseSnapshotMapper;
import com.quant.trade.marketdata.foundation.model.MdfCoverageWatermarkDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.model.MdfQualityResultDO;
import com.quant.trade.marketdata.foundation.model.MdfSymbolBarStatDO;
import com.quant.trade.marketdata.foundation.vo.CoverageWatermarkVO;
import com.quant.trade.marketdata.foundation.vo.QualityResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据质量门禁（契约 AC-05，13 检查族）+ 覆盖水位计算 + 版本资格判定。
 *
 * 门禁规则：任一 FAIL 或空数据（row_count=0）→ REJECTED；否则 QUALIFIED（WARN 保留为降级提示）。
 * 只有 QUALIFIED 版本可发布（DatasetPublicationService）。
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
    private final MdfCoverageWatermarkMapper coverageMapper;
    private final MdfUniverseSnapshotMapper universeMapper;
    private final MdfIndustryMembershipMapper membershipMapper;
    private final TransactionTemplate txRequiresNew;
    private final ObjectMapper objectMapper;
    private final Clock marketDataClock;

    /** 运行 13 族检查 → 覆盖水位 → 版本状态（QUALIFIED/REJECTED）。编排见各私有检查方法。 */
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
        persist(ctx, results);
        log.info("质量检查完成: versionId={}, rows={}, 非 OK 项={}", versionId, ctx.totalRows(),
                results.stream().filter(r -> !"OK".equals(r.getStatus())).count());
        return qualityResultMapper.selectByVersion(versionId);
    }

    /** 检查上下文：一次查询聚合，13 族检查复用（避免重复查库）。 */
    private record QualityContext(MdfDatasetVersionDO version, String source, String adjust,
                                  LocalDateTime now, long totalRows, long calendarDays,
                                  Map<String, MdfSymbolBarStatDO> statBySymbol,
                                  List<String> universeSymbols,
                                  List<MdfCoverageWatermarkDO> coverageRows) {
    }

    private QualityContext buildContext(MdfDatasetVersionDO version) {
        String source = version.getSourceProvider();
        String adjust = resolveAdjustType(version);
        List<MdfSymbolBarStatDO> stats = qualitySourceMapper.selectSymbolStats(
                source, adjust, version.getStartDate(), version.getEndDate(), null);
        long totalRows = stats.stream().mapToLong(s -> s.getRowCount() == null ? 0 : s.getRowCount()).sum();
        long calendarDays = qualitySourceMapper.countCalendarDays("CN", version.getStartDate(), version.getEndDate());
        Map<String, MdfSymbolBarStatDO> statBySymbol = new LinkedHashMap<>();
        stats.forEach(stat -> statBySymbol.put(stat.getCanonicalSymbol(), stat));
        LocalDate latestAsOf = universeMapper.selectLatestAsOfDate();
        List<String> universeSymbols = latestAsOf == null ? List.of()
                : universeMapper.selectSymbolsByAsOf(latestAsOf);
        return new QualityContext(version, source, adjust, LocalDateTime.now(marketDataClock),
                totalRows, calendarDays, statBySymbol, universeSymbols,
                buildCoverageRows(version.getId(), calendarDays, statBySymbol, LocalDateTime.now(marketDataClock)));
    }

    private List<MdfCoverageWatermarkDO> buildCoverageRows(long versionId, long calendarDays,
                                                           Map<String, MdfSymbolBarStatDO> statBySymbol,
                                                           LocalDateTime now) {
        List<MdfCoverageWatermarkDO> rows = new ArrayList<>();
        for (Map.Entry<String, MdfSymbolBarStatDO> entry : statBySymbol.entrySet()) {
            MdfSymbolBarStatDO stat = entry.getValue();
            long covered = stat.getRowCount() == null ? 0 : stat.getRowCount();
            rows.add(MdfCoverageWatermarkDO.builder()
                    .datasetVersionId(versionId).canonicalSymbol(entry.getKey())
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
            versionMapper.updateRowCount(versionId, ctx.totalRows());
            boolean fail = results.stream().anyMatch(r -> FoundationConstants.QUALITY_FAIL.equals(r.getStatus()));
            String nextStatus = fail || ctx.totalRows() == 0
                    ? FoundationConstants.VERSION_REJECTED : FoundationConstants.VERSION_QUALIFIED;
            versionMapper.updateStatus(versionId, nextStatus, ctx.now(), null, null);
        });
    }

    // ---------------------------------------------------------------- 13 检查族（每族一个方法，语义冻结）

    /** 1) 空数据（FAIL，直接阻断发布）。 */
    private MdfQualityResultDO checkEmptyDataset(QualityContext ctx) {
        boolean empty = ctx.totalRows() == 0;
        return build(ctx.version().getId(), FoundationConstants.CHECK_EMPTY_DATASET,
                empty ? FoundationConstants.QUALITY_FAIL : FoundationConstants.QUALITY_OK,
                empty ? 1L : 0L, detail("totalRows", ctx.totalRows()), ctx.now());
    }

    /** 2) 日期范围覆盖（无日历基准时 WARN）。 */
    private MdfQualityResultDO checkDateRangeCoverage(QualityContext ctx) {
        return build(ctx.version().getId(), FoundationConstants.CHECK_DATE_RANGE_COVERAGE,
                ctx.calendarDays() <= 0 ? FoundationConstants.QUALITY_WARN : FoundationConstants.QUALITY_OK,
                ctx.calendarDays(), detail("calendarTradingDays", ctx.calendarDays()), ctx.now());
    }

    /** 3) 证券池覆盖（事实 symbol / 最新池 symbol；无池快照或存在缺口 WARN）。 */
    private MdfQualityResultDO checkUniverseCoverage(QualityContext ctx) {
        long affected = 0;
        String status = FoundationConstants.QUALITY_OK;
        if (ctx.universeSymbols().isEmpty()) {
            status = FoundationConstants.QUALITY_WARN;
        } else {
            affected = ctx.universeSymbols().stream().filter(s -> !ctx.statBySymbol().containsKey(s)).count();
            if (affected > 0) {
                status = FoundationConstants.QUALITY_WARN;
            }
        }
        return build(ctx.version().getId(), FoundationConstants.CHECK_UNIVERSE_COVERAGE, status,
                affected, detail("universeSize", ctx.universeSymbols().size()), ctx.now());
    }

    /** 4) 日 K 缺口（expected>0 且 covered<expected 的证券数；WARN）。 */
    private MdfQualityResultDO checkDailyBarGap(QualityContext ctx) {
        long gapSymbols = ctx.coverageRows().stream()
                .filter(row -> row.getExpectedDays() != null && row.getExpectedDays() > 0
                        && row.getCoveredDays() < row.getExpectedDays())
                .count();
        return build(ctx.version().getId(), FoundationConstants.CHECK_DAILY_BAR_GAP,
                gapSymbols > 0 ? FoundationConstants.QUALITY_WARN : FoundationConstants.QUALITY_OK,
                gapSymbols, detail("gapSymbols", gapSymbols), ctx.now());
    }

    /** 5) 重复数据（同 symbol+同日多行=跨源共存；FAIL）。 */
    private MdfQualityResultDO checkDuplicates(QualityContext ctx) {
        long duplicates = qualitySourceMapper.countDuplicateSymbolDateRows(
                ctx.source(), ctx.adjust(), ctx.version().getStartDate(), ctx.version().getEndDate(), null);
        return build(ctx.version().getId(), FoundationConstants.CHECK_DUPLICATE_ROWS,
                duplicates > 0 ? FoundationConstants.QUALITY_FAIL : FoundationConstants.QUALITY_OK,
                duplicates, detail("duplicateSymbolDatePairs", duplicates), ctx.now());
    }

    /** 6) OHLC 合法性（FAIL）。 */
    private MdfQualityResultDO checkOhlcValidity(QualityContext ctx) {
        long violations = qualitySourceMapper.countOhlcViolations(
                ctx.source(), ctx.adjust(), ctx.version().getStartDate(), ctx.version().getEndDate(), null);
        return build(ctx.version().getId(), FoundationConstants.CHECK_OHLC_VALIDITY,
                violations > 0 ? FoundationConstants.QUALITY_FAIL : FoundationConstants.QUALITY_OK,
                violations, detail("violationRows", violations), ctx.now());
    }

    /** 7) 成交量/成交额单位异常（VWAP 出界 / 负值；FAIL）。 */
    private MdfQualityResultDO checkUnitAnomaly(QualityContext ctx) {
        long anomalies = qualitySourceMapper.countUnitAnomalies(
                ctx.source(), ctx.adjust(), ctx.version().getStartDate(), ctx.version().getEndDate(), null);
        return build(ctx.version().getId(), FoundationConstants.CHECK_UNIT_ANOMALY,
                anomalies > 0 ? FoundationConstants.QUALITY_FAIL : FoundationConstants.QUALITY_OK,
                anomalies, detail("anomalyRows", anomalies), ctx.now());
    }

    /** 8) 周末/非交易日异常（FAIL：日历存在时含日历外日期）。 */
    private MdfQualityResultDO checkNonTradingDay(QualityContext ctx) {
        long rows = qualitySourceMapper.countNonTradingDayRows("CN", ctx.source(), ctx.adjust(),
                ctx.version().getStartDate(), ctx.version().getEndDate(), null, ctx.calendarDays());
        return build(ctx.version().getId(), FoundationConstants.CHECK_NON_TRADING_DAY,
                rows > 0 ? FoundationConstants.QUALITY_FAIL : FoundationConstants.QUALITY_OK,
                rows, detail("nonTradingRows", rows), ctx.now());
    }

    /** 9) 行业成员重叠（FAIL）。 */
    private MdfQualityResultDO checkMembershipOverlap(QualityContext ctx) {
        long overlaps = membershipMapper.countOverlapPairs("SINA_INDUSTRY");
        return build(ctx.version().getId(), FoundationConstants.CHECK_MEMBERSHIP_OVERLAP,
                overlaps > 0 ? FoundationConstants.QUALITY_FAIL : FoundationConstants.QUALITY_OK,
                overlaps, detail("overlapPairs", overlaps), ctx.now());
    }

    /** 10) 行业成员无效有效期（FAIL）。 */
    private MdfQualityResultDO checkMembershipInvalidPeriod(QualityContext ctx) {
        long invalid = membershipMapper.countInvalidPeriods("SINA_INDUSTRY");
        return build(ctx.version().getId(), FoundationConstants.CHECK_MEMBERSHIP_INVALID_PERIOD,
                invalid > 0 ? FoundationConstants.QUALITY_FAIL : FoundationConstants.QUALITY_OK,
                invalid, detail("invalidPeriodRows", invalid), ctx.now());
    }

    /** 11) 证券未映射行业（WARN）。 */
    private MdfQualityResultDO checkUnmappedIndustry(QualityContext ctx) {
        long unmapped = 0;
        String status = FoundationConstants.QUALITY_OK;
        if (!ctx.universeSymbols().isEmpty()) {
            long mapped = membershipMapper.countDistinctSymbols("SINA_INDUSTRY");
            unmapped = Math.max(0, ctx.universeSymbols().size() - mapped);
            if (unmapped > 0) {
                status = FoundationConstants.QUALITY_WARN;
            }
        }
        return build(ctx.version().getId(), FoundationConstants.CHECK_UNMAPPED_INDUSTRY, status,
                unmapped, detail("unmappedSymbols", unmapped), ctx.now());
    }

    /** 12) Provider/复权口径混用（窗口内出现声明口径之外来源/复权行；FAIL）。 */
    private MdfQualityResultDO checkProviderAdjustMixing(QualityContext ctx) {
        long mixing = qualitySourceMapper.countProviderAdjustMixingRows(
                ctx.source(), ctx.adjust(), ctx.version().getStartDate(), ctx.version().getEndDate(), null);
        return build(ctx.version().getId(), FoundationConstants.CHECK_PROVIDER_ADJUST_MIXING,
                mixing > 0 ? FoundationConstants.QUALITY_FAIL : FoundationConstants.QUALITY_OK,
                mixing, detail("foreignRows", mixing), ctx.now());
    }

    /** 13) 数据陈旧度（WARN）。 */
    private MdfQualityResultDO checkStaleness(QualityContext ctx) {
        LocalDateTime maxFetchedAt = qualitySourceMapper.selectMaxFetchedAt(
                ctx.source(), ctx.adjust(), ctx.version().getStartDate(), ctx.version().getEndDate(), null);
        long staleDays = maxFetchedAt == null ? Long.MAX_VALUE
                : Duration.between(maxFetchedAt, ctx.now()).toDays();
        return build(ctx.version().getId(), FoundationConstants.CHECK_DATA_STALENESS,
                staleDays > STALENESS_WARN_DAYS ? FoundationConstants.QUALITY_WARN : FoundationConstants.QUALITY_OK,
                maxFetchedAt == null ? 1L : 0L,
                detail("maxFetchedAt", maxFetchedAt == null ? null : maxFetchedAt.toString()), ctx.now());
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

    // ---------------------------------------------------------------- VO 装配（controller 不接触持久化模型）

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

    private String resolveAdjustType(MdfDatasetVersionDO version) {
        // 版本事实行的 adjust_type 即数据集声明口径（首期冻结 NONE，D4）。
        MdfDatasetDO dataset = datasetMapper.selectById(version.getDatasetId());
        return dataset == null ? "NONE" : dataset.getAdjustType();
    }

    private MdfQualityResultDO build(long versionId, String checkCode, String status,
                                     long affectedCount, String detailJson, LocalDateTime checkedAt) {
        return MdfQualityResultDO.builder()
                .datasetVersionId(versionId).checkCode(checkCode).status(status)
                .affectedCount(affectedCount).detailJson(detailJson).checkedAt(checkedAt)
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
}
