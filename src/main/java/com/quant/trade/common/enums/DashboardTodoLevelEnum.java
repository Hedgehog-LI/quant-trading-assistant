package com.quant.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作台待办级别枚举。
 * <p>
 * INFO 提示、WARNING 关注、RISK 风险优先处理。不与既有 RiskLevelEnum 混用，
 * 仅用于工作台待办展示优先级。
 */
@Getter
@AllArgsConstructor
public enum DashboardTodoLevelEnum {

    /** 提示 */
    INFO("INFO", "提示"),
    /** 关注 */
    WARNING("WARNING", "关注"),
    /** 风险 */
    RISK("RISK", "风险");

    /** 级别码 */
    private final String code;
    /** 中文描述 */
    private final String description;

    public static boolean isValid(String code) {
        for (DashboardTodoLevelEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static DashboardTodoLevelEnum fromCode(String code) {
        for (DashboardTodoLevelEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid DashboardTodoLevelEnum code: " + code);
    }
}
