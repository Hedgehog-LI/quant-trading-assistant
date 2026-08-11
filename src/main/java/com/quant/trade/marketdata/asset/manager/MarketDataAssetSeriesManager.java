package com.quant.trade.marketdata.asset.manager;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.asset.dto.MarketDataAssetSeriesQueryDTO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetAvailabilityVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetRelatedTasksVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetSeriesVO;
import com.quant.trade.marketdata.constant.MarketDataAssetConstants;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketDataWatermarkMapper;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.dao.StockMinuteBarMapper;
import com.quant.trade.marketdata.model.MarketDataWatermarkDO;
import com.quant.trade.marketdata.model.StockBarAvailabilityDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import com.quant.trade.marketdata.model.StockMinuteBarDO;
import com.quant.trade.marketdata.util.MarketDataAssetTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * P1.9-A 行情资产只读查询编排。
 * <p>
 * 只读现有日 K / 分钟 K / 证券 / 水位 / 日历 / 同步计划与任务明细表，不写库、
 * 不调用 provider、不新建 migration。查询编排在此，参数/范围、摘要/覆盖/新鲜度、
 * 组合构建、任务关联分别委托给 {@link MarketDataAssetSeriesQueryParser}、
 * {@link MarketDataAssetSeriesCoverage}、{@link MarketDataAssetComboAssembler}、
 * {@link MarketDataAssetRelatedTasksAssembler}。
 */
@Component
@RequiredArgsConstructor
public class MarketDataAssetSeriesManager {

    private final StockDailyBarMapper dailyBarMapper;
    private final StockMinuteBarMapper minuteBarMapper;
    private final MarketDataWatermarkMapper watermarkMapper;
    private final MarketDataAssetSeriesQueryParser queryParser;
    private final MarketDataAssetSecurityMeta securityMeta;
    private final MarketDataAssetComboAssembler comboAssembler;
    private final MarketDataAssetSeriesCoverage coverage;
    private final MarketDataAssetRelatedTasksAssembler relatedTasksAssembler;

    // ==================== availability ====================

    /** 构建 availability：证券 + 真实存在的 interval/source/adjust 组合（含组合级新鲜度）。 */
    public MarketDataAssetAvailabilityVO buildAvailability(String rawSymbol) {
        String canonicalSymbol = securityMeta.normalize(rawSymbol);
        StockBasicDO security = securityMeta.loadSecurity(canonicalSymbol);
        return new MarketDataAssetAvailabilityVO(securityMeta.toSecurityVO(security),
                comboAssembler.buildCombinations(canonicalSymbol, security));
    }

    // ==================== series ====================

    /**
     * 构建 series：校验区间 → 组合存在性 → 有界取 bars（SQL LIMIT 2001）→ 摘要/质量/新鲜度/整体覆盖。
     * <p>
     * 第 2001 条仅用于标记 truncated，不返回前端，Java 层不做截断。
     */
    public MarketDataAssetSeriesVO buildSeries(String rawSymbol, String interval, String from, String to,
                                               String adjustType, String dataSource) {
        String canonicalSymbol = securityMeta.normalize(rawSymbol);
        StockBasicDO security = securityMeta.loadSecurity(canonicalSymbol);
        MarketDataAssetSeriesQueryDTO query = queryParser.parseAndValidate(interval, from, to, adjustType, dataSource);
        String market = securityMeta.marketOf(security);
        queryParser.validateRange(query, securityMeta.marketCodeOf(market));

        StockBarAvailabilityDO comboRow = comboAssembler.findComboRow(canonicalSymbol, query);
        if (comboRow == null) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_ASSET_COMBINATION_NOT_FOUND,
                    "该证券不存在 " + query.interval() + "/" + query.dataSource() + "/" + query.adjustType() + " 组合");
        }
        MarketDataWatermarkDO watermark = watermarkMapper.selectByUniqueKey(canonicalSymbol,
                query.dataSource(), query.interval(), query.adjustType());

        BarLoad loaded = query.isDaily() ? loadDailyBars(canonicalSymbol, query) : loadMinuteBars(canonicalSymbol, query);
        MarketDataAssetSeriesVO.Availability availability =
                comboAssembler.toSeriesAvailability(comboRow, watermark, query.isDaily());
        MarketDataAssetSeriesVO.Summary summary = coverage.buildSummary(loaded.points());
        LocalDateTime now = LocalDateTime.now(MarketDataAssetTimeFormatter.STORAGE_ZONE);
        LocalDateTime latestBarTime = query.isDaily() ? null
                : latestOf(comboRow.getLastBarTime(), watermark == null ? null : watermark.getLastBarTime());
        LocalDate latestTradeDate = query.isDaily() ? (watermark != null && watermark.getLastTradeDate() != null
                ? watermark.getLastTradeDate() : comboRow.getLastBarDate()) : null;
        MarketDataAssetSeriesVO.Quality quality = coverage.buildQuality(query, market, loaded.truncated(),
                loaded.points().size(), loaded.suspectBarCount(), latestBarTime, latestTradeDate, now);
        return new MarketDataAssetSeriesVO(securityMeta.toSecurityVO(security),
                new MarketDataAssetSeriesVO.Query(query.interval(), query.fromRaw(), query.toRaw(),
                        query.adjustType(), query.dataSource()),
                availability, quality, summary, loaded.bars());
    }

    // ==================== bar 加载 ====================

    /** 有界加载的日 K/分钟 K 结果（第 SERIES_FETCH_LIMIT 条仅标记截断，不返回）。 */
    private record BarLoad(List<MarketDataAssetSeriesVO.Bar> bars,
                           List<MarketDataAssetSeriesCoverage.BarPoint> points,
                           int suspectBarCount, boolean truncated) {
    }

    private BarLoad loadDailyBars(String canonicalSymbol, MarketDataAssetSeriesQueryDTO query) {
        List<StockDailyBarDO> rows = dailyBarMapper.selectByFilter(canonicalSymbol,
                query.fromDate(), query.toDate(), query.adjustType(), query.dataSource(),
                MarketDataAssetConstants.SERIES_FETCH_LIMIT, 0);
        boolean truncated = rows.size() > MarketDataAssetConstants.MAX_BARS_PER_REQUEST;
        List<StockDailyBarDO> visible = truncated
                ? rows.subList(0, MarketDataAssetConstants.MAX_BARS_PER_REQUEST) : rows;
        List<MarketDataAssetSeriesVO.Bar> bars = new ArrayList<>();
        List<MarketDataAssetSeriesCoverage.BarPoint> points = new ArrayList<>();
        for (StockDailyBarDO row : visible) {
            bars.add(toDailyBar(row));
            points.add(coverage.fromDaily(row));
        }
        return new BarLoad(bars, points, 0, truncated);
    }

    private BarLoad loadMinuteBars(String canonicalSymbol, MarketDataAssetSeriesQueryDTO query) {
        List<StockMinuteBarDO> rows = minuteBarMapper.selectByFilter(canonicalSymbol,
                query.interval(), query.adjustType(), query.dataSource(),
                query.fromTime(), query.toTime(), null,
                MarketDataAssetConstants.SERIES_FETCH_LIMIT, 0);
        boolean truncated = rows.size() > MarketDataAssetConstants.MAX_BARS_PER_REQUEST;
        List<StockMinuteBarDO> visible = truncated
                ? rows.subList(0, MarketDataAssetConstants.MAX_BARS_PER_REQUEST) : rows;
        List<MarketDataAssetSeriesVO.Bar> bars = new ArrayList<>();
        List<MarketDataAssetSeriesCoverage.BarPoint> points = new ArrayList<>();
        int suspectBarCount = 0;
        for (StockMinuteBarDO row : visible) {
            bars.add(toMinuteBar(row));
            points.add(coverage.fromMinute(row));
            if (WorkbenchConstants.QUALITY_SUSPECT.equals(row.getQualityStatus())) {
                suspectBarCount++;
            }
        }
        return new BarLoad(bars, points, suspectBarCount, truncated);
    }

    // ==================== related-tasks ====================

    /** 构建 related-tasks：与该证券相关的采集计划 + 最近采集记录（分页）。 */
    public MarketDataAssetRelatedTasksVO buildRelatedTasks(String rawSymbol, String interval, String from, String to,
                                                           int page, int size) {
        return relatedTasksAssembler.build(rawSymbol, interval, from, to, page, size);
    }

    // ==================== bar 转换 ====================

    private MarketDataAssetSeriesVO.Bar toDailyBar(StockDailyBarDO row) {
        return new MarketDataAssetSeriesVO.Bar(
                row.getTradeDate().toString(),
                priceText(row.getOpenPrice()), priceText(row.getHighPrice()), priceText(row.getLowPrice()),
                priceText(row.getClosePrice()),
                row.getVolume(), priceText(row.getAmount()),
                null, MarketDataAssetTimeFormatter.formatStoredTime(row.getFetchedAt()));
    }

    private MarketDataAssetSeriesVO.Bar toMinuteBar(StockMinuteBarDO row) {
        return new MarketDataAssetSeriesVO.Bar(
                MarketDataAssetTimeFormatter.formatStoredTime(row.getBarStartTime()),
                priceText(row.getOpenPrice()), priceText(row.getHighPrice()), priceText(row.getLowPrice()),
                priceText(row.getClosePrice()),
                row.getVolume(), priceText(row.getAmount()),
                row.getQualityStatus(), MarketDataAssetTimeFormatter.formatStoredTime(row.getFetchedAt()));
    }

    private static String priceText(BigDecimal value) {
        return MarketDataAssetTimeFormatter.priceText(value);
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
}
