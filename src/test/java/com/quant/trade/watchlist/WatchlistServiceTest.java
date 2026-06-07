package com.quant.trade.watchlist;

import com.quant.trade.common.enums.AttentionLevelEnum;
import com.quant.trade.common.enums.MarketTypeEnum;
import com.quant.trade.common.enums.TradeStyleEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.watchlist.dto.CreateWatchlistDTO;
import com.quant.trade.watchlist.dto.UpdateEnabledDTO;
import com.quant.trade.watchlist.service.WatchlistService;
import com.quant.trade.watchlist.vo.WatchlistVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自选股服务测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WatchlistServiceTest {

    @Autowired
    private WatchlistService watchlistService;

    @Test
    void createWatchlistSuccess() {
        CreateWatchlistDTO dto = new CreateWatchlistDTO(
                "300750", "宁德时代",
                MarketTypeEnum.A_SHARE.getCode(), "新能源",
                "关注锂电池龙头反弹",
                TradeStyleEnum.DO_T.getCode(),
                AttentionLevelEnum.HIGH.getCode(),
                new BigDecimal("210.00"), new BigDecimal("240.00"),
                new BigDecimal("200.00"), "注意大盘系统性风险"
        );

        WatchlistVO vo = watchlistService.create(dto);
        assertNotNull(vo.id());
        assertEquals("300750", vo.symbol());
        assertTrue(vo.enabled());
    }

    @Test
    void symbolIsTrimmedAndUppercased() {
        CreateWatchlistDTO dto = new CreateWatchlistDTO(
                "  600519  ", "贵州茅台",
                MarketTypeEnum.A_SHARE.getCode(), null, null, null, null,
                null, null, null, null
        );

        WatchlistVO vo = watchlistService.create(dto);
        assertEquals("600519", vo.symbol());
    }

    @Test
    void duplicateSymbolThrowsError() {
        CreateWatchlistDTO dto1 = new CreateWatchlistDTO(
                "600519", "贵州茅台", null, null, null, null, null,
                null, null, null, null
        );
        watchlistService.create(dto1);

        CreateWatchlistDTO dto2 = new CreateWatchlistDTO(
                "600519", "贵州茅台2", null, null, null, null, null,
                null, null, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> watchlistService.create(dto2));
        assertEquals(ErrorCodeEnum.DUPLICATE_RESOURCE, ex.getErrorCode());
    }

    @Test
    void invalidAttentionLevelThrowsError() {
        CreateWatchlistDTO dto = new CreateWatchlistDTO(
                "000001", "平安银行", null, null, null, null,
                "INVALID_LEVEL", null, null, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> watchlistService.create(dto));
        assertEquals(ErrorCodeEnum.INVALID_ENUM_CODE, ex.getErrorCode());
    }

    @Test
    void invalidTradeStyleThrowsError() {
        CreateWatchlistDTO dto = new CreateWatchlistDTO(
                "000002", "万科A", null, null, null, "INVALID_STYLE",
                null, null, null, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> watchlistService.create(dto));
        assertEquals(ErrorCodeEnum.INVALID_ENUM_CODE, ex.getErrorCode());
    }

    @Test
    void resistancePriceMustBeGreaterThanSupportPrice() {
        CreateWatchlistDTO dto = new CreateWatchlistDTO(
                "000003", "测试股", null, null, null, null, null,
                new BigDecimal("240.00"), new BigDecimal("210.00"),
                null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> watchlistService.create(dto));
        assertEquals(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }

    @Test
    void disableWatchlistSuccess() {
        CreateWatchlistDTO dto = new CreateWatchlistDTO(
                "000001", "平安银行", null, null, null, null, null,
                null, null, null, null
        );
        WatchlistVO created = watchlistService.create(dto);

        WatchlistVO disabled = watchlistService.updateEnabled(
                created.id(), new UpdateEnabledDTO(false));
        assertFalse(disabled.enabled());
    }
}
