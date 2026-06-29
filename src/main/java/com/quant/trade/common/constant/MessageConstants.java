package com.quant.trade.common.constant;

/**
 * 业务消息常量。
 * <p>
 * 集中管理所有运行时返回给用户的提示文案、告警文案和异常消息，
 * 避免在 Manager/Service 中散落固定中文文案。
 */
public final class MessageConstants {

    // ==================== HTTP 请求异常 ====================

    /** 请求的 API 或静态资源不存在 */
    public static final String REQUEST_RESOURCE_NOT_FOUND =
            "请求资源不存在";

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

    // ==================== 持仓账本告警 ====================

    /** 股票未维护当前价，浮动盈亏不可用（%s = symbol） */
    public static final String PORTFOLIO_NO_CURRENT_PRICE =
            "股票 %s 未维护当前价，浮动盈亏相关字段返回空，不计入汇总统计";

    /** 股票存在卖出超过持仓的异常数据（%s = symbol） */
    public static final String PORTFOLIO_OVERSOLD_ANOMALY =
            "股票 %s 存在卖出数量超过持仓的异常数据，该股票不参与统计，请核对交易记录";

    /** 暂无已结算交易，胜率与平均指标不适用 */
    public static final String PORTFOLIO_NO_CLOSED_TRADES =
            "暂无已结算交易，平均收益率、平均持仓天数、胜率暂不适用";

    // ==================== 持仓快照校验 ====================

    /** 持仓快照不存在（%d = snapshot id） */
    public static final String POSITION_SNAPSHOT_NOT_FOUND =
            "持仓快照不存在: %d";

    /** 只有草稿状态允许编辑 */
    public static final String POSITION_SNAPSHOT_ONLY_DRAFT_EDITABLE =
            "只有草稿状态的持仓快照允许编辑";

    /** 创建时只允许草稿或已确认状态 */
    public static final String POSITION_SNAPSHOT_INVALID_INITIAL_STATUS =
            "新建持仓快照只允许 DRAFT 或 CONFIRMED 状态";

    /** 无效快照状态编码（%s = status） */
    public static final String POSITION_SNAPSHOT_INVALID_STATUS_CODE =
            "无效的持仓快照状态: %s";

    /** 无效快照来源编码（%s = source type） */
    public static final String POSITION_SNAPSHOT_INVALID_SOURCE_CODE =
            "无效的持仓快照来源: %s";

    /** 无效市场编码（%s = market type） */
    public static final String POSITION_SNAPSHOT_INVALID_MARKET_CODE =
            "无效的证券市场类型: %s";

    /** 状态流转非法（第一个 %s = current status，第二个 %s = target status） */
    public static final String POSITION_SNAPSHOT_INVALID_TRANSITION =
            "持仓快照不允许从 %s 流转到 %s";

    /** 快照日期与时间中的日期必须一致 */
    public static final String POSITION_SNAPSHOT_DATE_TIME_MISMATCH =
            "snapshotDate 必须与 snapshotTime 中的日期一致";

    /** 日期查询区间非法 */
    public static final String POSITION_SNAPSHOT_INVALID_DATE_RANGE =
            "fromDate 不能晚于 toDate";

    /** 快照内股票代码重复（%s = symbol） */
    public static final String POSITION_SNAPSHOT_DUPLICATE_SYMBOL =
            "同一持仓快照内股票代码不能重复: %s";

    /** 可用数量超过持仓数量（%s = symbol） */
    public static final String POSITION_SNAPSHOT_AVAILABLE_EXCEEDS_HOLDING =
            "股票 %s 的可用数量不能超过持仓数量";

    /** 持仓明细数值非法（%s = symbol） */
    public static final String POSITION_SNAPSHOT_INVALID_ITEM =
            "股票 %s 的数量和价格必须符合持仓快照规则";

    private MessageConstants() {
    }
}
