package com.quant.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易计划状态枚举。
 */
@Getter
@AllArgsConstructor
public enum PlanStatusEnum {

    DRAFT("DRAFT", "草稿"),
    ACTIVE("ACTIVE", "生效中"),
    DONE("DONE", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;

    public static PlanStatusEnum fromCode(String code) {
        for (PlanStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid PlanStatusEnum code: " + code);
    }

    public static boolean isValid(String code) {
        for (PlanStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
