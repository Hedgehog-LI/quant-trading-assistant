package com.quant.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易风格枚举。
 */
@Getter
@AllArgsConstructor
public enum TradeStyleEnum {

    SHORT_TERM("SHORT_TERM", "短线"),
    DO_T("DO_T", "做T"),
    SWING("SWING", "波段"),
    OBSERVE("OBSERVE", "观察");

    private final String code;
    private final String description;

    public static TradeStyleEnum fromCode(String code) {
        for (TradeStyleEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid TradeStyleEnum code: " + code);
    }

    public static boolean isValid(String code) {
        for (TradeStyleEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
