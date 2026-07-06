package com.quant.trade.journal.manager;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.constant.RiskConstants;
import com.quant.trade.common.enums.PlanStatusEnum;
import com.quant.trade.common.enums.ReviewStatusEnum;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.common.util.RiskMathUtil;
import com.quant.trade.journal.dao.TradeJournalMapper;
import com.quant.trade.journal.model.TradeJournalDO;
import com.quant.trade.tradeplan.manager.TradePlanManager;
import com.quant.trade.tradeplan.model.TradePlanDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 交易记录领域业务规则层。
 * <p>
 * 负责交易记录的核心校验、金额自动计算、费用归一与总费用计算、计划关联校验、批量状态更新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeJournalManager {

    private final TradeJournalMapper tradeJournalMapper;
    private final TradePlanManager tradePlanManager;

    /**
     * 校验创建参数并自动计算金额与费用。
     *
     * @param record 交易记录（会被原地修改 amount / 各费用 / totalFee 字段）
     */
    public void validateAndFillForCreate(TradeJournalDO record) {
        validateSide(record.getSide());
        validateReviewStatus(record.getReviewStatus());
        validatePlanLinkage(record.getPlanId(), record.getSymbol());

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
        // 更新时 existing.symbol 为 DB 原值（converter 对 symbol 用 IGNORE），planId 可能为新关联。
        validatePlanLinkage(record.getPlanId(), record.getSymbol());

        // 如果更新了价格或数量，重新计算金额
        if (record.getPrice() != null && record.getQuantity() != null) {
            record.setAmount(calculateAmount(record.getPrice(), record.getQuantity()));
        }

        // 费用字段为全量编辑语义：每次更新都归一（null→0）并重算 totalFee。
        // 配合 Converter 的 SET_TO_NULL 与 MyBatis 的非 null 更新，支持清空费用（清空后落库为 0.000000）。
        fillFees(record);
    }

    /**
     * 校验交易记录与交易计划的关联合法性。
     * <ul>
     *   <li>{@code planId} 为空：放行（未关联计划），返回 {@code null}。</li>
     *   <li>{@code planId} 非空：计划必须存在
     *       （{@link ErrorCodeEnum#TRADE_PLAN_NOT_FOUND}）、
     *       未取消（{@link ErrorCodeEnum#TRADE_PLAN_NOT_LINKABLE}）、
     *       证券代码与计划一致（{@link ErrorCodeEnum#TRADE_PLAN_SYMBOL_MISMATCH}）。</li>
     * </ul>
     * 注意：{@code allowedToTrade=false} 的计划允许关联——此类偏离需要进入复盘，由工作台单独提醒。
     *
     * @param planId        交易记录关联的计划 ID，可空
     * @param journalSymbol 交易记录证券代码；更新时由调用方传入 existing.symbol
     * @return 校验通过时返回关联计划，未关联计划时返回 {@code null}
     */
    public TradePlanDO validatePlanLinkage(Long planId, String journalSymbol) {
        if (planId == null) {
            return null;
        }
        TradePlanDO plan = tradePlanManager.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCodeEnum.TRADE_PLAN_NOT_FOUND,
                    String.format(MessageConstants.TRADE_PLAN_NOT_FOUND, planId));
        }
        if (PlanStatusEnum.CANCELLED.getCode().equals(plan.getPlanStatus())) {
            throw new BusinessException(ErrorCodeEnum.TRADE_PLAN_NOT_LINKABLE,
                    String.format(MessageConstants.TRADE_PLAN_NOT_LINKABLE, planId));
        }
        String normalizedJournal = normalizeSymbol(journalSymbol);
        String normalizedPlan = normalizeSymbol(plan.getSymbol());
        if (!normalizedJournal.equals(normalizedPlan)) {
            throw new BusinessException(ErrorCodeEnum.TRADE_PLAN_SYMBOL_MISMATCH,
                    String.format(MessageConstants.TRADE_PLAN_SYMBOL_MISMATCH,
                            normalizedJournal, planId, normalizedPlan));
        }
        return plan;
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
     * 截止指定时点的全量交易流水（持仓对账使用），按交易时间正序。
     *
     * @param snapshotDate     快照日期
     * @param snapshotDateTime 快照时点
     * @return 截止时点的流水
     */
    public List<TradeJournalDO> listAllOrderedUpTo(LocalDate snapshotDate,
                                                   java.time.LocalDateTime snapshotDateTime) {
        return tradeJournalMapper.selectAllOrderedUpTo(snapshotDate, snapshotDateTime);
    }

    /**
     * 统计 trade_date &lt;= toDate 的待复盘交易数（Dashboard 历史日期口径）。
     *
     * @param toDate 截止日期（包含）
     */
    public long countPendingReviewUpTo(LocalDate toDate) {
        return tradeJournalMapper.countByReviewStatusUpTo(toDate, ReviewStatusEnum.PENDING.getCode());
    }

    /**
     * 查询 trade_date &lt;= toDate 的待复盘交易列表（Dashboard 历史日期口径）。
     *
     * @param toDate 截止日期（包含）
     */
    public List<TradeJournalDO> listPendingReviewUpTo(LocalDate toDate) {
        return tradeJournalMapper.selectByReviewStatusUpTo(toDate, ReviewStatusEnum.PENDING.getCode());
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

    /**
     * 按受影响 ID 和「当前被任意复盘引用的 ID 集合」重算复盘状态。
     * <p>
     * 受影响 ID 在引用集合中 -> REVIEWED，否则 -> PENDING。用于复盘新增/编辑/删除后的状态回算。
     *
     * @param affectedIds    受影响的交易记录 ID
     * @param referencedIds  当前被任意复盘引用的 ID 集合（由 ReviewManager 扫全表得出）
     */
    public void recalculateReviewStatus(Collection<Long> affectedIds, java.util.Set<Long> referencedIds) {
        if (affectedIds == null || affectedIds.isEmpty()) {
            return;
        }
        java.util.Set<Long> refSet = referencedIds == null ? java.util.Collections.emptySet() : referencedIds;
        java.util.List<Long> toReview = new java.util.ArrayList<>();
        java.util.List<Long> toPending = new java.util.ArrayList<>();
        for (Long id : affectedIds) {
            if (id == null) {
                continue;
            }
            if (refSet.contains(id)) {
                toReview.add(id);
            } else {
                toPending.add(id);
            }
        }
        if (!toReview.isEmpty()) {
            tradeJournalMapper.batchUpdateReviewStatus(toReview, ReviewStatusEnum.REVIEWED.getCode());
        }
        if (!toPending.isEmpty()) {
            tradeJournalMapper.batchUpdateReviewStatus(toPending, ReviewStatusEnum.PENDING.getCode());
        }
        log.info("Recalculated review status: reviewed={}, pending={}", toReview.size(), toPending.size());
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

    /**
     * 物理删除交易记录。
     * <p>
     * 删除前先校验存在性，不存在则抛 RESOURCE_NOT_FOUND。
     * <p>
     * 关联限制：删除保护（被复盘引用时拒绝）由 service 层调用 ReviewManager 完成，
     * 避免本 manager 与 ReviewManager 形成循环依赖。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        getByIdOrThrow(id);
        tradeJournalMapper.deleteById(id);
    }

    // ==================== 私有方法 ====================

    private String normalizeSymbol(String symbol) {
        return StringUtils.trimToEmpty(symbol).toUpperCase(Locale.ROOT);
    }

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

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(RiskConstants.DECIMAL_SCALE, RoundingMode.HALF_UP);
    }
}
