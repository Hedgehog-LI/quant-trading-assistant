package com.quant.trade.risk;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.risk.dto.PositionSizeCalculateDTO;
import com.quant.trade.risk.service.RiskCalculatorService;
import com.quant.trade.risk.vo.PositionSizeVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RiskCalculatorServiceTest {

    @Autowired
    private RiskCalculatorService riskCalculatorService;

    @Test
    void normalPositionSizeCalculation() {
        PositionSizeVO vo = calculate(new BigDecimal("100000"),
                new BigDecimal("0.01"), new BigDecimal("50.00"),
                new BigDecimal("48.00"), new BigDecimal("0.20"));

        assertEquals(0, new BigDecimal("1000.000000").compareTo(vo.riskAmount()));
        assertEquals(0, new BigDecimal("2.000000").compareTo(vo.perShareRisk()));
        assertEquals(500, vo.riskBasedQuantity());
        assertEquals(400, vo.positionCapQuantity());
        assertEquals(400, vo.finalQuantity());
        assertEquals(0, new BigDecimal("20000.000000").compareTo(vo.positionAmount()));
        assertEquals("LOW", vo.riskLevel());
        assertTrue(vo.warnings().isEmpty());
        assertNotNull(vo.disclaimer());
    }

    @Test
    void stopLossNotLowerThanBuyPrice() {
        assertThrows(BusinessException.class, () -> calculate(
                new BigDecimal("100000"), new BigDecimal("0.01"),
                new BigDecimal("210.00"), new BigDecimal("220.00"),
                new BigDecimal("0.20")));
    }

    @Test
    void stopLossEqualsBuyPrice() {
        assertThrows(BusinessException.class, () -> calculate(
                new BigDecimal("100000"), new BigDecimal("0.01"),
                new BigDecimal("220.00"), new BigDecimal("220.00"),
                new BigDecimal("0.20")));
    }

    @Test
    void lotSizeRoundsDown() {
        PositionSizeVO vo = calculate(new BigDecimal("100000"),
                new BigDecimal("0.005"), new BigDecimal("25.00"),
                new BigDecimal("24.00"), new BigDecimal("0.20"));

        // riskAmount=500, perShareRisk=1, riskBased=500, cap=800
        // min=500, floor(500/100)*100=500
        assertEquals(500, vo.finalQuantity());
    }

    @Test
    void zeroQuantityMeansHighRisk() {
        PositionSizeVO vo = calculate(new BigDecimal("1000"),
                new BigDecimal("0.005"), new BigDecimal("200.00"),
                new BigDecimal("199.00"), new BigDecimal("0.10"));

        assertEquals(0, vo.finalQuantity());
        assertEquals("HIGH", vo.riskLevel());
        assertFalse(vo.warnings().isEmpty());
    }

    @Test
    void highPositionRatioWarning() {
        // positionRatio will be 0.20 which is the threshold, not above
        // Use a smaller buyPrice so cap is higher and risk-based limits it
        PositionSizeVO vo = calculate(new BigDecimal("100000"),
                new BigDecimal("0.05"), new BigDecimal("10.00"),
                new BigDecimal("9.00"), new BigDecimal("0.50"));

        // riskAmount=5000, perShareRisk=1, riskBased=5000, cap=5000
        // final=min(5000,5000)=5000, floor(5000/100)*100=5000
        // positionAmount=50000, ratio=0.5 > 0.2 => HIGH
        assertEquals("HIGH", vo.riskLevel());
    }

    private PositionSizeVO calculate(BigDecimal capital, BigDecimal riskPct,
                                     BigDecimal buy, BigDecimal stop, BigDecimal maxRatio) {
        return riskCalculatorService.calculatePositionSize(
                new PositionSizeCalculateDTO(capital, riskPct, buy, stop, maxRatio, 100));
    }
}
