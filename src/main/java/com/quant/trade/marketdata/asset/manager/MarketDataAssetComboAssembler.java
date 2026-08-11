package com.quant.trade.marketdata.asset.manager;

import com.quant.trade.marketdata.asset.dto.MarketDataAssetSeriesQueryDTO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetCombinationVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetSeriesVO;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketDataWatermarkMapper;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.dao.StockMinuteBarMapper;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.model.MarketDataWatermarkDO;
import com.quant.trade.marketdata.model.StockBarAvailabilityDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.util.MarketDataAssetTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P1.9-A availability 组合构建：真实存在的 interval/source/adjust 组合 + 水位 + 组合级新鲜度。
 * <p>
 * 只聚合真实存在于 bar 表的分组（不虚拟组合）；每个组合的新鲜度按该组合最新 bar / 水位
 * 与最近已完成交易时段判定，无权威日历返回 UNKNOWN。
 */
@Component
@RequiredArgsConstructor
public class MarketDataAssetComboAssembler {

    private static final int WATERMARK_FETCH_LIMIT = 500;
    private static final int RECENT_CALENDAR_DAYS = 20;

    private final StockDailyBarMapper dailyBarMapper;
    private final StockMinuteBarMapper minuteBarMapper;
    private final MarketDataWatermarkMapper watermarkMapper;
    private final TradingSessionManager tradingSessionManager;
    private final MarketDataAssetSecurityMeta securityMeta;

    /** 构建 availability：证券 + 真实存在的 interval/source/adjust 组合（含组合级新鲜度）。 */
    public List<MarketDataAssetCombinationVO> buildCombinations(String canonicalSymbol, StockBasicDO security) {
        String market = securityMeta.marketOf(security);
        String marketCode = securityMeta.marketCodeOf(market);
        LocalDateTime now = LocalDateTime.now(MarketDataAssetTimeFormatter.STORAGE_ZONE);
        List<LocalDate> recentTradingDays = tradingSessionManager.getTradingDays(marketCode,
                now.toLocalDate().minusDays(RECENT_CALENDAR_DAYS), now.toLocalDate());
        List<int[]> sessions = tradingSessionManager.getSessionWindows(marketCode, false);

        Map<String, MarketDataWatermarkDO> watermarkIndex = loadWatermarkIndex(canonicalSymbol);
        List<MarketDataAssetCombinationVO> combinations = new ArrayList<>();
        for (StockBarAvailabilityDO row : dailyBarMapper.selectDailyAvailability(canonicalSymbol)) {
            MarketDataWatermarkDO watermark = watermarkIndex.get(
                    watermarkKey(WorkbenchConstants.INTERVAL_1D, row.getDataSource(), row.getAdjustType()));
            combinations.add(toCombination(row, true, watermark, recentTradingDays, sessions, now));
        }
        for (StockBarAvailabilityDO row : minuteBarMapper.selectMinuteAvailability(canonicalSymbol)) {
            MarketDataWatermarkDO watermark = watermarkIndex.get(
                    watermarkKey(row.getIntervalType(), row.getDataSource(), row.getAdjustType()));
            combinations.add(toCombination(row, false, watermark, recentTradingDays, sessions, now));
        }
        combinations.sort(Comparator.comparing(MarketDataAssetCombinationVO::interval)
                .thenComparing(MarketDataAssetCombinationVO::dataSource)
                .thenComparing(MarketDataAssetCombinationVO::adjustType));
        return combinations;
    }

    /** 组合存在性：过滤 availability 聚合结果，空说明该证券没有该组合。 */
    public StockBarAvailabilityDO findComboRow(String canonicalSymbol, MarketDataAssetSeriesQueryDTO query) {
        if (query.isDaily()) {
            return dailyBarMapper.selectDailyAvailability(canonicalSymbol).stream()
                    .filter(row -> query.dataSource().equals(row.getDataSource())
                            && query.adjustType().equals(row.getAdjustType()))
                    .findFirst().orElse(null);
        }
        return minuteBarMapper.selectMinuteAvailability(canonicalSymbol).stream()
                .filter(row -> query.interval().equals(row.getIntervalType())
                        && query.dataSource().equals(row.getDataSource())
                        && query.adjustType().equals(row.getAdjustType()))
                .findFirst().orElse(null);
    }

    public MarketDataAssetSeriesVO.Availability toSeriesAvailability(StockBarAvailabilityDO row,
                                                                     MarketDataWatermarkDO watermark,
                                                                     boolean daily) {
        String firstBarTime = daily ? MarketDataAssetTimeFormatter.dateText(row.getFirstBarDate())
                : MarketDataAssetTimeFormatter.formatStoredTime(row.getFirstBarTime());
        String lastBarTime = daily ? MarketDataAssetTimeFormatter.dateText(row.getLastBarDate())
                : MarketDataAssetTimeFormatter.formatStoredTime(row.getLastBarTime());
        return new MarketDataAssetSeriesVO.Availability(firstBarTime, lastBarTime,
                MarketDataAssetTimeFormatter.formatStoredTime(row.getLatestFetchedAt()),
                watermarkTimeOf(watermark));
    }

    private MarketDataAssetCombinationVO toCombination(StockBarAvailabilityDO row, boolean daily,
                                                       MarketDataWatermarkDO watermark,
                                                       List<LocalDate> recentTradingDays,
                                                       List<int[]> sessions, LocalDateTime now) {
        String interval = daily ? WorkbenchConstants.INTERVAL_1D : row.getIntervalType();
        LocalDateTime latestBarTime = daily ? null
                : latestOf(row.getLastBarTime(), watermark == null ? null : watermark.getLastBarTime());
        LocalDate latestTradeDate = daily ? (watermark != null && watermark.getLastTradeDate() != null
                ? watermark.getLastTradeDate() : row.getLastBarDate()) : null;
        int minutes = daily ? 0 : intervalMinutes(interval);
        String freshness = MarketDataAssetSeriesFreshness.evaluate(daily, recentTradingDays, sessions,
                latestBarTime, latestTradeDate, now, minutes);
        return new MarketDataAssetCombinationVO(
                interval,
                row.getDataSource(),
                row.getAdjustType(),
                row.getBarCount(),
                daily ? MarketDataAssetTimeFormatter.dateText(row.getFirstBarDate())
                        : MarketDataAssetTimeFormatter.formatStoredTime(row.getFirstBarTime()),
                daily ? MarketDataAssetTimeFormatter.dateText(row.getLastBarDate())
                        : MarketDataAssetTimeFormatter.formatStoredTime(row.getLastBarTime()),
                MarketDataAssetTimeFormatter.formatStoredTime(row.getLatestFetchedAt()),
                watermarkTimeOf(watermark),
                freshness);
    }

    private Map<String, MarketDataWatermarkDO> loadWatermarkIndex(String canonicalSymbol) {
        Map<String, MarketDataWatermarkDO> index = new HashMap<>();
        List<MarketDataWatermarkDO> watermarks =
                watermarkMapper.selectByFilter(canonicalSymbol, null, null, WATERMARK_FETCH_LIMIT, 0);
        if (watermarks != null) {
            for (MarketDataWatermarkDO watermark : watermarks) {
                index.put(watermarkKey(watermark.getIntervalType(), watermark.getDataSource(),
                        watermark.getAdjustType()), watermark);
            }
        }
        return index;
    }

    private String watermarkKey(String interval, String source, String adjust) {
        return interval + "|" + source + "|" + adjust;
    }

    private String watermarkTimeOf(MarketDataWatermarkDO watermark) {
        if (watermark == null) {
            return null;
        }
        if (watermark.getLastBarTime() != null) {
            return MarketDataAssetTimeFormatter.formatStoredTime(watermark.getLastBarTime());
        }
        if (watermark.getLastTradeDate() != null) {
            return watermark.getLastTradeDate().toString();
        }
        return null;
    }

    private static LocalDateTime latestOf(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static int intervalMinutes(String interval) {
        return switch (interval) {
            case "1M" -> 1;
            case "5M" -> 5;
            case "15M" -> 15;
            case "30M" -> 30;
            case "60M" -> 60;
            default -> 1;
        };
    }
}
