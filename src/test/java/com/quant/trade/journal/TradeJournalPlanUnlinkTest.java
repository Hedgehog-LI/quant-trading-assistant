package com.quant.trade.journal;

import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.dto.UpdateTradeJournalDTO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 交易记录关联计划的三态更新测试（v0.1.1 收尾修复）：
 * 创建关联 / 改关联 / 解除关联 / 不传字段保持原值。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TradeJournalPlanUnlinkTest {

    @Autowired
    private TradeJournalService tradeJournalService;

    @Autowired
    private TradePlanService tradePlanService;

    @Test
    void unlinkPlanClearsPlanId() {
        Long planId = createPlan("300750", LocalDate.of(2026, 7, 5));
        Long journalId = createJournalWithPlan("300750", planId);
        assertEquals(planId, tradeJournalService.getById(journalId).planId());

        // 解绑：unlinkPlan=true，planId 必须真正更新为 NULL
        tradeJournalService.update(journalId, updateDto(null, true));
        TradeJournalVO after = tradeJournalService.getById(journalId);
        assertNull(after.planId());
        assertNull(after.planDate());
        assertNull(after.planStatus());
    }

    @Test
    void changePlanAssociation() {
        Long plan1 = createPlan("300750", LocalDate.of(2026, 7, 5));
        Long plan2 = createPlan("300750", LocalDate.of(2026, 7, 6));
        Long journalId = createJournalWithPlan("300750", plan1);

        // 改关联到 plan2（planId 非 null + unlinkPlan!=true）
        tradeJournalService.update(journalId, updateDto(plan2, null));
        assertEquals(plan2, tradeJournalService.getById(journalId).planId());
    }

    @Test
    void noPlanFieldsKeepsOriginalPlanId() {
        Long planId = createPlan("300750", LocalDate.of(2026, 7, 5));
        Long journalId = createJournalWithPlan("300750", planId);

        // 既不传 planId 也不传 unlinkPlan：保持原值
        tradeJournalService.update(journalId, updateDto(null, null));
        assertEquals(planId, tradeJournalService.getById(journalId).planId());
    }

    @Test
    void unlinkPlanOverridesPlanId() {
        Long plan1 = createPlan("300750", LocalDate.of(2026, 7, 5));
        Long plan2 = createPlan("300750", LocalDate.of(2026, 7, 6));
        Long journalId = createJournalWithPlan("300750", plan1);

        // 同时传 planId 和 unlinkPlan=true：以 unlinkPlan 为准（解绑）
        tradeJournalService.update(journalId, updateDto(plan2, true));
        assertNull(tradeJournalService.getById(journalId).planId());
    }

    private Long createPlan(String symbol, LocalDate date) {
        TradePlanVO vo = tradePlanService.create(new CreateTradePlanDTO(
                date, symbol, symbol, "ACTIVE",
                "买入条件", "卖出条件",
                new BigDecimal("9.50"), new BigDecimal("11.00"),
                new BigDecimal("0.10"), new BigDecimal("500"),
                true, null, null));
        return vo.id();
    }

    private Long createJournalWithPlan(String symbol, Long planId) {
        TradeJournalVO vo = tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.of(2026, 7, 5), null, symbol, symbol,
                TradeSideEnum.BUY.getCode(), new BigDecimal("10.00"), 100L,
                null, null, null, null, null,
                null, planId, "测试", new BigDecimal("9.50"), new BigDecimal("11.00"),
                true, null, null, null));
        return vo.id();
    }

    private UpdateTradeJournalDTO updateDto(Long planId, Boolean unlinkPlan) {
        return new UpdateTradeJournalDTO(
                TradeSideEnum.BUY.getCode(),
                new BigDecimal("10.00"),
                100L,
                null, null, null, null, null,
                null,
                planId,
                null,
                new BigDecimal("9.50"),
                new BigDecimal("11.00"),
                true,
                null, null,
                null,
                null,
                unlinkPlan);
    }
}
