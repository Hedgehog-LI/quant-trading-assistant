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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 盘后复盘应用服务。
 * <p>
 * 负责事务边界和业务流程编排，核心校验和 DB 读写委托给 {@link ReviewManager}。
 * 复盘新增/编辑/删除后，对受影响交易记录（旧关联 ∪ 新关联）触发 reviewStatus 回算：
 * 仍被任意复盘引用 -> REVIEWED，不再被引用 -> PENDING。
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

        // 关联回算：新增关联的 journal 标记为 REVIEWED
        recalculateReviewStatus(dto.linkedJournalIds());

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

        // 旧关联：编辑时必须同时覆盖被移除的部分
        List<Long> oldIds = reviewManager.parseLinkedIds(existing.getLinkedJournalIds());

        // 校验新关联的 journal
        if (dto.linkedJournalIds() != null) {
            reviewManager.validateLinkedJournals(dto.linkedJournalIds());
        }

        reviewNoteConverter.updateDOFromDTO(dto, existing);
        existing.setId(id);
        reviewManager.updateById(existing);

        // 受影响 = 旧关联 ∪ 新关联
        Set<Long> affected = new HashSet<>();
        if (oldIds != null) {
            affected.addAll(oldIds);
        }
        if (dto.linkedJournalIds() != null) {
            affected.addAll(dto.linkedJournalIds());
        }
        recalculateReviewStatus(affected);

        log.info("Updated review note: id={}", id);
        return reviewNoteConverter.toVO(reviewManager.selectById(id));
    }

    public long countByDate(LocalDate date) {
        return reviewManager.countByDate(date);
    }

    /**
     * 物理删除复盘记录，并回算原关联交易记录的复盘状态。
     */
    @Transactional
    public void delete(Long id) {
        ReviewNoteDO existing = reviewManager.getByIdOrThrow(id);
        List<Long> affected = reviewManager.parseLinkedIds(existing.getLinkedJournalIds());
        reviewManager.deleteById(id);
        recalculateReviewStatus(affected);
        log.info("Deleted review note: id={}", id);
    }

    /**
     * 回算受影响交易记录的复盘状态。
     * <p>
     * 取最新"被任意复盘引用的 ID 集合"，受影响 ID 在集合中 -> REVIEWED，否则 -> PENDING。
     */
    private void recalculateReviewStatus(Collection<Long> affectedIds) {
        if (affectedIds == null || affectedIds.isEmpty()) {
            return;
        }
        Set<Long> referenced = reviewManager.collectReferencedJournalIds();
        tradeJournalService.recalculateReviewStatus(affectedIds, referenced);
    }
}
