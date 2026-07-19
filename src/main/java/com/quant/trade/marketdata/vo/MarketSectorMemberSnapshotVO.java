package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;

/** 某次行业快照中的成分证券行情与资金字段。 */
public record MarketSectorMemberSnapshotVO(Long id, Long snapshotId, String canonicalSymbol,
                                           String securityName, BigDecimal currentPrice,
                                           BigDecimal previousClose, BigDecimal changeRate,
                                           BigDecimal netInflow, BigDecimal turnoverAmount,
                                           BigDecimal volume, BigDecimal totalShares,
                                           BigDecimal circulatingShares, String tags,
                                           Integer tradeStatus, Boolean delayed) {
}
