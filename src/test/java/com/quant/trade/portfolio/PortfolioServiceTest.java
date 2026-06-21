package com.quant.trade.portfolio;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.portfolio.dto.PriceSnapshotDTO;
import com.quant.trade.portfolio.service.PortfolioService;
import com.quant.trade.portfolio.vo.ClosedTradeVO;
import com.quant.trade.portfolio.vo.PortfolioSummaryVO;
import com.quant.trade.portfolio.vo.PositionVO;
import com.quant.trade.portfolio.vo.PriceSnapshotVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 持仓账本端到端集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PortfolioServiceTest {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeJournalService tradeJournalService;

    /** 场景 8：全周期 summary/positions/closed-trades 聚合正确。 */
    @Test
    void fullCycleAggregatesCorrectly() {
        buy("000001", "2026-01-01", "10", 100);
        sell("000001", "2026-01-11", "12", 100);
        buy("000002", "2026-01-01", "20", 200);
        upsertPrice("000001", "11", "2026-01-12");
        upsertPrice("000002", "22", "2026-01-12");

        PortfolioSummaryVO s = portfolioService.getSummary();
        // 已实现 A：12*100 - 10*100 = 200
        assertEquals(0, new BigDecimal("200.000000").compareTo(s.realizedPnl()));
        // 浮动 B：成本 20*200=4000，市值 22*200=4400，+400
        assertEquals(0, new BigDecimal("400.000000").compareTo(s.unrealizedPnl()));
        assertEquals(1, s.closedTradeCount());
        assertEquals(1, s.winCount());

        // A 已清仓，只剩 B 持仓
        List<PositionVO> positions = portfolioService.getPositions();
        assertEquals(1, positions.size());
        assertEquals("000002", positions.get(0).symbol());

        List<ClosedTradeVO> closed = portfolioService.getClosedTrades(null, null, null);
        assertEquals(1, closed.size());
    }

    /** 场景 9：超持仓 symbol 降级，其余股票正常统计。 */
    @Test
    void oversoldSymbolDegradesButOthersStillWork() {
        buy("000001", "2026-01-01", "10", 100);
        sell("000001", "2026-01-11", "12", 100);
        buy("000002", "2026-01-01", "10", 100);
        sell("000002", "2026-01-11", "12", 150);

        PortfolioSummaryVO s = portfolioService.getSummary();
        // 只含 A 的已实现 200，B 异常不计入统计
        assertEquals(0, new BigDecimal("200.000000").compareTo(s.realizedPnl()));
        assertEquals(1, s.closedTradeCount());
        assertFalse(s.warnings().isEmpty());
    }

    /** 场景 10：缺价 symbol 不计入浮动盈亏，持仓浮动字段为 null。 */
    @Test
    void missingPriceExcludedFromSummary() {
        buy("000001", "2026-01-01", "10", 100);

        PortfolioSummaryVO s = portfolioService.getSummary();
        assertEquals(0, new BigDecimal("0").compareTo(s.unrealizedPnl()));

        List<PositionVO> positions = portfolioService.getPositions();
        assertEquals(1, positions.size());
        assertNull(positions.get(0).currentPrice());
        assertNull(positions.get(0).unrealizedPnl());
    }

    /** 场景 11：单股票详情遇超持仓，抛 INSUFFICIENT_HOLDING。 */
    @Test
    void symbolDetailOversoldThrows() {
        buy("000001", "2026-01-01", "10", 100);
        sell("000001", "2026-01-11", "12", 150);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> portfolioService.getSymbolDetail("000001"));
        assertEquals(ErrorCodeEnum.INSUFFICIENT_HOLDING, ex.getErrorCode());
    }

    /** 场景 12：price upsert 同 symbol+date 覆盖，不同 date 新增。 */
    @Test
    void priceUpsertInsertThenUpdate() {
        PriceSnapshotVO v1 = upsertPrice("000001", "10", "2026-01-01");
        assertNotNull(v1.id());
        PriceSnapshotVO v2 = upsertPrice("000001", "12", "2026-01-01");
        assertEquals(v1.id(), v2.id());
        assertEquals(0, new BigDecimal("12.000000").compareTo(v2.currentPrice()));
        PriceSnapshotVO v3 = upsertPrice("000001", "15", "2026-01-05");
        assertNotEquals(v1.id(), v3.id());
        assertEquals(2, portfolioService.listPrices().size());
    }

    /** 场景 13：持仓计算用最新价（MAX price_date）。 */
    @Test
    void priceUpsertLatestUsedInPositions() {
        buy("000001", "2026-01-01", "10", 100);
        upsertPrice("000001", "11", "2026-01-05");
        upsertPrice("000001", "13", "2026-01-10");

        List<PositionVO> positions = portfolioService.getPositions();
        PositionVO p = positions.get(0);
        assertEquals(0, new BigDecimal("13.000000").compareTo(p.currentPrice()));
        // 市值 1300，成本 1000，浮动 300
        assertEquals(0, new BigDecimal("300.000000").compareTo(p.unrealizedPnl()));
    }

    /** 场景 14：closed-trades 按 symbol 与 sellDate 区间过滤。 */
    @Test
    void closedTradesFilterBySymbolAndDate() {
        buy("000001", "2026-01-01", "10", 100);
        sell("000001", "2026-01-11", "12", 100);
        buy("000002", "2026-01-01", "10", 100);
        sell("000002", "2026-02-15", "12", 100);

        List<ClosedTradeVO> bySymbol = portfolioService.getClosedTrades("000001", null, null);
        assertEquals(1, bySymbol.size());
        assertEquals("000001", bySymbol.get(0).symbol());

        List<ClosedTradeVO> byDate = portfolioService.getClosedTrades(null,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertEquals(1, byDate.size());
        assertEquals("000002", byDate.get(0).symbol());
    }

    // ==================== helpers ====================

    private void buy(String symbol, String date, String price, long qty) {
        createJournal(symbol, "BUY", date, price, qty, null);
    }

    private void sell(String symbol, String date, String price, long qty) {
        createJournal(symbol, "SELL", date, price, qty, null);
    }

    private void createJournal(String symbol, String side, String date, String price, long qty, String totalFee) {
        tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.parse(date), null, symbol, symbol, side,
                new BigDecimal(price), qty,
                null, null, null, null, totalFee != null ? new BigDecimal(totalFee) : null,
                null, null, null, null, null, null, null, null, null));
    }

    private PriceSnapshotVO upsertPrice(String symbol, String price, String date) {
        return portfolioService.upsertPrice(new PriceSnapshotDTO(
                symbol, symbol, new BigDecimal(price), LocalDate.parse(date), null));
    }
}
