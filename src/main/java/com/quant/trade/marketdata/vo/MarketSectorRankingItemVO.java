package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;

/** 单个全市场板块排行明细。 */
public record MarketSectorRankingItemVO(
        Long id, Long batchId, Integer rankNo, String providerSectorId, String sectorName,
        BigDecimal changeRate, String leadingName, String leadingSymbol,
        BigDecimal leadingChangeRate, String indicatorName, String indicatorValue) {
}
