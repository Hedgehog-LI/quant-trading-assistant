package com.quant.trade.marketdata.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 增强证券目录详情。 */
public record SecurityDetailVO(
        Long id,
        String canonicalSymbol,
        String symbol,
        String displayName,
        String name,
        String nameCn,
        String nameHk,
        String nameEn,
        String shortName,
        String pinyinFull,
        String pinyinAbbr,
        String market,
        String exchange,
        String currency,
        String securityType,
        String listStatus,
        boolean delisted,
        LocalDate listDate,
        String dataSource,
        Instant sourceUpdatedAt,
        String sourceHash,
        List<StockAliasVO> aliases
) {
}
