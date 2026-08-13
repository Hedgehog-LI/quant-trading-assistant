package com.quant.trade.marketdata.analysis.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.analysis.constant.SectorAnalyticsConstants;
import com.quant.trade.marketdata.analysis.dao.SectorAnalyticsMapper;
import com.quant.trade.marketdata.analysis.derived.RelativeStrengthCalculator;
import com.quant.trade.marketdata.analysis.derived.SectorRotationPersistenceCalculator;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsCalculationRunDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsPublicationBatchDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsSourceBatchDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsSourceItemDO;
import com.quant.trade.marketdata.analysis.model.SectorRelativeStrengthResultDO;
import com.quant.trade.marketdata.analysis.model.SectorRotationPersistenceResultDO;
import com.quant.trade.marketdata.analysis.readiness.SectorAnalyticsReadinessManager;
import com.quant.trade.marketdata.analysis.vo.MarketResearchVO;
import com.quant.trade.marketdata.dao.SyncScopeLockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 从合格 CLOSE 排行事实生成并原子发布板块研究衍生资产。 */
@Service
@RequiredArgsConstructor
public class SectorAnalyticsCalculationService {

    private final SectorAnalyticsMapper mapper;
    private final SyncScopeLockMapper scopeLockMapper;
    private final RelativeStrengthCalculator relativeStrengthCalculator;
    private final SectorRotationPersistenceCalculator persistenceCalculator;
    private final SectorAnalyticsReadinessManager readinessManager;

    @Transactional
    public MarketResearchVO.Calculation calculate(String market, LocalDate asOfDate, int windowDays) {
        String normalizedMarket = normalizeMarket(market);
        requireWindow(windowDays);
        LocalDate targetDate = asOfDate == null ? LocalDate.now() : asOfDate;
        requireAuthoritativeCalendar(normalizedMarket, targetDate, windowDays);
        int momentumWindowDays = SectorAnalyticsConstants.RADAR_MOMENTUM_WINDOW_DAYS;
        int requiredDays = Math.max(windowDays, momentumWindowDays);
        List<SectorAnalyticsSourceBatchDO> batches = mapper.selectCloseBatches(
                SectorAnalyticsConstants.PROVIDER_LONGPORT, normalizedMarket, targetDate, requiredDays);
        if (batches.size() < requiredDays) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "收盘排行样本不足: required=" + requiredDays + ", actual=" + batches.size());
        }
        if (batches.stream().anyMatch(batch -> batch.getProviderQuoteTime() == null)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "来源行情时间缺失，未发布衍生结果: SOURCE_TIME_UNKNOWN");
        }
        batches = batches.stream().sorted(Comparator.comparing(
                SectorAnalyticsSourceBatchDO::getTradeDate)).toList();
        requireContinuousDates(normalizedMarket, batches);
        LocalDate effectiveAsOfDate = batches.get(batches.size() - 1).getTradeDate();
        List<SectorAnalyticsSourceItemDO> items = mapper.selectSourceItems(
                batches.stream().map(SectorAnalyticsSourceBatchDO::getId).toList());
        List<RelativeStrengthCalculator.DailyReturns> dailyReturns = toDailyReturns(batches, items);
        String rsManifest = sourceManifest(batches, windowDays);
        String persistenceManifest = sourceManifest(batches, momentumWindowDays);
        String rsSourceHash = sha256(rsManifest);
        String persistenceSourceHash = sha256(persistenceManifest);
        String rsParameterHash = relativeStrengthParameterHash(dailyReturns, windowDays);
        String persistenceParameterHash = persistenceParameterHash(momentumWindowDays);
        String publicationParameterHash = sha256("strength=" + rsParameterHash
                + ";momentum=" + persistenceParameterHash
                + ";strengthThreshold=" + SectorAnalyticsConstants.ROTATION_STRENGTH_THRESHOLD
                + ";momentumThreshold=" + SectorAnalyticsConstants.ROTATION_MOMENTUM_THRESHOLD);
        String calculationScopeHash = sha256(normalizedMarket + "|" + effectiveAsOfDate + "|"
                + windowDays + "|" + momentumWindowDays + "|" + publicationParameterHash
                + "|" + rsSourceHash + "|" + persistenceSourceHash);
        scopeLockMapper.upsert(SectorAnalyticsConstants.PROVIDER_LONGPORT,
                "SECTOR_ANALYTICS", calculationScopeHash);
        scopeLockMapper.selectForUpdate(SectorAnalyticsConstants.PROVIDER_LONGPORT,
                "SECTOR_ANALYTICS", calculationScopeHash);

        CalculationRuns runs = calculateAndPersist(normalizedMarket, effectiveAsOfDate, windowDays,
                momentumWindowDays, rsParameterHash, persistenceParameterHash,
                rsSourceHash, persistenceSourceHash, rsManifest, persistenceManifest, dailyReturns);
        String formulaSetHash = requiredFormulaSetHash(rsParameterHash, persistenceParameterHash);
        String sourceGroupHash = sourceManifestGroupHash(runs, rsParameterHash,
                persistenceParameterHash, rsSourceHash, persistenceSourceHash);
        SectorAnalyticsPublicationBatchDO existing = mapper.selectPublicationByIdentity(
                SectorAnalyticsConstants.PROVIDER_LONGPORT, normalizedMarket, effectiveAsOfDate, windowDays,
                momentumWindowDays, SectorAnalyticsConstants.FORMULA_VERSION,
                publicationParameterHash, sourceGroupHash);
        if (existing != null && SectorAnalyticsConstants.STATUS_PUBLISHED.equals(existing.getStatus())) {
            return new MarketResearchVO.Calculation(existing.getId(), existing.getAsOfDate(), windowDays,
                    momentumWindowDays, existing.getStatus(), mapper.selectResearchRows(existing.getId()).size(), true);
        }
        SectorAnalyticsPublicationBatchDO publication = publish(normalizedMarket, effectiveAsOfDate,
                windowDays, momentumWindowDays, publicationParameterHash, formulaSetHash, sourceGroupHash, runs);
        return new MarketResearchVO.Calculation(publication.getId(), effectiveAsOfDate, windowDays,
                momentumWindowDays, publication.getStatus(), runs.sampleSize(), false);
    }

    private CalculationRuns calculateAndPersist(String market, LocalDate asOfDate, int strengthWindowDays,
            int momentumWindowDays, String rsParameterHash, String persistenceParameterHash,
            String rsSourceHash, String persistenceSourceHash, String rsManifest,
            String persistenceManifest, List<RelativeStrengthCalculator.DailyReturns> dailyReturns) {
        var crossSections = dailyReturns.stream().map(day ->
                new SectorRotationPersistenceCalculator.DailyCrossSection(day.tradeDate(), day.returns())).toList();
        var relativeStrength = relativeStrengthCalculator.calculate(dailyReturns, strengthWindowDays,
                SectorAnalyticsConstants.MINIMUM_COHORT_SIZE);
        var persistence = persistenceCalculator.calculate(crossSections, momentumWindowDays,
                SectorAnalyticsConstants.MINIMUM_COHORT_SIZE);
        if (relativeStrength.isEmpty() || persistence.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "固定 cohort 样本不足或分类口径变化: INSUFFICIENT_SAMPLE_OR_ORIGIN_CHANGED");
        }
        SectorAnalyticsCalculationRunDO rsRun = persistRelativeStrength(market, asOfDate, strengthWindowDays,
                rsParameterHash, rsSourceHash, rsManifest, relativeStrength);
        SectorAnalyticsCalculationRunDO persistenceRun = persistPersistence(market, asOfDate, momentumWindowDays,
                persistenceParameterHash, persistenceSourceHash, persistenceManifest, persistence);
        return new CalculationRuns(rsRun, persistenceRun, relativeStrength.size());
    }

    private SectorAnalyticsCalculationRunDO persistRelativeStrength(String market, LocalDate asOfDate,
            int windowDays, String parameterHash, String sourceHash, String manifest,
            List<RelativeStrengthCalculator.Result> results) {
        SectorAnalyticsCalculationRunDO run = ensureRun(market, asOfDate, windowDays,
                SectorAnalyticsConstants.FORMULA_RELATIVE_STRENGTH,
                parameterHash, sourceHash, manifest, results.size());
        if (SectorAnalyticsConstants.STATUS_RUNNING.equals(run.getStatus())) {
            mapper.insertRelativeStrength(results.stream().map(result -> SectorRelativeStrengthResultDO.builder()
                    .calculationRunId(run.getId()).sectorIdentityId(result.sectorIdentityId())
                    .asOfDate(asOfDate).windowDays(windowDays).sectorReturn(result.sectorReturn())
                    .benchmarkReturn(result.benchmarkReturn()).relativeReturn(result.relativeReturn())
                    .rsRankPercentile(result.rsRankPercentile())
                    .qualityStatus(SectorAnalyticsConstants.QUALITY_OK).build()).toList());
            mapper.completeCalculationRun(run.getId(), SectorAnalyticsConstants.QUALITY_OK, null, results.size());
        }
        return run;
    }

    private SectorAnalyticsCalculationRunDO persistPersistence(String market, LocalDate asOfDate,
            int windowDays, String parameterHash, String sourceHash, String manifest,
            List<SectorRotationPersistenceCalculator.Result> results) {
        SectorAnalyticsCalculationRunDO run = ensureRun(market, asOfDate, windowDays,
                SectorAnalyticsConstants.FORMULA_ROTATION_PERSISTENCE,
                parameterHash, sourceHash, manifest, results.size());
        if (SectorAnalyticsConstants.STATUS_RUNNING.equals(run.getStatus())) {
            mapper.insertPersistence(results.stream().map(result -> SectorRotationPersistenceResultDO.builder()
                    .calculationRunId(run.getId()).sectorIdentityId(result.sectorIdentityId())
                    .asOfDate(asOfDate).windowDays(windowDays).currentRank(result.currentRank())
                    .previousRank(result.previousRank()).meanRankPercentile(result.meanRankPercentile())
                    .rankPercentileStdDev(result.rankPercentileStdDev())
                    .topBucketOccupancyRate(result.topBucketOccupancyRate())
                    .consecutiveLeadingDays(result.consecutiveLeadingDays())
                    .consecutiveLaggingDays(result.consecutiveLaggingDays())
                    .rankPercentileChange(result.rankPercentileChange())
                    .qualityStatus(SectorAnalyticsConstants.QUALITY_OK).build()).toList());
            mapper.completeCalculationRun(run.getId(), SectorAnalyticsConstants.QUALITY_OK, null, results.size());
        }
        return run;
    }

    private SectorAnalyticsPublicationBatchDO publish(String market, LocalDate asOfDate, int windowDays,
            int momentumWindowDays,
            String parameterHash, String formulaSetHash, String sourceHash, CalculationRuns runs) {
        SectorAnalyticsPublicationBatchDO publication = SectorAnalyticsPublicationBatchDO.builder()
                .providerCode(SectorAnalyticsConstants.PROVIDER_LONGPORT).marketCode(market)
                .asOfDate(asOfDate).windowDays(windowDays).momentumWindowDays(momentumWindowDays)
                .formulaVersion(SectorAnalyticsConstants.FORMULA_VERSION)
                .parameterHash(parameterHash).requiredFormulaSetHash(formulaSetHash)
                .sourceManifestGroupHash(sourceHash).scopeCode(SectorAnalyticsConstants.SCOPE_RANKED_UNIVERSE)
                .status(SectorAnalyticsConstants.STATUS_PUBLISHED).qualityStatus(SectorAnalyticsConstants.QUALITY_OK)
                .publishedAt(LocalDateTime.now()).build();
        try {
            mapper.insertPublication(publication);
            requirePublicationMember(mapper.insertPublicationMember(publication.getId(),
                    runs.relativeStrengthRun().getId(), SectorAnalyticsConstants.FORMULA_RELATIVE_STRENGTH));
            requirePublicationMember(mapper.insertPublicationMember(publication.getId(),
                    runs.persistenceRun().getId(), SectorAnalyticsConstants.FORMULA_ROTATION_PERSISTENCE));
            return publication;
        } catch (DuplicateKeyException exception) {
            SectorAnalyticsPublicationBatchDO existing = mapper.selectPublicationByIdentity(
                    SectorAnalyticsConstants.PROVIDER_LONGPORT, market, asOfDate, windowDays, momentumWindowDays,
                    SectorAnalyticsConstants.FORMULA_VERSION, parameterHash, sourceHash);
            if (existing == null) {
                throw exception;
            }
            return existing;
        }
    }

    private void requirePublicationMember(int insertedRows) {
        if (insertedRows != 1) {
            throw new IllegalStateException("发布成员与批次范围不一致");
        }
    }

    private SectorAnalyticsCalculationRunDO ensureRun(String market, LocalDate asOfDate, int windowDays,
                                                          String formulaCode, String parameterHash,
                                                          String sourceHash, String manifest, int sampleSize) {
        SectorAnalyticsCalculationRunDO existing = mapper.selectRunByIdentity(formulaCode,
                SectorAnalyticsConstants.FORMULA_VERSION, parameterHash, sourceHash);
        if (existing != null) {
            return existing;
        }
        SectorAnalyticsCalculationRunDO run = SectorAnalyticsCalculationRunDO.builder()
                .providerCode(SectorAnalyticsConstants.PROVIDER_LONGPORT).marketCode(market)
                .asOfDate(asOfDate).formulaCode(formulaCode)
                .formulaVersion(SectorAnalyticsConstants.FORMULA_VERSION).windowDays(windowDays)
                .parameterHash(parameterHash).sourceManifestHash(sourceHash).sourceManifest(manifest)
                .status(SectorAnalyticsConstants.STATUS_RUNNING).qualityStatus("PENDING")
                .sampleSize(sampleSize).startedAt(LocalDateTime.now()).build();
        mapper.insertCalculationRun(run);
        return run;
    }

    private List<RelativeStrengthCalculator.DailyReturns> toDailyReturns(
            List<SectorAnalyticsSourceBatchDO> batches, List<SectorAnalyticsSourceItemDO> items) {
        Map<Long, Map<Long, BigDecimal>> byBatch = new LinkedHashMap<>();
        items.forEach(item -> byBatch.computeIfAbsent(item.getBatchId(), ignored -> new LinkedHashMap<>())
                .put(item.getSectorIdentityId(), item.getChangeRate()));
        List<RelativeStrengthCalculator.DailyReturns> result = new ArrayList<>();
        for (SectorAnalyticsSourceBatchDO batch : batches) {
            result.add(new RelativeStrengthCalculator.DailyReturns(batch.getTradeDate(),
                    byBatch.getOrDefault(batch.getId(), Map.of())));
        }
        return result;
    }

    private String sourceManifest(List<SectorAnalyticsSourceBatchDO> batches, int windowDays) {
        return batches.stream().skip(Math.max(0, batches.size() - windowDays))
                .map(batch -> batch.getId() + "@" + batch.getTradeDate() + "@" + batch.getProviderQuoteTime())
                .reduce((left, right) -> left + "," + right).orElseThrow();
    }

    private String relativeStrengthParameterHash(List<RelativeStrengthCalculator.DailyReturns> days,
                                                 int windowDays) {
        return sha256("formula=" + SectorAnalyticsConstants.FORMULA_RELATIVE_STRENGTH
                + ";scope=" + SectorAnalyticsConstants.SCOPE_RANKED_UNIVERSE
                + ";window=" + windowDays + ";benchmark=RANK_SET_EQUAL_WEIGHT;unit=DECIMAL_RATIO"
                + ";minCohort=" + SectorAnalyticsConstants.MINIMUM_COHORT_SIZE
                + ";cohort=" + cohortFingerprint(days, windowDays));
    }

    private String persistenceParameterHash(int windowDays) {
        return sha256("formula=" + SectorAnalyticsConstants.FORMULA_ROTATION_PERSISTENCE
                + ";scope=" + SectorAnalyticsConstants.SCOPE_RANKED_UNIVERSE
                + ";window=" + windowDays + ";rank=ASCENDING_AVERAGE"
                + ";topBucket=" + SectorAnalyticsConstants.TOP_BUCKET_THRESHOLD
                + ";minCohort=" + SectorAnalyticsConstants.MINIMUM_COHORT_SIZE);
    }

    private String cohortFingerprint(List<RelativeStrengthCalculator.DailyReturns> days, int windowDays) {
        List<RelativeStrengthCalculator.DailyReturns> window = days.stream()
                .skip(Math.max(0, days.size() - windowDays)).toList();
        var cohort = new HashSet<>(window.get(0).returns().keySet());
        window.forEach(day -> cohort.retainAll(day.returns().keySet()));
        return sha256(cohort.stream().sorted().map(String::valueOf)
                .reduce((left, right) -> left + "," + right).orElse("EMPTY"));
    }

    private String requiredFormulaSetHash(String rsParameterHash, String persistenceParameterHash) {
        return sha256(List.of(
                formulaIdentity(SectorAnalyticsConstants.FORMULA_RELATIVE_STRENGTH, rsParameterHash),
                formulaIdentity(SectorAnalyticsConstants.FORMULA_ROTATION_PERSISTENCE, persistenceParameterHash))
                .stream().sorted().reduce((left, right) -> left + "," + right).orElseThrow());
    }

    private String sourceManifestGroupHash(CalculationRuns runs, String rsParameterHash,
            String persistenceParameterHash, String rsSourceHash, String persistenceSourceHash) {
        return sha256(List.of(
                runIdentity(SectorAnalyticsConstants.FORMULA_RELATIVE_STRENGTH, rsParameterHash,
                        rsSourceHash, runs.relativeStrengthRun().getId()),
                runIdentity(SectorAnalyticsConstants.FORMULA_ROTATION_PERSISTENCE, persistenceParameterHash,
                        persistenceSourceHash, runs.persistenceRun().getId()))
                .stream().sorted().reduce((left, right) -> left + "," + right).orElseThrow());
    }

    private String formulaIdentity(String formulaCode, String parameterHash) {
        return formulaCode + ":" + SectorAnalyticsConstants.FORMULA_VERSION + ":" + parameterHash;
    }

    private String runIdentity(String formulaCode, String parameterHash, String sourceHash, Long runId) {
        return formulaIdentity(formulaCode, parameterHash) + ":" + sourceHash + ":" + runId;
    }

    private String normalizeMarket(String market) {
        String normalized = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CN", "HK", "US").contains(normalized)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "market 必须为 CN/HK/US");
        }
        return normalized;
    }

    private void requireWindow(int windowDays) {
        if (!SectorAnalyticsConstants.SUPPORTED_WINDOWS.contains(windowDays)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "window 仅支持 5/10/20/50");
        }
    }

    private void requireAuthoritativeCalendar(String market, LocalDate asOfDate, int windowDays) {
        if ("CN".equals(market) || windowDays < SectorAnalyticsReadinessManager.RS_WINDOW) {
            return;
        }
        if (!readinessManager.hasVerifiedCalendar(market, asOfDate, windowDays)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "HK/US 长窗口缺少足量权威交易日历: [CALENDAR_INFERRED, INSUFFICIENT_RAW]");
        }
    }

    private void requireContinuousDates(String market, List<SectorAnalyticsSourceBatchDO> batches) {
        List<LocalDate> expected = mapper.selectTradingDates(market,
                batches.get(0).getTradeDate(), batches.get(batches.size() - 1).getTradeDate());
        if (expected.isEmpty()) {
            return;
        }
        List<LocalDate> actual = batches.stream().map(SectorAnalyticsSourceBatchDO::getTradeDate).toList();
        if (!actual.containsAll(expected)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "收盘排行缺少交易日历中的应有日期，未发布衍生结果: INSUFFICIENT_SAMPLE");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private record CalculationRuns(SectorAnalyticsCalculationRunDO relativeStrengthRun,
                                   SectorAnalyticsCalculationRunDO persistenceRun,
                                   int sampleSize) {
    }
}
