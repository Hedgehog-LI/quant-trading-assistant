package com.quant.trade.risk.service;

import com.quant.trade.risk.manager.RiskCalculatorManager;
import com.quant.trade.risk.dto.PositionSizeCalculateDTO;
import com.quant.trade.risk.vo.PositionSizeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 风控计算器应用服务。
 * <p>
 * 纯计算无事务，委托给 {@link RiskCalculatorManager} 执行核心逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskCalculatorService {

    private final RiskCalculatorManager riskCalculatorManager;

    /**
     * 计算建议仓位大小。
     */
    public PositionSizeVO calculatePositionSize(PositionSizeCalculateDTO dto) {
        return riskCalculatorManager.calculatePositionSize(dto);
    }
}
