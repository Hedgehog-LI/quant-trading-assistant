package com.quant.trade.marketdata.analysis.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.analysis.constant.SectorAnalyticsConstants;
import com.quant.trade.marketdata.analysis.dao.SectorAnalyticsMapper;
import com.quant.trade.marketdata.analysis.derived.RelativeStrengthCalculator;
import com.quant.trade.marketdata.analysis.enums.FlowMetricNatureEnum;
import com.quant.trade.marketdata.analysis.manager.RotationStateClassifier;
import com.quant.trade.marketdata.analysis.model.MarketResearchRowDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsPublicationBatchDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsSourceBatchDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsSourceItemDO;
import com.quant.trade.marketdata.analysis.vo.MarketResearchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 查询已发布板块分析资产或最新一日收盘强度，不调用外部 provider。 */
@Service
@RequiredArgsConstructor
public class MarketResearchQueryService {

    private static final String SCOPE_DESCRIPTION = "排行样本，不代表全市场";
    private static final String ANALYSIS_MODE_ONE_DAY = "ONE_DAY_STRENGTH";
    private static final String ANALYSIS_MODE_MULTI_DAY = "MULTI_DAY_ROTATION";
    private static final String ONE_DAY_REASON = "ONE_DAY_STRENGTH_ONLY";
    private static final String ROTATION_UNAVAILABLE_REASON = "ROTATION_REQUIRES_5_DAYS";
    private static final int EXPECTED_ITEM_COUNT = 100;
    private static final int MAX_HISTORY_DAYS = 120;

    private final SectorAnalyticsMapper mapper;
    private final RotationStateClassifier rotationStateClassifier;
    private final RelativeStrengthCalculator relativeStrengthCalculator;

    public MarketResearchVO.Radar radar(String market, int windowDays) {
        String normalizedMarket = normalizeMarket(market);
        requireQueryWindow(windowDays);
        if (isOneDay(windowDays)) {
            return oneDayRadar(normalizedMarket);
        }
        SectorAnalyticsPublicationBatchDO publication = mapper.selectLatestPublication(
                normalizedMarket, windowDays, SectorAnalyticsConstants.RADAR_MOMENTUM_WINDOW_DAYS);
        if (publication == null) {
            throw noDerivedData(normalizedMarket, windowDays);
        }
        List<MarketResearchRowDO> rows = mapper.selectResearchRows(publication.getId());
        if (rows.isEmpty()) {
            throw noDerivedData(normalizedMarket, windowDays);
        }
        List<MarketResearchVO.Sector> sectors = rows.stream().map(this::toSector).toList();
        MarketResearchRowDO first = rows.get(0);
        return new MarketResearchVO.Radar(publication.getId(), first.getSourceBatchId(),
                first.getCalculationRunId(), first.getMomentumCalculationRunId(),
                ANALYSIS_MODE_MULTI_DAY, true, normalizedMarket, publication.getAsOfDate(),
                windowDays, publication.getMomentumWindowDays(), publication.getScopeCode(), SCOPE_DESCRIPTION,
                SectorAnalyticsConstants.FORMULA_RELATIVE_STRENGTH,
                SectorAnalyticsConstants.FORMULA_ROTATION_PERSISTENCE,
                publication.getFormulaVersion(), publication.getParameterHash(), publication.getQualityStatus(),
                qualityReasons(first.getActualItemCount()), first.getSourceQuoteTime(), publication.getPublishedAt(),
                value(first.getActualItemCount()), EXPECTED_ITEM_COUNT, coverage(first.getActualItemCount()),
                FlowMetricNatureEnum.UNAVAILABLE.name(), null, sectors);
    }

    public MarketResearchVO.RankingHistory rankingHistory(String market, int windowDays, int days) {
        String normalizedMarket = normalizeMarket(market);
        requireQueryWindow(windowDays);
        int safeDays = Math.min(Math.max(days, 1), MAX_HISTORY_DAYS);
        if (isOneDay(windowDays)) {
            return oneDayRankingHistory(normalizedMarket, safeDays);
        }
        List<MarketResearchRowDO> rows = mapper.selectRankingHistoryRows(
                normalizedMarket, windowDays, safeDays);
        Map<Long, List<MarketResearchRowDO>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.getSectorId(), ignored -> new ArrayList<>()).add(row));
        List<MarketResearchVO.SectorHistory> sectors = grouped.values().stream().map(group ->
                new MarketResearchVO.SectorHistory(group.get(0).getSectorId(), group.get(0).getSectorName(),
                        group.stream().map(this::toHistoryPoint).toList())).toList();
        return new MarketResearchVO.RankingHistory(normalizedMarket, windowDays,
                SectorAnalyticsConstants.SCOPE_RANKED_UNIVERSE, sectors);
    }

    public MarketResearchVO.SectorDetail sectorDetail(String market, Long sectorId,
                                                       int windowDays, int days) {
        String normalizedMarket = normalizeMarket(market);
        requireQueryWindow(windowDays);
        int safeDays = Math.min(Math.max(days, 1), MAX_HISTORY_DAYS);
        if (isOneDay(windowDays)) {
            return oneDaySectorDetail(normalizedMarket, sectorId, safeDays);
        }
        List<MarketResearchRowDO> rows = mapper.selectSectorHistory(
                normalizedMarket, sectorId, windowDays, safeDays);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                    "未找到已发布板块研究数据: sectorId=" + sectorId);
        }
        MarketResearchRowDO latest = rows.get(0);
        return new MarketResearchVO.SectorDetail(latest.getSectorId(), latest.getSectorName(),
                latest.getProviderSectorId(), latest.getTaxonomyVersion(), normalizedMarket, windowDays,
                ANALYSIS_MODE_MULTI_DAY, true, latest.getScopeCode(), SCOPE_DESCRIPTION,
                latest.getLeadingName(), latest.getLeadingSymbol(), latest.getTrackingSymbol(),
                rows.stream().map(this::toHistoryPoint).toList(), latest.getSourceQuoteTime(),
                value(latest.getActualItemCount()), EXPECTED_ITEM_COUNT, coverage(latest.getActualItemCount()),
                latest.getPublicationQualityStatus(), List.of());
    }

    private MarketResearchVO.Radar oneDayRadar(String market) {
        OneDaySnapshot snapshot = oneDaySnapshots(market, 1).get(0);
        List<MarketResearchVO.Sector> sectors = snapshot.results().stream()
                .map(result -> toOneDaySector(snapshot, result)).toList();
        int actualItemCount = sectors.size();
        return new MarketResearchVO.Radar(null, snapshot.batch().getId(), null, null,
                ANALYSIS_MODE_ONE_DAY, false, market, snapshot.batch().getTradeDate(),
                SectorAnalyticsConstants.ONE_DAY_STRENGTH_WINDOW_DAYS, 0,
                SectorAnalyticsConstants.SCOPE_RANKED_UNIVERSE, SCOPE_DESCRIPTION,
                SectorAnalyticsConstants.FORMULA_RELATIVE_STRENGTH, null,
                SectorAnalyticsConstants.FORMULA_VERSION, null, SectorAnalyticsConstants.QUALITY_OK,
                oneDayQualityReasons(actualItemCount), snapshot.batch().getProviderQuoteTime(), null,
                actualItemCount, EXPECTED_ITEM_COUNT, coverage(actualItemCount),
                FlowMetricNatureEnum.UNAVAILABLE.name(), null, sectors);
    }

    private MarketResearchVO.RankingHistory oneDayRankingHistory(String market, int days) {
        List<OneDaySnapshot> snapshots = oneDaySnapshots(market, days);
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, List<MarketResearchVO.HistoryPoint>> points = new LinkedHashMap<>();
        for (OneDaySnapshot snapshot : snapshots) {
            Map<Long, SectorAnalyticsSourceItemDO> items = itemsBySector(snapshot.items());
            for (RelativeStrengthCalculator.Result result : snapshot.results()) {
                SectorAnalyticsSourceItemDO item = items.get(result.sectorIdentityId());
                names.putIfAbsent(result.sectorIdentityId(), item.getSectorName());
                points.computeIfAbsent(result.sectorIdentityId(), ignored -> new ArrayList<>()).add(
                        oneDayHistoryPoint(snapshot, result));
            }
        }
        List<MarketResearchVO.SectorHistory> sectors = points.entrySet().stream().map(entry ->
                new MarketResearchVO.SectorHistory(entry.getKey(), names.get(entry.getKey()), entry.getValue()))
                .toList();
        return new MarketResearchVO.RankingHistory(market, 1,
                SectorAnalyticsConstants.SCOPE_RANKED_UNIVERSE, sectors);
    }

    private MarketResearchVO.SectorDetail oneDaySectorDetail(String market, Long sectorId, int days) {
        List<OneDaySnapshot> snapshots = oneDaySnapshots(market, days);
        List<MarketResearchVO.HistoryPoint> history = new ArrayList<>();
        SectorAnalyticsSourceItemDO latestItem = null;
        OneDaySnapshot latestSnapshot = null;
        for (OneDaySnapshot snapshot : snapshots) {
            SectorAnalyticsSourceItemDO item = itemsBySector(snapshot.items()).get(sectorId);
            RelativeStrengthCalculator.Result result = resultsBySector(snapshot.results()).get(sectorId);
            if (item != null && result != null) {
                history.add(oneDayHistoryPoint(snapshot, result));
                if (latestItem == null) {
                    latestItem = item;
                    latestSnapshot = snapshot;
                }
            }
        }
        if (latestItem == null || latestSnapshot == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                    "未找到板块一日强度数据: sectorId=" + sectorId);
        }
        int actualItemCount = latestSnapshot.results().size();
        return new MarketResearchVO.SectorDetail(sectorId, latestItem.getSectorName(),
                latestItem.getProviderSectorId(), latestItem.getTaxonomyVersion(), market, 1,
                ANALYSIS_MODE_ONE_DAY, false, SectorAnalyticsConstants.SCOPE_RANKED_UNIVERSE,
                SCOPE_DESCRIPTION, latestItem.getLeadingName(), latestItem.getLeadingSymbol(),
                latestItem.getTrackingSymbol(), history, latestSnapshot.batch().getProviderQuoteTime(),
                actualItemCount, EXPECTED_ITEM_COUNT, coverage(actualItemCount),
                SectorAnalyticsConstants.QUALITY_OK, oneDayQualityReasons(actualItemCount));
    }

    private List<OneDaySnapshot> oneDaySnapshots(String market, int days) {
        List<SectorAnalyticsSourceBatchDO> batches = mapper.selectLatestCloseBatches(
                SectorAnalyticsConstants.PROVIDER_LONGPORT, market, days);
        if (batches.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                    "暂无可用板块收盘排行: market=" + market);
        }
        List<SectorAnalyticsSourceItemDO> items = mapper.selectSourceItems(
                batches.stream().map(SectorAnalyticsSourceBatchDO::getId).toList());
        Map<Long, List<SectorAnalyticsSourceItemDO>> itemsByBatch = items.stream()
                .collect(Collectors.groupingBy(SectorAnalyticsSourceItemDO::getBatchId,
                        LinkedHashMap::new, Collectors.toList()));
        List<OneDaySnapshot> snapshots = new ArrayList<>();
        for (SectorAnalyticsSourceBatchDO batch : batches) {
            if (batch.getProviderQuoteTime() == null) {
                throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                        "来源行情时间缺失，无法生成一日强度: SOURCE_TIME_UNKNOWN");
            }
            List<SectorAnalyticsSourceItemDO> batchItems = itemsByBatch.getOrDefault(batch.getId(), List.of());
            Map<Long, BigDecimal> returns = batchItems.stream().collect(Collectors.toMap(
                    SectorAnalyticsSourceItemDO::getSectorIdentityId,
                    SectorAnalyticsSourceItemDO::getChangeRate, (left, right) -> right, LinkedHashMap::new));
            List<RelativeStrengthCalculator.Result> results = relativeStrengthCalculator.calculate(
                    List.of(new RelativeStrengthCalculator.DailyReturns(batch.getTradeDate(), returns)),
                    1, SectorAnalyticsConstants.MINIMUM_COHORT_SIZE);
            if (results.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                        "一日强度有效板块样本不足: minimum="
                                + SectorAnalyticsConstants.MINIMUM_COHORT_SIZE + ", actual=" + returns.size());
            }
            snapshots.add(new OneDaySnapshot(batch, batchItems, results));
        }
        return snapshots;
    }

    private MarketResearchVO.Sector toOneDaySector(OneDaySnapshot snapshot,
                                                    RelativeStrengthCalculator.Result result) {
        SectorAnalyticsSourceItemDO item = itemsBySector(snapshot.items()).get(result.sectorIdentityId());
        List<String> evidence = List.of(
                "当日涨跌 " + signedPercent(result.sectorReturn()),
                "当日强度百分位 " + percent(result.rsRankPercentile()),
                "相对样本均值 " + signedPercent(result.relativeReturn()));
        return new MarketResearchVO.Sector(result.sectorIdentityId(), item.getSectorName(),
                item.getProviderSectorId(), result.sectorReturn(), result.benchmarkReturn(),
                result.relativeReturn(), result.rsRankPercentile(),
                sourceRank(item), null, null, null, null, null, null, null,
                "INSUFFICIENT_DATA", item.getLeadingName(), item.getLeadingSymbol(), evidence,
                List.of(ONE_DAY_REASON, ROTATION_UNAVAILABLE_REASON));
    }

    private MarketResearchVO.HistoryPoint oneDayHistoryPoint(OneDaySnapshot snapshot,
            RelativeStrengthCalculator.Result result) {
        SectorAnalyticsSourceItemDO item = itemsBySector(snapshot.items()).get(result.sectorIdentityId());
        return new MarketResearchVO.HistoryPoint(snapshot.batch().getTradeDate(), null,
                snapshot.batch().getId(), result.rsRankPercentile(),
                sourceRank(item), null, SectorAnalyticsConstants.QUALITY_OK);
    }

    private Map<Long, SectorAnalyticsSourceItemDO> itemsBySector(List<SectorAnalyticsSourceItemDO> items) {
        return items.stream().collect(Collectors.toMap(SectorAnalyticsSourceItemDO::getSectorIdentityId,
                Function.identity(), (left, right) -> right, LinkedHashMap::new));
    }

    private Map<Long, RelativeStrengthCalculator.Result> resultsBySector(
            List<RelativeStrengthCalculator.Result> results) {
        return results.stream().collect(Collectors.toMap(RelativeStrengthCalculator.Result::sectorIdentityId,
                Function.identity(), (left, right) -> right, LinkedHashMap::new));
    }

    private MarketResearchVO.Sector toSector(MarketResearchRowDO row) {
        List<String> evidence = new ArrayList<>();
        evidence.add("相对强弱百分位 " + percent(row.getRsRankPercentile()));
        evidence.add("窗口头部占用率 " + percent(row.getTopBucketOccupancyRate()));
        if (row.getConsecutiveLeadingDays() != null && row.getConsecutiveLeadingDays() > 0) {
            evidence.add("连续领涨 " + row.getConsecutiveLeadingDays() + " 个交易日");
        } else if (row.getRankPercentileChange() != null) {
            evidence.add("窗口位次变化 " + signedPercent(row.getRankPercentileChange()));
        }
        return new MarketResearchVO.Sector(row.getSectorId(), row.getSectorName(),
                row.getProviderSectorId(), row.getSectorReturn(), row.getBenchmarkReturn(),
                row.getRelativeReturn(), row.getRsRankPercentile(), row.getCurrentRank(),
                row.getPreviousRank(), row.getMeanRankPercentile(), row.getRankPercentileStdDev(),
                row.getTopBucketOccupancyRate(), value(row.getConsecutiveLeadingDays()),
                value(row.getConsecutiveLaggingDays()), row.getRankPercentileChange(),
                rotationStateClassifier.classify(row.getRsRankPercentile(), row.getRankPercentileChange()).name(),
                row.getLeadingName(), row.getLeadingSymbol(), evidence,
                List.of("RANKED_UNIVERSE_LIMITED_COVERAGE"));
    }

    private MarketResearchVO.HistoryPoint toHistoryPoint(MarketResearchRowDO row) {
        return new MarketResearchVO.HistoryPoint(row.getAsOfDate(), row.getPublicationBatchId(),
                row.getSourceBatchId(), row.getRsRankPercentile(), row.getCurrentRank(),
                row.getMeanRankPercentile(), row.getPublicationQualityStatus());
    }

    private BigDecimal sourceRank(SectorAnalyticsSourceItemDO item) {
        if (item == null || item.getRankNo() == null) {
            return null;
        }
        return BigDecimal.valueOf(item.getRankNo());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal coverage(Integer actualItemCount) {
        if (actualItemCount == null) {
            return null;
        }
        return BigDecimal.valueOf(actualItemCount)
                .divide(BigDecimal.valueOf(EXPECTED_ITEM_COUNT), 4, RoundingMode.HALF_UP);
    }

    private List<String> qualityReasons(Integer actualItemCount) {
        List<String> reasons = new ArrayList<>();
        reasons.add("RANKED_UNIVERSE_LIMITED_COVERAGE");
        reasons.add("CAPITAL_FLOW_UNAVAILABLE");
        if (actualItemCount != null && actualItemCount >= EXPECTED_ITEM_COUNT) {
            reasons.add("PROVIDER_RANK_LIMIT_REACHED");
        }
        return reasons;
    }

    private List<String> oneDayQualityReasons(Integer actualItemCount) {
        List<String> reasons = new ArrayList<>(qualityReasons(actualItemCount));
        reasons.add(ONE_DAY_REASON);
        reasons.add(ROTATION_UNAVAILABLE_REASON);
        return reasons;
    }

    private String percent(BigDecimal value) {
        if (value == null) {
            return "不可用";
        }
        return value.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private String signedPercent(BigDecimal value) {
        String result = percent(value);
        return value != null && value.signum() > 0 ? "+" + result : result;
    }

    private String normalizeMarket(String market) {
        String normalized = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CN", "HK", "US").contains(normalized)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "market 必须为 CN/HK/US");
        }
        return normalized;
    }

    private void requireQueryWindow(int windowDays) {
        if (!SectorAnalyticsConstants.SUPPORTED_QUERY_WINDOWS.contains(windowDays)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "window 仅支持 1/5/10/20/50");
        }
    }

    private boolean isOneDay(int windowDays) {
        return windowDays == SectorAnalyticsConstants.ONE_DAY_STRENGTH_WINDOW_DAYS;
    }

    private BusinessException noDerivedData(String market, int windowDays) {
        return new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                "暂无已发布板块研究数据: market=" + market + ", window=" + windowDays);
    }

    private record OneDaySnapshot(SectorAnalyticsSourceBatchDO batch,
                                  List<SectorAnalyticsSourceItemDO> items,
                                  List<RelativeStrengthCalculator.Result> results) {
    }
}
