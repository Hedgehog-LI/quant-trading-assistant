package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;

/** 行业层级节点摘要。 */
public record MarketSectorPeerVO(String market, String topName, String name, String providerSectorId,
                                 Integer stockCount, BigDecimal changeRate,
                                 BigDecimal yearToDateChangeRate, boolean hasChildren,
                                 String providerCode) {
}
