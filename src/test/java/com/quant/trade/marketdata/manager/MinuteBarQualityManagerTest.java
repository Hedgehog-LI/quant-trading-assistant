package com.quant.trade.marketdata.manager;

import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.model.StockMinuteBarDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 分钟 K 数据质量校验单测。 */
class MinuteBarQualityManagerTest {

    private final MinuteBarQualityManager manager = new MinuteBarQualityManager();

    @Test
    void validOhlcReturnsValid() {
        StockMinuteBarDO bar = baseBar();
        assertEquals(WorkbenchConstants.QUALITY_VALID, manager.validate(bar));
    }

    @Test
    void highLowerThanOpenReturnsRejected() {
        StockMinuteBarDO bar = baseBar();
        bar.setHighPrice(new BigDecimal("10.00"));
        bar.setOpenPrice(new BigDecimal("11.00"));
        assertEquals(WorkbenchConstants.QUALITY_REJECTED, manager.validate(bar));
    }

    @Test
    void lowHigherThanCloseReturnsRejected() {
        StockMinuteBarDO bar = baseBar();
        bar.setLowPrice(new BigDecimal("11.00"));
        bar.setClosePrice(new BigDecimal("10.00"));
        assertFalse(manager.isValidOhlc(bar));
        assertEquals(WorkbenchConstants.QUALITY_REJECTED, manager.validate(bar));
    }

    @Test
    void negativeVolumeReturnsRejected() {
        StockMinuteBarDO bar = baseBar();
        bar.setVolume(-100L);
        assertTrue(manager.hasNegativeVolumeOrAmount(bar));
        assertEquals(WorkbenchConstants.QUALITY_REJECTED, manager.validate(bar));
    }

    @Test
    void negativeAmountReturnsRejected() {
        StockMinuteBarDO bar = baseBar();
        bar.setAmount(new BigDecimal("-1.5"));
        assertEquals(WorkbenchConstants.QUALITY_REJECTED, manager.validate(bar));
    }

    @Test
    void negativeTurnoverRateReturnsSuspect() {
        StockMinuteBarDO bar = baseBar();
        bar.setTurnoverRate(new BigDecimal("-0.01"));
        assertEquals(WorkbenchConstants.QUALITY_SUSPECT, manager.validate(bar));
    }

    @Test
    void nullBarReturnsRejected() {
        assertEquals(WorkbenchConstants.QUALITY_REJECTED, manager.validate(null));
    }

    @Test
    void isBarInSessionRegularHours() {
        // 10:30 落在 AM 0930-1130
        LocalDateTime time = LocalDateTime.of(2026, 7, 10, 10, 30);
        List<int[]> windows = List.of(new int[]{930, 1130, 0}, new int[]{1300, 1500, 0}, new int[]{915, 925, 1});
        assertTrue(manager.isBarInSession(time, windows, false));
    }

    @Test
    void isBarInSessionLunchBreak() {
        // 12:00 不落在任何窗口
        LocalDateTime time = LocalDateTime.of(2026, 7, 10, 12, 0);
        List<int[]> windows = List.of(new int[]{930, 1130, 0}, new int[]{1300, 1500, 0});
        assertFalse(manager.isBarInSession(time, windows, false));
    }

    @Test
    void isBarInSessionAuctionExcluded() {
        // 09:20 落在集合竞价窗口，但 includeAuction=false
        LocalDateTime time = LocalDateTime.of(2026, 7, 10, 9, 20);
        List<int[]> windows = List.of(new int[]{930, 1130, 0}, new int[]{915, 925, 1});
        assertFalse(manager.isBarInSession(time, windows, false));
    }

    @Test
    void isBarInSessionAuctionIncluded() {
        LocalDateTime time = LocalDateTime.of(2026, 7, 10, 9, 20);
        List<int[]> windows = List.of(new int[]{930, 1130, 0}, new int[]{915, 925, 1});
        assertTrue(manager.isBarInSession(time, windows, true));
    }

    @Test
    void contentConflictDetected() {
        StockMinuteBarDO existing = baseBar();
        StockMinuteBarDO incoming = baseBar();
        incoming.setClosePrice(new BigDecimal("11.00"));
        assertTrue(manager.isContentConflict(existing, incoming));
    }

    @Test
    void contentConsistentNoConflict() {
        StockMinuteBarDO existing = baseBar();
        StockMinuteBarDO incoming = baseBar();
        assertFalse(manager.isContentConflict(existing, incoming));
    }

    private StockMinuteBarDO baseBar() {
        return StockMinuteBarDO.builder()
                .canonicalSymbol("SH.600519")
                .tradeDate(java.time.LocalDate.of(2026, 7, 10))
                .barStartTime(LocalDateTime.of(2026, 7, 10, 10, 0))
                .barEndTime(LocalDateTime.of(2026, 7, 10, 10, 30))
                .intervalType(WorkbenchConstants.INTERVAL_30M)
                .sessionType(WorkbenchConstants.SESSION_AM)
                .openPrice(new BigDecimal("10.50"))
                .highPrice(new BigDecimal("11.00"))
                .lowPrice(new BigDecimal("10.20"))
                .closePrice(new BigDecimal("10.80"))
                .volume(5000L)
                .amount(new BigDecimal("54000"))
                .turnoverRate(new BigDecimal("0.05"))
                .adjustType("NONE")
                .dataSource("LONGPORT")
                .qualityStatus(WorkbenchConstants.QUALITY_VALID)
                .build();
    }
}
