package com.quant.trade.marketdata.vo;

import java.time.LocalDate;

/** 证券别名响应。 */
public record StockAliasVO(
        String alias,
        String normalizedAlias,
        String aliasType,
        String language,
        String dataSource,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
