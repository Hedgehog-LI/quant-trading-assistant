package com.quant.trade.marketdata.provider;

import com.quant.trade.marketdata.config.LongPortProperties;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.provider.longport.LongPortSectorClient;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** LongPort 行业排行和层级的只读适配器。 */
@RequiredArgsConstructor
public class LongPortMarketSectorProvider implements MarketSectorProvider {

    private final LongPortProperties properties;
    private final LongPortSectorClient client;

    @Override
    public String getProviderCode() {
        return MarketDataConstants.PROVIDER_CODE_LONGPORT;
    }

    @Override
    public boolean isConfigured() {
        return properties.isEnabled() && client.isConfigured();
    }

    @Override
    public List<SectorRank> getIndustryRank(String market, String indicator, String sortType, int limit) {
        return client.getIndustryRank(market, indicator, sortType, limit).stream()
                .map(item -> new SectorRank(market, item.name(), item.counterId(), item.changeRate(),
                        item.leadingName(), item.leadingSymbol(), item.leadingChangeRate(),
                        item.indicatorName(), item.indicatorValue(), getProviderCode()))
                .toList();
    }

    @Override
    public SectorPeer getIndustryPeers(String market, String counterId) {
        var peer = client.getIndustryPeers(market, counterId);
        return new SectorPeer(peer.market(), peer.topName(), peer.name(), peer.counterId(),
                peer.stockCount(), peer.changeRate(), peer.yearToDateChangeRate(), peer.hasChildren(),
                getProviderCode());
    }

    @Override
    public SectorConstituents getIndustryConstituents(String counterId) {
        var result = client.getIndustryConstituents(counterId);
        return new SectorConstituents(result.riseCount(), result.fallCount(), result.flatCount(),
                result.stocks().stream().map(item -> new SectorConstituent(item.canonicalSymbol(), item.name(),
                        item.currentPrice(), item.previousClose(), item.changeRate(), item.netInflow(),
                        item.turnoverAmount(), item.volume(), item.totalShares(), item.circulatingShares(),
                        item.tags(), item.tradeStatus(), item.delayed())).toList(), getProviderCode());
    }
}
