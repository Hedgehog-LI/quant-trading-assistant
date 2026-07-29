package com.quant.trade.marketdata.vo;

import com.quant.trade.marketdata.enums.SecurityMatchedByEnum;

/** 证券目录搜索项。 */
public record SecuritySearchItemVO(
        String canonicalSymbol,
        String symbol,
        String displayName,
        String name,
        String nameCn,
        String nameHk,
        String nameEn,
        String shortName,
        String market,
        String exchange,
        String currency,
        String securityType,
        String listStatus,
        SecurityMatchedByEnum matchedBy
) {
}
