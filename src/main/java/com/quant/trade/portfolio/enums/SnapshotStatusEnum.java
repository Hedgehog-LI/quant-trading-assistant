package com.quant.trade.portfolio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 持仓快照状态枚举。
 */
@Getter
@AllArgsConstructor
public enum SnapshotStatusEnum {

    DRAFT("DRAFT", "草稿"),
    CONFIRMED("CONFIRMED", "已确认"),
    CANCELED("CANCELED", "已作废");

    private final String code;
    private final String description;

    public static boolean isValid(String code) {
        for (SnapshotStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
