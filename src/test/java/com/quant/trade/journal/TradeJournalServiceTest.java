package com.quant.trade.journal;

import com.quant.trade.common.enums.ReviewStatusEnum;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.dto.UpdateReviewStatusDTO;
import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.journal.vo.TradeJournalVO;
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
class TradeJournalServiceTest {

    @Autowired
    private TradeJournalService tradeJournalService;

    @Test
    void createBuySuccess() {
        TradeJournalVO vo = createBuy();
        assertNotNull(vo.id());
        assertEquals(TradeSideEnum.BUY.getCode(), vo.side());
        assertEquals(ReviewStatusEnum.PENDING.getCode(), vo.reviewStatus());
        assertEquals(List.of("CALM"), vo.emotionTags());
    }

    @Test
    void createSellSuccess() {
        CreateTradeJournalDTO dto = new CreateTradeJournalDTO(
                LocalDate.of(2026, 6, 8), null, "300750", "宁德时代",
                TradeSideEnum.SELL.getCode(), new BigDecimal("230.00"), 100L, null, null,
                "止盈卖出", null, null, true, null, null, null
        );

        TradeJournalVO vo = tradeJournalService.create(dto);
        assertEquals(TradeSideEnum.SELL.getCode(), vo.side());
    }

    @Test
    void invalidSideThrowsError() {
        CreateTradeJournalDTO dto = new CreateTradeJournalDTO(
                LocalDate.of(2026, 6, 8), null, "300750", "宁德时代",
                "INVALID", new BigDecimal("220.00"), 100L, null, null,
                null, null, null, null, null, null, null
        );

        assertThrows(BusinessException.class, () -> tradeJournalService.create(dto));
    }

    @Test
    void buyWithoutStopLossReturnsWarning() {
        CreateTradeJournalDTO dto = new CreateTradeJournalDTO(
                LocalDate.of(2026, 6, 8), null, "600519", "贵州茅台",
                TradeSideEnum.BUY.getCode(), new BigDecimal("1700.00"), 10L, null, null,
                "跟风买入", null, null, null, null, null, null
        );

        TradeJournalVO vo = tradeJournalService.create(dto);
        assertFalse(vo.warnings().isEmpty());
        assertTrue(vo.warnings().get(0).contains("止损"));
    }

    @Test
    void updateReviewStatusSuccess() {
        TradeJournalVO created = createBuy();
        TradeJournalVO updated = tradeJournalService.updateReviewStatus(
                created.id(), new UpdateReviewStatusDTO(ReviewStatusEnum.REVIEWED.getCode()));
        assertEquals(ReviewStatusEnum.REVIEWED.getCode(), updated.reviewStatus());
    }

    @Test
    void invalidReviewStatusThrowsError() {
        assertThrows(BusinessException.class, () ->
                tradeJournalService.updateReviewStatus(1L,
                        new UpdateReviewStatusDTO("INVALID")));
    }

    @Test
    void amountAutoCalculated() {
        TradeJournalVO vo = createBuy();
        assertNotNull(vo.amount());
        assertEquals(0, new BigDecimal("22050.000000").compareTo(vo.amount()));
    }

    private TradeJournalVO createBuy() {
        CreateTradeJournalDTO dto = new CreateTradeJournalDTO(
                LocalDate.of(2026, 6, 8), null, "300750", "宁德时代",
                TradeSideEnum.BUY.getCode(), new BigDecimal("220.50"), 100L,
                new BigDecimal("0.10"), null, "突破220，放量买入",
                new BigDecimal("210.00"), new BigDecimal("240.00"),
                true, List.of("CALM"), null, null
        );
        return tradeJournalService.create(dto);
    }
}
