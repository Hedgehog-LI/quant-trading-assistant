package com.quant.trade.common.util;

import com.quant.trade.common.constant.RiskConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 风控计算工具类。
 * <p>
 * 封装 BigDecimal 风控相关计算，统一精度和舍入模式，
 * 避免在业务代码中散落数学运算细节。
 */
public final class RiskMathUtil {

    private RiskMathUtil() {
    }

    /**
     * 乘法运算，使用标准精度。
     *
     * @param a 被乘数
     * @param b 乘数
     * @return a * b，精度为 {@link RiskConstants#DECIMAL_SCALE}
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return a.multiply(b).setScale(RiskConstants.DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 减法运算，使用标准精度。
     *
     * @param a 被减数
     * @param b 减数
     * @return a - b，精度为 {@link RiskConstants#DECIMAL_SCALE}
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return a.subtract(b).setScale(RiskConstants.DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 除法运算，向零取整（即 floor 对正数的行为）。
     *
     * @param dividend 被除数
     * @param divisor  除数
     * @return 商，精度为 0，向零取整
     */
    public static BigDecimal floorDivide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, 0, RoundingMode.FLOOR);
    }

    /**
     * 除法运算，标准精度。
     *
     * @param dividend 被除数
     * @param divisor  除数
     * @param scale    小数位数
     * @return 商
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor, int scale) {
        return dividend.divide(divisor, scale, RoundingMode.HALF_UP);
    }

    /**
     * 按交易手数向下取整。
     * <p>
     * 例如 finalQuantity=90, lotSize=100 → 0；
     * finalQuantity=450, lotSize=100 → 400。
     *
     * @param quantity 原始数量
     * @param lotSize  最小交易单位
     * @return 取整后的数量
     */
    public static long roundDownToLot(long quantity, int lotSize) {
        if (lotSize <= 0) {
            throw new IllegalArgumentException("lotSize must be positive: " + lotSize);
        }
        return (quantity / lotSize) * lotSize;
    }

    /**
     * 计算比例。
     *
     * @param part   部分值
     * @param total  总值
     * @param scale  小数位数
     * @return part / total
     */
    public static BigDecimal ratio(BigDecimal part, BigDecimal total, int scale) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return part.divide(total, scale, RoundingMode.HALF_UP);
    }
}
