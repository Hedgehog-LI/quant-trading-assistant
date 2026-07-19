package com.quant.trade.marketdata.provider;

import com.quant.trade.marketdata.config.LongPortProperties;
import com.quant.trade.marketdata.provider.longport.LongPortSectorClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongPortMarketSectorProviderTest {

    @Test
    void mapsClientIndustryModels() {
        LongPortProperties properties = new LongPortProperties();
        properties.setEnabled(true);
        LongPortMarketSectorProvider provider = new LongPortMarketSectorProvider(properties, new StubClient());

        assertTrue(provider.isConfigured());
        var rank = provider.getIndustryRank("HK", "popularity", "single", 5).get(0);
        assertEquals("BK/HK/IN001", rank.providerSectorId());
        assertEquals("LONGPORT", rank.providerCode());
        assertEquals(12, provider.getIndustryPeers("HK", rank.providerSectorId()).stockCount());
    }

    private static class StubClient implements LongPortSectorClient {
        @Override public boolean isConfigured() { return true; }
        @Override public List<LongPortIndustryRank> getIndustryRank(String market, String indicator,
                                                                    String sortType, int limit) {
            return List.of(new LongPortIndustryRank("科技", "BK/HK/IN001", new BigDecimal("0.02"),
                    "龙头", "00001.HK", new BigDecimal("0.03"), indicator, "1"));
        }
        @Override public LongPortIndustryPeer getIndustryPeers(String market, String counterId) {
            return new LongPortIndustryPeer(market, "全部", "科技", counterId, 12,
                    new BigDecimal("0.02"), new BigDecimal("0.1"), true);
        }
        @Override public LongPortIndustryConstituents getIndustryConstituents(String counterId) {
            return new LongPortIndustryConstituents(1, 0, 0, List.of());
        }
    }
}
