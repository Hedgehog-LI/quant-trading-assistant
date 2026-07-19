package com.quant.trade.marketdata.manager;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** 将用户选择的市场和精确代码转换为系统统一证券标识。 */
@Component
public class SecurityCodeManager {

    public String toCanonicalSymbol(String market, String code) {
        String normalizedMarket = market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        try {
            return switch (normalizedMarket) {
                case "CN" -> CanonicalSymbolUtils.normalize(inferAShareMarket(normalizedCode) + "." + normalizedCode);
                case "HK" -> CanonicalSymbolUtils.normalize("HK." + normalizedCode);
                case "US" -> CanonicalSymbolUtils.normalize("US." + normalizedCode);
                default -> throw invalid("市场必须为 CN/HK/US");
            };
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private String inferAShareMarket(String code) {
        if (!code.matches("\\d{6}")) {
            throw invalid("A 股证券代码必须为 6 位数字");
        }
        if (code.startsWith("92") || code.startsWith("4") || code.startsWith("8")) return "BJ";
        if (code.startsWith("5") || code.startsWith("6") || code.startsWith("9")) return "SH";
        if (code.matches("[0-3].*")) return "SZ";
        throw invalid("无法根据该 A 股代码确定交易所");
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL, message);
    }
}
