package com.quant.trade.marketdata.provider;

import com.quant.trade.marketdata.constant.MarketDataConstants;
import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.Set;

/** 内部 canonical_symbol <-> LongPort symbol 双向映射。 SH.600519 <-> 600519.SH */
@Component
public class LongPortSymbolMapper {
    private static final Set<String> VALID = MarketDataConstants.VALID_MARKETS;

    public String toLongPort(String canonical) {
        String upper = canonical.trim().toUpperCase(Locale.ROOT);
        int dot = upper.indexOf('.');
        if (dot <= 0 || dot >= upper.length() - 1) return null;
        String market = upper.substring(0, dot);
        String code = upper.substring(dot + 1);
        if (!VALID.contains(market) || !code.matches("\\d{4,6}")) return null;
        return code + "." + market;
    }

    public String fromLongPort(String longPortSymbol) {
        String s = longPortSymbol.trim().toUpperCase(Locale.ROOT);
        int dot = s.indexOf('.');
        if (dot <= 0 || dot >= s.length() - 1) return null;
        String code = s.substring(0, dot);
        String market = s.substring(dot + 1);
        if (!VALID.contains(market) || !code.matches("\\d{4,6}")) return null;
        return market + "." + code;
    }
}
