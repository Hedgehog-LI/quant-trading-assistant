package com.quant.trade.marketdata.util;

import com.quant.trade.marketdata.constant.MarketDataConstants;

import java.util.Locale;

/** 统一证券标识规范化工具。 */
public final class CanonicalSymbolUtils {

    private static final int HK_SYMBOL_LENGTH = 5;
    private static final int MAX_US_SYMBOL_LENGTH = 16;

    private CanonicalSymbolUtils() {
    }

    /**
     * 规范化证券标识：A 股保留原代码，港股补齐五位，美股代码转大写。
     *
     * @param canonicalSymbol 待规范化标识，例如 HK.2498、US.aapl
     * @return 规范化标识，例如 HK.02498、US.AAPL
     * @throws IllegalArgumentException 标识格式不合法
     */
    public static String normalize(String canonicalSymbol) {
        if (canonicalSymbol == null || canonicalSymbol.isBlank()) {
            throw new IllegalArgumentException("证券代码不能为空");
        }
        String upper = canonicalSymbol.trim().toUpperCase(Locale.ROOT);
        int separator = upper.indexOf('.');
        if (separator <= 0 || separator >= upper.length() - 1) {
            throw new IllegalArgumentException("证券代码格式不合法: " + canonicalSymbol);
        }
        String market = upper.substring(0, separator);
        String symbol = upper.substring(separator + 1);
        if (!MarketDataConstants.VALID_MARKETS.contains(market)) {
            throw new IllegalArgumentException("市场必须为 SH/SZ/BJ/HK/US: " + market);
        }

        String normalizedSymbol = switch (market) {
            case MarketDataConstants.MARKET_HK -> normalizeHongKongSymbol(symbol);
            case MarketDataConstants.MARKET_US -> normalizeUsSymbol(symbol);
            default -> normalizeAShareSymbol(symbol);
        };
        return market + "." + normalizedSymbol;
    }

    public static boolean isValid(String canonicalSymbol) {
        try {
            normalize(canonicalSymbol);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizeAShareSymbol(String symbol) {
        if (!symbol.matches(MarketDataConstants.A_SHARE_SYMBOL_REGEX)) {
            throw new IllegalArgumentException("A 股证券代码必须为 4-6 位数字: " + symbol);
        }
        return symbol;
    }

    private static String normalizeHongKongSymbol(String symbol) {
        if (!symbol.matches("\\d{1,5}")) {
            throw new IllegalArgumentException("港股证券代码必须为 1-5 位数字: " + symbol);
        }
        int numericSymbol = Integer.parseInt(symbol);
        if (numericSymbol <= 0) {
            throw new IllegalArgumentException("港股证券代码必须大于 0: " + symbol);
        }
        return String.format(Locale.ROOT, "%0" + HK_SYMBOL_LENGTH + "d", numericSymbol);
    }

    private static String normalizeUsSymbol(String symbol) {
        if (symbol.length() > MAX_US_SYMBOL_LENGTH
                || !symbol.matches(MarketDataConstants.US_SYMBOL_REGEX)) {
            throw new IllegalArgumentException("美股证券代码格式不合法: " + symbol);
        }
        return symbol;
    }
}
