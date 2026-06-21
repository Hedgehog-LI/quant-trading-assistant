package com.quant.trade.common.constant;

/**
 * 交易免责声明常量。
 * <p>
 * 所有面向用户的交易相关输出必须附带免责声明。
 */
public final class TradingDisclaimerConstants {

    /** 风控计算器免责声明 */
    public static final String RISK_CALCULATOR_DISCLAIMER =
            "本计算结果仅为辅助参考，不构成任何投资建议。"
            + "实际交易应结合个人风险承受能力、市场环境等因素综合判断。"
            + "投资有风险，入市需谨慎。";

    /** 交易计划免责声明 */
    public static final String TRADE_PLAN_DISCLAIMER =
            "交易计划为用户的盘前纪律记录，不是交易建议。"
            + "请根据实际市场情况和个人风险承受能力独立决策。";

    /** 交易记录免责声明 */
    public static final String TRADE_JOURNAL_DISCLAIMER =
            "交易记录为手工录入，仅供复盘参考。"
            + "不连接券商，不自动同步真实交易。";

    /** 持仓账本免责声明 */
    public static final String PORTFOLIO_DISCLAIMER =
            "持仓账本基于手工录入的交易记录按 FIFO 规则实时计算，仅供复盘参考。"
            + "当前价为手工维护，不连接实时行情。不构成任何投资建议，投资有风险，入市需谨慎。";

    private TradingDisclaimerConstants() {
    }
}
