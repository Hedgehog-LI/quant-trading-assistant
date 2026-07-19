package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;

/** 市场行业排行项。 */
public record MarketSectorRankVO(String market, String name, String providerSectorId,
                                 BigDecimal changeRate, String leadingName, String leadingSymbol,
                                 BigDecimal leadingChangeRate, String indicatorName,
                                 String indicatorValue, String providerCode) {
}
