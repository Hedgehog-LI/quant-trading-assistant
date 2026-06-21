package com.quant.trade.journal.manager;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.constant.RiskConstants;
import com.quant.trade.common.enums.ReviewStatusEnum;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.common.util.RiskMathUtil;
import com.quant.trade.journal.dao.TradeJournalMapper;
import com.quant.trade.journal.model.TradeJournalDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 交易记录领域业务规则层。
 * <p>
 * 负责交易记录的核心校验、金额自动计算、费用归一与总费用计算、批量状态更新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeJournalManager {

    private final TradeJournalMapper tradeJournalMapper;

    /**
     * 校验创建参数并自动计算金额与费用。
     *
     * @param record 交易记录（会被原地修改 amount / 各费用 / totalFee 字段）
     */
    public void validateAndFillForCreate(TradeJournalDO record) {
        validateSide(record.getSide());
        validateReviewStatus(record.getReviewStatus());

        // 自动计算金额
        record.setAmount(calculateAmount(record.getPrice(), record.getQuantity()));

        // 费用归一与总费用计算
        fillFees(record);
    }

    /**
     * 校验更新参数。
     */
    public void validateForUpdate(TradeJournalDO record) {
        if (record.getSide() != null) {
            validateSide(record.getSide());
        }
        if (record.getReviewStatus() != null) {
            validateReviewStatus(record.getReviewStatus());
        }

        // 如果更新了价格或数量，重新计算金额
        if (record.getPrice() != null && record.getQuantity() != null) {
            record.setAmount(calculateAmount(record.getPrice(), record.getQuantity()));
        }

        // 如果更新了任意费用字段，重新归一与计算总费用
        if (anyFeeTouched(record)) {
            fillFees(record);
        }
    }

    /**
     * 根据 ID 查询，不存在则抛异常。
     */
    public TradeJournalDO getByIdOrThrow(Long id) {
        TradeJournalDO record = tradeJournalMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                    "Trade journal not found: " + id);
        }
        return record;
    }

    /**
     * 校验复核状态值合法性。
     */
    public void validateReviewStatus(String reviewStatus) {
        if (reviewStatus != null && !ReviewStatusEnum.isValid(reviewStatus)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    "Invalid reviewStatus: " + reviewStatus);
        }
    }

    /**
     * 按条件筛选交易记录。
     */
    public List<TradeJournalDO> listByFilter(LocalDate date, String symbol, String reviewStatus) {
        return tradeJournalMapper.selectByFilter(date, symbol, reviewStatus);
    }

    /**
     * 全量交易流水（按交易时间正序），供 portfolio FIFO 计算使用。
     *
     * @param fromDate 起始日期（可空）
     * @param toDate   截止日期（可空）
     */
    public List<TradeJournalDO> listAllOrdered(LocalDate fromDate, LocalDate toDate) {
        return tradeJournalMapper.selectAllOrdered(fromDate, toDate);
    }

    /**
     * 单股票交易流水（按交易时间正序）。
     */
    public List<TradeJournalDO> listBySymbolOrdered(String symbol) {
        return tradeJournalMapper.selectBySymbolOrdered(symbol);
    }

    /**
     * 校验指定的交易记录 ID 是否全部存在。
     *
     * @param ids 交易记录 ID 列表
     * @throws BusinessException 如果部分 ID 不存在
     */
    public void validateJournalExistence(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<TradeJournalDO> journals = tradeJournalMapper.selectByIds(ids);
        if (journals.size() != ids.size()) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                    MessageConstants.LINKED_JOURNALS_NOT_FOUND);
        }
    }

    /**
     * 批量标记为已复盘。
     *
     * @param ids 交易记录 ID 列表
     */
    public void markAsReviewed(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        validateJournalExistence(ids);
        tradeJournalMapper.batchUpdateReviewStatus(ids, ReviewStatusEnum.REVIEWED.getCode());
        log.info("Marked {} journals as REVIEWED", ids.size());
    }

    public long countByDate(LocalDate date) {
        return tradeJournalMapper.countByTradeDate(date);
    }

    public long countByReviewStatus(String reviewStatus) {
        return tradeJournalMapper.countByReviewStatus(reviewStatus);
    }

    // ==================== DB 读写 ====================

    public void insert(TradeJournalDO record) {
        tradeJournalMapper.insert(record);
    }

    public void updateById(TradeJournalDO record) {
        tradeJournalMapper.updateById(record);
    }

    public TradeJournalDO selectById(Long id) {
        return tradeJournalMapper.selectById(id);
    }

    // ==================== 私有方法 ====================

    private void validateSide(String side) {
        if (!TradeSideEnum.isValid(side)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    "Invalid side: " + side);
        }
    }

    private BigDecimal calculateAmount(BigDecimal price, Long quantity) {
        if (price != null && quantity != null) {
            return RiskMathUtil.multiply(price, BigDecimal.valueOf(quantity));
        }
        return null;
    }

    /**
     * 费用归一（null -> 0）并计算总费用。
     * <p>
     * 规则：前端传 totalFee 则以其为准；否则 totalFee = 佣金 + 印花税 + 过户费 + 其他。
     */
    private void fillFees(TradeJournalDO record) {
        record.setCommissionFee(nullToZero(record.getCommissionFee()));
        record.setStampTax(nullToZero(record.getStampTax()));
        record.setTransferFee(nullToZero(record.getTransferFee()));
        record.setOtherFee(nullToZero(record.getOtherFee()));
        if (record.getTotalFee() != null) {
            record.setTotalFee(scale(record.getTotalFee()));
        } else {
            BigDecimal summed = record.getCommissionFee()
                    .add(record.getStampTax())
                    .add(record.getTransferFee())
                    .add(record.getOtherFee());
            record.setTotalFee(scale(summed));
        }
    }

    private boolean anyFeeTouched(TradeJournalDO record) {
        return record.getTotalFee() != null
                || record.getCommissionFee() != null
                || record.getStampTax() != null
                || record.getTransferFee() != null
                || record.getOtherFee() != null;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(RiskConstants.DECIMAL_SCALE, RoundingMode.HALF_UP);
    }
}
