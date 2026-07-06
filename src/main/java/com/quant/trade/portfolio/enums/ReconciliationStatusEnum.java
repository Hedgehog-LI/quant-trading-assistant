package com.quant.trade.portfolio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 持仓快照与 FIFO 账本对账状态枚举。
 * <p>
 * 对账以数量为核心一致性判断，成本差异只展示不直接判错。
 */
@Getter
@AllArgsConstructor
public enum ReconciliationStatusEnum {

    /** 快照数量与账本数量一致 */
    MATCHED("MATCHED", "一致"),
    /** 两边都有，但数量不同 */
    QUANTITY_MISMATCH("QUANTITY_MISMATCH", "数量不一致"),
    /** 快照有持仓，账本没有 */
    SNAPSHOT_ONLY("SNAPSHOT_ONLY", "仅快照有"),
    /** 账本有持仓，快照没有 */
    LEDGER_ONLY("LEDGER_ONLY", "仅账本有");

    /** 状态码 */
    private final String code;
    /** 中文描述 */
    private final String description;

    public static boolean isValid(String code) {
        for (ReconciliationStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static ReconciliationStatusEnum fromCode(String code) {
        for (ReconciliationStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid ReconciliationStatusEnum code: " + code);
    }
}
