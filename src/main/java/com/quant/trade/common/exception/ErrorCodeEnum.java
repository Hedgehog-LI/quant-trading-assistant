package com.quant.trade.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举。
 * <p>
 * 每个错误码包含 code（字符串标识）和 description（中文描述），
 * 用于 BusinessException 携带结构化错误信息。
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {

    SUCCESS("SUCCESS", "操作成功"),
    PARAM_ERROR("PARAM_ERROR", "参数错误"),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "资源不存在"),
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", "资源已存在，不可重复创建"),
    BUSINESS_RULE_VIOLATION("BUSINESS_RULE_VIOLATION", "违反业务规则"),
    INVALID_ENUM_CODE("INVALID_ENUM_CODE", "无效的枚举值"),
    RISK_CALCULATION_ERROR("RISK_CALCULATION_ERROR", "风控计算异常"),
    PORTFOLIO_CALCULATION_ERROR("PORTFOLIO_CALCULATION_ERROR", "持仓账本计算异常"),
    PRICE_SNAPSHOT_NOT_FOUND("PRICE_SNAPSHOT_NOT_FOUND", "当前价快照不存在"),
    INSUFFICIENT_HOLDING("INSUFFICIENT_HOLDING", "卖出数量超过持仓"),
    POSITION_SNAPSHOT_NOT_FOUND("POSITION_SNAPSHOT_NOT_FOUND", "持仓快照不存在"),
    POSITION_SNAPSHOT_NOT_EDITABLE("POSITION_SNAPSHOT_NOT_EDITABLE", "持仓快照不可编辑"),
    POSITION_SNAPSHOT_INVALID_TRANSITION("POSITION_SNAPSHOT_INVALID_TRANSITION", "持仓快照状态流转非法"),
    POSITION_SNAPSHOT_DUPLICATE_SYMBOL("POSITION_SNAPSHOT_DUPLICATE_SYMBOL", "持仓快照存在重复股票"),
    POSITION_SNAPSHOT_INVALID_ITEM("POSITION_SNAPSHOT_INVALID_ITEM", "持仓快照明细不合法"),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误");

    /** 错误码标识 */
    private final String code;

    /** 错误码中文描述 */
    private final String description;
}
