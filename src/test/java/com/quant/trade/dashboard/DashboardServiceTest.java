package com.quant.trade.dashboard;

import com.quant.trade.common.enums.AttentionLevelEnum;
import com.quant.trade.common.enums.MarketTypeEnum;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.common.enums.TradeStyleEnum;
import com.quant.trade.dashboard.service.DashboardService;
import com.quant.trade.dashboard.vo.DashboardTodayVO;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.review.dto.CreateReviewDTO;
import com.quant.trade.review.service.ReviewService;
import com.quant.trade.watchlist.dto.CreateWatchlistDTO;
import com.quant.trade.watchlist.service.WatchlistService;
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
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private TradeJournalService tradeJournalService;

    @Autowired
    private ReviewService reviewService;

    @Test
    void aggregatesRealData() {
        LocalDate date = LocalDate.of(2026, 6, 8);

        // 创建自选股
        watchlistService.create(new CreateWatchlistDTO(
                "300750", "宁德时代",
                MarketTypeEnum.A_SHARE.getCode(), null,
                null, TradeStyleEnum.DO_T.getCode(),
                AttentionLevelEnum.HIGH.getCode(),
                null, null, null, null
        ));

        // 创建交易记录（21 个参数：tradeDate..actualResult，含 5 个费用字段）
        tradeJournalService.create(new CreateTradeJournalDTO(
                date, null, "300750", "宁德时代",
                TradeSideEnum.BUY.getCode(),
                new BigDecimal("220.00"), 100L,
                null, null, null, null, null,
                null, null, null, new BigDecimal("210.00"), null,
                null, null, null, null
        ));

        // 创建复盘
        reviewService.create(new CreateReviewDTO(
                date, null, "每日总复盘", null, null, null,
                null, null, null, null, null
        ));

        DashboardTodayVO vo = dashboardService.getToday(date);

        assertEquals(date, vo.date());
        assertEquals(1, vo.enabledWatchlistCount());
        assertEquals(1, vo.todayJournalCount());
        assertEquals(1, vo.todayReviewCount());
        assertFalse(vo.highAttentionStocks().isEmpty());
    }

    @Test
    void pendingReviewCountIsCorrect() {
        LocalDate date = LocalDate.of(2026, 6, 8);

        tradeJournalService.create(new CreateTradeJournalDTO(
                date, null, "000001", "平安银行",
                TradeSideEnum.BUY.getCode(),
                new BigDecimal("15.00"), 100L,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        ));
        tradeJournalService.create(new CreateTradeJournalDTO(
                date, null, "000002", "万科A",
                TradeSideEnum.BUY.getCode(),
                new BigDecimal("10.00"), 200L,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        ));

        DashboardTodayVO vo = dashboardService.getToday(date);

        assertTrue(vo.pendingReviewCount() >= 2);
        assertFalse(vo.riskWarnings().isEmpty());
    }
}
