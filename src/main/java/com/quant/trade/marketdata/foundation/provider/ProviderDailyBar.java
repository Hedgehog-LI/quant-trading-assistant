package com.quant.trade.marketdata.foundation.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 回补 Provider 日 K 行（单位冻结：价格元 / volume 股 / amount 元 / 换手率小数）。
 */
public record ProviderDailyBar(
        String canonicalSymbol,
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volumeShares,
        BigDecimal amountYuan,
        BigDecimal turnoverRate
) {
}
