package com.quant.trade.review;

import com.quant.trade.common.enums.ReviewStatusEnum;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.journal.vo.TradeJournalVO;
import com.quant.trade.review.dto.CreateReviewDTO;
import com.quant.trade.review.dto.UpdateReviewDTO;
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

/**
 * 复盘关联一致性回算与删除保护测试（v0.1.1 功能二）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewConsistencyTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private TradeJournalService tradeJournalService;

    @Test
    void reviewCreateMarksJournalReviewed() {
        Long journalId = createJournal("300750");
        assertEquals(ReviewStatusEnum.PENDING.getCode(),
                tradeJournalService.getById(journalId).reviewStatus());

        createReview(List.of(journalId));

        assertEquals(ReviewStatusEnum.REVIEWED.getCode(),
                tradeJournalService.getById(journalId).reviewStatus());
    }

    @Test
    void reviewUpdateRemovingLinkRestoresPending() {
        Long journalId = createJournal("300750");
        Long reviewId = createReview(List.of(journalId));

        reviewService.update(reviewId, new UpdateReviewDTO(
                "改", null, null, null, null, null, null, null, List.of()));

        assertEquals(ReviewStatusEnum.PENDING.getCode(),
                tradeJournalService.getById(journalId).reviewStatus());
    }

    @Test
    void reviewUpdateSwitchesLinkFromAToB() {
        Long journalA = createJournal("300750");
        Long journalB = createJournal("600519");
        Long reviewId = createReview(List.of(journalA));

        reviewService.update(reviewId, new UpdateReviewDTO(
                "改", null, null, null, null, null, null, null, List.of(journalB)));

        assertEquals(ReviewStatusEnum.PENDING.getCode(),
                tradeJournalService.getById(journalA).reviewStatus());
        assertEquals(ReviewStatusEnum.REVIEWED.getCode(),
                tradeJournalService.getById(journalB).reviewStatus());
    }

    @Test
    void reviewDeleteRestoresPending() {
        Long journalId = createJournal("300750");
        Long reviewId = createReview(List.of(journalId));

        reviewService.delete(reviewId);

        assertEquals(ReviewStatusEnum.PENDING.getCode(),
                tradeJournalService.getById(journalId).reviewStatus());
    }

    @Test
    void deleteReferencedJournalRejected() {
        Long journalId = createJournal("300750");
        createReview(List.of(journalId));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tradeJournalService.delete(journalId));
        assertEquals(ErrorCodeEnum.JOURNAL_REFERENCED_BY_REVIEW, ex.getErrorCode());
    }

    @Test
    void deleteUnreferencedJournalSucceeds() {
        Long journalId = createJournal("300750");
        tradeJournalService.delete(journalId);
        assertThrows(BusinessException.class, () -> tradeJournalService.getById(journalId));
    }

    @Test
    void linkNonExistentJournalRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> createReview(List.of(99999999L)));
        assertEquals(ErrorCodeEnum.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void journalReferencedByMultipleReviewsStaysReviewed() {
        Long journalId = createJournal("300750");
        createReview(List.of(journalId));
        Long review2 = createReview(List.of(journalId));

        // 删除其中一条复盘，另一条仍引用 -> journal 仍 REVIEWED
        reviewService.delete(review2);
        assertEquals(ReviewStatusEnum.REVIEWED.getCode(),
                tradeJournalService.getById(journalId).reviewStatus());
    }

    private Long createJournal(String symbol) {
        TradeJournalVO vo = tradeJournalService.create(new CreateTradeJournalDTO(
                LocalDate.of(2026, 7, 5), null, symbol, symbol + "名称",
                TradeSideEnum.BUY.getCode(), new BigDecimal("10.00"), 100L,
                null, null, null, null, null,
                null, null, "测试", new BigDecimal("9.50"), null, true,
                null, null, null));
        return vo.id();
    }

    private Long createReview(List<Long> linkedJournalIds) {
        ReviewVO vo = reviewService.create(new CreateReviewDTO(
                LocalDate.of(2026, 7, 5), null, "测试复盘",
                null, null, null, null, null, null, null, linkedJournalIds));
        return vo.id();
    }
}
