package com.quant.trade.marketdata.provider;

import java.math.BigDecimal;
import java.util.List;

/** 市场板块只读数据源，不承担自定义分组 CRUD。 */
public interface MarketSectorProvider {

    String getProviderCode();

    boolean isConfigured();

    List<SectorRank> getIndustryRank(String market, String indicator, String sortType, int limit);

    SectorPeer getIndustryPeers(String market, String counterId);

    SectorConstituents getIndustryConstituents(String counterId);

    record SectorRank(String market, String name, String providerSectorId, BigDecimal changeRate,
                      String leadingName, String leadingSymbol, BigDecimal leadingChangeRate,
                      String indicatorName, String indicatorValue, String providerCode) {}

    record SectorPeer(String market, String topName, String name, String providerSectorId,
                      Integer stockCount, BigDecimal changeRate, BigDecimal yearToDateChangeRate,
                      boolean hasChildren, String providerCode) {}

    record SectorConstituents(Integer riseCount, Integer fallCount, Integer flatCount,
                              List<SectorConstituent> stocks, String providerCode) {}

    record SectorConstituent(String canonicalSymbol, String name, BigDecimal currentPrice,
                             BigDecimal previousClose, BigDecimal changeRate, BigDecimal netInflow,
                             BigDecimal turnoverAmount, BigDecimal volume, BigDecimal totalShares,
                             BigDecimal circulatingShares, String tags, Integer tradeStatus,
                             boolean delayed) {}
}
