package com.quant.trade.marketdata.asset.manager;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.asset.dto.MarketDataAssetSeriesQueryDTO;
import com.quant.trade.marketdata.constant.MarketDataAssetConstants;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.MarketCalendarMapper;
import com.quant.trade.marketdata.model.MarketCalendarDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * P1.9-A series 查询参数校验与范围解析。
 * <p>
 * 校验 interval/dataSource/adjustType 枚举、解析日/分钟时间、校验时间倒置与范围上限；
 * 分钟时间优先按带 offset 的 ISO-8601 折算到存储时区，否则按存储时区墙钟直接采用。
 */
@Component
@RequiredArgsConstructor
public class MarketDataAssetSeriesQueryParser {

    /** series 支持的数据源集合（与采集支持一致）。 */
    private static final Set<String> VALID_DATA_SOURCES = Set.of(
            MarketDataConstants.DATA_SOURCE_LONGPORT,
            MarketDataConstants.DATA_SOURCE_CSV,
            MarketDataConstants.DATA_SOURCE_MANUAL);

    private final MarketCalendarMapper calendarMapper;

    public MarketDataAssetSeriesQueryDTO parseAndValidate(String interval, String from, String to,
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

    public LocalDate requireDate(String value, String name) {
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

    /** 可选日期（related-tasks 使用）；空或非法时抛出校验错误。 */
    public LocalDate parseOptionalDate(String value, String name) {
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

    /** 范围上限校验：日 K 按自然日，1M 按权威日历交易日（无日历用周末回退），其余分钟按自然日。 */
    public void validateRange(MarketDataAssetSeriesQueryDTO query, String marketCode) {
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

    /** 数据实际存储时区：与采集链路 quote-time-zone / serverTimezone 保持一致。 */
    private static final java.time.ZoneId STORAGE_ZONE = java.time.ZoneId.of("Asia/Shanghai");
}
