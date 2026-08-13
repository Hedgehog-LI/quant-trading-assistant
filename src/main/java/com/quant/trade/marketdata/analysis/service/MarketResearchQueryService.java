package com.quant.trade.marketdata.analysis.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.analysis.constant.SectorAnalyticsConstants;
import com.quant.trade.marketdata.analysis.dao.SectorAnalyticsMapper;
import com.quant.trade.marketdata.analysis.enums.FlowMetricNatureEnum;
import com.quant.trade.marketdata.analysis.manager.RotationStateClassifier;
import com.quant.trade.marketdata.analysis.model.MarketResearchRowDO;
import com.quant.trade.marketdata.analysis.model.SectorAnalyticsPublicationBatchDO;
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

/** 查询已发布板块分析资产，不调用外部 provider。 */
@Service
@RequiredArgsConstructor
public class MarketResearchQueryService {

    private static final String SCOPE_DESCRIPTION = "排行样本，不代表全市场";
    private static final int EXPECTED_ITEM_COUNT = 100;
    private static final int MAX_HISTORY_DAYS = 120;

    private final SectorAnalyticsMapper mapper;
    private final RotationStateClassifier rotationStateClassifier;

    public MarketResearchVO.Radar radar(String market, int windowDays) {
        String normalizedMarket = normalizeMarket(market);
        requireWindow(windowDays);
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
        return new MarketResearchVO.Radar(publication.getId(), first.getCalculationRunId(),
                first.getMomentumCalculationRunId(), normalizedMarket, publication.getAsOfDate(),
                windowDays, publication.getMomentumWindowDays(), publication.getScopeCode(), SCOPE_DESCRIPTION,
                SectorAnalyticsConstants.FORMULA_RELATIVE_STRENGTH,
                SectorAnalyticsConstants.FORMULA_ROTATION_PERSISTENCE,
                publication.getFormulaVersion(), publication.getParameterHash(), publication.getQualityStatus(),
                qualityReasons(first.getActualItemCount()),
                first.getSourceQuoteTime(), publication.getPublishedAt(), value(first.getActualItemCount()),
                EXPECTED_ITEM_COUNT, coverage(first.getActualItemCount()),
                FlowMetricNatureEnum.UNAVAILABLE.name(), null, sectors);
    }

    public MarketResearchVO.RankingHistory rankingHistory(String market, int windowDays, int days) {
        String normalizedMarket = normalizeMarket(market);
        requireWindow(windowDays);
        int safeDays = Math.min(Math.max(days, 1), MAX_HISTORY_DAYS);
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
        requireWindow(windowDays);
        int safeDays = Math.min(Math.max(days, 1), MAX_HISTORY_DAYS);
        List<MarketResearchRowDO> rows = mapper.selectSectorHistory(
                normalizedMarket, sectorId, windowDays, safeDays);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                    "未找到已发布板块研究数据: sectorId=" + sectorId);
        }
        MarketResearchRowDO latest = rows.get(0);
        return new MarketResearchVO.SectorDetail(latest.getSectorId(), latest.getSectorName(),
                latest.getProviderSectorId(), latest.getTaxonomyVersion(), normalizedMarket, windowDays,
                latest.getScopeCode(), SCOPE_DESCRIPTION, latest.getLeadingName(), latest.getLeadingSymbol(),
                latest.getTrackingSymbol(), rows.stream().map(this::toHistoryPoint).toList(),
                latest.getSourceQuoteTime(), value(latest.getActualItemCount()), EXPECTED_ITEM_COUNT,
                coverage(latest.getActualItemCount()), latest.getPublicationQualityStatus(), List.of());
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
                row.getRsRankPercentile(), row.getCurrentRank(), row.getMeanRankPercentile(),
                row.getPublicationQualityStatus());
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

    private void requireWindow(int windowDays) {
        if (!SectorAnalyticsConstants.SUPPORTED_WINDOWS.contains(windowDays)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "window 仅支持 5/10/20/50");
        }
    }

    private BusinessException noDerivedData(String market, int windowDays) {
        return new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                "暂无已发布板块研究数据: market=" + market + ", window=" + windowDays);
    }
}
