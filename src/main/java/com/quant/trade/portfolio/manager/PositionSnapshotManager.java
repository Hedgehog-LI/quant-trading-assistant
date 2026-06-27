package com.quant.trade.portfolio.manager;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.constant.RiskConstants;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.common.util.RiskMathUtil;
import com.quant.trade.portfolio.dao.PositionSnapshotItemMapper;
import com.quant.trade.portfolio.dao.PositionSnapshotMapper;
import com.quant.trade.portfolio.enums.PositionMarketTypeEnum;
import com.quant.trade.portfolio.enums.SnapshotSourceTypeEnum;
import com.quant.trade.portfolio.enums.SnapshotStatusEnum;
import com.quant.trade.portfolio.model.PositionSnapshotDO;
import com.quant.trade.portfolio.model.PositionSnapshotItemDO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 持仓快照领域规则与持久化编排层。
 * <p>
 * 负责状态校验、明细标准化、金额重算、重复股票校验和 Mapper 调用。
 * 前端提交的金额、盈亏与仓位比例不会直接入库。
 */
@Component
@RequiredArgsConstructor
public class PositionSnapshotManager {

    private final PositionSnapshotMapper positionSnapshotMapper;
    private final PositionSnapshotItemMapper positionSnapshotItemMapper;

    /**
     * 校验并计算新快照。
     */
    public void prepareForCreate(PositionSnapshotDO snapshot, List<PositionSnapshotItemDO> items) {
        validateSourceType(snapshot.getSourceType());
        validateInitialStatus(snapshot.getSnapshotStatus());
        validateHeader(snapshot);
        normalizeAndCalculate(snapshot, items);
    }

    /**
     * 校验并重新计算已有草稿。
     */
    public void prepareForUpdate(PositionSnapshotDO snapshot, List<PositionSnapshotItemDO> items) {
        ensureDraft(snapshot);
        validateSourceType(snapshot.getSourceType());
        validateHeader(snapshot);
        normalizeAndCalculate(snapshot, items);
    }

    /**
     * 新增快照及其全部明细。
     */
    public void insert(PositionSnapshotDO snapshot, List<PositionSnapshotItemDO> items) {
        positionSnapshotMapper.insert(snapshot);
        assignSnapshotId(snapshot.getId(), items);
        insertItems(items);
    }

    /**
     * 整批覆盖草稿主表计算结果和明细。
     */
    public void replaceDraft(PositionSnapshotDO snapshot, List<PositionSnapshotItemDO> items) {
        if (positionSnapshotMapper.updateDraft(snapshot) == 0) {
            PositionSnapshotDO current = getByIdOrThrow(snapshot.getId());
            ensureDraft(current);
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_NOT_EDITABLE,
                    MessageConstants.POSITION_SNAPSHOT_ONLY_DRAFT_EDITABLE);
        }
        positionSnapshotItemMapper.deleteBySnapshotId(snapshot.getId());
        assignSnapshotId(snapshot.getId(), items);
        insertItems(items);
    }

    /**
     * 将草稿确认为正式历史快照。
     */
    public void confirm(Long id) {
        transition(id, SnapshotStatusEnum.CONFIRMED,
                List.of(SnapshotStatusEnum.DRAFT.getCode()));
    }

    /**
     * 作废草稿或已确认快照。
     */
    public void cancel(Long id) {
        transition(id, SnapshotStatusEnum.CANCELED,
                List.of(SnapshotStatusEnum.DRAFT.getCode(), SnapshotStatusEnum.CONFIRMED.getCode()));
    }

    /**
     * 根据 ID 查询，不存在时抛出明确业务错误。
     */
    public PositionSnapshotDO getByIdOrThrow(Long id) {
        PositionSnapshotDO record = positionSnapshotMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_NOT_FOUND,
                    String.format(MessageConstants.POSITION_SNAPSHOT_NOT_FOUND, id));
        }
        return record;
    }

    /** 查询快照明细。 */
    public List<PositionSnapshotItemDO> listItems(Long snapshotId) {
        return positionSnapshotItemMapper.selectBySnapshotId(snapshotId);
    }

    /**
     * 查询历史快照；未显式指定状态时由 includeCanceled 控制是否展示已作废记录。
     */
    public List<PositionSnapshotDO> list(LocalDate fromDate,
                                         LocalDate toDate,
                                         String status,
                                         String sourceType,
                                         boolean includeCanceled) {
        validateDateRange(fromDate, toDate);
        if (StringUtils.isNotBlank(status) && !SnapshotStatusEnum.isValid(status)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    String.format(MessageConstants.POSITION_SNAPSHOT_INVALID_STATUS_CODE, status));
        }
        if (StringUtils.isNotBlank(sourceType)) {
            validateSourceType(sourceType);
        }
        return positionSnapshotMapper.selectByFilter(
                fromDate, toDate, status, sourceType, includeCanceled);
    }

    /** 查询最新一条已确认快照，不存在时返回 null。 */
    public PositionSnapshotDO getLatestConfirmed() {
        return positionSnapshotMapper.selectLatestConfirmed();
    }

    private void normalizeAndCalculate(PositionSnapshotDO snapshot,
                                       List<PositionSnapshotItemDO> items) {
        if (items == null) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_ITEM,
                    String.format(MessageConstants.POSITION_SNAPSHOT_INVALID_ITEM, "UNKNOWN"));
        }

        snapshot.setSnapshotName(StringUtils.trimToNull(snapshot.getSnapshotName()));
        snapshot.setRemark(StringUtils.trimToNull(snapshot.getRemark()));

        Set<String> symbols = new HashSet<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalMarketValue = BigDecimal.ZERO;

        for (int index = 0; index < items.size(); index++) {
            PositionSnapshotItemDO item = items.get(index);
            normalizeAndValidateItem(item, index, symbols);

            BigDecimal quantity = BigDecimal.valueOf(item.getHoldingQuantity());
            BigDecimal costAmount = RiskMathUtil.multiply(item.getCostPrice(), quantity);
            BigDecimal marketValue = RiskMathUtil.multiply(item.getCurrentPrice(), quantity);
            BigDecimal unrealizedPnl = RiskMathUtil.subtract(marketValue, costAmount);

            item.setCostAmount(costAmount);
            item.setMarketValue(marketValue);
            item.setUnrealizedPnl(unrealizedPnl);
            item.setPnlRate(RiskMathUtil.ratio(
                    unrealizedPnl, costAmount, RiskConstants.DECIMAL_SCALE));

            totalCost = totalCost.add(costAmount);
            totalMarketValue = totalMarketValue.add(marketValue);
        }

        totalCost = totalCost.setScale(RiskConstants.DECIMAL_SCALE, RoundingMode.HALF_UP);
        totalMarketValue = totalMarketValue.setScale(RiskConstants.DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalPnl = RiskMathUtil.subtract(totalMarketValue, totalCost);

        for (PositionSnapshotItemDO item : items) {
            item.setPositionRatio(RiskMathUtil.ratio(
                    item.getMarketValue(), totalMarketValue, RiskConstants.DECIMAL_SCALE));
        }

        snapshot.setTotalCostAmount(totalCost);
        snapshot.setTotalMarketValue(totalMarketValue);
        snapshot.setTotalUnrealizedPnl(totalPnl);
        snapshot.setTotalPnlRate(RiskMathUtil.ratio(
                totalPnl, totalCost, RiskConstants.DECIMAL_SCALE));
        snapshot.setPositionCount(items.size());
    }

    private void normalizeAndValidateItem(PositionSnapshotItemDO item,
                                          int index,
                                          Set<String> symbols) {
        if (item == null) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_ITEM,
                    String.format(MessageConstants.POSITION_SNAPSHOT_INVALID_ITEM, "UNKNOWN"));
        }
        String symbol = StringUtils.trimToEmpty(item.getSymbol()).toUpperCase(Locale.ROOT);
        if (StringUtils.isBlank(symbol)) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_ITEM,
                    String.format(MessageConstants.POSITION_SNAPSHOT_INVALID_ITEM, "UNKNOWN"));
        }
        if (!symbols.add(symbol)) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_DUPLICATE_SYMBOL,
                    String.format(MessageConstants.POSITION_SNAPSHOT_DUPLICATE_SYMBOL, symbol));
        }

        String marketType = StringUtils.isBlank(item.getMarketType())
                ? PositionMarketTypeEnum.UNKNOWN.getCode()
                : item.getMarketType().trim().toUpperCase(Locale.ROOT);
        if (!PositionMarketTypeEnum.isValid(marketType)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    String.format(MessageConstants.POSITION_SNAPSHOT_INVALID_MARKET_CODE, marketType));
        }

        if (item.getHoldingQuantity() == null || item.getHoldingQuantity() <= 0
                || item.getCostPrice() == null || item.getCostPrice().signum() < 0
                || item.getCurrentPrice() == null || item.getCurrentPrice().signum() < 0) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_ITEM,
                    String.format(MessageConstants.POSITION_SNAPSHOT_INVALID_ITEM, symbol));
        }

        Long availableQuantity = item.getAvailableQuantity() == null
                ? item.getHoldingQuantity() : item.getAvailableQuantity();
        if (availableQuantity < 0 || availableQuantity > item.getHoldingQuantity()) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_ITEM,
                    String.format(MessageConstants.POSITION_SNAPSHOT_AVAILABLE_EXCEEDS_HOLDING, symbol));
        }

        item.setSymbol(symbol);
        item.setName(StringUtils.trimToNull(item.getName()));
        item.setMarketType(marketType);
        item.setAvailableQuantity(availableQuantity);
        item.setCostPrice(item.getCostPrice().setScale(RiskConstants.DECIMAL_SCALE, RoundingMode.HALF_UP));
        item.setCurrentPrice(item.getCurrentPrice().setScale(RiskConstants.DECIMAL_SCALE, RoundingMode.HALF_UP));
        item.setRemark(StringUtils.trimToNull(item.getRemark()));
        item.setSortOrder(index);
    }

    private void validateHeader(PositionSnapshotDO snapshot) {
        if (snapshot.getSnapshotDate() == null || snapshot.getSnapshotTime() == null
                || !snapshot.getSnapshotDate().equals(snapshot.getSnapshotTime().toLocalDate())) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    MessageConstants.POSITION_SNAPSHOT_DATE_TIME_MISMATCH);
        }
    }

    private void validateInitialStatus(String status) {
        if (!SnapshotStatusEnum.isValid(status)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    String.format(MessageConstants.POSITION_SNAPSHOT_INVALID_STATUS_CODE, status));
        }
        if (!SnapshotStatusEnum.DRAFT.getCode().equals(status)
                && !SnapshotStatusEnum.CONFIRMED.getCode().equals(status)) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_TRANSITION,
                    MessageConstants.POSITION_SNAPSHOT_INVALID_INITIAL_STATUS);
        }
    }

    private void validateSourceType(String sourceType) {
        if (!SnapshotSourceTypeEnum.isValid(sourceType)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    String.format(MessageConstants.POSITION_SNAPSHOT_INVALID_SOURCE_CODE, sourceType));
        }
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    MessageConstants.POSITION_SNAPSHOT_INVALID_DATE_RANGE);
        }
    }

    private void ensureDraft(PositionSnapshotDO snapshot) {
        if (!SnapshotStatusEnum.DRAFT.getCode().equals(snapshot.getSnapshotStatus())) {
            throw new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_NOT_EDITABLE,
                    MessageConstants.POSITION_SNAPSHOT_ONLY_DRAFT_EDITABLE);
        }
    }

    private void transition(Long id,
                            SnapshotStatusEnum target,
                            List<String> allowedCurrentStatuses) {
        PositionSnapshotDO existing = getByIdOrThrow(id);
        if (!allowedCurrentStatuses.contains(existing.getSnapshotStatus())) {
            throw invalidTransition(existing.getSnapshotStatus(), target.getCode());
        }
        if (positionSnapshotMapper.updateStatus(id, target.getCode(), allowedCurrentStatuses) == 0) {
            PositionSnapshotDO current = getByIdOrThrow(id);
            throw invalidTransition(current.getSnapshotStatus(), target.getCode());
        }
    }

    private BusinessException invalidTransition(String currentStatus, String targetStatus) {
        return new BusinessException(ErrorCodeEnum.POSITION_SNAPSHOT_INVALID_TRANSITION,
                String.format(MessageConstants.POSITION_SNAPSHOT_INVALID_TRANSITION,
                        currentStatus, targetStatus));
    }

    private void assignSnapshotId(Long snapshotId, List<PositionSnapshotItemDO> items) {
        for (PositionSnapshotItemDO item : items) {
            item.setSnapshotId(snapshotId);
        }
    }

    private void insertItems(List<PositionSnapshotItemDO> items) {
        if (!items.isEmpty()) {
            positionSnapshotItemMapper.insertBatch(items);
        }
    }
}
