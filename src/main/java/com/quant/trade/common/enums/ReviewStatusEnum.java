package com.quant.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 复盘状态枚举。
 */
@Getter
@AllArgsConstructor
public enum ReviewStatusEnum {

    PENDING("PENDING", "待复盘"),
    REVIEWED("REVIEWED", "已复盘");

    private final String code;
    private final String description;

    public static ReviewStatusEnum fromCode(String code) {
        for (ReviewStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid ReviewStatusEnum code: " + code);
    }

    public static boolean isValid(String code) {
        for (ReviewStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
