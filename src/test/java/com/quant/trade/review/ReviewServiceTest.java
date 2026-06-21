package com.quant.trade.review;

import com.quant.trade.common.enums.ReviewStatusEnum;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.journal.vo.TradeJournalVO;
import com.quant.trade.review.dto.CreateReviewDTO;
import com.quant.trade.review.service.ReviewService;
import com.quant.trade.review.vo.ReviewVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewServiceTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private TradeJournalService tradeJournalService;

    @Test
    void createDailyReviewSuccess() {
        CreateReviewDTO dto = new CreateReviewDTO(
                LocalDate.of(2026, 6, 8), null,
                "6月8日每日总复盘", "大盘震荡", null, null,
                null, null, null, null, null
        );

        ReviewVO vo = reviewService.create(dto);
        assertNotNull(vo.id());
        assertNull(vo.symbol());
    }

    @Test
    void createStockReviewWithLinkedJournals() {
        TradeJournalVO journal = createJournal();
        assertEquals(ReviewStatusEnum.PENDING.getCode(), journal.reviewStatus());

        CreateReviewDTO dto = new CreateReviewDTO(
                LocalDate.of(2026, 6, 8), "300750",
                "宁德时代复盘", null, null, null, null, null,
                null, null, List.of(journal.id())
        );

        ReviewVO vo = reviewService.create(dto);
        assertEquals(List.of(journal.id()), vo.linkedJournalIds());

        TradeJournalVO updated = tradeJournalService.getById(journal.id());
        assertEquals(ReviewStatusEnum.REVIEWED.getCode(), updated.reviewStatus());
    }

    @Test
    void linkedNonExistentJournalThrowsError() {
        CreateReviewDTO dto = new CreateReviewDTO(
                LocalDate.of(2026, 6, 8), "300750",
                "复盘", null, null, null, null, null,
                null, null, List.of(99999L)
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reviewService.create(dto));
        assertEquals(ErrorCodeEnum.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void listByDate() {
        reviewService.create(new CreateReviewDTO(
                LocalDate.of(2026, 6, 8), "300750", "复盘",
                null, null, null, null, null, null, null, null
        ));

        var results = reviewService.list(LocalDate.of(2026, 6, 8), null);
        assertFalse(results.isEmpty());
    }

    private TradeJournalVO createJournal() {
        return tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.of(2026, 6, 8), null, "300750", "宁德时代",
                TradeSideEnum.BUY.getCode(), new BigDecimal("220.00"), 100L,
                null, null, null, null, null,
                null, null, "突破买入", new BigDecimal("210.00"), null, true,
                null, null, null
        ));
    }
}
