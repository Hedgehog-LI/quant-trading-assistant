package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.marketdata.provider.MarketSectorProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketSectorCatalogServiceTest {

    @Test
    void returnsNormalizedIndustryRankAndPeer() {
        MarketSectorCatalogService service = new MarketSectorCatalogService(new StubProvider(true));

        var ranks = service.getIndustryRank("cn", "leading-gainer", "single", 20);
        assertEquals("CN", ranks.get(0).market());
        assertEquals("BK/SH/IN001", ranks.get(0).providerSectorId());

        var peer = service.getIndustryPeers("CN", "BK/SH/IN001");
        assertEquals(30, peer.stockCount());
        assertEquals("LONGPORT", peer.providerCode());
    }

    @Test
    void rejectsInvalidParametersBeforeCallingProvider() {
        MarketSectorCatalogService service = new MarketSectorCatalogService(new StubProvider(true));
        assertThrows(BusinessException.class,
                () -> service.getIndustryRank("JP", "leading-gainer", "single", 20));
        assertThrows(BusinessException.class,
                () -> service.getIndustryRank("CN", "money-flow", "single", 20));
        assertThrows(BusinessException.class,
                () -> service.getIndustryRank("CN", "leading-gainer", "single", 101));
        assertThrows(BusinessException.class,
                () -> service.getIndustryPeers("CN", "BK/US/IN001"));
    }

    @Test
    void reportsProviderUnavailableWithoutFakingEmptyResult() {
        MarketSectorCatalogService service = new MarketSectorCatalogService(new StubProvider(false));
        assertThrows(BusinessException.class,
                () -> service.getIndustryRank("CN", "leading-gainer", "single", 20));
    }

    private record StubProvider(boolean configured) implements MarketSectorProvider {
        @Override public String getProviderCode() { return "LONGPORT"; }
        @Override public boolean isConfigured() { return configured; }

        @Override
        public List<SectorRank> getIndustryRank(String market, String indicator, String sortType, int limit) {
            String segment = "CN".equals(market) ? "SH" : market;
            return List.of(new SectorRank(market, "科技", "BK/" + segment + "/IN001",
                    new BigDecimal("0.02"), "领涨股", "TEST." + market, new BigDecimal("0.05"),
                    indicator, "1", "LONGPORT"));
        }

        @Override
        public SectorPeer getIndustryPeers(String market, String counterId) {
            return new SectorPeer(market, "全部行业", "科技", counterId, 30,
                    new BigDecimal("0.02"), new BigDecimal("0.1"), true, "LONGPORT");
        }

        @Override
        public SectorConstituents getIndustryConstituents(String counterId) {
            return new SectorConstituents(1, 0, 0, List.of(), "LONGPORT");
        }
    }
}
