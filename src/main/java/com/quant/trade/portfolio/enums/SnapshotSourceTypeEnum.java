package com.quant.trade.portfolio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 持仓快照数据来源枚举。
 */
@Getter
@AllArgsConstructor
public enum SnapshotSourceTypeEnum {

    MANUAL("MANUAL", "手工录入"),
    IMAGE_RECOGNITION("IMAGE_RECOGNITION", "图片识别"),
    CSV_IMPORT("CSV_IMPORT", "CSV 导入");

    private final String code;
    private final String description;

    public static boolean isValid(String code) {
        for (SnapshotSourceTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
