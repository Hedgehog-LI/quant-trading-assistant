package com.quant.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 市场类型枚举。
 */
@Getter
@AllArgsConstructor
public enum MarketTypeEnum {

    A_SHARE("A_SHARE", "A股"),
    HK("HK", "港股"),
    US("US", "美股"),
    ETF("ETF", "ETF基金"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String description;

    /**
     * 根据 code 获取枚举实例。
     *
     * @param code 枚举编码
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果 code 无效
     */
    public static MarketTypeEnum fromCode(String code) {
        for (MarketTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid MarketTypeEnum code: " + code);
    }

    /**
     * 判断 code 是否为合法枚举值。
     */
    public static boolean isValid(String code) {
        for (MarketTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
