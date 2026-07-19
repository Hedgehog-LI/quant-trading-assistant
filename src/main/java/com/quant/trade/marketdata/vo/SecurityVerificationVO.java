package com.quant.trade.marketdata.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 精确证券代码只读验证结果。 */
public record SecurityVerificationVO(
        String canonicalSymbol,
        String providerSymbol,
        String displayName,
        String market,
        String exchange,
        String currency,
        Integer lotSize,
        String verificationStatus,
        boolean quoteAvailable,
        BigDecimal lastPrice,
        LocalDateTime quoteTime,
        String tradeStatus,
        String quoteDelay,
        String provider,
        String message) {
}
