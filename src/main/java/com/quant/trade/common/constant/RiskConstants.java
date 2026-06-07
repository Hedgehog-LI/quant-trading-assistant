package com.quant.trade.common.constant;

import java.math.BigDecimal;

/**
 * 风控相关常量。
 * <p>
 * 集中管理所有风控阈值、默认值和公式参数，避免在业务代码中散落魔法值。
 * <p>
 * 注意：这些默认值仅用于学习和模拟，不构成投资建议。
 */
public final class RiskConstants {

    /** 默认最小交易单位（A 股 100 股） */
    public static final int DEFAULT_LOT_SIZE = 100;

    /** 默认单笔风险比例（0.5%） */
    public static final BigDecimal DEFAULT_SINGLE_TRADE_RISK_RATIO = new BigDecimal("0.005");

    /** 单笔风险比例最大值（10%） */
    public static final BigDecimal MAX_SINGLE_TRADE_RISK_RATIO = new BigDecimal("0.10");

    /** 新手建议单笔风险比例上限（1%） */
    public static final BigDecimal SUGGESTED_MAX_RISK_RATIO = new BigDecimal("0.01");

    /** 高仓位比例阈值（20%）：超过此值视为高风险 */
    public static final BigDecimal HIGH_POSITION_RATIO_THRESHOLD = new BigDecimal("0.20");

    /** 高每股风险比例阈值（5%）：每股止损距离占买入价比例超过此值告警 */
    public static final BigDecimal HIGH_PER_SHARE_RISK_RATIO_THRESHOLD = new BigDecimal("0.05");

    /** 单笔风险比例告警阈值（2%）：超过此值触发告警 */
    public static final BigDecimal RISK_RATIO_WARNING_THRESHOLD = new BigDecimal("0.02");

    /** 仓位比例合法上限 */
    public static final BigDecimal MAX_POSITION_RATIO = BigDecimal.ONE;

    /** BigDecimal 精度（小数位数） */
    public static final int DECIMAL_SCALE = 6;

    private RiskConstants() {
    }
}
