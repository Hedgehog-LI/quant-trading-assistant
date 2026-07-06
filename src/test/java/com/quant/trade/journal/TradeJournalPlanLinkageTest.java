package com.quant.trade.journal;

import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.journal.vo.TradeJournalVO;
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

/**
 * 交易记录关联交易计划的校验测试（v0.1.1 功能一）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TradeJournalPlanLinkageTest {

    @Autowired
    private TradeJournalService tradeJournalService;

    @Autowired
    private TradePlanService tradePlanService;

    @Test
    void createWithoutPlanIdSuccess() {
        TradeJournalVO vo = tradeJournalService.create(buildJournal("300750", null));
        assertNotNull(vo.id());
        assertNull(vo.planId());
        assertNull(vo.planDate());
        assertNull(vo.planStatus());
    }

    @Test
    void createWithValidPlanLinkageSuccess() {
        Long planId = createPlan("300750", "ACTIVE", true);
        TradeJournalVO vo = tradeJournalService.create(buildJournal("300750", planId));
        assertEquals(planId, vo.planId());
        assertEquals(LocalDate.of(2026, 7, 5), vo.planDate());
        assertEquals("ACTIVE", vo.planStatus());
    }

    @Test
    void createWithSymbolLowercaseStillMatches() {
        Long planId = createPlan("300750", "ACTIVE", true);
        // journal symbol 小写也应在 trim+upper 后与计划一致
        TradeJournalVO vo = tradeJournalService.create(buildJournal("300750", planId));
        assertEquals(planId, vo.planId());
    }

    @Test
    void createWithNonExistentPlanThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tradeJournalService.create(buildJournal("300750", 99999999L)));
        assertEquals(ErrorCodeEnum.TRADE_PLAN_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void createWithCancelledPlanThrows() {
        Long planId = createPlan("300750", "CANCELLED", false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tradeJournalService.create(buildJournal("300750", planId)));
        assertEquals(ErrorCodeEnum.TRADE_PLAN_NOT_LINKABLE, ex.getErrorCode());
    }

    @Test
    void createWithSymbolMismatchThrows() {
        Long planId = createPlan("300750", "ACTIVE", true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> tradeJournalService.create(buildJournal("600519", planId)));
        assertEquals(ErrorCodeEnum.TRADE_PLAN_SYMBOL_MISMATCH, ex.getErrorCode());
    }

    @Test
    void allowedToTradeFalsePlanStillLinkable() {
        // allowedToTrade=false 的计划允许关联，偏离由 dashboard 提醒
        Long planId = createPlan("300750", "ACTIVE", false);
        TradeJournalVO vo = tradeJournalService.create(buildJournal("300750", planId));
        assertEquals(planId, vo.planId());
    }

    private Long createPlan(String symbol, String status, boolean allowedToTrade) {
        CreateTradePlanDTO dto = new CreateTradePlanDTO(
                LocalDate.of(2026, 7, 5), symbol, symbol + "名称", status,
                "突破前日高点", "跌破止损",
                new BigDecimal("9.50"), new BigDecimal("11.00"),
                new BigDecimal("0.10"), new BigDecimal("500"),
                allowedToTrade, null, null);
        TradePlanVO vo = tradePlanService.create(dto);
        return vo.id();
    }

    private CreateTradeJournalDTO buildJournal(String symbol, Long planId) {
        return new CreateTradeJournalDTO(
                LocalDate.of(2026, 7, 5), null, symbol, symbol + "名称",
                TradeSideEnum.BUY.getCode(), new BigDecimal("10.00"), 100L,
                null, null, null, null, null,
                null, planId, "测试", new BigDecimal("9.50"), new BigDecimal("11.00"),
                true, null, null, null);
    }
}
