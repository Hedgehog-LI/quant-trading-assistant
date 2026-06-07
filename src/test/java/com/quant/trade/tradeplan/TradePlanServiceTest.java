package com.quant.trade.tradeplan;

import com.quant.trade.common.enums.PlanStatusEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.tradeplan.dto.CreateTradePlanDTO;
import com.quant.trade.tradeplan.service.TradePlanService;
import com.quant.trade.tradeplan.vo.TradePlanVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TradePlanServiceTest {

    @Autowired
    private TradePlanService tradePlanService;

    @Test
    void createTradePlanSuccess() {
        CreateTradePlanDTO dto = new CreateTradePlanDTO(
                LocalDate.of(2026, 6, 8), "300750", "宁德时代",
                PlanStatusEnum.DRAFT.getCode(), "突破220买入", "跌破210卖出",
                new BigDecimal("210.00"), new BigDecimal("240.00"),
                new BigDecimal("0.10"), new BigDecimal("2000.00"),
                false, "注意量能配合", "测试计划"
        );

        TradePlanVO vo = tradePlanService.create(dto);
        assertNotNull(vo.id());
        assertEquals(PlanStatusEnum.DRAFT.getCode(), vo.planStatus());
    }

    @Test
    void duplicateSymbolDateThrowsError() {
        LocalDate date = LocalDate.of(2026, 6, 8);
        createPlan(date, "300750");
        assertThrows(BusinessException.class, () -> createPlan(date, "300750"));
    }

    @Test
    void allowedToTradeWithoutStopLossThrowsError() {
        CreateTradePlanDTO dto = new CreateTradePlanDTO(
                LocalDate.of(2026, 6, 8), "600519", "贵州茅台",
                PlanStatusEnum.ACTIVE.getCode(), "突破1700买入", null,
                null, null, null, null,
                true, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tradePlanService.create(dto));
        assertEquals(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }

    @Test
    void allowedToTradeWithoutPlannedPositionRatioThrowsError() {
        CreateTradePlanDTO dto = new CreateTradePlanDTO(
                LocalDate.of(2026, 6, 8), "600519", "贵州茅台",
                PlanStatusEnum.ACTIVE.getCode(), "突破1700买入", null,
                new BigDecimal("1650.00"), null, null, null,
                true, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tradePlanService.create(dto));
        assertEquals(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }

    @Test
    void takeProfitMustBeGreaterThanStopLoss() {
        CreateTradePlanDTO dto = new CreateTradePlanDTO(
                LocalDate.of(2026, 6, 9), "000001", "平安银行",
                PlanStatusEnum.DRAFT.getCode(), null, null,
                new BigDecimal("15.00"), new BigDecimal("12.00"),
                null, null, false, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tradePlanService.create(dto));
        assertEquals(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }

    @Test
    void invalidPlanStatusThrowsError() {
        CreateTradePlanDTO dto = new CreateTradePlanDTO(
                LocalDate.of(2026, 6, 8), "300750", "宁德时代",
                "INVALID", null, null, null, null, null, null,
                false, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tradePlanService.create(dto));
        assertEquals(ErrorCodeEnum.INVALID_ENUM_CODE, ex.getErrorCode());
    }

    private void createPlan(LocalDate date, String symbol) {
        tradePlanService.create(new CreateTradePlanDTO(
                date, symbol, "测试",
                PlanStatusEnum.DRAFT.getCode(),
                null, null, null, null, null, null,
                false, null, null
        ));
    }
}
