package com.quant.trade.marketdata.provider.longport;

import java.math.BigDecimal;
import java.util.List;

/** Longbridge 行业 HTTPS 只读客户端边界。 */
public interface LongPortSectorClient {

    boolean isConfigured();

    List<LongPortIndustryRank> getIndustryRank(String market, String indicator, String sortType, int limit);

    LongPortIndustryPeer getIndustryPeers(String market, String counterId);

    LongPortIndustryConstituents getIndustryConstituents(String counterId);

    record LongPortIndustryRank(String name, String counterId, BigDecimal changeRate,
                                String leadingName, String leadingSymbol, BigDecimal leadingChangeRate,
                                String indicatorName, String indicatorValue) {
    }

    record LongPortIndustryPeer(String market, String topName, String name, String counterId,
                                Integer stockCount, BigDecimal changeRate, BigDecimal yearToDateChangeRate,
                                boolean hasChildren) {
    }

    record LongPortIndustryConstituents(Integer riseCount, Integer fallCount, Integer flatCount,
                                        List<LongPortIndustryConstituent> stocks) {
    }

    record LongPortIndustryConstituent(String canonicalSymbol, String name, BigDecimal currentPrice,
                                       BigDecimal previousClose, BigDecimal changeRate, BigDecimal netInflow,
                                       BigDecimal turnoverAmount, BigDecimal volume, BigDecimal totalShares,
                                       BigDecimal circulatingShares, String tags, Integer tradeStatus,
                                       boolean delayed) {
    }
}
