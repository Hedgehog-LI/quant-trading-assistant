package com.quant.trade.marketdata.provider;

import com.quant.trade.marketdata.constant.MarketDataConstants;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import org.springframework.stereotype.Component;
import java.util.Locale;

/** 内部 canonical_symbol 与 LongPort symbol 双向映射。 */
@Component
public class LongPortSymbolMapper {

    public String toLongPort(String canonical) {
        try {
            String normalized = CanonicalSymbolUtils.normalize(canonical);
            int separator = normalized.indexOf('.');
            String market = normalized.substring(0, separator);
            String symbol = normalized.substring(separator + 1);
            if (MarketDataConstants.MARKET_HK.equals(market)) {
                symbol = String.valueOf(Integer.parseInt(symbol));
            }
            return symbol + "." + market;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String fromLongPort(String longPortSymbol) {
        if (longPortSymbol == null || longPortSymbol.isBlank()) {
            return null;
        }
        String s = longPortSymbol.trim().toUpperCase(Locale.ROOT);
        int separator = s.lastIndexOf('.');
        if (separator <= 0 || separator >= s.length() - 1) {
            return null;
        }
        String symbol = s.substring(0, separator);
        String market = s.substring(separator + 1);
        try {
            return CanonicalSymbolUtils.normalize(market + "." + symbol);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
