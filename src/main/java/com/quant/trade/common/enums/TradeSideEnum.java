package com.quant.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易方向枚举。
 */
@Getter
@AllArgsConstructor
public enum TradeSideEnum {

    BUY("BUY", "买入"),
    SELL("SELL", "卖出");

    private final String code;
    private final String description;

    public static TradeSideEnum fromCode(String code) {
        for (TradeSideEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid TradeSideEnum code: " + code);
    }

    public static boolean isValid(String code) {
        for (TradeSideEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
