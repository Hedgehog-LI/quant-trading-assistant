package com.quant.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 关注等级枚举。
 */
@Getter
@AllArgsConstructor
public enum AttentionLevelEnum {

    HIGH("HIGH", "高"),
    MEDIUM("MEDIUM", "中"),
    LOW("LOW", "低");

    private final String code;
    private final String description;

    public static AttentionLevelEnum fromCode(String code) {
        for (AttentionLevelEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid AttentionLevelEnum code: " + code);
    }

    public static boolean isValid(String code) {
        for (AttentionLevelEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
