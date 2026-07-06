package com.quant.trade.review.manager;

import com.quant.trade.common.constant.MessageConstants;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.journal.manager.TradeJournalManager;
import com.quant.trade.review.dao.ReviewNoteMapper;
import com.quant.trade.review.model.ReviewNoteDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 盘后复盘领域业务规则层。
 * <p>
 * 负责复盘记录的校验、关联交易记录的引用解析与状态联动。
 * 关联回算的核心策略：扫全表解析 linked_journal_ids（CSV），形成"当前被引用 ID 集合"，
 * 由 TradeJournalManager 按集合重算受影响交易记录的 reviewStatus。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewManager {

    private final ReviewNoteMapper reviewNoteMapper;
    private final TradeJournalManager tradeJournalManager;

    /**
     * 根据 ID 查询，不存在则抛异常。
     */
    public ReviewNoteDO getByIdOrThrow(Long id) {
        ReviewNoteDO record = reviewNoteMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND,
                    "Review note not found: " + id);
        }
        return record;
    }

    /**
     * 校验关联的交易记录是否存在。
     *
     * @param linkedJournalIds 关联 ID 列表
     */
    public void validateLinkedJournals(List<Long> linkedJournalIds) {
        if (linkedJournalIds == null || linkedJournalIds.isEmpty()) {
            return;
        }
        tradeJournalManager.validateJournalExistence(linkedJournalIds);
    }

    /**
     * 按条件筛选复盘记录。
     */
    public List<ReviewNoteDO> listByFilter(LocalDate date, String symbol) {
        return reviewNoteMapper.selectByFilter(date, symbol);
    }

    public long countByDate(LocalDate date) {
        return reviewNoteMapper.countByReviewDate(date);
    }

    // ==================== DB 读写 ====================

    public void insert(ReviewNoteDO record) {
        reviewNoteMapper.insert(record);
    }

    public void updateById(ReviewNoteDO record) {
        reviewNoteMapper.updateById(record);
    }

    public ReviewNoteDO selectById(Long id) {
        return reviewNoteMapper.selectById(id);
    }

    /**
     * 物理删除复盘记录。
     * <p>
     * 删除前先校验存在性，不存在则抛 RESOURCE_NOT_FOUND。
     * 关联方向是 review -> journal（review 引用 journal），删除 review 不会产生悬空引用。
     * 受影响的原关联交易记录的 reviewStatus 由 service 层触发回算。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        getByIdOrThrow(id);
        reviewNoteMapper.deleteById(id);
    }

    // ==================== 关联解析与一致性回算 ====================

    /**
     * 解析 linked_journal_ids 的 CSV 字符串为 Long 列表。
     * <p>
     * 容忍历史无效引用（非数字、空段），跳过不报错，与"读取历史容忍无效引用"的产品规则一致。
     *
     * @param csv CSV 字符串，如 "1,2,3"；可空
     * @return 解析出的有效 ID 列表，空输入返回空列表
     */
    public List<Long> parseLinkedIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.valueOf(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 扫描全部复盘记录，收集当前被任意复盘引用的交易记录 ID 集合。
     * <p>
     * 用于：1) 关联回算时判断受影响 ID 是否仍被引用；2) 删除交易记录时的引用保护。
     *
     * @return 当前被引用的交易记录 ID 集合
     */
    public Set<Long> collectReferencedJournalIds() {
        List<ReviewNoteDO> all = reviewNoteMapper.selectAll();
        Set<Long> referenced = new HashSet<>();
        for (ReviewNoteDO review : all) {
            referenced.addAll(parseLinkedIds(review.getLinkedJournalIds()));
        }
        return referenced;
    }

    /**
     * 判断指定交易记录是否被任意复盘引用（删除保护）。
     *
     * @param journalId 交易记录 ID
     * @return 被引用返回 true
     */
    public boolean isJournalReferencedByAnyReview(Long journalId) {
        if (journalId == null) {
            return false;
        }
        return collectReferencedJournalIds().contains(journalId);
    }

    /**
     * 提供给 service 层触发的回算入口：取最新引用集合后委托 journal manager 重算。
     *
     * @param affectedIds 受影响的交易记录 ID（旧关联 ∪ 新关联）
     */
    public void recalculateAffectedReviewStatus(Collection<Long> affectedIds) {
        if (affectedIds == null || affectedIds.isEmpty()) {
            return;
        }
        Set<Long> referenced = collectReferencedJournalIds();
        tradeJournalManager.recalculateReviewStatus(affectedIds, referenced);
    }

    // ==================== 静态文案占位（避免魔法字符串） ====================

    /** 用于 ReviewService 删除保护错误消息的常量引用 */
    public static final String JOURNAL_REFERENCED_LOG_TEMPLATE =
            MessageConstants.JOURNAL_REFERENCED_BY_REVIEW;
}
