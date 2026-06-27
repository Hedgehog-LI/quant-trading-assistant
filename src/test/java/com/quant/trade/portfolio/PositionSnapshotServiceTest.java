package com.quant.trade.portfolio;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.portfolio.dto.CreatePositionSnapshotDTO;
import com.quant.trade.portfolio.dto.PositionSnapshotItemDTO;
import com.quant.trade.portfolio.dto.UpdatePositionSnapshotDTO;
import com.quant.trade.portfolio.enums.SnapshotSourceTypeEnum;
import com.quant.trade.portfolio.enums.SnapshotStatusEnum;
import com.quant.trade.portfolio.service.PositionSnapshotService;
import com.quant.trade.portfolio.vo.PositionSnapshotDetailVO;
import com.quant.trade.portfolio.vo.PositionSnapshotSummaryVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 持仓快照服务集成测试。
 * <p>
 * 使用 H2 + Flyway + MyBatis 验证金额计算、SQL 映射、整批更新和状态流转。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PositionSnapshotServiceTest {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 6, 27);
    private static final LocalDateTime SNAPSHOT_TIME = LocalDateTime.of(2026, 6, 27, 15, 5);

    @Autowired
    private PositionSnapshotService positionSnapshotService;

    @Test
    void createDraftRecalculatesAmountsAndRatios() {
        PositionSnapshotDetailVO result = positionSnapshotService.create(createRequest(
                SnapshotStatusEnum.DRAFT.getCode(),
                SNAPSHOT_TIME,
                List.of(
                        item("300750", "宁德时代", "SZ", 100L, 80L, "10", "12"),
                        item("600519", "贵州茅台", "SH", 50L, null, "20", "18"))));

        assertNotNull(result.id());
        assertEquals(SnapshotStatusEnum.DRAFT.getCode(), result.snapshotStatus());
        assertEquals(2, result.positionCount());
        assertDecimal("2000.000000", result.totalCostAmount());
        assertDecimal("2100.000000", result.totalMarketValue());
        assertDecimal("100.000000", result.totalUnrealizedPnl());
        assertDecimal("0.050000", result.totalPnlRate());

        assertEquals(2, result.items().size());
        assertDecimal("1000.000000", result.items().get(0).costAmount());
        assertDecimal("1200.000000", result.items().get(0).marketValue());
        assertDecimal("200.000000", result.items().get(0).unrealizedPnl());
        assertDecimal("0.200000", result.items().get(0).pnlRate());
        assertDecimal("0.571429", result.items().get(0).positionRatio());
        assertEquals(50L, result.items().get(1).availableQuantity());
        assertDecimal("0.428571", result.items().get(1).positionRatio());
    }

    @Test
    void updateDraftReplacesItemsAndRecalculatesSummary() {
        PositionSnapshotDetailVO created = positionSnapshotService.create(createRequest(
                SnapshotStatusEnum.DRAFT.getCode(),
                SNAPSHOT_TIME,
                List.of(item("300750", "宁德时代", "SZ", 100L, 100L, "10", "12"))));

        UpdatePositionSnapshotDTO update = new UpdatePositionSnapshotDTO(
                SNAPSHOT_DATE,
                SNAPSHOT_TIME.plusHours(1),
                "收盘修正版",
                "重新核对券商页面",
                List.of(item("000001", "平安银行", "SZ", 200L, 100L, "8", "9")));

        PositionSnapshotDetailVO result = positionSnapshotService.updateDraft(created.id(), update);

        assertEquals("收盘修正版", result.snapshotName());
        assertEquals(1, result.positionCount());
        assertEquals(1, result.items().size());
        assertEquals("000001", result.items().get(0).symbol());
        assertDecimal("1600.000000", result.totalCostAmount());
        assertDecimal("1800.000000", result.totalMarketValue());
        assertDecimal("200.000000", result.totalUnrealizedPnl());
        assertDecimal("0.125000", result.totalPnlRate());
    }

    @Test
    void confirmedSnapshotCannotBeEditedAndCanBeCanceled() {
        PositionSnapshotDetailVO created = positionSnapshotService.create(createRequest(
                SnapshotStatusEnum.DRAFT.getCode(),
                SNAPSHOT_TIME,
                List.of(item("300750", "宁德时代", "SZ", 100L, 100L, "10", "12"))));

        PositionSnapshotDetailVO confirmed = positionSnapshotService.confirm(created.id());
        assertEquals(SnapshotStatusEnum.CONFIRMED.getCode(), confirmed.snapshotStatus());

        UpdatePositionSnapshotDTO update = new UpdatePositionSnapshotDTO(
                SNAPSHOT_DATE, SNAPSHOT_TIME, "不应成功", null, List.of());
        BusinessException editError = assertThrows(BusinessException.class,
                () -> positionSnapshotService.updateDraft(created.id(), update));
        assertEquals(ErrorCodeEnum.POSITION_SNAPSHOT_NOT_EDITABLE, editError.getErrorCode());

        PositionSnapshotDetailVO canceled = positionSnapshotService.cancel(created.id());
        assertEquals(SnapshotStatusEnum.CANCELED.getCode(), canceled.snapshotStatus());

        BusinessException transitionError = assertThrows(BusinessException.class,
                () -> positionSnapshotService.confirm(created.id()));
        assertEquals(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_TRANSITION,
                transitionError.getErrorCode());
    }

    @Test
    void canceledSnapshotsAreHiddenByDefaultAndCanBeQueriedExplicitly() {
        PositionSnapshotDetailVO created = positionSnapshotService.create(createRequest(
                SnapshotStatusEnum.DRAFT.getCode(), SNAPSHOT_TIME, List.of()));
        positionSnapshotService.cancel(created.id());

        List<PositionSnapshotSummaryVO> defaultList = positionSnapshotService.list(
                null, null, null, null, false);
        assertTrue(defaultList.stream().noneMatch(item -> item.id().equals(created.id())));

        List<PositionSnapshotSummaryVO> canceledList = positionSnapshotService.list(
                null, null, SnapshotStatusEnum.CANCELED.getCode(), null, false);
        assertTrue(canceledList.stream().anyMatch(item -> item.id().equals(created.id())));
    }

    @Test
    void latestReturnsNewestConfirmedSnapshotOnly() {
        assertNull(positionSnapshotService.getLatestConfirmed());

        PositionSnapshotDetailVO first = positionSnapshotService.create(createRequest(
                SnapshotStatusEnum.CONFIRMED.getCode(),
                SNAPSHOT_TIME.minusDays(1),
                List.of(item("000001", "平安银行", "SZ", 100L, 100L, "8", "9"))));
        PositionSnapshotDetailVO second = positionSnapshotService.create(createRequest(
                SnapshotStatusEnum.CONFIRMED.getCode(),
                SNAPSHOT_TIME,
                List.of(item("300750", "宁德时代", "SZ", 100L, 100L, "10", "12"))));
        positionSnapshotService.create(createRequest(
                SnapshotStatusEnum.DRAFT.getCode(),
                SNAPSHOT_TIME.plusHours(1),
                List.of()));

        PositionSnapshotDetailVO latest = positionSnapshotService.getLatestConfirmed();
        assertNotNull(latest);
        assertEquals(second.id(), latest.id());
        assertTrue(latest.id() > first.id());
    }

    @Test
    void duplicateNormalizedSymbolIsRejected() {
        CreatePositionSnapshotDTO request = createRequest(
                SnapshotStatusEnum.DRAFT.getCode(),
                SNAPSHOT_TIME,
                List.of(
                        item("300750", "宁德时代", "SZ", 100L, 100L, "10", "12"),
                        item(" 300750 ", "宁德时代", "SZ", 50L, 50L, "11", "12")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> positionSnapshotService.create(request));
        assertEquals(ErrorCodeEnum.POSITION_SNAPSHOT_DUPLICATE_SYMBOL, error.getErrorCode());
    }

    @Test
    void availableQuantityCannotExceedHoldingQuantity() {
        CreatePositionSnapshotDTO request = createRequest(
                SnapshotStatusEnum.DRAFT.getCode(),
                SNAPSHOT_TIME,
                List.of(item("300750", "宁德时代", "SZ", 100L, 101L, "10", "12")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> positionSnapshotService.create(request));
        assertEquals(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_ITEM, error.getErrorCode());
    }

    @Test
    void snapshotDateMustMatchSnapshotTime() {
        CreatePositionSnapshotDTO request = new CreatePositionSnapshotDTO(
                SNAPSHOT_DATE.minusDays(1),
                SNAPSHOT_TIME,
                "日期不一致",
                SnapshotSourceTypeEnum.MANUAL.getCode(),
                SnapshotStatusEnum.DRAFT.getCode(),
                null,
                List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> positionSnapshotService.create(request));
        assertEquals(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, error.getErrorCode());
    }

    private CreatePositionSnapshotDTO createRequest(String status,
                                                    LocalDateTime snapshotTime,
                                                    List<PositionSnapshotItemDTO> items) {
        return new CreatePositionSnapshotDTO(
                snapshotTime.toLocalDate(),
                snapshotTime,
                "收盘持仓",
                SnapshotSourceTypeEnum.MANUAL.getCode(),
                status,
                "手工录入",
                items);
    }

    private PositionSnapshotItemDTO item(String symbol,
                                         String name,
                                         String marketType,
                                         Long holdingQuantity,
                                         Long availableQuantity,
                                         String costPrice,
                                         String currentPrice) {
        return new PositionSnapshotItemDTO(
                symbol,
                name,
                marketType,
                holdingQuantity,
                availableQuantity,
                new BigDecimal(costPrice),
                new BigDecimal(currentPrice),
                null);
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected=" + expected + ", actual=" + actual);
    }
}
