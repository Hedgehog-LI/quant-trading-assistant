package com.quant.trade.marketdata.asset.vo;

import java.util.List;

/** availability 响应：证券 + 真实存在的 interval/source/adjust 组合。 */
public record MarketDataAssetAvailabilityVO(
        MarketDataAssetSecurityVO security,
        List<MarketDataAssetCombinationVO> combinations) {
}
