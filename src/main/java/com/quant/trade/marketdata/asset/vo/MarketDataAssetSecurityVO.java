package com.quant.trade.marketdata.asset.vo;

/** 行情资产响应中的证券标识与展示上下文。 */
public record MarketDataAssetSecurityVO(
        String canonicalSymbol,
        String displayName,
        String market,
        String currency,
        String timeZone) {
}
