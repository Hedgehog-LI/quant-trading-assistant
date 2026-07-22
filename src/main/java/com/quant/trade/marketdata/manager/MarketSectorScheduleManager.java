package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.constant.MarketSectorCollectionConstants;
import com.quant.trade.marketdata.model.MarketSectorRankingConfigDO;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** 按各市场时区和交易窗口计算板块排行采集时间桶。 */
@Component
public class MarketSectorScheduleManager {

    public Optional<CollectionWindow> nextWindow(MarketSectorRankingConfigDO config, Instant instant,
                                                  ZoneId storageZone) {
        MarketSchedule schedule = schedule(config.getMarketCode());
        ZonedDateTime marketNow = instant.atZone(schedule.zoneId());
        if (marketNow.getDayOfWeek() == DayOfWeek.SATURDAY || marketNow.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return Optional.empty();
        }
        int interval = config.getIntradayIntervalMinutes() == null ? 0 : config.getIntradayIntervalMinutes();
        if (interval > 0) {
            Optional<LocalDateTime> bucket = intradayBucket(schedule, marketNow, interval, storageZone);
            if (bucket.isPresent()) {
                LocalDateTime storedBucket = bucket.get();
                if (config.getLastIntradayAt() == null || config.getLastIntradayAt().isBefore(storedBucket)) {
                    return Optional.of(new CollectionWindow(MarketSectorCollectionConstants.SNAPSHOT_INTRADAY,
                            marketNow.toLocalDate(), storedBucket));
                }
                return Optional.empty();
            }
        }
        if (Boolean.TRUE.equals(config.getCloseSnapshotEnabled())
                && !marketNow.toLocalTime().isBefore(schedule.close().plusMinutes(schedule.closeDelayMinutes()))
                && !marketNow.toLocalDate().equals(config.getLastCloseTradeDate())) {
            ZonedDateTime bucket = ZonedDateTime.of(marketNow.toLocalDate(), schedule.close(), schedule.zoneId());
            return Optional.of(new CollectionWindow(MarketSectorCollectionConstants.SNAPSHOT_CLOSE,
                    marketNow.toLocalDate(), LocalDateTime.ofInstant(bucket.toInstant(), storageZone)));
        }
        return Optional.empty();
    }

    public LocalDate tradeDate(String market, Instant instant) {
        return instant.atZone(schedule(market).zoneId()).toLocalDate();
    }

    public boolean isMarketOpen(String market, Instant instant) {
        MarketSchedule schedule = schedule(market);
        ZonedDateTime marketNow = instant.atZone(schedule.zoneId());
        if (marketNow.getDayOfWeek() == DayOfWeek.SATURDAY || marketNow.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        return schedule.sessions().stream().anyMatch(session -> session.contains(marketNow.toLocalTime()));
    }

    /** 返回当前交易窗口内、以该窗口开市时间为锚点的采集桶。 */
    public Optional<LocalDateTime> intradayBucket(String market, int intervalMinutes, Instant instant,
                                                   ZoneId storageZone) {
        if (intervalMinutes <= 0) {
            return Optional.empty();
        }
        MarketSchedule schedule = schedule(market);
        ZonedDateTime marketNow = instant.atZone(schedule.zoneId());
        if (marketNow.getDayOfWeek() == DayOfWeek.SATURDAY || marketNow.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return Optional.empty();
        }
        return intradayBucket(schedule, marketNow, intervalMinutes, storageZone);
    }

    public LocalDateTime manualBucket(Instant instant, ZoneId storageZone) {
        return LocalDateTime.ofInstant(instant, storageZone).truncatedTo(ChronoUnit.MINUTES);
    }

    private Optional<LocalDateTime> intradayBucket(MarketSchedule schedule, ZonedDateTime marketNow,
                                                    int intervalMinutes, ZoneId storageZone) {
        LocalTime now = marketNow.toLocalTime();
        for (Session session : schedule.sessions()) {
            if (session.contains(now)) {
                long elapsed = Duration.between(session.start(), now).toMinutes();
                LocalTime bucketTime = session.start().plusMinutes(elapsed / intervalMinutes * intervalMinutes);
                ZonedDateTime bucket = ZonedDateTime.of(marketNow.toLocalDate(), bucketTime, schedule.zoneId());
                return Optional.of(LocalDateTime.ofInstant(bucket.toInstant(), storageZone));
            }
        }
        return Optional.empty();
    }

    private MarketSchedule schedule(String market) {
        return switch (market) {
            case MarketSectorCollectionConstants.MARKET_CN -> new MarketSchedule(ZoneId.of("Asia/Shanghai"),
                    List.of(new Session(LocalTime.of(9, 15), LocalTime.of(9, 25), true),
                            new Session(LocalTime.of(9, 30), LocalTime.of(11, 30), false),
                            new Session(LocalTime.of(13, 0), LocalTime.of(15, 0), false)),
                    LocalTime.of(15, 0), 5);
            case MarketSectorCollectionConstants.MARKET_HK -> new MarketSchedule(ZoneId.of("Asia/Hong_Kong"),
                    List.of(new Session(LocalTime.of(9, 30), LocalTime.NOON, false),
                            new Session(LocalTime.of(13, 0), LocalTime.of(16, 0), false)),
                    LocalTime.of(16, 0), 15);
            case MarketSectorCollectionConstants.MARKET_US -> new MarketSchedule(ZoneId.of("America/New_York"),
                    List.of(new Session(LocalTime.of(9, 30), LocalTime.of(16, 0), false)),
                    LocalTime.of(16, 0), 10);
            default -> throw new IllegalArgumentException("不支持的市场: " + market);
        };
    }

    public record CollectionWindow(String snapshotType, LocalDate tradeDate, LocalDateTime bucketTime) {}
    private record MarketSchedule(ZoneId zoneId, List<Session> sessions, LocalTime close,
                                  int closeDelayMinutes) {}

    private record Session(LocalTime start, LocalTime end, boolean includeEndMinute) {
        boolean contains(LocalTime time) {
            if (time.isBefore(start)) {
                return false;
            }
            if (time.isBefore(end)) {
                return true;
            }
            return includeEndMinute && time.getHour() == end.getHour() && time.getMinute() == end.getMinute();
        }
    }
}
