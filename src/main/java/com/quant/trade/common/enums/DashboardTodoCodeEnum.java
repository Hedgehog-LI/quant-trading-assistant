package com.quant.trade.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作台待办类型枚举。
 * <p>
 * 每种待办对应工作台需要用户关注的一类数据质量或纪律事项，仅做记录与提醒，不构成买卖建议。
 */
@Getter
@AllArgsConstructor
public enum DashboardTodoCodeEnum {

    /** 存在待复盘交易 */
    PENDING_REVIEW("PENDING_REVIEW", "待复盘交易"),
    /** 交易未关联计划 */
    UNLINKED_TRADE_PLAN("UNLINKED_TRADE_PLAN", "交易未关联计划"),
    /** 关联计划不允许交易或标记未按计划 */
    TRADE_AGAINST_PLAN("TRADE_AGAINST_PLAN", "交易偏离计划"),
    /** 买入交易没有计划止损 */
    MISSING_STOP_LOSS("MISSING_STOP_LOSS", "买入交易缺少止损"),
    /** 最近已确认快照超过 3 个自然日 */
    STALE_POSITION_SNAPSHOT("STALE_POSITION_SNAPSHOT", "持仓快照过期"),
    /** 最新快照与账本数量不一致 */
    POSITION_RECONCILIATION_MISMATCH("POSITION_RECONCILIATION_MISMATCH", "快照与账本不一致");

    /** 待办码 */
    private final String code;
    /** 中文描述 */
    private final String description;

    public static boolean isValid(String code) {
        for (DashboardTodoCodeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static DashboardTodoCodeEnum fromCode(String code) {
        for (DashboardTodoCodeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid DashboardTodoCodeEnum code: " + code);
    }
}
