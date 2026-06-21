package com.quant.trade.portfolio;

import com.quant.trade.portfolio.manager.PortfolioPriceManager;
import com.quant.trade.portfolio.model.PortfolioPriceSnapshotDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 手工当前价快照领域逻辑测试（manager 层）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PortfolioPriceServiceTest {

    @Autowired
    private PortfolioPriceManager portfolioPriceManager;

    /** upsert 同 symbol+date 覆盖。 */
    @Test
    void upsertInsertsThenUpdatesSameSymbolDate() {
        PortfolioPriceSnapshotDO a = portfolioPriceManager.upsert(snapshot("000001", "10", "2026-01-01"));
        assertNotNull(a.getId());
        PortfolioPriceSnapshotDO b = portfolioPriceManager.upsert(snapshot("000001", "12", "2026-01-01"));
        assertEquals(a.getId(), b.getId());
        assertEquals(0, new BigDecimal("12.000000").compareTo(b.getCurrentPrice()));
    }

    /** 取最新价按 MAX(price_date)。 */
    @Test
    void getLatestPricePicksMaxDate() {
        portfolioPriceManager.upsert(snapshot("000001", "10", "2026-01-01"));
        portfolioPriceManager.upsert(snapshot("000001", "15", "2026-01-10"));
        BigDecimal latest = portfolioPriceManager.getLatestPrice("000001");
        assertEquals(0, new BigDecimal("15.000000").compareTo(latest));
    }

    /** 跨 symbol 最新价映射。 */
    @Test
    void getLatestPriceMapAcrossSymbols() {
        portfolioPriceManager.upsert(snapshot("000001", "10", "2026-01-01"));
        portfolioPriceManager.upsert(snapshot("000002", "20", "2026-01-01"));
        Map<String, BigDecimal> map = portfolioPriceManager.getLatestPriceMap();
        assertEquals(2, map.size());
        assertEquals(0, new BigDecimal("10.000000").compareTo(map.get("000001")));
    }

    /** 未知 symbol 返回 null。 */
    @Test
    void getLatestPriceReturnsNullForUnknownSymbol() {
        assertNull(portfolioPriceManager.getLatestPrice("999999"));
    }

    private PortfolioPriceSnapshotDO snapshot(String symbol, String price, String date) {
        return PortfolioPriceSnapshotDO.builder()
                .symbol(symbol)
                .name(symbol)
                .currentPrice(new BigDecimal(price))
                .priceDate(LocalDate.parse(date))
                .build();
    }
}
