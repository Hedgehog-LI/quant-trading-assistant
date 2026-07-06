package com.quant.trade.journal;

import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.dto.UpdateTradeJournalDTO;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.journal.vo.TradeJournalVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 交易记录费用字段与 totalFee 自动计算测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TradeJournalFeeTest {

    @Autowired
    private TradeJournalService tradeJournalService;

    /** totalFee 为空时自动求和。 */
    @Test
    void totalFeeAutoSummedWhenNull() {
        TradeJournalVO vo = tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.of(2026, 1, 1), null, "000001", "T", TradeSideEnum.BUY.getCode(),
                new BigDecimal("10"), 100L,
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("0.5"), new BigDecimal("0.5"), null,
                null, null, null, null, null, null, null, null, null));
        // 1 + 2 + 0.5 + 0.5 = 4
        assertEquals(0, new BigDecimal("4.000000").compareTo(vo.totalFee()));
    }

    /** 传入 totalFee 时以其为准。 */
    @Test
    void totalFeeRespectedWhenProvided() {
        TradeJournalVO vo = tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.of(2026, 1, 1), null, "000001", "T", TradeSideEnum.BUY.getCode(),
                new BigDecimal("10"), 100L,
                new BigDecimal("1"), new BigDecimal("2"), null, null, new BigDecimal("99"),
                null, null, null, null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("99.000000").compareTo(vo.totalFee()));
    }

    /** 全部费用为空时按 0 处理。 */
    @Test
    void nullFeesTreatedAsZero() {
        TradeJournalVO vo = tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.of(2026, 1, 1), null, "000001", "T", TradeSideEnum.BUY.getCode(),
                new BigDecimal("10"), 100L,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("0.000000").compareTo(vo.totalFee()));
        assertEquals(0, new BigDecimal("0.000000").compareTo(vo.commissionFee()));
    }

    /** 更新费用字段时 totalFee 重算。 */
    @Test
    void updateRecalculatesTotalFee() {
        // 创建：commissionFee=1，totalFee 自动=1
        TradeJournalVO created = tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.of(2026, 1, 1), null, "000001", "T", TradeSideEnum.BUY.getCode(),
                new BigDecimal("10"), 100L,
                new BigDecimal("1"), null, null, null, null,
                null, null, null, null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("1.000000").compareTo(created.totalFee()));

        // 更新 commissionFee=3，totalFee 重算=3
        TradeJournalVO updated = tradeJournalService.update(created.id(), new UpdateTradeJournalDTO(
                TradeSideEnum.BUY.getCode(), new BigDecimal("10"), 100L,
                new BigDecimal("3"), null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("3.000000").compareTo(updated.totalFee()));
        assertEquals(0, new BigDecimal("3.000000").compareTo(updated.commissionFee()));
    }

    /** 更新时清空全部费用字段：全量编辑语义下归一为 0 并落库，避免旧费用残留。 */
    @Test
    void updateClearsAllFeesWhenAllNull() {
        // 创建：commissionFee=5，totalFee 自动=5
        TradeJournalVO created = tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.of(2026, 1, 1), null, "000001", "T", TradeSideEnum.BUY.getCode(),
                new BigDecimal("10"), 100L,
                new BigDecimal("5"), null, null, null, null,
                null, null, null, null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("5.000000").compareTo(created.totalFee()));

        // 更新：清空全部费用字段（前端全量编辑清空输入框 → DTO 为 null）
        TradeJournalVO updated = tradeJournalService.update(created.id(), new UpdateTradeJournalDTO(
                TradeSideEnum.BUY.getCode(), new BigDecimal("10"), 100L,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("0.000000").compareTo(updated.commissionFee()));
        assertEquals(0, new BigDecimal("0.000000").compareTo(updated.stampTax()));
        assertEquals(0, new BigDecimal("0.000000").compareTo(updated.transferFee()));
        assertEquals(0, new BigDecimal("0.000000").compareTo(updated.otherFee()));
        assertEquals(0, new BigDecimal("0.000000").compareTo(updated.totalFee()));
    }

    /** 更新时传入 totalFee：以 totalFee 为准，不受明细合计影响。 */
    @Test
    void updateRespectsTotalFeeWhenProvided() {
        // 创建：commissionFee=1，stampTax=2，totalFee 自动=3
        TradeJournalVO created = tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.of(2026, 1, 1), null, "000001", "T", TradeSideEnum.BUY.getCode(),
                new BigDecimal("10"), 100L,
                new BigDecimal("1"), new BigDecimal("2"), null, null, null,
                null, null, null, null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("3.000000").compareTo(created.totalFee()));

        // 更新：明细合计=1+2=3，但 totalFee=99，以 totalFee 为准
        TradeJournalVO updated = tradeJournalService.update(created.id(), new UpdateTradeJournalDTO(
                TradeSideEnum.BUY.getCode(), new BigDecimal("10"), 100L,
                new BigDecimal("1"), new BigDecimal("2"), null, null, new BigDecimal("99"),
                null, null, null, null, null, null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("99.000000").compareTo(updated.totalFee()));
    }
}
