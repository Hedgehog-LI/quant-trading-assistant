package com.quant.trade.marketdata.asset.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.asset.dto.MarketDataAssetSeriesQueryDTO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetAvailabilityVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetCombinationVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetRelatedTasksVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetSecurityVO;
import com.quant.trade.marketdata.asset.vo.MarketDataAssetSeriesVO;
import com.quant.trade.marketdata.constant.MarketDataAssetConstants;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketCalendarMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncPlanMapper;
import com.quant.trade.marketdata.dao.MarketDataSyncTaskItemMapper;
import com.quant.trade.marketdata.dao.MarketDataWatermarkMapper;
import com.quant.trade.marketdata.dao.StockBasicMapper;
import com.quant.trade.marketdata.dao.StockDailyBarMapper;
import com.quant.trade.marketdata.dao.StockMinuteBarMapper;
import com.quant.trade.marketdata.exception.MarketDataAssetNotFoundException;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.model.MarketCalendarDO;
import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import com.quant.trade.marketdata.model.MarketDataSyncTaskItemDO;
import com.quant.trade.marketdata.model.MarketDataWatermarkDO;
import com.quant.trade.marketdata.model.StockBarAvailabilityDO;
import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.model.StockDailyBarDO;
import com.quant.trade.marketdata.model.StockMinuteBarDO;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * P1.9-A 行情资产只读查询领域逻辑。
 * <p>
 * 只读现有日 K / 分钟 K / 证券 / 水位 / 日历 / 同步计划与任务明细表，不写库、
 * 不调用 provider、不新建 migration。
 * <p>
 * 时间口径：分钟 bar 与抓取时间按<b>数据存储时区</b>输出带 offset 的 ISO-8601。
 * 采集链路（LongPort quote-time-zone 与 JDBC serverTimezone 均为 Asia/Shanghai）
 * 把分钟 bar 统一存为 Asia/Shanghai 墙钟时间，因此这里对任意市场都附加 +08:00，
 * 保证 ISO 字符串表达的瞬时一致、不与存储墙钟错位。{@code security.timeZone} 仍按
 * 交易所市场时区展示（CN→Asia/Shanghai、HK→Asia/Hong_Kong、US→America/New_York）。
 */
@Component
@RequiredArgsConstructor
public class MarketDataAssetSeriesManager {

    /** 数据实际存储时区：与采集链路 quote-time-zone / serverTimezone 保持一致。 */
    private static final ZoneId STORAGE_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter ISO_WITH_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static final int WATERMARK_FETCH_LIMIT = 500;
    private static final int PLAN_FETCH_LIMIT = 500;
    private static final int RUN_FETCH_LIMIT = 500;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** series 支持的数据源集合（与采集支持一致）。 */
    private static final Set<String> VALID_DATA_SOURCES = Set.of(
            MarketDataConstants.DATA_SOURCE_LONGPORT,
            MarketDataConstants.DATA_SOURCE_CSV,
            MarketDataConstants.DATA_SOURCE_MANUAL);

    private final StockBasicMapper stockBasicMapper;
    private final StockDailyBarMapper dailyBarMapper;
    private final StockMinuteBarMapper minuteBarMapper;
    private final MarketDataWatermarkMapper watermarkMapper;
    private final MarketCalendarMapper calendarMapper;
    private final MarketDataSyncPlanMapper syncPlanMapper;
    private final MarketDataSyncTaskItemMapper taskItemMapper;
    private final TradingSessionManager tradingSessionManager;
    private final ObjectMapper objectMapper;

    // ==================== availability ====================

    /** 构建 availability：证券 + 真实存在的 interval/source/adjust 组合。 */
    public MarketDataAssetAvailabilityVO buildAvailability(String rawSymbol) {
        String canonicalSymbol = normalize(rawSymbol);
        StockBasicDO security = loadSecurity(canonicalSymbol);
        Map<String, MarketDataWatermarkDO> watermarkIndex = loadWatermarkIndex(canonicalSymbol);
        List<MarketDataAssetCombinationVO> combinations = new ArrayList<>();
        for (StockBarAvailabilityDO row : dailyBarMapper.selectDailyAvailability(canonicalSymbol)) {
            combinations.add(toDailyCombination(row, watermarkIndex.get(
                    watermarkKey(WorkbenchConstants.INTERVAL_1D, row.getDataSource(), row.getAdjustType()))));
        }
        for (StockBarAvailabilityDO row : minuteBarMapper.selectMinuteAvailability(canonicalSymbol)) {
            combinations.add(toMinuteCombination(row, watermarkIndex.get(
                    watermarkKey(row.getIntervalType(), row.getDataSource(), row.getAdjustType()))));
        }
        combinations.sort(Comparator.comparing(MarketDataAssetCombinationVO::interval)
                .thenComparing(MarketDataAssetCombinationVO::dataSource)
                .thenComparing(MarketDataAssetCombinationVO::adjustType));
        return new MarketDataAssetAvailabilityVO(toSecurityVO(security), combinations);
    }

    // ==================== series ====================

    /**
     * 构建 series：校验区间 → 组合存在性 → 有界取 bars（SQL LIMIT 2001）→ 摘要/质量/整体覆盖。
     *
     * @param truncated 由 {@link MarketDataAssetConstants#SERIES_FETCH_LIMIT} 判定：第 2001 条仅用于标记，
     *                  不返回前端，Java 层不做截断。
     */
    public MarketDataAssetSeriesVO buildSeries(String rawSymbol, String interval, String from, String to,
                                               String adjustType, String dataSource) {
        String canonicalSymbol = normalize(rawSymbol);
        StockBasicDO security = loadSecurity(canonicalSymbol);
        MarketDataAssetSeriesQueryDTO query = parseAndValidateQuery(interval, from, to, adjustType, dataSource);
        String market = marketOf(security);
        validateRange(query, marketCodeOf(market));

        StockBarAvailabilityDO comboRow = findComboRow(canonicalSymbol, query);
        if (comboRow == null) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_ASSET_COMBINATION_NOT_FOUND,
                    "该证券不存在 " + query.interval() + "/" + query.dataSource() + "/" + query.adjustType() + " 组合");
        }
        MarketDataWatermarkDO watermark = watermarkMapper.selectByUniqueKey(canonicalSymbol,
                query.dataSource(), query.interval(), query.adjustType());

        List<MarketDataAssetSeriesVO.Bar> bars = new ArrayList<>();
        List<BarPoint> points = new ArrayList<>();
        int suspectBarCount = 0;
        boolean truncated;
        if (query.isDaily()) {
            List<StockDailyBarDO> rows = dailyBarMapper.selectByFilter(canonicalSymbol,
                    query.fromDate(), query.toDate(), query.adjustType(), query.dataSource(),
                    MarketDataAssetConstants.SERIES_FETCH_LIMIT, 0);
            truncated = rows.size() > MarketDataAssetConstants.MAX_BARS_PER_REQUEST;
            List<StockDailyBarDO> visible = truncated
                    ? rows.subList(0, MarketDataAssetConstants.MAX_BARS_PER_REQUEST) : rows;
            for (StockDailyBarDO row : visible) {
                bars.add(toDailyBar(row));
                points.add(toDailyPoint(row));
            }
        } else {
            List<StockMinuteBarDO> rows = minuteBarMapper.selectByFilter(canonicalSymbol,
                    query.interval(), query.adjustType(), query.dataSource(),
                    query.fromTime(), query.toTime(), null,
                    MarketDataAssetConstants.SERIES_FETCH_LIMIT, 0);
            truncated = rows.size() > MarketDataAssetConstants.MAX_BARS_PER_REQUEST;
            List<StockMinuteBarDO> visible = truncated
                    ? rows.subList(0, MarketDataAssetConstants.MAX_BARS_PER_REQUEST) : rows;
            for (StockMinuteBarDO row : visible) {
                bars.add(toMinuteBar(row));
                points.add(toMinutePoint(row));
                if (WorkbenchConstants.QUALITY_SUSPECT.equals(row.getQualityStatus())) {
                    suspectBarCount++;
                }
            }
        }

        MarketDataAssetSeriesVO.Availability availability = toSeriesAvailability(comboRow, watermark, query.isDaily());
        MarketDataAssetSeriesVO.Summary summary = buildSummary(points);
        MarketDataAssetSeriesVO.Quality quality =
                buildQuality(query, market, truncated, points.size(), suspectBarCount);
        return new MarketDataAssetSeriesVO(toSecurityVO(security),
                new MarketDataAssetSeriesVO.Query(query.interval(), query.fromRaw(), query.toRaw(),
                        query.adjustType(), query.dataSource()),
                availability, quality, summary, bars);
    }

    // ==================== related-tasks ====================

    /**
     * 构建 related-tasks：与该证券相关的采集计划 + 最近采集记录（分页）。
     * 血缘口径：scope_json 包含证券 + interval 匹配 + 时间范围过滤，不声称逐条 K 线血缘。
     */
    public MarketDataAssetRelatedTasksVO buildRelatedTasks(String rawSymbol, String interval, String from, String to,
                                                           int page, int size) {
        String canonicalSymbol = normalize(rawSymbol);
        StockBasicDO security = loadSecurity(canonicalSymbol);
        LocalDate fromDate = parseOptionalDate(from, "from");
        LocalDate toDate = parseOptionalDate(to, "to");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "from 不能晚于 to");
        }

        Map<Long, MarketDataSyncPlanDO> planById = loadPlansById();
        List<MarketDataAssetRelatedTasksVO.RelatedTaskItem> plans = new ArrayList<>();
        for (MarketDataSyncPlanDO plan : planById.values()) {
            if (!scopeContainsSymbol(plan.getScopeJson(), canonicalSymbol)) {
                continue;
            }
            if (!matchesInterval(plan.getIntervalType(), interval)) {
                continue;
            }
            if (!planOverlapsRange(plan, fromDate, toDate)) {
                continue;
            }
            plans.add(toPlanItem(plan));
        }
        plans.sort(Comparator.comparing(MarketDataAssetRelatedTasksVO.RelatedTaskItem::startedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<MarketDataSyncTaskItemDO> items = taskItemMapper.selectBySymbol(canonicalSymbol, RUN_FETCH_LIMIT, 0);
        List<MarketDataAssetRelatedTasksVO.RelatedTaskItem> allRuns = new ArrayList<>();
        for (MarketDataSyncTaskItemDO item : items) {
            MarketDataSyncPlanDO plan = item.getPlanId() == null ? null : planById.get(item.getPlanId());
            if (!matchesInterval(plan == null ? null : plan.getIntervalType(), interval)) {
                continue;
            }
            if (!itemOverlapsRange(item, fromDate, toDate)) {
                continue;
            }
            allRuns.add(toRunItem(item, plan));
        }
        allRuns.sort(Comparator.comparing(MarketDataAssetRelatedTasksVO.RelatedTaskItem::startedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (Math.max(page, 1) - 1) * safeSize;
        List<MarketDataAssetRelatedTasksVO.RelatedTaskItem> runs = offset >= allRuns.size()
                ? List.of()
                : allRuns.subList(offset, Math.min(offset + safeSize, allRuns.size()));

        return new MarketDataAssetRelatedTasksVO(toSecurityVO(security), plans, runs);
    }

    // ==================== 参数校验与解析 ====================

    private MarketDataAssetSeriesQueryDTO parseAndValidateQuery(String interval, String from, String to,
                                                                String adjustType, String dataSource) {
        if (interval == null || !MarketDataAssetConstants.VALID_INTERVALS.contains(interval)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "interval 必须为 1D/1M/5M/15M/30M/60M: " + interval);
        }
        if (dataSource == null || !VALID_DATA_SOURCES.contains(dataSource)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "dataSource 必须为 LONGPORT/CSV/MANUAL: " + dataSource);
        }
        if (adjustType == null || !MarketDataConstants.VALID_ADJUST_TYPES.contains(adjustType)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    "adjustType 必须为 NONE/QF/HF: " + adjustType);
        }
        if (WorkbenchConstants.INTERVAL_1D.equals(interval)) {
            LocalDate fromDate = requireDate(from, "from");
            LocalDate toDate = requireDate(to, "to");
            if (fromDate.isAfter(toDate)) {
                throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "from 不能晚于 to");
            }
            return new MarketDataAssetSeriesQueryDTO(interval, dataSource, adjustType, from, to,
                    fromDate, toDate, null, null);
        }
        LocalDateTime fromTime = requireMinuteTime(from, "from");
        LocalDateTime toTime = requireMinuteTime(to, "to");
        if (fromTime.isAfter(toTime)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "from 不能晚于 to");
        }
        return new MarketDataAssetSeriesQueryDTO(interval, dataSource, adjustType, from, to,
                null, null, fromTime, toTime);
    }

    private LocalDate requireDate(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, name + " 不能为空");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    name + " 必须是 YYYY-MM-DD: " + value);
        }
    }

    /** 分钟时间：优先按带 offset 的 ISO-8601 折算到存储时区，否则按存储时区墙钟直接采用。 */
    private LocalDateTime requireMinuteTime(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, name + " 不能为空");
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed).atZoneSameInstant(STORAGE_ZONE).toLocalDateTime();
        } catch (DateTimeParseException withOffsetFailed) {
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException bareFailed) {
                throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                        name + " 必须是 ISO-8601 日期时间（如 2026-07-17T09:30:00 或 2026-07-17T09:30:00+08:00）: " + value);
            }
        }
    }

    private LocalDate parseOptionalDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR,
                    name + " 必须是 YYYY-MM-DD: " + value);
        }
    }

    /** 范围上限校验：日 K 按自然日，分钟按自然日；1M 需权威日历交易日（无日历用周末回退）。 */
    private void validateRange(MarketDataAssetSeriesQueryDTO query, String marketCode) {
        if (query.isDaily()) {
            long days = ChronoUnit.DAYS.between(query.fromDate(), query.toDate()) + 1;
            if (days > MarketDataAssetConstants.INTERVAL_MAX_NATURAL_DAYS.get(WorkbenchConstants.INTERVAL_1D)) {
                throw new BusinessException(ErrorCodeEnum.MARKET_DATA_ASSET_RANGE_TOO_LARGE, "日 K 最多查询 10 年");
            }
            return;
        }
        if (WorkbenchConstants.INTERVAL_1M.equals(query.interval())) {
            long tradingDays = countTradingDays(marketCode,
                    query.fromTime().toLocalDate(), query.toTime().toLocalDate());
            if (tradingDays > MarketDataAssetConstants.INTERVAL_1M_MAX_TRADING_DAYS) {
                throw new BusinessException(ErrorCodeEnum.MARKET_DATA_ASSET_RANGE_TOO_LARGE,
                        "1M 粒度最多查询 " + MarketDataAssetConstants.INTERVAL_1M_MAX_TRADING_DAYS + " 个交易日");
            }
            return;
        }
        long days = ChronoUnit.DAYS.between(query.fromTime().toLocalDate(), query.toTime().toLocalDate()) + 1;
        Integer maxDays = MarketDataAssetConstants.INTERVAL_MAX_NATURAL_DAYS.get(query.interval());
        if (maxDays != null && days > maxDays) {
            throw new BusinessException(ErrorCodeEnum.MARKET_DATA_ASSET_RANGE_TOO_LARGE,
                    query.interval() + " 粒度最多查询 " + maxDays + " 个自然日");
        }
    }

    /** 优先权威日历，缺失时按周末规则回退（早停，最多统计到上限+1）。 */
    private long countTradingDays(String marketCode, LocalDate from, LocalDate to) {
        List<MarketCalendarDO> rows = calendarMapper.selectByRange(marketCode, from, to, null);
        if (rows != null && !rows.isEmpty()) {
            return rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsTradingDay())).count();
        }
        long weekdays = 0;
        long total = ChronoUnit.DAYS.between(from, to);
        for (long i = 0; i <= total; i++) {
            DayOfWeek day = from.plusDays(i).getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                    && ++weekdays > MarketDataAssetConstants.INTERVAL_1M_MAX_TRADING_DAYS) {
                return weekdays;
            }
        }
        return weekdays;
    }

    // ==================== series 辅助 ====================

    /** 组合存在性：过滤 availability 聚合结果，空说明该证券没有该组合。 */
    private StockBarAvailabilityDO findComboRow(String canonicalSymbol, MarketDataAssetSeriesQueryDTO query) {
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

    private MarketDataAssetSeriesVO.Availability toSeriesAvailability(StockBarAvailabilityDO row,
                                                                      MarketDataWatermarkDO watermark,
                                                                      boolean daily) {
        String firstBarTime = daily ? dateText(row.getFirstBarDate()) : formatStoredTime(row.getFirstBarTime());
        String lastBarTime = daily ? dateText(row.getLastBarDate()) : formatStoredTime(row.getLastBarTime());
        return new MarketDataAssetSeriesVO.Availability(firstBarTime, lastBarTime,
                formatStoredTime(row.getLatestFetchedAt()), watermarkTimeOf(watermark));
    }

    private MarketDataAssetSeriesVO.Bar toDailyBar(StockDailyBarDO row) {
        return new MarketDataAssetSeriesVO.Bar(
                row.getTradeDate().toString(),
                priceText(row.getOpenPrice()), priceText(row.getHighPrice()), priceText(row.getLowPrice()),
                priceText(row.getClosePrice()),
                row.getVolume(), priceText(row.getAmount()),
                null, formatStoredTime(row.getFetchedAt()));
    }

    private MarketDataAssetSeriesVO.Bar toMinuteBar(StockMinuteBarDO row) {
        return new MarketDataAssetSeriesVO.Bar(
                formatStoredTime(row.getBarStartTime()),
                priceText(row.getOpenPrice()), priceText(row.getHighPrice()), priceText(row.getLowPrice()),
                priceText(row.getClosePrice()),
                row.getVolume(), priceText(row.getAmount()),
                row.getQualityStatus(), formatStoredTime(row.getFetchedAt()));
    }

    /** 区间摘要计算用最小投影（避免从 VO 字符串回解析）。 */
    private record BarPoint(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                            Long volume, BigDecimal amount) {
    }

    private BarPoint toDailyPoint(StockDailyBarDO row) {
        return new BarPoint(row.getOpenPrice(), row.getHighPrice(), row.getLowPrice(), row.getClosePrice(),
                row.getVolume(), row.getAmount());
    }

    private BarPoint toMinutePoint(StockMinuteBarDO row) {
        return new BarPoint(row.getOpenPrice(), row.getHighPrice(), row.getLowPrice(), row.getClosePrice(),
                row.getVolume(), row.getAmount());
    }

    private MarketDataAssetSeriesVO.Summary buildSummary(List<BarPoint> points) {
        if (points.isEmpty()) {
            return new MarketDataAssetSeriesVO.Summary(null, null, null, null, null, null, 0L, null, 0);
        }
        BigDecimal firstOpen = points.get(0).open();
        BigDecimal lastClose = points.get(points.size() - 1).close();
        BigDecimal absoluteChange = null;
        BigDecimal changeRate = null;
        if (lastClose != null) {
            BigDecimal base = firstOpen == null ? BigDecimal.ZERO : firstOpen;
            absoluteChange = lastClose.subtract(base);
            if (base.signum() != 0) {
                changeRate = absoluteChange.divide(base, 10, RoundingMode.HALF_UP);
            }
        }
        BigDecimal highest = null;
        BigDecimal lowest = null;
        for (BarPoint point : points) {
            if (point.high() != null && (highest == null || point.high().compareTo(highest) > 0)) {
                highest = point.high();
            }
            if (point.low() != null && (lowest == null || point.low().compareTo(lowest) < 0)) {
                lowest = point.low();
            }
        }
        long totalVolume = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (BarPoint point : points) {
            totalVolume += point.volume() == null ? 0L : point.volume();
            if (point.amount() != null) {
                totalAmount = totalAmount.add(point.amount());
            }
        }
        return new MarketDataAssetSeriesVO.Summary(
                priceText(firstOpen), priceText(lastClose), priceText(absoluteChange), priceText(changeRate),
                priceText(highest), priceText(lowest), totalVolume, priceText(totalAmount), points.size());
    }

    /**
     * 质量：CN 有权威日历可算 VERIFIED/PARTIAL 与缺失数；HK/US 或日历未就绪返回 UNKNOWN。
     * expected = 交易日 × 每日 bars（日 K 为 1，分钟按会话窗口折算）；suspect 来自分钟 bar 的 quality_status。
     */
    private MarketDataAssetSeriesVO.Quality buildQuality(MarketDataAssetSeriesQueryDTO query, String market,
                                                         boolean truncated, int actualBarCount,
                                                         int suspectBarCount) {
        List<String> reasonCodes = new ArrayList<>();
        if (truncated) {
            reasonCodes.add("TRUNCATED");
        }
        if (suspectBarCount > 0) {
            reasonCodes.add("SUSPECT_BARS");
        }
        if ("HK".equals(market) || "US".equals(market)) {
            return new MarketDataAssetSeriesVO.Quality("UNKNOWN", actualBarCount, null, null,
                    suspectBarCount, truncated, reasonCodes);
        }
        String marketCode = marketCodeOf(market);
        LocalDate from = query.isDaily() ? query.fromDate() : query.fromTime().toLocalDate();
        LocalDate to = query.isDaily() ? query.toDate() : query.toTime().toLocalDate();
        List<MarketCalendarDO> calendarRows = calendarMapper.selectByRange(marketCode, from, to, null);
        if (calendarRows == null || calendarRows.isEmpty()) {
            return new MarketDataAssetSeriesVO.Quality("UNKNOWN", actualBarCount, null, null,
                    suspectBarCount, truncated, reasonCodes);
        }
        long tradingDays = calendarRows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getIsTradingDay())).count();
        long expected = tradingDays * (query.isDaily() ? 1 : barsPerDay(query.interval(), marketCode));
        long missing = Math.max(0, expected - actualBarCount);
        if (missing > 0) {
            reasonCodes.add("MISSING_BARS");
        }
        return new MarketDataAssetSeriesVO.Quality(missing == 0 ? "VERIFIED" : "PARTIAL",
                actualBarCount, (int) expected, (int) missing, suspectBarCount, truncated, reasonCodes);
    }

    /** 每个交易日该粒度应有的 bars 数：按连续竞价会话窗口折算（窗口为 HHMM，先换算分钟数）。 */
    private int barsPerDay(String interval, String marketCode) {
        int minutes = intervalMinutes(interval);
        int bars = 0;
        for (int[] session : tradingSessionManager.getSessionWindows(marketCode, false)) {
            bars += (hhmmToMinutes(session[1]) - hhmmToMinutes(session[0])) / minutes;
        }
        return Math.max(bars, 1);
    }

    /** HHMM 整数 → 当日分钟数：930 → 570，1130 → 690，1300 → 780，1500 → 900。 */
    private int hhmmToMinutes(int hhmm) {
        return (hhmm / 100) * 60 + (hhmm % 100);
    }

    private int intervalMinutes(String interval) {
        return switch (interval) {
            case "1M" -> 1;
            case "5M" -> 5;
            case "15M" -> 15;
            case "30M" -> 30;
            case "60M" -> 60;
            default -> 1;
        };
    }

    // ==================== related-tasks 辅助 ====================

    private Map<Long, MarketDataSyncPlanDO> loadPlansById() {
        Map<Long, MarketDataSyncPlanDO> byId = new HashMap<>();
        List<MarketDataSyncPlanDO> plans = syncPlanMapper.selectByFilter(null, null, null, PLAN_FETCH_LIMIT, 0);
        if (plans != null) {
            for (MarketDataSyncPlanDO plan : plans) {
                byId.put(plan.getId(), plan);
            }
        }
        return byId;
    }

    /** scope_json 结构化解析 + 兜底子串匹配是否覆盖该证券。 */
    private boolean scopeContainsSymbol(String scopeJson, String canonicalSymbol) {
        if (scopeJson == null || scopeJson.isBlank()) {
            return false;
        }
        if (scopeJson.contains(canonicalSymbol)) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(scopeJson);
            JsonNode symbolNode = root.get("canonicalSymbol");
            if (symbolNode != null && canonicalSymbol.equals(symbolNode.asText())) {
                return true;
            }
            JsonNode symbols = root.get("symbols");
            if (symbols != null && symbols.isArray()) {
                for (JsonNode node : symbols) {
                    if (canonicalSymbol.equals(node.asText())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesInterval(String actual, String filter) {
        return filter == null || filter.isBlank() || filter.equals(actual);
    }

    private boolean planOverlapsRange(MarketDataSyncPlanDO plan, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        LocalDate planStart = scopeDate(plan.getScopeJson(), "startDate");
        LocalDate planEnd = scopeDate(plan.getScopeJson(), "endDate");
        if (planStart == null && planEnd == null) {
            return true;
        }
        return rangesOverlap(planStart, planEnd, from, to);
    }

    private boolean itemOverlapsRange(MarketDataSyncTaskItemDO item, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        LocalDateTime started = item.getStartedAt() != null ? item.getStartedAt() : item.getCreatedAt();
        if (started == null) {
            return false;
        }
        LocalDate day = started.toLocalDate();
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }

    /** 两个（可能无界）闭区间是否有重叠：任一区间端点为 null 表示无界。 */
    private boolean rangesOverlap(LocalDate a, LocalDate b, LocalDate c, LocalDate d) {
        boolean strictlyBefore = (b != null && c != null && b.isBefore(c))
                || (d != null && a != null && d.isBefore(a));
        return !strictlyBefore;
    }

    /** 从 scope_json 取日期字段（容错：解析失败返回 null）。 */
    private LocalDate scopeDate(String scopeJson, String field) {
        if (scopeJson == null || scopeJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(scopeJson).get(field);
            if (node == null || node.isNull()) {
                return null;
            }
            return LocalDate.parse(node.asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    private MarketDataAssetRelatedTasksVO.RelatedTaskItem toPlanItem(MarketDataSyncPlanDO plan) {
        return new MarketDataAssetRelatedTasksVO.RelatedTaskItem(
                "PLAN",
                plan.getId(),
                plan.getPlanName() != null ? plan.getPlanName() : ("采集计划 #" + plan.getId()),
                plan.getTaskType(),
                plan.getIntervalType(),
                Boolean.TRUE.equals(plan.getEnabled()) ? "ENABLED" : "DISABLED",
                dateText(scopeDate(plan.getScopeJson(), "startDate")),
                dateText(scopeDate(plan.getScopeJson(), "endDate")),
                formatStoredTime(plan.getLastRunAt()),
                null, null, null);
    }

    private MarketDataAssetRelatedTasksVO.RelatedTaskItem toRunItem(MarketDataSyncTaskItemDO item,
                                                                    MarketDataSyncPlanDO plan) {
        String name = plan != null && plan.getPlanName() != null
                ? plan.getPlanName() + "（记录 #" + item.getId() + "）"
                : ("采集记录 #" + item.getId());
        return new MarketDataAssetRelatedTasksVO.RelatedTaskItem(
                "RUN",
                item.getId(),
                name,
                plan != null ? plan.getTaskType() : null,
                plan != null ? plan.getIntervalType() : null,
                item.getStatus(),
                dateText(scopeDate(item.getScopeDetail(), "startDate")),
                dateText(scopeDate(item.getScopeDetail(), "endDate")),
                formatStoredTime(item.getStartedAt()),
                formatStoredTime(item.getFinishedAt()),
                item.getErrorCode(),
                item.getErrorMessage());
    }

    // ==================== 证券元信息 ====================

    private StockBasicDO loadSecurity(String canonicalSymbol) {
        StockBasicDO security = stockBasicMapper.selectByCanonicalSymbol(canonicalSymbol);
        if (security == null) {
            throw new MarketDataAssetNotFoundException(canonicalSymbol);
        }
        return security;
    }

    private MarketDataAssetSecurityVO toSecurityVO(StockBasicDO security) {
        String market = marketOf(security);
        return new MarketDataAssetSecurityVO(
                security.getCanonicalSymbol(),
                displayNameOf(security),
                market,
                currencyOf(security, market),
                timeZoneOf(market));
    }

    /** 市场代码：优先 stock_basic.market，缺失时按 canonical 前缀回退。 */
    private String marketOf(StockBasicDO security) {
        if (security.getMarket() != null && !security.getMarket().isBlank()) {
            return security.getMarket();
        }
        int separator = security.getCanonicalSymbol().indexOf('.');
        return separator > 0 ? security.getCanonicalSymbol().substring(0, separator) : "SH";
    }

    private String displayNameOf(StockBasicDO security) {
        for (String value : List.of(
                Objects.requireNonNullElse(security.getName(), ""),
                Objects.requireNonNullElse(security.getNameCn(), ""),
                Objects.requireNonNullElse(security.getNameHk(), ""),
                Objects.requireNonNullElse(security.getNameEn(), ""),
                security.getCanonicalSymbol())) {
            if (!value.isBlank()) {
                return value;
            }
        }
        return security.getCanonicalSymbol();
    }

    private String currencyOf(StockBasicDO security, String market) {
        if (security.getCurrency() != null && !security.getCurrency().isBlank()) {
            return security.getCurrency();
        }
        return switch (market) {
            case "HK" -> "HKD";
            case "US" -> "USD";
            default -> "CNY";
        };
    }

    private String timeZoneOf(String market) {
        return switch (market) {
            case "HK" -> "Asia/Hong_Kong";
            case "US" -> "America/New_York";
            default -> "Asia/Shanghai";
        };
    }

    private String marketCodeOf(String market) {
        return switch (market) {
            case "HK" -> "HK";
            case "US" -> "US";
            default -> WorkbenchConstants.MARKET_CN_A;
        };
    }

    // ==================== 组合构建 ====================

    private MarketDataAssetCombinationVO toDailyCombination(StockBarAvailabilityDO row,
                                                            MarketDataWatermarkDO watermark) {
        return new MarketDataAssetCombinationVO(
                WorkbenchConstants.INTERVAL_1D,
                row.getDataSource(),
                row.getAdjustType(),
                row.getBarCount(),
                dateText(row.getFirstBarDate()),
                dateText(row.getLastBarDate()),
                formatStoredTime(row.getLatestFetchedAt()),
                watermarkTimeOf(watermark));
    }

    private MarketDataAssetCombinationVO toMinuteCombination(StockBarAvailabilityDO row,
                                                             MarketDataWatermarkDO watermark) {
        return new MarketDataAssetCombinationVO(
                row.getIntervalType(),
                row.getDataSource(),
                row.getAdjustType(),
                row.getBarCount(),
                formatStoredTime(row.getFirstBarTime()),
                formatStoredTime(row.getLastBarTime()),
                formatStoredTime(row.getLatestFetchedAt()),
                watermarkTimeOf(watermark));
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
            return formatStoredTime(watermark.getLastBarTime());
        }
        if (watermark.getLastTradeDate() != null) {
            return watermark.getLastTradeDate().toString();
        }
        return null;
    }

    // ==================== 时间/数值格式化 ====================

    /** 分钟/抓取时间：按存储时区附加 offset，输出秒级 ISO-8601（如 2026-07-17T09:30:00+08:00）。 */
    private String formatStoredTime(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        ZoneOffset offset = STORAGE_ZONE.getRules().getOffset(time);
        return ISO_WITH_OFFSET.format(time.atOffset(offset));
    }

    private String dateText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /** BigDecimal → 去尾零十进制字符串；null 原样返回。 */
    private String priceText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    // ==================== 参数 ====================

    private String normalize(String raw) {
        try {
            return CanonicalSymbolUtils.normalize(raw);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL, exception.getMessage());
        }
    }
}
