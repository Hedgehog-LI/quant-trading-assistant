package com.quant.trade.marketdata.service;

import com.quant.trade.marketdata.dao.MarketSectorRankingConfigMapper;
import com.quant.trade.marketdata.dao.MarketSectorWatchMapper;
import com.quant.trade.marketdata.manager.MarketSectorScheduleManager;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.analysis.service.SectorAnalyticsCalculationService;
import com.quant.trade.marketdata.model.MarketSectorRankingConfigDO;
import com.quant.trade.marketdata.model.MarketSectorWatchDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketSectorCollectionSchedulerTest {
    @Mock MarketSectorRankingConfigMapper configMapper;
    @Mock MarketSectorWatchMapper watchMapper;
    @Mock MarketSectorRankingService rankingService;
    @Mock MarketSectorWatchService watchService;
    @Mock TradingSessionManager tradingSessionManager;
    @Mock SectorAnalyticsCalculationService calculationService;

    @Test
    void afterCloseDoesNotRunPeriodicRankingOrWatchCollection() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T07:01:00Z"), ZoneId.of("Asia/Shanghai"));
        MarketSectorRankingConfigDO config = MarketSectorRankingConfigDO.builder().id(1L)
                .marketCode("CN").intradayIntervalMinutes(5).closeSnapshotEnabled(false).build();
        MarketSectorWatchDO watch = MarketSectorWatchDO.builder().id(2L).marketCode("CN")
                .autoCollectEnabled(true).collectIntervalMinutes(5).build();
        when(configMapper.selectRunnable(any())).thenReturn(List.of(config));
        when(watchMapper.selectAutoRunnable(any())).thenReturn(List.of(watch));
        when(tradingSessionManager.isTradingDay("CN_A", java.time.LocalDate.of(2026, 7, 22))).thenReturn(true);
        MarketSectorCollectionScheduler scheduler = new MarketSectorCollectionScheduler(configMapper, watchMapper,
                rankingService, watchService, new MarketSectorScheduleManager(), tradingSessionManager,
                calculationService, clock);

        scheduler.scan();

        verifyNoInteractions(rankingService, watchService);
    }

    @Test
    void exchangeHolidayDoesNotRunRankingOrWatchCollection() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T01:30:00Z"), ZoneId.of("Asia/Shanghai"));
        MarketSectorRankingConfigDO config = MarketSectorRankingConfigDO.builder().id(1L)
                .marketCode("CN").intradayIntervalMinutes(5).closeSnapshotEnabled(true).build();
        MarketSectorWatchDO watch = MarketSectorWatchDO.builder().id(2L).marketCode("CN")
                .autoCollectEnabled(true).collectIntervalMinutes(5).build();
        when(configMapper.selectRunnable(any())).thenReturn(List.of(config));
        when(watchMapper.selectAutoRunnable(any())).thenReturn(List.of(watch));
        when(tradingSessionManager.isTradingDay("CN_A", java.time.LocalDate.of(2026, 7, 22))).thenReturn(false);
        MarketSectorCollectionScheduler scheduler = new MarketSectorCollectionScheduler(configMapper, watchMapper,
                rankingService, watchService, new MarketSectorScheduleManager(), tradingSessionManager,
                calculationService, clock);

        scheduler.scan();

        verifyNoInteractions(rankingService, watchService);
    }

    @Test
    void closeCollectionTriggersEverySupportedAnalyticsWindow() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T07:05:00Z"), ZoneId.of("Asia/Shanghai"));
        MarketSectorRankingConfigDO config = MarketSectorRankingConfigDO.builder().id(3L)
                .providerCode("LONGPORT").marketCode("CN").intradayIntervalMinutes(0)
                .closeSnapshotEnabled(true).build();
        when(configMapper.selectRunnable(any())).thenReturn(List.of(config));
        when(watchMapper.selectAutoRunnable(any())).thenReturn(List.of());
        when(tradingSessionManager.isTradingDay("CN_A", java.time.LocalDate.of(2026, 7, 22)))
                .thenReturn(true);
        MarketSectorCollectionScheduler scheduler = new MarketSectorCollectionScheduler(configMapper, watchMapper,
                rankingService, watchService, new MarketSectorScheduleManager(), tradingSessionManager,
                calculationService, clock);

        scheduler.scan();

        verify(rankingService).collectScheduled(any(), any());
        verify(calculationService).calculate("CN", java.time.LocalDate.of(2026, 7, 22), 5);
        verify(calculationService).calculate("CN", java.time.LocalDate.of(2026, 7, 22), 10);
        verify(calculationService).calculate("CN", java.time.LocalDate.of(2026, 7, 22), 20);
        verify(calculationService).calculate("CN", java.time.LocalDate.of(2026, 7, 22), 50);
    }
}
