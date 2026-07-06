package com.quant.trade.journal.service;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.enums.ReviewStatusEnum;
import com.quant.trade.common.enums.TradeSideEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.journal.convert.TradeJournalConverter;
import com.quant.trade.journal.dto.CreateTradeJournalDTO;
import com.quant.trade.journal.dto.UpdateReviewStatusDTO;
import com.quant.trade.journal.dto.UpdateTradeJournalDTO;
import com.quant.trade.journal.flow.TradeFlowItem;
import com.quant.trade.journal.manager.TradeJournalManager;
import com.quant.trade.journal.model.TradeJournalDO;
import com.quant.trade.journal.vo.TradeJournalVO;
import com.quant.trade.review.manager.ReviewManager;
import com.quant.trade.tradeplan.manager.TradePlanManager;
import com.quant.trade.tradeplan.model.TradePlanDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交易记录应用服务。
 * <p>
 * 负责事务边界和业务流程编排，核心校验和 DB 读写委托给 {@link TradeJournalManager}。
 * 关联计划的展示字段（planDate/planStatus）由本层根据 planId 查询填充，避免 DO 携带非持久化字段。
 * 删除交易记录前通过 {@link ReviewManager} 做引用保护；为复盘模块提供 reviewStatus 回算入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeJournalService {

    private final TradeJournalManager tradeJournalManager;
    private final TradeJournalConverter tradeJournalConverter;
    private final TradePlanManager tradePlanManager;
    private final ReviewManager reviewManager;

    /**
     * 新增交易记录。
     *
     * @return 包含风险提示和关联计划摘要的交易记录 VO
     */
    @Transactional
    public TradeJournalVO create(CreateTradeJournalDTO dto) {
        TradeJournalDO record = tradeJournalConverter.toDO(dto);
        // 默认值
        if (record.getReviewStatus() == null) {
            record.setReviewStatus(ReviewStatusEnum.PENDING.getCode());
        }

        // 校验并自动计算（金额、费用、总费用、计划关联）
        tradeJournalManager.validateAndFillForCreate(record);

        // 买入无止损 -> warning（不阻断）
        List<String> warnings = Collections.emptyList();
        if (TradeSideEnum.BUY.getCode().equals(record.getSide()) && record.getPlanStopLoss() == null) {
            warnings = List.of(MessageConstants.JOURNAL_BUY_NO_STOP_LOSS);
        }

        tradeJournalManager.insert(record);

        log.info("Created trade journal: symbol={}, side={}, date={}",
                record.getSymbol(), record.getSide(), record.getTradeDate());

        TradePlanDO plan = record.getPlanId() == null ? null : tradePlanManager.selectById(record.getPlanId());
        LocalDate planDate = plan == null ? null : plan.getPlanDate();
        String planStatus = plan == null ? null : plan.getPlanStatus();
        return tradeJournalConverter.toVO(record).withPlanAndWarnings(planDate, planStatus, warnings);
    }

    public List<TradeJournalVO> list(LocalDate date, String symbol, String reviewStatus) {
        List<TradeJournalDO> records = tradeJournalManager.listByFilter(date, symbol, reviewStatus);
        return attachPlans(tradeJournalConverter.toVOList(records));
    }

    public TradeJournalVO getById(Long id) {
        TradeJournalDO record = tradeJournalManager.getByIdOrThrow(id);
        return attachPlan(tradeJournalConverter.toVO(record));
    }

    @Transactional
    public TradeJournalVO update(Long id, UpdateTradeJournalDTO dto) {
        TradeJournalDO existing = tradeJournalManager.getByIdOrThrow(id);
        tradeJournalConverter.updateDOFromDTO(dto, existing);
        // 计划关联三态：unlinkPlan 优先 / planId 更新 / 否则保持原值（converter IGNORE 已保留 DB 值）
        if (Boolean.TRUE.equals(dto.unlinkPlan())) {
            existing.setPlanId(null);
        } else if (dto.planId() != null) {
            existing.setPlanId(dto.planId());
        }
        existing.setId(id);

        tradeJournalManager.validateForUpdate(existing);
        tradeJournalManager.updateById(existing);

        log.info("Updated trade journal: id={}, planId={}", id, existing.getPlanId());
        return attachPlan(tradeJournalConverter.toVO(tradeJournalManager.selectById(id)));
    }

    @Transactional
    public TradeJournalVO updateReviewStatus(Long id, UpdateReviewStatusDTO dto) {
        tradeJournalManager.validateReviewStatus(dto.reviewStatus());
        TradeJournalDO existing = tradeJournalManager.getByIdOrThrow(id);

        existing.setReviewStatus(dto.reviewStatus());
        tradeJournalManager.updateById(existing);

        log.info("Updated journal review status: id={}, status={}", id, dto.reviewStatus());
        return attachPlan(tradeJournalConverter.toVO(tradeJournalManager.selectById(id)));
    }

    /**
     * 批量标记为已复盘（供 Review 模块调用）。
     */
    @Transactional
    public void markAsReviewed(List<Long> journalIds) {
        tradeJournalManager.markAsReviewed(journalIds);
    }

    /**
     * 复盘关联一致性回算入口（供 Review 模块调用）。
     * <p>
     * 受影响 ID 在 referencedIds 中 -> REVIEWED，否则 -> PENDING。
     *
     * @param affectedIds   受影响的交易记录 ID（旧关联 ∪ 新关联）
     * @param referencedIds 当前被任意复盘引用的 ID 集合
     */
    public void recalculateReviewStatus(Collection<Long> affectedIds, Set<Long> referencedIds) {
        tradeJournalManager.recalculateReviewStatus(affectedIds, referencedIds);
    }

    /**
     * 全量交易流水（portfolio 入参契约，时间正序）。供 Portfolio 模块 FIFO 计算调用。
     *
     * @param fromDate 起始日期（可空）
     * @param toDate   截止日期（可空）
     */
    public List<TradeFlowItem> listFlowItems(LocalDate fromDate, LocalDate toDate) {
        return tradeJournalConverter.toFlowItemList(tradeJournalManager.listAllOrdered(fromDate, toDate));
    }

    /**
     * 单股票交易流水（portfolio 入参契约，时间正序）。
     */
    public List<TradeFlowItem> listFlowItemsBySymbol(String symbol) {
        return tradeJournalConverter.toFlowItemList(tradeJournalManager.listBySymbolOrdered(symbol));
    }

    /**
     * 截止指定时点的全量交易流水（持仓对账使用），时间正序。
     * <p>
     * 由 PositionSnapshot 对账模块调用，按快照日期和时间精度过滤。
     *
     * @param snapshotDate     快照日期
     * @param snapshotDateTime 快照时点
     * @return 截止时点的流水
     */
    public List<TradeFlowItem> listFlowItemsUpTo(LocalDate snapshotDate, LocalDateTime snapshotDateTime) {
        return tradeJournalConverter.toFlowItemList(
                tradeJournalManager.listAllOrderedUpTo(snapshotDate, snapshotDateTime));
    }

    public long countByDate(LocalDate date) {
        return tradeJournalManager.countByDate(date);
    }

    public long countByReviewStatus(String reviewStatus) {
        return tradeJournalManager.countByReviewStatus(reviewStatus);
    }

    /**
     * 物理删除交易记录。
     * <p>
     * 删除保护：被任意复盘引用时拒绝（{@link ErrorCodeEnum#JOURNAL_REFERENCED_BY_REVIEW}），
     * 提示用户先在相关复盘中移除关联。
     */
    @Transactional
    public void delete(Long id) {
        if (reviewManager.isJournalReferencedByAnyReview(id)) {
            throw new BusinessException(ErrorCodeEnum.JOURNAL_REFERENCED_BY_REVIEW,
                    String.format(MessageConstants.JOURNAL_REFERENCED_BY_REVIEW, id));
        }
        tradeJournalManager.deleteById(id);
        log.info("Deleted trade journal: id={}", id);
    }

    // ==================== 关联计划展示填充 ====================

    private TradeJournalVO attachPlan(TradeJournalVO vo) {
        if (vo == null || vo.planId() == null) {
            return vo;
        }
        TradePlanDO plan = tradePlanManager.selectById(vo.planId());
        if (plan == null) {
            return vo;
        }
        return vo.withPlanAndWarnings(plan.getPlanDate(), plan.getPlanStatus(), vo.warnings());
    }

    private List<TradeJournalVO> attachPlans(List<TradeJournalVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return vos;
        }
        Set<Long> planIds = vos.stream()
                .map(TradeJournalVO::planId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (planIds.isEmpty()) {
            return vos;
        }
        Map<Long, TradePlanDO> planMap = loadPlanMap(planIds);
        return vos.stream()
                .map(vo -> {
                    if (vo.planId() == null) {
                        return vo;
                    }
                    TradePlanDO plan = planMap.get(vo.planId());
                    if (plan == null) {
                        return vo;
                    }
                    return vo.withPlanAndWarnings(plan.getPlanDate(), plan.getPlanStatus(), vo.warnings());
                })
                .collect(Collectors.toList());
    }

    private Map<Long, TradePlanDO> loadPlanMap(Set<Long> planIds) {
        Map<Long, TradePlanDO> map = new HashMap<>();
        for (Long id : planIds) {
            TradePlanDO plan = tradePlanManager.selectById(id);
            if (plan != null) {
                map.put(id, plan);
            }
        }
        return map;
    }
}
