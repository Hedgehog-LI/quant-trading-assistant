package com.quant.trade.marketdata.service;

import com.quant.trade.marketdata.dao.MarketSectorRankingBatchMapper;
import com.quant.trade.marketdata.dao.MarketSectorRankingConfigMapper;
import com.quant.trade.marketdata.dao.MarketSectorRankingItemMapper;
import com.quant.trade.marketdata.manager.MarketSectorRankingPersistenceManager;
import com.quant.trade.marketdata.manager.MarketSectorScheduleManager;
import com.quant.trade.marketdata.model.MarketSectorRankingBatchDO;
import com.quant.trade.marketdata.model.MarketSectorRankingConfigDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketSectorRankingServiceTest {
    @Mock MarketSectorRankingConfigMapper configMapper;
    @Mock MarketSectorRankingBatchMapper batchMapper;
    @Mock MarketSectorRankingItemMapper itemMapper;
    @Mock MarketSectorCatalogService catalogService;
    @Mock MarketSectorRankingPersistenceManager persistenceManager;

    @Test
    void existingBucketRepairsSuccessWatermarkWithoutCallingProvider() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        MarketSectorRankingConfigDO config = MarketSectorRankingConfigDO.builder().id(1L)
                .providerCode("LONGPORT").marketCode("CN").rankLimit(100).build();
        MarketSectorRankingBatchDO existing = MarketSectorRankingBatchDO.builder().id(9L)
                .providerCode("LONGPORT").marketCode("CN").tradeDate(LocalDate.of(2026, 7, 22))
                .snapshotType("MANUAL").snapshotBucketTime(LocalDateTime.of(2026, 7, 22, 10, 0))
                .snapshotTime(LocalDateTime.of(2026, 7, 22, 10, 0)).build();
        when(configMapper.selectByMarket("CN")).thenReturn(config);
        when(configMapper.tryClaim(eq(1L), anyString(), any(), any())).thenReturn(1);
        when(batchMapper.selectByBucket("LONGPORT", "CN", "MANUAL",
                LocalDateTime.of(2026, 7, 22, 10, 0))).thenReturn(existing);
        MarketSectorRankingService service = new MarketSectorRankingService(configMapper, batchMapper, itemMapper,
                catalogService, persistenceManager, new MarketSectorScheduleManager(), clock);

        service.collectNow("CN");

        verify(configMapper).markSuccess(eq(1L), eq("MANUAL"), any(),
                eq(LocalDate.of(2026, 7, 22)), anyString());
        verifyNoInteractions(catalogService, persistenceManager);
    }
}
