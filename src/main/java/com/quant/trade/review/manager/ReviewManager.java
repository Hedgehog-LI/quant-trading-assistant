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
import java.util.Collection;
import java.util.List;

/**
 * 盘后复盘领域业务规则层。
 * <p>
 * 负责复盘记录的校验和关联交易记录的状态联动。
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
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        getByIdOrThrow(id);
        reviewNoteMapper.deleteById(id);
    }
}
