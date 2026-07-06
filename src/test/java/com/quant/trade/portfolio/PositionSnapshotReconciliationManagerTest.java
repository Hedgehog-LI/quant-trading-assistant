package com.quant.trade.portfolio;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.journal.flow.TradeFlowItem;
import com.quant.trade.portfolio.calculator.FifoCalculatorManager;
import com.quant.trade.portfolio.enums.ReconciliationStatusEnum;
import com.quant.trade.portfolio.manager.PositionSnapshotReconciliationManager;
import com.quant.trade.portfolio.model.PositionSnapshotDO;
import com.quant.trade.portfolio.model.PositionSnapshotItemDO;
import com.quant.trade.portfolio.vo.PositionSnapshotReconciliationItemVO;
import com.quant.trade.portfolio.vo.PositionSnapshotReconciliationVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 持仓快照与 FIFO 账本对账纯计算 Manager 测试（v0.1.1 功能四）。
 */
class PositionSnapshotReconciliationManagerTest {

    private final PositionSnapshotReconciliationManager manager =
            new PositionSnapshotReconciliationManager(new FifoCalculatorManager());

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    void matchedWhenQuantitiesEqual() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of(snapItem("300750", "X", 100, "10"));
        List<TradeFlowItem> flows = List.of(buy(1L, "2026-07-04", null, "300750", 100));

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);

        assertFalse(result.hasMismatch());
        assertEquals(1, result.matchedCount());
        assertEquals(0, result.mismatchCount());
        Map<String, String> statusBySymbol = result.items().stream()
                .collect(Collectors.toMap(PositionSnapshotReconciliationItemVO::symbol,
                        PositionSnapshotReconciliationItemVO::status));
        assertEquals(ReconciliationStatusEnum.MATCHED.getCode(), statusBySymbol.get("300750"));
    }

    @Test
    void quantityMismatchWhenDifferent() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of(snapItem("300750", "X", 200, "10"));
        List<TradeFlowItem> flows = List.of(buy(1L, "2026-07-04", null, "300750", 100));

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);

        assertTrue(result.hasMismatch());
        assertEquals(ReconciliationStatusEnum.QUANTITY_MISMATCH.getCode(),
                result.items().get(0).status());
        assertEquals(100L, result.items().get(0).quantityDifference());
    }

    @Test
    void snapshotOnlyWhenNoLedger() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of(snapItem("300750", "X", 100, "10"));
        List<TradeFlowItem> flows = List.of();

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);

        assertEquals(ReconciliationStatusEnum.SNAPSHOT_ONLY.getCode(),
                result.items().get(0).status());
        assertTrue(result.hasMismatch());
    }

    @Test
    void ledgerOnlyWhenNoSnapshotItem() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of();
        List<TradeFlowItem> flows = List.of(buy(1L, "2026-07-04", null, "300750", 100));

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);

        assertEquals(ReconciliationStatusEnum.LEDGER_ONLY.getCode(),
                result.items().get(0).status());
        assertTrue(result.hasMismatch());
    }

    @Test
    void emptySnapshotAndFlowsNoItems() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        PositionSnapshotReconciliationVO result = manager.reconcile(snap, List.of(), List.of());
        assertTrue(result.items().isEmpty());
        assertFalse(result.hasMismatch());
    }

    @Test
    void sameDayNullTradeTimeProducesWarning() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of(snapItem("300750", "X", 100, "10"));
        // 同日 + tradeTime 为空
        List<TradeFlowItem> flows = List.of(buy(1L, "2026-07-05", null, "300750", 100));

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);

        assertTrue(result.warnings().stream()
                .anyMatch(w -> w.contains("trade_time 缺失")));
        // 仍 MATCHED（数量一致）
        assertEquals(ReconciliationStatusEnum.MATCHED.getCode(), result.items().get(0).status());
    }

    @Test
    void nonConfirmedSnapshotThrows() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        snap.setSnapshotStatus("DRAFT");
        assertThrows(BusinessException.class, () -> manager.reconcile(snap, List.of(), List.of()));
    }

    @Test
    void ledgerAverageCostExposed() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of(snapItem("300750", "X", 100, "10"));
        // BUY 100 @ 10.00，账本均价 10.00
        List<TradeFlowItem> flows = List.of(buy(1L, "2026-07-04", null, "300750", 100));

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);
        PositionSnapshotReconciliationItemVO row = result.items().get(0);
        assertNotNull(row.ledgerAverageCost());
        assertEquals(0, new BigDecimal("10.000000").compareTo(row.ledgerAverageCost()));
    }

    @Test
    void oversoldIsMismatchWithWarning() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of(snapItem("300750", "X", 100, "10"));
        // 买 100 卖 200 → 超卖，绝不判定为 MATCHED
        List<TradeFlowItem> flows = List.of(
                buy(1L, "2026-07-04", null, "300750", 100),
                sell(2L, "2026-07-04", null, "300750", 200));

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);

        assertTrue(result.hasMismatch());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("超卖")));
        assertEquals(ReconciliationStatusEnum.QUANTITY_MISMATCH.getCode(),
                result.items().get(0).status());
    }

    @Test
    void pureSellNoBuyWithEmptySnapshotIsMismatch() {
        // 空快照 + 直接卖出（无买入）→ 超卖，symbol 必须出现在结果集合
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of();
        List<TradeFlowItem> flows = List.of(sell(10L, "2026-07-04", null, "PURE", 100));

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);

        assertTrue(result.hasMismatch());
        assertFalse(result.items().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("超卖")));
        assertEquals(ReconciliationStatusEnum.QUANTITY_MISMATCH.getCode(),
                result.items().get(0).status());
    }

    @Test
    void partialThenOversellIsMismatch() {
        // BUY 100 → SELL 150（超卖 50）
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of(snapItem("OVER", "X", 100, "10"));
        List<TradeFlowItem> flows = List.of(
                buy(1L, "2026-07-03", null, "OVER", 100),
                sell(2L, "2026-07-04", null, "OVER", 150));

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);

        assertTrue(result.hasMismatch());
        assertEquals(ReconciliationStatusEnum.QUANTITY_MISMATCH.getCode(),
                result.items().get(0).status());
    }

    @Test
    void multipleBuysAndPartialSellsMatchByQuantity() {
        PositionSnapshotDO snap = confirmed(1L, "2026-07-05", "2026-07-05T15:00:00");
        List<PositionSnapshotItemDO> items = List.of(snapItem("300750", "X", 150, "10"));
        // 买 100 + 买 100 - 卖 50 = 150，与快照一致
        List<TradeFlowItem> flows = List.of(
                buy(1L, "2026-07-02", null, "300750", 100),
                buy(2L, "2026-07-03", null, "300750", 100),
                sell(3L, "2026-07-04", null, "300750", 50));

        PositionSnapshotReconciliationVO result = manager.reconcile(snap, items, flows);

        assertFalse(result.hasMismatch());
        assertEquals(ReconciliationStatusEnum.MATCHED.getCode(), result.items().get(0).status());
        assertEquals(150L, result.items().get(0).ledgerQuantity());
    }

    private PositionSnapshotDO confirmed(Long id, String date, String time) {
        return PositionSnapshotDO.builder()
                .id(id)
                .snapshotDate(LocalDate.parse(date))
                .snapshotTime(LocalDateTime.parse(time))
                .snapshotStatus("CONFIRMED")
                .build();
    }

    private PositionSnapshotItemDO snapItem(String symbol, String name, long qty, String costPrice) {
        return PositionSnapshotItemDO.builder()
                .symbol(symbol)
                .name(name)
                .holdingQuantity(qty)
                .costPrice(new BigDecimal(costPrice))
                .currentPrice(new BigDecimal(costPrice))
                .build();
    }

    private TradeFlowItem buy(Long id, String date, String time, String symbol, long qty) {
        LocalDateTime tradeTime = time == null ? null : LocalDateTime.parse(time);
        return new TradeFlowItem(
                id, LocalDate.parse(date), tradeTime, symbol, symbol,
                "BUY", new BigDecimal("10.00"), qty,
                ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    private TradeFlowItem sell(Long id, String date, String time, String symbol, long qty) {
        LocalDateTime tradeTime = time == null ? null : LocalDateTime.parse(time);
        return new TradeFlowItem(
                id, LocalDate.parse(date), tradeTime, symbol, symbol,
                "SELL", new BigDecimal("11.00"), qty,
                ZERO, ZERO, ZERO, ZERO, ZERO);
    }
}
