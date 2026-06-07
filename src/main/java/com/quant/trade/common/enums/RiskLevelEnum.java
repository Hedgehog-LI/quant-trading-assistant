package com.quant.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风险等级枚举。
 */
@Getter
@AllArgsConstructor
public enum RiskLevelEnum {

    LOW("LOW", "低风险"),
    MEDIUM("MEDIUM", "中风险"),
    HIGH("HIGH", "高风险");

    private final String code;
    private final String description;

    public static RiskLevelEnum fromCode(String code) {
        for (RiskLevelEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid RiskLevelEnum code: " + code);
    }

    public static boolean isValid(String code) {
        for (RiskLevelEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
