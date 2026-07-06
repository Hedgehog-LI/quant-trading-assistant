package com.quant.trade.portfolio;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.portfolio.enums.SnapshotChangeTypeEnum;
import com.quant.trade.portfolio.manager.PositionSnapshotComparisonManager;
import com.quant.trade.portfolio.model.PositionSnapshotDO;
import com.quant.trade.portfolio.model.PositionSnapshotItemDO;
import com.quant.trade.portfolio.vo.PositionSnapshotComparisonItemVO;
import com.quant.trade.portfolio.vo.PositionSnapshotComparisonVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 持仓快照对比纯计算 Manager 测试（v0.1.1 功能三）。
 */
class PositionSnapshotComparisonManagerTest {

    private final PositionSnapshotComparisonManager manager = new PositionSnapshotComparisonManager();

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 7, 4);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 5);

    @Test
    void allFiveChangeTypes() {
        PositionSnapshotDO base = confirmed(1L, "2026-07-04T15:00:00");
        PositionSnapshotDO target = confirmed(2L, "2026-07-05T15:00:00");

        List<PositionSnapshotItemDO> baseItems = List.of(
                item("INC", "加仓", 100, "10", "1000", "0"),
                item("RED", "减仓", 100, "10", "1000", "0"),
                item("CLO", "清仓", 100, "10", "1000", "0"),
                item("UNCH", "不变", 100, "10", "1000", "0"));
        List<PositionSnapshotItemDO> targetItems = List.of(
                item("NEW", "新增", 100, "10", "1000", "0"),
                item("INC", "加仓", 200, "10", "2000", "0"),
                item("RED", "减仓", 50, "10", "500", "0"),
                item("UNCH", "不变", 100, "10", "1000", "0"));

        PositionSnapshotComparisonVO result = manager.compare(base, baseItems, target, targetItems);

        Map<String, String> typeBySymbol = result.items().stream()
                .collect(Collectors.toMap(PositionSnapshotComparisonItemVO::symbol,
                        PositionSnapshotComparisonItemVO::changeType));
        assertEquals(SnapshotChangeTypeEnum.NEW.getCode(), typeBySymbol.get("NEW"));
        assertEquals(SnapshotChangeTypeEnum.INCREASED.getCode(), typeBySymbol.get("INC"));
        assertEquals(SnapshotChangeTypeEnum.REDUCED.getCode(), typeBySymbol.get("RED"));
        assertEquals(SnapshotChangeTypeEnum.CLOSED.getCode(), typeBySymbol.get("CLO"));
        assertEquals(SnapshotChangeTypeEnum.UNCHANGED.getCode(), typeBySymbol.get("UNCH"));
    }

    @Test
    void quantityAndAmountDeltaCorrect() {
        PositionSnapshotDO base = confirmed(1L, "2026-07-04T15:00:00");
        base.setTotalCostAmount(new BigDecimal("1000"));
        base.setTotalMarketValue(new BigDecimal("1000"));
        base.setTotalUnrealizedPnl(new BigDecimal("0"));
        base.setPositionCount(1);
        PositionSnapshotDO target = confirmed(2L, "2026-07-05T15:00:00");
        target.setTotalCostAmount(new BigDecimal("2000"));
        target.setTotalMarketValue(new BigDecimal("2500"));
        target.setTotalUnrealizedPnl(new BigDecimal("500"));
        target.setPositionCount(2);

        List<PositionSnapshotItemDO> baseItems = List.of(item("A", "A", 100, "10", "1000", "0"));
        List<PositionSnapshotItemDO> targetItems = List.of(item("A", "A", 200, "10", "2000", "500"));

        PositionSnapshotComparisonVO result = manager.compare(base, baseItems, target, targetItems);

        PositionSnapshotComparisonItemVO row = result.items().get(0);
        assertEquals(100L, row.quantityDelta());
        assertEquals(0, new BigDecimal("1000.000000").compareTo(row.marketValueDelta()));
        assertEquals(0, new BigDecimal("500.000000").compareTo(row.unrealizedPnlDelta()));

        assertEquals(0, new BigDecimal("1000.000000").compareTo(result.totalCostDelta()));
        assertEquals(0, new BigDecimal("1500.000000").compareTo(result.totalMarketValueDelta()));
        assertEquals(0, new BigDecimal("500.000000").compareTo(result.totalUnrealizedPnlDelta()));
        assertEquals(1, result.positionCountDelta());
    }

    @Test
    void sortByChangeTypeThenMarketValue() {
        PositionSnapshotDO base = confirmed(1L, "2026-07-04T15:00:00");
        PositionSnapshotDO target = confirmed(2L, "2026-07-05T15:00:00");
        // 两个 NEW：N2 目标市值大于 N1，应排在前
        List<PositionSnapshotItemDO> baseItems = List.of();
        List<PositionSnapshotItemDO> targetItems = List.of(
                item("N1", "n1", 100, "10", "1000", "0"),
                item("N2", "n2", 100, "10", "3000", "0"));

        PositionSnapshotComparisonVO result = manager.compare(base, baseItems, target, targetItems);
        assertEquals("N2", result.items().get(0).symbol());
        assertEquals("N1", result.items().get(1).symbol());
    }

    @Test
    void invalidTimeOrderThrows() {
        PositionSnapshotDO base = confirmed(2L, "2026-07-05T15:00:00");
        PositionSnapshotDO target = confirmed(1L, "2026-07-04T15:00:00");
        assertThrows(BusinessException.class, () -> manager.compare(base, List.of(), target, List.of()));
    }

    @Test
    void equalTimeThrows() {
        PositionSnapshotDO base = confirmed(1L, "2026-07-05T15:00:00");
        PositionSnapshotDO target = confirmed(2L, "2026-07-05T15:00:00");
        assertThrows(BusinessException.class, () -> manager.compare(base, List.of(), target, List.of()));
    }

    @Test
    void nonConfirmedThrows() {
        PositionSnapshotDO base = confirmed(1L, "2026-07-04T15:00:00");
        base.setSnapshotStatus("DRAFT");
        PositionSnapshotDO target = confirmed(2L, "2026-07-05T15:00:00");
        assertThrows(BusinessException.class, () -> manager.compare(base, List.of(), target, List.of()));
    }

    private PositionSnapshotDO confirmed(Long id, String time) {
        return PositionSnapshotDO.builder()
                .id(id)
                .snapshotDate(LocalDate.parse(time.substring(0, 10)))
                .snapshotTime(LocalDateTime.parse(time))
                .snapshotStatus("CONFIRMED")
                .totalCostAmount(BigDecimal.ZERO)
                .totalMarketValue(BigDecimal.ZERO)
                .totalUnrealizedPnl(BigDecimal.ZERO)
                .positionCount(0)
                .build();
    }

    private PositionSnapshotItemDO item(String symbol, String name, long qty,
                                        String costPrice, String marketValue, String pnl) {
        return PositionSnapshotItemDO.builder()
                .symbol(symbol)
                .name(name)
                .holdingQuantity(qty)
                .costPrice(new BigDecimal(costPrice))
                .currentPrice(new BigDecimal(costPrice))
                .marketValue(new BigDecimal(marketValue))
                .unrealizedPnl(new BigDecimal(pnl))
                .build();
    }
}
