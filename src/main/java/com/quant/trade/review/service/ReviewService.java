package com.quant.trade.review.service;

import com.quant.trade.journal.service.TradeJournalService;
import com.quant.trade.review.convert.ReviewNoteConverter;
import com.quant.trade.review.dto.CreateReviewDTO;
import com.quant.trade.review.dto.UpdateReviewDTO;
import com.quant.trade.review.manager.ReviewManager;
import com.quant.trade.review.model.ReviewNoteDO;
import com.quant.trade.review.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 盘后复盘应用服务。
 * <p>
 * 负责事务边界和业务流程编排，核心校验和 DB 读写委托给 {@link ReviewManager}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewManager reviewManager;
    private final ReviewNoteConverter reviewNoteConverter;
    private final TradeJournalService tradeJournalService;

    @Transactional
    public ReviewVO create(CreateReviewDTO dto) {
        // 校验关联的 journal 是否存在
        reviewManager.validateLinkedJournals(dto.linkedJournalIds());

        ReviewNoteDO record = reviewNoteConverter.toDO(dto);
        reviewManager.insert(record);

        // 关联 journal 标记为 REVIEWED
        if (dto.linkedJournalIds() != null && !dto.linkedJournalIds().isEmpty()) {
            tradeJournalService.markAsReviewed(dto.linkedJournalIds());
        }

        log.info("Created review note: date={}, title={}", record.getReviewDate(), record.getTitle());
        return reviewNoteConverter.toVO(record);
    }

    public List<ReviewVO> list(LocalDate date, String symbol) {
        List<ReviewNoteDO> records = reviewManager.listByFilter(date, symbol);
        return reviewNoteConverter.toVOList(records);
    }

    public ReviewVO getById(Long id) {
        ReviewNoteDO record = reviewManager.getByIdOrThrow(id);
        return reviewNoteConverter.toVO(record);
    }

    @Transactional
    public ReviewVO update(Long id, UpdateReviewDTO dto) {
        ReviewNoteDO existing = reviewManager.getByIdOrThrow(id);

        // 校验新关联的 journal
        if (dto.linkedJournalIds() != null) {
            reviewManager.validateLinkedJournals(dto.linkedJournalIds());
        }

        reviewNoteConverter.updateDOFromDTO(dto, existing);
        existing.setId(id);
        reviewManager.updateById(existing);

        // 关联 journal 标记为 REVIEWED
        if (dto.linkedJournalIds() != null && !dto.linkedJournalIds().isEmpty()) {
            tradeJournalService.markAsReviewed(dto.linkedJournalIds());
        }

        log.info("Updated review note: id={}", id);
        return reviewNoteConverter.toVO(reviewManager.selectById(id));
    }

    public long countByDate(LocalDate date) {
        return reviewManager.countByDate(date);
    }

    /**
     * 物理删除复盘记录。
     */
    @Transactional
    public void delete(Long id) {
        reviewManager.deleteById(id);
        log.info("Deleted review note: id={}", id);
    }
}
