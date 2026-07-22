package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.constant.MarketSectorCollectionConstants;
import com.quant.trade.marketdata.model.MarketSectorRankingConfigDO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class MarketSectorScheduleManagerTest {
    private final MarketSectorScheduleManager manager = new MarketSectorScheduleManager();
    private final ZoneId storageZone = ZoneId.of("Asia/Shanghai");

    @Test
    void cnIntradayAlignsToSelectedTenMinuteBucket() {
        MarketSectorRankingConfigDO config = config("CN", 10, true);

        var window = manager.nextWindow(config, Instant.parse("2026-07-22T01:47:00Z"), storageZone);

        assertTrue(window.isPresent());
        assertEquals(MarketSectorCollectionConstants.SNAPSHOT_INTRADAY, window.get().snapshotType());
        assertEquals(LocalDateTime.of(2026, 7, 22, 9, 40), window.get().bucketTime());
    }

    @Test
    void cnCollectsOpeningAuctionButSkipsGapBeforeContinuousTrading() {
        MarketSectorRankingConfigDO config = config("CN", 5, true);

        assertTrue(manager.nextWindow(config, Instant.parse("2026-07-22T01:14:00Z"), storageZone).isEmpty());
        var auctionStart = manager.nextWindow(config, Instant.parse("2026-07-22T01:15:10Z"), storageZone);
        assertTrue(auctionStart.isPresent());
        assertEquals(LocalDateTime.of(2026, 7, 22, 9, 15), auctionStart.get().bucketTime());

        config.setLastIntradayAt(LocalDateTime.of(2026, 7, 22, 9, 20));
        var auctionClose = manager.nextWindow(config, Instant.parse("2026-07-22T01:25:30Z"), storageZone);
        assertTrue(auctionClose.isPresent());
        assertEquals(LocalDateTime.of(2026, 7, 22, 9, 25), auctionClose.get().bucketTime());

        config.setLastIntradayAt(LocalDateTime.of(2026, 7, 22, 9, 25));
        assertTrue(manager.nextWindow(config, Instant.parse("2026-07-22T01:26:00Z"), storageZone).isEmpty());
        var continuousOpen = manager.nextWindow(config, Instant.parse("2026-07-22T01:30:00Z"), storageZone);
        assertTrue(continuousOpen.isPresent());
        assertEquals(LocalDateTime.of(2026, 7, 22, 9, 30), continuousOpen.get().bucketTime());
    }

    @Test
    void watchBucketUsesEachSessionStartAsFrequencyAnchor() {
        var firstBucket = manager.intradayBucket("CN", 60,
                Instant.parse("2026-07-22T01:31:00Z"), storageZone);
        var nextBucket = manager.intradayBucket("CN", 60,
                Instant.parse("2026-07-22T02:30:00Z"), storageZone);

        assertEquals(LocalDateTime.of(2026, 7, 22, 9, 30), firstBucket.orElseThrow());
        assertEquals(LocalDateTime.of(2026, 7, 22, 10, 30), nextBucket.orElseThrow());
    }

    @Test
    void usUsesNewYorkSessionInsteadOfCnSession() {
        MarketSectorRankingConfigDO config = config("US", 15, true);

        var window = manager.nextWindow(config, Instant.parse("2026-07-22T14:52:00Z"), storageZone);

        assertTrue(window.isPresent());
        assertEquals(LocalDate.of(2026, 7, 22), window.get().tradeDate());
        assertEquals(LocalDateTime.of(2026, 7, 22, 22, 45), window.get().bucketTime());
    }

    @Test
    void hkCloseWaitsForClosingAuctionAndRunsOnlyOnce() {
        MarketSectorRankingConfigDO config = config("HK", 0, true);

        assertTrue(manager.nextWindow(config, Instant.parse("2026-07-22T08:05:00Z"), storageZone).isEmpty());
        var window = manager.nextWindow(config, Instant.parse("2026-07-22T08:15:00Z"), storageZone);

        assertTrue(window.isPresent());
        assertEquals(MarketSectorCollectionConstants.SNAPSHOT_CLOSE, window.get().snapshotType());
        assertEquals(LocalDateTime.of(2026, 7, 22, 16, 0), window.get().bucketTime());
        config.setLastCloseTradeDate(LocalDate.of(2026, 7, 22));
        assertTrue(manager.nextWindow(config, Instant.parse("2026-07-22T08:20:00Z"), storageZone).isEmpty());
    }

    @Test
    void cnIntradayStopsAtCloseAndOnlyCloseSnapshotRemains() {
        MarketSectorRankingConfigDO config = config("CN", 5, true);

        assertTrue(manager.nextWindow(config, Instant.parse("2026-07-22T07:01:00Z"), storageZone).isEmpty());
        var close = manager.nextWindow(config, Instant.parse("2026-07-22T07:05:00Z"), storageZone);
        assertTrue(close.isPresent());
        assertEquals(MarketSectorCollectionConstants.SNAPSHOT_CLOSE, close.get().snapshotType());
        assertEquals(LocalDateTime.of(2026, 7, 22, 15, 0), close.get().bucketTime());

        config.setLastCloseTradeDate(LocalDate.of(2026, 7, 22));
        assertTrue(manager.nextWindow(config, Instant.parse("2026-07-22T07:10:00Z"), storageZone).isEmpty());
        assertFalse(manager.isMarketOpen("CN", Instant.parse("2026-07-22T07:10:00Z")));
    }

    @Test
    void weekendDoesNotRun() {
        MarketSectorRankingConfigDO config = config("CN", 5, true);
        assertFalse(manager.nextWindow(config, Instant.parse("2026-07-25T02:00:00Z"), storageZone).isPresent());
    }

    private MarketSectorRankingConfigDO config(String market, int interval, boolean closeEnabled) {
        return MarketSectorRankingConfigDO.builder().marketCode(market)
                .intradayIntervalMinutes(interval).closeSnapshotEnabled(closeEnabled).build();
    }
}
