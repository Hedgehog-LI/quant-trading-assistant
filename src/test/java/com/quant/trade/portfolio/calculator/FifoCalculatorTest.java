package com.quant.trade.portfolio.calculator;

import com.quant.trade.journal.flow.TradeFlowItem;
import com.quant.trade.portfolio.vo.ClosedTradeVO;
import com.quant.trade.portfolio.vo.PortfolioSummaryVO;
import com.quant.trade.portfolio.vo.PositionVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIFO 计算器纯单元测试（脱离 Spring 容器，直接 new，毫秒级）。
 */
class FifoCalculatorTest {

    private final FifoCalculatorManager calculator = new FifoCalculatorManager();

    /** 场景 1：单笔买入后全部卖出，精确配对。 */
    @Test
    void oneBuyOneSellMatchesExactly() {
        LocalDate buyDate = LocalDate.of(2026, 1, 1);
        LocalDate sellDate = LocalDate.of(2026, 1, 11);
        List<TradeFlowItem> flows = List.of(
                buy(1, buyDate, "10", 100, null),
                sell(2, sellDate, "12", 100, null));

        FifoCalculatorManager.SymbolCalcResult r = calculator.calculateSymbol(flows, null, sellDate);

        assertFalse(r.abnormal());
        assertEquals(1, r.closedTrades().size());
        ClosedTradeVO c = r.closedTrades().get(0);
        // 成本 1000，净收入 1200，盈利 200
        assertEquals(0, new BigDecimal("200.000000").compareTo(c.realizedPnl()));
        assertEquals(0, new BigDecimal("20.000000").compareTo(c.returnPoint()));
        assertEquals(10L, c.holdingDays());
        assertTrue(c.profitable());
        // 全部卖出后无持仓
        assertNull(r.position());
    }

    /** 场景 2：卖出消耗多个买入批次，buyJournalIds 多值，holdingDays 取最早买入日。 */
    @Test
    void sellConsumesMultipleBuyLots() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 5);
        LocalDate sellDate = LocalDate.of(2026, 1, 11);
        List<TradeFlowItem> flows = List.of(
                buy(1, d1, "10", 100, null),
                buy(2, d2, "12", 100, null),
                sell(3, sellDate, "13", 150, null));

        FifoCalculatorManager.SymbolCalcResult r = calculator.calculateSymbol(flows, null, sellDate);

        assertFalse(r.abnormal());
        assertEquals(1, r.closedTrades().size());
        ClosedTradeVO c = r.closedTrades().get(0);
        assertEquals(150L, c.quantity());
        assertEquals(2, c.buyJournalIds().size());
        assertEquals(1L, c.buyJournalIds().get(0));
        assertEquals(2L, c.buyJournalIds().get(1));
        // 取最早买入批次日期 d1 -> 1月1日到1月11日 = 10 天
        assertEquals(10L, c.holdingDays());
        // 成本 100*10 + 50*12 = 1600，净收入 150*13 = 1950，盈利 350
        assertEquals(0, new BigDecimal("350.000000").compareTo(c.realizedPnl()));
    }

    /** 场景 3：买入被多次部分卖出，remainingQty 递减。 */
    @Test
    void buyConsumedByMultipleSells() {
        LocalDate buyDate = LocalDate.of(2026, 1, 1);
        LocalDate sell1 = LocalDate.of(2026, 1, 6);
        LocalDate sell2 = LocalDate.of(2026, 1, 11);
        List<TradeFlowItem> flows = List.of(
                buy(1, buyDate, "10", 300, null),
                sell(2, sell1, "11", 100, null),
                sell(3, sell2, "12", 200, null));

        FifoCalculatorManager.SymbolCalcResult r = calculator.calculateSymbol(flows, null, sell2);

        assertFalse(r.abnormal());
        assertEquals(2, r.closedTrades().size());
        assertNull(r.position());
        // 第 1 笔：成本 100*10=1000，净收入 100*11=1100，盈利 100
        assertEquals(0, new BigDecimal("100.000000").compareTo(r.closedTrades().get(0).realizedPnl()));
        // 第 2 笔：成本 200*10=2000，净收入 200*12=2400，盈利 400
        assertEquals(0, new BigDecimal("400.000000").compareTo(r.closedTrades().get(1).realizedPnl()));
    }

    /** 场景 4：卖出数量超过持仓，标记异常，统计置零。 */
    @Test
    void oversoldTriggersAbnormal() {
        LocalDate buyDate = LocalDate.of(2026, 1, 1);
        LocalDate sellDate = LocalDate.of(2026, 1, 11);
        List<TradeFlowItem> flows = List.of(
                buy(1, buyDate, "10", 100, null),
                sell(2, sellDate, "12", 150, null));

        FifoCalculatorManager.SymbolCalcResult r = calculator.calculateSymbol(flows, null, sellDate);

        assertTrue(r.abnormal());
        assertFalse(r.warnings().isEmpty());
        assertEquals(0, r.closedTradeCountForStats());
        assertEquals(0, new BigDecimal("0").compareTo(r.realizedPnlForSummary()));
    }

    /** 场景 5：未维护当前价，浮动盈亏字段为 null 并告警。 */
    @Test
    void missingCurrentPriceReturnsNulls() {
        LocalDate buyDate = LocalDate.of(2026, 1, 1);
        LocalDate today = LocalDate.of(2026, 1, 11);
        List<TradeFlowItem> flows = List.of(buy(1, buyDate, "10", 100, null));

        FifoCalculatorManager.SymbolCalcResult r = calculator.calculateSymbol(flows, null, today);

        PositionVO p = r.position();
        assertNotNull(p);
        assertEquals(100L, p.quantity());
        assertNull(p.currentPrice());
        assertNull(p.marketValue());
        assertNull(p.unrealizedPnl());
        assertNull(p.unrealizedReturnPoint());
        assertFalse(p.warnings().isEmpty());
        // 缺价不计入浮动盈亏汇总
        assertEquals(0, new BigDecimal("0").compareTo(r.unrealizedPnlForSummary()));
    }

    /** 场景 6：手续费影响 realizedPnl 与 totalFee。 */
    @Test
    void feesAppliedToBuyAndSell() {
        LocalDate buyDate = LocalDate.of(2026, 1, 1);
        LocalDate sellDate = LocalDate.of(2026, 1, 11);
        List<TradeFlowItem> flows = List.of(
                buy(1, buyDate, "10", 100, "5"),
                sell(2, sellDate, "12", 100, "3"));

        FifoCalculatorManager.SymbolCalcResult r = calculator.calculateSymbol(flows, null, sellDate);

        ClosedTradeVO c = r.closedTrades().get(0);
        // 买入成本 10*100+5=1005，卖出净收入 12*100-3=1197，盈利 192
        assertEquals(0, new BigDecimal("192.000000").compareTo(c.realizedPnl()));
        // totalFee = 买入费摊 5 + 卖出费 3 = 8
        assertEquals(0, new BigDecimal("8.000000").compareTo(c.totalFee()));
    }

    /** 场景 7：无已结算交易时的零除保护 + 有价时的浮动盈亏。 */
    @Test
    void zeroDivisionGuardsWhenNoClosedTrades() {
        LocalDate buyDate = LocalDate.of(2026, 1, 1);
        LocalDate today = LocalDate.of(2026, 1, 11);
        List<TradeFlowItem> flows = List.of(buy(1, buyDate, "10", 100, null));

        FifoCalculatorManager.AllSymbolsResult r =
                calculator.calculateAll(flows, Map.of("600000", new BigDecimal("11")), today);

        PortfolioSummaryVO s = r.summary();
        assertEquals(0, s.closedTradeCount());
        assertEquals(0, new BigDecimal("0").compareTo(s.winRate()));
        assertEquals(0, new BigDecimal("0").compareTo(s.averageReturnPoint()));
        assertEquals(0, new BigDecimal("0").compareTo(s.averageHoldingDays()));
        // 市值 11*100=1100，成本 1000，浮动盈利 100
        assertEquals(0, new BigDecimal("100.000000").compareTo(s.unrealizedPnl()));
    }

    // ==================== helpers ====================

    private TradeFlowItem buy(long id, LocalDate date, String price, long qty, String totalFee) {
        return flow(id, date, "BUY", price, qty, totalFee);
    }

    private TradeFlowItem sell(long id, LocalDate date, String price, long qty, String totalFee) {
        return flow(id, date, "SELL", price, qty, totalFee);
    }

    private TradeFlowItem flow(long id, LocalDate date, String side, String price, long qty, String totalFee) {
        return new TradeFlowItem(id, date, null, "600000", "测试股", side,
                new BigDecimal(price), qty, null, null, null, null,
                totalFee != null ? new BigDecimal(totalFee) : null);
    }
}
