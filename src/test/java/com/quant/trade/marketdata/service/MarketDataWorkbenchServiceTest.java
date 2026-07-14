package com.quant.trade.marketdata.service;

import com.quant.trade.marketdata.constant.WorkbenchConstants;
import com.quant.trade.marketdata.dao.*;
import com.quant.trade.marketdata.dto.CreateSyncPlanDTO;
import com.quant.trade.marketdata.dto.MinuteBarUpsertDTO;
import com.quant.trade.marketdata.dto.UpdateSyncPlanDTO;
import com.quant.trade.marketdata.manager.MinuteBarQualityManager;
import com.quant.trade.marketdata.manager.TradingSessionManager;
import com.quant.trade.marketdata.model.MarketDataSyncPlanDO;
import com.quant.trade.marketdata.model.MarketDataWatermarkDO;
import com.quant.trade.marketdata.model.StockMinuteBarDO;
import com.quant.trade.marketdata.vo.MarketDataSyncPlanVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/** 行情工作台 service 单测：分钟 K 写入幂等/冲突/质量校验 + 水位更新 + 计划 CRUD。 */
@ExtendWith(MockitoExtension.class)
class MarketDataWorkbenchServiceTest {

    @Mock private MarketQuoteService marketQuoteService;
    @Mock private StockMinuteBarMapper minuteBarMapper;
    @Mock private MarketTradingSessionMapper tradingSessionMapper;
    @Mock private MarketDataSyncPlanMapper syncPlanMapper;
    @Mock private MarketDataSyncTaskItemMapper taskItemMapper;
    @Mock private MarketDataWatermarkMapper watermarkMapper;
    @Mock private MarketDataAlertMapper alertMapper;
    @Mock private MarketDataSyncTaskMapper syncTaskMapper;
    @Spy private MinuteBarQualityManager qualityManager = new MinuteBarQualityManager();
    @Mock private TradingSessionManager tradingSessionManager;

    @InjectMocks private MarketDataWorkbenchService service;

    @BeforeEach
    void stubTradingSession() {
        // 默认 stub：2026-07-10 是交易日，bar 时间 10:00 在 AM 窗口内
        lenient().when(tradingSessionManager.isTradingDay(anyString(), any())).thenReturn(true);
        lenient().when(tradingSessionManager.getSessionWindows(anyString(), anyBoolean()))
                .thenReturn(List.of(new int[]{930, 1130, 0}, new int[]{1300, 1500, 0}));
    }

    @Test
    void upsertMinuteBarInsertsWhenNew() {
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(null);
        when(watermarkMapper.selectByUniqueKey(any(), any(), any(), any())).thenReturn(null);

        var result = service.upsertMinuteBar(validDto());

        assertEquals("INSERTED", result.result());
        verify(minuteBarMapper).insert(any());
        verify(watermarkMapper).insert(any());
    }

    @Test
    void upsertMinuteBarSkipsWhenDuplicate() {
        StockMinuteBarDO existing = validBar();
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(existing);

        var result = service.upsertMinuteBar(validDto());

        assertEquals("SKIPPED", result.result());
        verify(minuteBarMapper, never()).insert(any());
    }

    @Test
    void upsertMinuteBarConflictProducesAlertNotOverwrite() {
        StockMinuteBarDO existing = validBar();
        existing.setClosePrice(new BigDecimal("99.99")); // 与 incoming 不同
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(existing);

        var result = service.upsertMinuteBar(validDto());

        assertEquals("CONFLICT", result.result());
        verify(minuteBarMapper, never()).insert(any());
        verify(alertMapper).insert(any()); // 产生冲突 alert
    }

    @Test
    void upsertMinuteBarRejectedDoesNotInsert() {
        var dto = validDto();
        dto.setHighPrice(new BigDecimal("1.00")); // high < open → REJECTED
        dto.setOpenPrice(new BigDecimal("10.00"));

        var result = service.upsertMinuteBar(dto);

        assertEquals("REJECTED", result.result());
        verify(minuteBarMapper, never()).insert(any());
        verify(alertMapper).insert(any());
    }

    @Test
    void upsertUpdatesExistingWatermark() {
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(null);
        MarketDataWatermarkDO existingWm = MarketDataWatermarkDO.builder()
                .totalRows(10L).build();
        when(watermarkMapper.selectByUniqueKey(any(), any(), any(), any())).thenReturn(existingWm);

        service.upsertMinuteBar(validDto());

        verify(watermarkMapper).updateByUniqueKey(any());
        assertEquals(11L, existingWm.getTotalRows());
    }

    @Test
    void upsertRejectsNonTradingDay() {
        // 2026-07-11 周六非交易日
        lenient().when(tradingSessionManager.isTradingDay(anyString(), eq(java.time.LocalDate.of(2026, 7, 11))))
                .thenReturn(false);
        var dto = validDto();
        dto.setBarStartTime(LocalDateTime.of(2026, 7, 11, 10, 0));
        dto.setBarEndTime(LocalDateTime.of(2026, 7, 11, 10, 30));

        var result = service.upsertMinuteBar(dto);

        assertEquals("REJECTED", result.result());
        verify(minuteBarMapper, never()).insert(any());
        verify(alertMapper).insert(any());
    }

    @Test
    void upsertMarksSuspectWhenBarOutOfSession() {
        // 12:00 午休不在交易窗口
        when(minuteBarMapper.selectByUniqueKey(any(), any(), any(), any(), any())).thenReturn(null);
        var dto = validDto();
        dto.setBarStartTime(LocalDateTime.of(2026, 7, 10, 12, 0));
        dto.setBarEndTime(LocalDateTime.of(2026, 7, 10, 12, 30));

        var result = service.upsertMinuteBar(dto);

        assertEquals("INSERTED", result.result());
        assertEquals("SUSPECT", result.qualityStatus());
        verify(minuteBarMapper).insert(any());
        verify(alertMapper).insert(any());
    }

    @Test
    void createPlanPersistsAndReturnsVO() {
        CreateSyncPlanDTO dto = new CreateSyncPlanDTO();
        dto.setPlanName("茅台30M补档");
        dto.setTaskType("MINUTE_BAR_BACKFILL");
        dto.setProvider("LONGPORT");
        dto.setScopeJson("{\"symbols\":[\"SH.600519\"]}");
        dto.setIntervalType("30M");
        dto.setAdjustType("NONE");
        dto.setTriggerType("MANUAL");

        MarketDataSyncPlanVO vo = service.createPlan(dto);

        assertNotNull(vo);
        assertEquals("茅台30M补档", vo.getPlanName());
        verify(syncPlanMapper).insert(any());
    }

    @Test
    void togglePlanUpdatesEnabled() {
        MarketDataSyncPlanDO plan = MarketDataSyncPlanDO.builder().id(1L).enabled(true).build();
        when(syncPlanMapper.selectById(1L)).thenReturn(plan);

        MarketDataSyncPlanVO vo = service.togglePlan(1L, false);

        assertFalse(vo.getEnabled());
        verify(syncPlanMapper).updateEnabled(eq(1L), eq(false), any());
    }

    private MinuteBarUpsertDTO validDto() {
        var dto = new MinuteBarUpsertDTO();
        dto.setCanonicalSymbol("SH.600519");
        dto.setBarStartTime(LocalDateTime.of(2026, 7, 10, 10, 0));
        dto.setBarEndTime(LocalDateTime.of(2026, 7, 10, 10, 30));
        dto.setIntervalType("30M");
        dto.setAdjustType("NONE");
        dto.setDataSource("LONGPORT");
        dto.setOpenPrice(new BigDecimal("10.50"));
        dto.setHighPrice(new BigDecimal("11.00"));
        dto.setLowPrice(new BigDecimal("10.20"));
        dto.setClosePrice(new BigDecimal("10.80"));
        dto.setVolume(5000L);
        dto.setAmount(new BigDecimal("54000"));
        dto.setTurnoverRate(new BigDecimal("0.05"));
        return dto;
    }

    private StockMinuteBarDO validBar() {
        return StockMinuteBarDO.builder()
                .canonicalSymbol("SH.600519")
                .tradeDate(java.time.LocalDate.of(2026, 7, 10))
                .barStartTime(LocalDateTime.of(2026, 7, 10, 10, 0))
                .barEndTime(LocalDateTime.of(2026, 7, 10, 10, 30))
                .intervalType(WorkbenchConstants.INTERVAL_30M)
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
