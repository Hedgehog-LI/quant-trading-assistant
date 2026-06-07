package com.quant.trade.risk.manager;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.constant.RiskConstants;
import com.quant.trade.common.constant.TradingDisclaimerConstants;
import com.quant.trade.common.enums.RiskLevelEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.common.util.RiskMathUtil;
import com.quant.trade.risk.dto.PositionSizeCalculateDTO;
import com.quant.trade.risk.vo.PositionSizeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 风控计算领域规则层。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>仓位大小计算</li>
 *   <li>风险等级判定</li>
 *   <li>告警提示生成</li>
 * </ul>
 * <p>
 * 纯计算，无状态，不访问数据库。
 */
@Slf4j
@Component
public class RiskCalculatorManager {

    /**
     * 计算建议仓位大小。
     *
     * @param dto 计算参数
     * @return 计算结果
     * @throws BusinessException 当 stopLossPrice >= buyPrice 时
     */
    public PositionSizeVO calculatePositionSize(PositionSizeCalculateDTO dto) {
        // 前置校验：止损价必须低于买入价
        if (dto.stopLossPrice().compareTo(dto.buyPrice()) >= 0) {
            throw new BusinessException(ErrorCodeEnum.RISK_CALCULATION_ERROR,
                    "stopLossPrice must be lower than buyPrice");
        }

        // riskAmount = totalCapital * riskPercent
        BigDecimal riskAmount = RiskMathUtil.multiply(dto.totalCapital(), dto.riskPercent());

        // perShareRisk = buyPrice - stopLossPrice
        BigDecimal perShareRisk = RiskMathUtil.subtract(dto.buyPrice(), dto.stopLossPrice());

        // riskBasedQuantity = floor(riskAmount / perShareRisk)
        long riskBasedQuantity = RiskMathUtil.floorDivide(riskAmount, perShareRisk).longValue();

        // positionCapQuantity = floor(totalCapital * maxPositionRatio / buyPrice)
        BigDecimal positionCap = RiskMathUtil.multiply(dto.totalCapital(), dto.maxPositionRatio());
        long positionCapQuantity = RiskMathUtil.floorDivide(positionCap, dto.buyPrice()).longValue();

        // finalQuantity = min(riskBasedQuantity, positionCapQuantity)
        long finalQuantity = Math.min(riskBasedQuantity, positionCapQuantity);

        // 按手数向下取整
        finalQuantity = RiskMathUtil.roundDownToLot(finalQuantity, dto.lotSize());

        // estimatedLoss = finalQuantity * perShareRisk
        BigDecimal estimatedLoss = RiskMathUtil.multiply(BigDecimal.valueOf(finalQuantity), perShareRisk);

        // positionAmount = finalQuantity * buyPrice
        BigDecimal positionAmount = RiskMathUtil.multiply(BigDecimal.valueOf(finalQuantity), dto.buyPrice());

        // positionRatio = positionAmount / totalCapital
        BigDecimal positionRatio = (finalQuantity > 0)
                ? RiskMathUtil.ratio(positionAmount, dto.totalCapital(), RiskConstants.DECIMAL_SCALE)
                : BigDecimal.ZERO;

        // 风险等级和告警
        RiskLevelAndWarnings result = determineRiskLevelAndWarnings(
                finalQuantity, positionRatio, perShareRisk, dto.buyPrice(), dto.riskPercent());

        log.info("Position size calculated: finalQuantity={}, riskLevel={}", finalQuantity, result.level.getCode());

        return new PositionSizeVO(
                riskAmount,
                perShareRisk,
                riskBasedQuantity,
                positionCapQuantity,
                finalQuantity,
                estimatedLoss,
                positionAmount,
                positionRatio,
                result.level.getCode(),
                result.warnings,
                TradingDisclaimerConstants.RISK_CALCULATOR_DISCLAIMER
        );
    }

    // ==================== 风险等级和告警判定 ====================

    private RiskLevelAndWarnings determineRiskLevelAndWarnings(
            long finalQuantity, BigDecimal positionRatio,
            BigDecimal perShareRisk, BigDecimal buyPrice, BigDecimal riskPercent) {

        RiskLevelEnum level = RiskLevelEnum.LOW;
        List<String> warnings = new ArrayList<>();

        // 条件 1：finalQuantity=0，无法建仓
        if (finalQuantity <= 0) {
            level = RiskLevelEnum.HIGH;
            warnings.add(MessageConstants.RISK_NO_TRADE_CONDITION);
        }

        // 条件 2：仓位占比超过阈值
        if (positionRatio.compareTo(RiskConstants.HIGH_POSITION_RATIO_THRESHOLD) > 0) {
            level = RiskLevelEnum.HIGH;
            warnings.add(MessageConstants.RISK_OVERSIZED_POSITION);
        }

        // 条件 3：每股风险占比过大
        BigDecimal perShareRiskRatio = RiskMathUtil.ratio(perShareRisk, buyPrice, RiskConstants.DECIMAL_SCALE);
        if (perShareRiskRatio.compareTo(RiskConstants.HIGH_PER_SHARE_RISK_RATIO_THRESHOLD) > 0) {
            if (level != RiskLevelEnum.HIGH) {
                level = RiskLevelEnum.MEDIUM;
            }
            warnings.add(MessageConstants.RISK_PER_SHARE_TOO_HIGH);
        }

        // 条件 4：单笔风险比例过高
        if (riskPercent.compareTo(RiskConstants.RISK_RATIO_WARNING_THRESHOLD) > 0) {
            if (level != RiskLevelEnum.HIGH) {
                level = RiskLevelEnum.MEDIUM;
            }
            warnings.add(MessageConstants.RISK_RATIO_TOO_HIGH);
        }

        return new RiskLevelAndWarnings(level, warnings);
    }

    private record RiskLevelAndWarnings(RiskLevelEnum level, List<String> warnings) {}
}
