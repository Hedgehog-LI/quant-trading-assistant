package com.quant.trade.portfolio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 持仓快照对比明细变化类型枚举。
 */
@Getter
@AllArgsConstructor
public enum SnapshotChangeTypeEnum {

    /** 基准没有、目标存在 */
    NEW("NEW", "新增"),
    /** 目标持仓数量增加 */
    INCREASED("INCREASED", "加仓"),
    /** 目标持仓数量减少但仍持有 */
    REDUCED("REDUCED", "减仓"),
    /** 基准存在、目标没有 */
    CLOSED("CLOSED", "清仓"),
    /** 持仓数量未变化 */
    UNCHANGED("UNCHANGED", "未变化");

    /** 类型码 */
    private final String code;
    /** 中文描述 */
    private final String description;

    public static boolean isValid(String code) {
        for (SnapshotChangeTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static SnapshotChangeTypeEnum fromCode(String code) {
        for (SnapshotChangeTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid SnapshotChangeTypeEnum code: " + code);
    }
}
