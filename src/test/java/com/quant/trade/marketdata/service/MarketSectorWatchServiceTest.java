package com.quant.trade.marketdata.service;

import com.quant.trade.marketdata.dao.MarketSectorMemberSnapshotMapper;
import com.quant.trade.marketdata.dao.MarketSectorSnapshotMapper;
import com.quant.trade.marketdata.dao.MarketSectorWatchMapper;
import com.quant.trade.marketdata.manager.MarketSectorPersistenceManager;
import com.quant.trade.marketdata.model.MarketSectorSnapshotDO;
import com.quant.trade.marketdata.model.MarketSectorWatchDO;
import com.quant.trade.marketdata.provider.MarketSectorProvider;
import com.quant.trade.marketdata.vo.MarketSectorPeerVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketSectorWatchServiceTest {

    @Mock MarketSectorWatchMapper watchMapper;
    @Mock MarketSectorSnapshotMapper snapshotMapper;
    @Mock MarketSectorMemberSnapshotMapper memberMapper;
    @Mock MarketSectorCatalogService catalogService;
    @Mock MarketSectorProvider provider;
    @Mock MarketSectorPersistenceManager persistenceManager;

    @Test
    void refreshAggregatesCapitalAndPersistsConstituentSnapshot() {
        MarketSectorWatchDO watch = MarketSectorWatchDO.builder().id(7L).providerCode("LONGPORT")
                .providerSectorId("BK/SH/IN40159").marketCode("CN").sectorName("综合油气公司")
                .enabled(true).build();
        when(watchMapper.selectById(7L)).thenReturn(watch);
        when(catalogService.getIndustryPeers("CN", "BK/SH/IN40159"))
                .thenReturn(new MarketSectorPeerVO("CN", "能源", "综合油气公司", "BK/SH/IN40159",
                        2, new BigDecimal("0.024"), new BigDecimal("0.115"), false, "LONGPORT"));
        when(provider.getIndustryConstituents("BK/SH/IN40159")).thenReturn(
                new MarketSectorProvider.SectorConstituents(1, 1, 0, List.of(
                        stock("SH.601857", "中国石油", "0.03", "281", "2693", "265"),
                        stock("SH.600028", "中国石化", "-0.01", "177", "1720", "341")), "LONGPORT"));
        MarketSectorWatchService service = new MarketSectorWatchService(watchMapper, snapshotMapper,
                memberMapper, catalogService, provider, persistenceManager,
                Clock.fixed(Instant.parse("2026-07-18T02:00:00Z"), ZoneOffset.UTC));

        service.refresh(7L);

        ArgumentCaptor<MarketSectorSnapshotDO> captor = ArgumentCaptor.forClass(MarketSectorSnapshotDO.class);
        verify(persistenceManager).appendSnapshot(captor.capture(), anyList());
        assertEquals(new BigDecimal("458"), captor.getValue().getTotalNetInflow());
        assertEquals(new BigDecimal("4413"), captor.getValue().getTotalTurnoverAmount());
        assertEquals("SH.601857", captor.getValue().getLeadingSymbol());
    }

    private MarketSectorProvider.SectorConstituent stock(String symbol, String name, String change,
                                                           String inflow, String turnover, String volume) {
        return new MarketSectorProvider.SectorConstituent(symbol, name, BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal(change), new BigDecimal(inflow), new BigDecimal(turnover), new BigDecimal(volume),
                null, null, "", 108, false);
    }
}
