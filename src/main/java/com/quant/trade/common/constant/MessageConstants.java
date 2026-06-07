package com.quant.trade.common.constant;

/**
 * 业务消息常量。
 * <p>
 * 集中管理所有运行时返回给用户的提示文案、告警文案和异常消息，
 * 避免在 Manager/Service 中散落固定中文文案。
 */
public final class MessageConstants {

    // ==================== 风控告警 ====================

    /** 资金或止损距离不满足交易条件 */
    public static final String RISK_NO_TRADE_CONDITION =
            "资金或止损距离不满足交易条件，建议缩小止损距离或增加资金";

    /** 仓位占比超过阈值 */
    public static final String RISK_OVERSIZED_POSITION =
            "仓位占比超过20%，建议控制单票仓位";

    /** 每股风险占买入价比例过大 */
    public static final String RISK_PER_SHARE_TOO_HIGH =
            "每股风险占买入价比例超过5%，止损距离偏大";

    /** 单笔风险比例过高 */
    public static final String RISK_RATIO_TOO_HIGH =
            "单笔风险比例超过2%，新手建议控制在0.5%-1%以内";

    // ==================== 交易记录告警 ====================

    /** 买入交易未设置止损价 */
    public static final String JOURNAL_BUY_NO_STOP_LOSS =
            "买入交易未设置止损价，建议设置 planStopLoss";

    // ==================== Dashboard 风险提醒 ====================

    /** 今日有 N 条允许交易的计划未设置止损价 */
    public static final String DASHBOARD_PLAN_NO_STOP_LOSS =
            "今日有 %d 条允许交易的计划未设置止损价";

    /** 今日有 N 条计划仓位比例超过阈值 */
    public static final String DASHBOARD_PLAN_OVERSIZED =
            "今日有 %d 条计划仓位比例超过20%%";

    /** 有 N 条交易记录待复盘 */
    public static final String DASHBOARD_PENDING_REVIEW =
            "有 %d 条交易记录待复盘";

    // ==================== 关联校验 ====================

    /** 部分关联的交易记录不存在 */
    public static final String LINKED_JOURNALS_NOT_FOUND =
            "部分关联的交易记录不存在";

    private MessageConstants() {
    }
}
