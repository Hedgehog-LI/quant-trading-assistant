package com.quant.trade.portfolio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 持仓快照中的证券交易市场枚举。
 */
@Getter
@AllArgsConstructor
public enum PositionMarketTypeEnum {

    SH("SH", "上海证券交易所"),
    SZ("SZ", "深圳证券交易所"),
    BJ("BJ", "北京证券交易所"),
    UNKNOWN("UNKNOWN", "未知市场");

    private final String code;
    private final String description;

    public static boolean isValid(String code) {
        for (PositionMarketTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
