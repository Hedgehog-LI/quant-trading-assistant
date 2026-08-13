package com.quant.trade.marketdata.analysis.manager;

import com.quant.trade.marketdata.analysis.constant.SectorAnalyticsConstants;
import com.quant.trade.marketdata.analysis.enums.RotationStateEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 使用冻结阈值解释板块轮动象限。 */
@Component
public class RotationStateClassifier {

    public RotationStateEnum classify(BigDecimal strength, BigDecimal momentum) {
        if (strength == null || momentum == null) {
            return RotationStateEnum.INSUFFICIENT_DATA;
        }
        boolean strong = strength.compareTo(SectorAnalyticsConstants.ROTATION_STRENGTH_THRESHOLD) >= 0;
        boolean improving = momentum.compareTo(SectorAnalyticsConstants.ROTATION_MOMENTUM_THRESHOLD) >= 0;
        if (strong && improving) {
            return RotationStateEnum.LEADING;
        }
        if (!strong && improving) {
            return RotationStateEnum.IMPROVING;
        }
        if (strong) {
            return RotationStateEnum.WEAKENING;
        }
        return RotationStateEnum.LAGGING;
    }
}
